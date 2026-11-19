package com.example.data.export

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.example.data.local.LapSplitEntity
import com.example.data.local.TrackPointEntity
import com.example.data.local.WorkoutEntity
import com.example.data.model.ActivityType
import com.example.data.model.GeoPoint
import com.example.tracking.GeoMath
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.roundToInt

data class ImportedWorkoutData(
    val workout: WorkoutEntity,
    val trackPoints: List<TrackPointEntity>,
    val splits: List<LapSplitEntity>
)

data class ImportSummary(
    val importedCount: Int,
    val skippedDuplicatesCount: Int
) {
    fun toUserMessage(): String {
        val workoutWord = if (importedCount == 1) "workout" else "workouts"
        return if (skippedDuplicatesCount > 0) {
            val dupWord = if (skippedDuplicatesCount == 1) "duplicate" else "duplicates"
            "Imported $importedCount new $workoutWord ($skippedDuplicatesCount $dupWord skipped)"
        } else {
            "Imported $importedCount new $workoutWord"
        }
    }
}

object WorkoutImporter {

    private const val MAX_IMPORT_BYTES = 25 * 1024 * 1024 // 25 MB compressed/raw limit
    private const val MAX_UNCOMPRESSED_ZIP_BYTES = 50 * 1024 * 1024 // 50 MB zip extraction cap

    fun parseFromUri(
        contentResolver: ContentResolver,
        uri: Uri,
        defaultWeightKg: Float
    ): List<ImportedWorkoutData> {
        val fileName = queryDisplayName(contentResolver, uri)
        val bytes = contentResolver.openInputStream(uri)?.use { stream ->
            val raw = stream.readBytes()
            if (raw.size > MAX_IMPORT_BYTES) {
                throw IllegalArgumentException("File is too large to import (max 25 MB).")
            }
            raw
        } ?: throw IllegalArgumentException("Unable to read the selected file.")

        return parseRawBytes(bytes, fileName, defaultWeightKg)
    }

    fun parseRawBytes(
        bytes: ByteArray,
        fileName: String? = null,
        defaultWeightKg: Float = 70f
    ): List<ImportedWorkoutData> {
        if (bytes.isEmpty()) {
            throw IllegalArgumentException("The selected file is empty.")
        }
        val lowerName = fileName?.lowercase(Locale.US).orEmpty()
        if (isZipHeader(bytes) || lowerName.endsWith(".zip")) {
            return parseZipBytes(bytes, defaultWeightKg)
        }
        return parseRawContent(bytes.toString(Charsets.UTF_8), fileName, defaultWeightKg)
    }

    private fun isZipHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false
        return bytes[0] == 0x50.toByte() &&
            bytes[1] == 0x4B.toByte() &&
            ((bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) ||
                (bytes[2] == 0x05.toByte() && bytes[3] == 0x06.toByte()))
    }

    fun parseZipBytes(
        zipBytes: ByteArray,
        defaultWeightKg: Float = 70f
    ): List<ImportedWorkoutData> {
        val jsonEntries = mutableListOf<Pair<String, String>>()
        val gpxEntries = mutableListOf<Pair<String, String>>()
        var totalExtractedBytes = 0

        try {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.orEmpty()
                    val simpleName = entryName.substringAfterLast('/')
                    if (!entry.isDirectory && !entryName.startsWith("__MACOSX/") && !simpleName.startsWith(".")) {
                        val lower = simpleName.lowercase(Locale.US)
                        if (lower.endsWith(".json") || lower.endsWith(".gpx")) {
                            val out = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zis.read(buffer)
                                if (read <= 0) break
                                totalExtractedBytes += read
                                if (totalExtractedBytes > MAX_UNCOMPRESSED_ZIP_BYTES) {
                                    throw IllegalArgumentException("ZIP archive exceeds 50 MB uncompressed safety limit.")
                                }
                                out.write(buffer, 0, read)
                            }
                            val text = out.toByteArray().toString(Charsets.UTF_8)
                            if (lower.endsWith(".json")) {
                                jsonEntries.add(simpleName to text)
                            } else {
                                gpxEntries.add(simpleName to text)
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (_: Exception) {
            throw IllegalArgumentException("Corrupted ZIP archive: unable to extract workout files.")
        }

        if (jsonEntries.isEmpty() && gpxEntries.isEmpty()) {
            throw IllegalArgumentException("ZIP archive contains no .json or .gpx workout files.")
        }

        val combined = mutableListOf<ImportedWorkoutData>()
        val coveredStartSeconds = mutableSetOf<Long>()

        // Prefer JSON entries first since they carry rich metadata, splits, and stats
        jsonEntries.forEach { (_, text) ->
            val parsed = parseBackupJson(text, defaultWeightKg)
            parsed.forEach { item ->
                val sec = item.workout.startTimeMillis / 1000L
                val alreadyInBundle = coveredStartSeconds.any { abs(it - sec) <= 2L }
                if (!alreadyInBundle) {
                    coveredStartSeconds.add(sec)
                    combined.add(item)
                }
            }
        }

        // Import any GPX files not already covered by JSON in the same ZIP bundle
        gpxEntries.forEach { (name, text) ->
            val parsed = parseGpx(text, name.substringBeforeLast('.'), defaultWeightKg)
            parsed.forEach { item ->
                val sec = item.workout.startTimeMillis / 1000L
                val alreadyInBundle = coveredStartSeconds.any { abs(it - sec) <= 2L }
                if (!alreadyInBundle) {
                    coveredStartSeconds.add(sec)
                    combined.add(item)
                }
            }
        }

        if (combined.isEmpty()) {
            throw IllegalArgumentException("ZIP archive contained no importable workouts.")
        }
        return combined
    }

    fun parseRawContent(
        rawText: String,
        fileName: String? = null,
        defaultWeightKg: Float = 70f
    ): List<ImportedWorkoutData> {
        val trimmed = rawText.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("The selected file is empty.")
        }

        val lowerName = fileName?.lowercase(Locale.US).orEmpty()
        return when {
            trimmed.startsWith("{") || trimmed.startsWith("[") || lowerName.endsWith(".json") ->
                parseBackupJson(trimmed, defaultWeightKg)
            trimmed.startsWith("<") || lowerName.endsWith(".gpx") ->
                parseGpx(trimmed, fileName?.substringBeforeLast('.'), defaultWeightKg)
            else ->
                throw IllegalArgumentException("Unsupported file format. Please select a .json, .gpx, or .zip backup file.")
        }
    }

    fun parseBackupJson(
        rawJson: String,
        defaultWeightKg: Float = 70f
    ): List<ImportedWorkoutData> {
        val root = try {
            JSONObject(rawJson)
        } catch (_: Exception) {
            throw IllegalArgumentException("Corrupted JSON file: unable to parse workout backup.")
        }

        val workoutsArray = when {
            root.has("workouts") -> root.optJSONArray("workouts")
                ?: throw IllegalArgumentException("Invalid backup JSON: 'workouts' is not an array.")
            root.has("workout") && root.optJSONObject("workout") != null ->
                JSONArray().put(root.getJSONObject("workout"))
            root.has("startTimeMillis") || root.has("startTime") ->
                JSONArray().put(root)
            else ->
                throw IllegalArgumentException("Invalid backup JSON: missing 'workouts' array.")
        }

        if (workoutsArray.length() == 0) {
            throw IllegalArgumentException("Backup file contains 0 workouts.")
        }

        val results = mutableListOf<ImportedWorkoutData>()
        for (i in 0 until workoutsArray.length()) {
            val obj = workoutsArray.optJSONObject(i)
                ?: throw IllegalArgumentException("Corrupted workout entry at index $i.")

            val activityType = ActivityType.fromString(obj.optString("activityType", "RUN"))
            val startMillis = parseStartMillis(obj)
                ?: throw IllegalArgumentException("Workout at index $i is missing a valid start timestamp.")

            val rawPoints = obj.optJSONArray("trackPoints")
            val parsedPoints = parseJsonTrackPoints(rawPoints, startMillis)
            val parsedSplits = parseJsonSplits(obj.optJSONArray("splits"))

            val endMillis = obj.optLong("endTimeMillis", -1L).takeIf { it > 0L }
                ?: parseIsoMillis(obj.optString("endTime", ""))
                ?: parsedPoints.lastOrNull()?.timestampMillis
                ?: (startMillis + (obj.optDouble("elapsedSeconds", 0.0) * 1000.0).toLong())

            val elapsedMillis = (obj.optDouble("elapsedSeconds", -1.0)
                .takeIf { it >= 0.0 }?.times(1000.0)?.toLong())
                ?: (endMillis - startMillis).coerceAtLeast(0L)

            val computedFromPoints = if (parsedPoints.isNotEmpty()) {
                computeMetricsFromTrackPoints(parsedPoints, activityType, defaultWeightKg)
            } else {
                null
            }

            val distanceMeters = obj.optDouble("distanceMeters", Double.NaN)
                .takeIf { !it.isNaN() && it >= 0.0 }
                ?: computedFromPoints?.distanceMeters
                ?: 0.0

            val avgPaceSecPerKm = obj.optDouble("avgPaceSecPerKm", Double.NaN)
                .takeIf { !it.isNaN() && it >= 0.0 }
                ?: if (distanceMeters >= 5.0 && elapsedMillis > 0L) {
                    (elapsedMillis / 1000.0) / (distanceMeters / 1000.0)
                } else {
                    0.0
                }

            val calories = obj.optInt("caloriesBurned", -1)
                .takeIf { it >= 0 }
                ?: computedFromPoints?.caloriesBurned
                ?: 0

            val elevGain = obj.optDouble("elevationGainMeters", Double.NaN)
                .takeIf { !it.isNaN() && it >= 0.0 }
                ?: computedFromPoints?.elevationGainMeters
                ?: 0.0

            val elevLoss = obj.optDouble("elevationLossMeters", Double.NaN)
                .takeIf { !it.isNaN() && it >= 0.0 }
                ?: computedFromPoints?.elevationLossMeters
                ?: 0.0

            val title = obj.optString("title", "").trim()
                .ifEmpty { buildDefaultTitle(activityType, startMillis) }

            val finalPoints = computedFromPoints?.trackPoints ?: parsedPoints
            val finalSplits = if (parsedSplits.isNotEmpty()) parsedSplits else (computedFromPoints?.splits ?: emptyList())
            val preview = GeoMath.encodePreviewPoints(
                finalPoints.map {
                    GeoPoint(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        altitudeMeters = it.altitudeMeters,
                        timestampMillis = it.timestampMillis,
                        elapsedMillis = it.elapsedMillis,
                        speedMps = it.speedMps,
                        distanceFromStartMeters = it.distanceFromStartMeters,
                        segmentIndex = it.segmentIndex
                    )
                }
            )

            results.add(
                ImportedWorkoutData(
                    workout = WorkoutEntity(
                        id = 0L,
                        title = title,
                        activityType = activityType.name,
                        startTimeMillis = startMillis,
                        endTimeMillis = endMillis,
                        elapsedMillis = elapsedMillis,
                        distanceMeters = distanceMeters,
                        avgPaceSecPerKm = avgPaceSecPerKm,
                        caloriesBurned = calories,
                        elevationGainMeters = elevGain,
                        elevationLossMeters = elevLoss,
                        encodedPreviewPoints = preview
                    ),
                    trackPoints = finalPoints,
                    splits = finalSplits
                )
            )
        }
        return results
    }

    fun parseGpx(
        rawGpx: String,
        fallbackTitle: String? = null,
        defaultWeightKg: Float = 70f
    ): List<ImportedWorkoutData> {
        val parser = Xml.newPullParser()
        try {
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(rawGpx))
        } catch (_: Exception) {
            throw IllegalArgumentException("Corrupted GPX file: invalid XML header.")
        }

        var foundGpxRoot = false
        var metadataName: String? = null
        var metadataTimeMillis: Long? = null
        var trkName: String? = null
        var trkType: String? = null

        val rawTrackPoints = mutableListOf<RawGpxPoint>()
        var currentSegmentIndex = -1
        var insideMetadata = false
        var insideTrk = false
        var insideTrkPt = false
        var currentLat: Double? = null
        var currentLon: Double? = null
        var currentEle: Double = 0.0
        var currentTimeMillis: Long? = null
        val textBuffer = StringBuilder()

        try {
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name?.lowercase(Locale.US).orEmpty()
                        textBuffer.setLength(0)
                        when (tag) {
                            "gpx" -> foundGpxRoot = true
                            "metadata" -> insideMetadata = true
                            "trk" -> insideTrk = true
                            "trkseg" -> currentSegmentIndex += 1
                            "trkpt", "rtept" -> {
                                if (currentSegmentIndex < 0) currentSegmentIndex = 0
                                insideTrkPt = true
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                                currentEle = 0.0
                                currentTimeMillis = null
                            }
                        }
                    }

                    XmlPullParser.TEXT -> {
                        textBuffer.append(parser.text.orEmpty())
                    }

                    XmlPullParser.END_TAG -> {
                        val tag = parser.name?.lowercase(Locale.US).orEmpty()
                        val text = textBuffer.toString().trim()
                        when (tag) {
                            "metadata" -> insideMetadata = false
                            "trk" -> insideTrk = false
                            "name" -> {
                                if (insideTrk && trkName == null && text.isNotEmpty()) {
                                    trkName = text
                                } else if (insideMetadata && metadataName == null && text.isNotEmpty()) {
                                    metadataName = text
                                }
                            }
                            "type" -> {
                                if (insideTrk && trkType == null && text.isNotEmpty()) {
                                    trkType = text
                                }
                            }
                            "ele" -> {
                                if (insideTrkPt) {
                                    currentEle = text.toDoubleOrNull() ?: 0.0
                                }
                            }
                            "time" -> {
                                val parsedTs = parseIsoMillis(text)
                                if (insideTrkPt) {
                                    currentTimeMillis = parsedTs
                                } else if (insideMetadata && metadataTimeMillis == null) {
                                    metadataTimeMillis = parsedTs
                                }
                            }
                            "trkpt", "rtept" -> {
                                val lat = currentLat
                                val lon = currentLon
                                if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                                    rawTrackPoints.add(
                                        RawGpxPoint(
                                            latitude = lat,
                                            longitude = lon,
                                            altitudeMeters = currentEle,
                                            timestampMillis = currentTimeMillis,
                                            segmentIndex = currentSegmentIndex.coerceAtLeast(0)
                                        )
                                    )
                                } else {
                                    throw IllegalArgumentException("Invalid GPX trackpoint coordinates.")
                                }
                                insideTrkPt = false
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (_: Exception) {
            throw IllegalArgumentException("Corrupted GPX file: malformed XML structure.")
        }

        if (!foundGpxRoot) {
            throw IllegalArgumentException("Invalid GPX file: missing <gpx> root element.")
        }
        if (rawTrackPoints.isEmpty()) {
            throw IllegalArgumentException("GPX file contains no valid GPS track points.")
        }

        val activityType = if (trkType?.contains("walk", ignoreCase = true) == true ||
            (trkName ?: metadataName ?: fallbackTitle)?.contains("walk", ignoreCase = true) == true
        ) {
            ActivityType.WALK
        } else {
            ActivityType.RUN
        }

        val baseTime = rawTrackPoints.firstNotNullOfOrNull { it.timestampMillis }
            ?: metadataTimeMillis
            ?: System.currentTimeMillis()

        // Normalize missing per-point timestamps cleanly if a GPX omits <time> on some/all points
        val normalizedEntities = mutableListOf<TrackPointEntity>()
        var lastTimestamp = baseTime
        rawTrackPoints.forEachIndexed { idx, pt ->
            val ts = pt.timestampMillis ?: if (idx == 0) baseTime else (lastTimestamp + 2000L)
            lastTimestamp = ts.coerceAtLeast(lastTimestamp)
            normalizedEntities.add(
                TrackPointEntity(
                    id = 0L,
                    workoutId = 0L,
                    latitude = pt.latitude,
                    longitude = pt.longitude,
                    altitudeMeters = pt.altitudeMeters,
                    timestampMillis = lastTimestamp,
                    elapsedMillis = (lastTimestamp - baseTime).coerceAtLeast(0L),
                    speedMps = 0f,
                    distanceFromStartMeters = 0.0,
                    segmentIndex = pt.segmentIndex
                )
            )
        }

        val computed = computeMetricsFromTrackPoints(normalizedEntities, activityType, defaultWeightKg)
        val startTimeMillis = normalizedEntities.first().timestampMillis
        val endTimeMillis = normalizedEntities.last().timestampMillis
        val activeElapsedMillis = computeActiveElapsedMillis(normalizedEntities)

        val avgPace = if (computed.distanceMeters >= 5.0 && activeElapsedMillis > 0L) {
            (activeElapsedMillis / 1000.0) / (computed.distanceMeters / 1000.0)
        } else {
            0.0
        }

        val title = (trkName ?: metadataName ?: fallbackTitle)?.trim().orEmpty()
            .ifEmpty { buildDefaultTitle(activityType, startTimeMillis) }

        val preview = GeoMath.encodePreviewPoints(
            computed.trackPoints.map {
                GeoPoint(
                    latitude = it.latitude,
                    longitude = it.longitude,
                    altitudeMeters = it.altitudeMeters,
                    timestampMillis = it.timestampMillis,
                    elapsedMillis = it.elapsedMillis,
                    speedMps = it.speedMps,
                    distanceFromStartMeters = it.distanceFromStartMeters,
                    segmentIndex = it.segmentIndex
                )
            }
        )

        return listOf(
            ImportedWorkoutData(
                workout = WorkoutEntity(
                    id = 0L,
                    title = title,
                    activityType = activityType.name,
                    startTimeMillis = startTimeMillis,
                    endTimeMillis = endTimeMillis,
                    elapsedMillis = activeElapsedMillis,
                    distanceMeters = computed.distanceMeters,
                    avgPaceSecPerKm = avgPace,
                    caloriesBurned = computed.caloriesBurned,
                    elevationGainMeters = computed.elevationGainMeters,
                    elevationLossMeters = computed.elevationLossMeters,
                    encodedPreviewPoints = preview
                ),
                trackPoints = computed.trackPoints,
                splits = computed.splits
            )
        )
    }

    private data class RawGpxPoint(
        val latitude: Double,
        val longitude: Double,
        val altitudeMeters: Double,
        val timestampMillis: Long?,
        val segmentIndex: Int
    )

    private data class ComputedTrackStats(
        val trackPoints: List<TrackPointEntity>,
        val splits: List<LapSplitEntity>,
        val distanceMeters: Double,
        val elevationGainMeters: Double,
        val elevationLossMeters: Double,
        val caloriesBurned: Int
    )

    private fun computeMetricsFromTrackPoints(
        points: List<TrackPointEntity>,
        activityType: ActivityType,
        weightKg: Float
    ): ComputedTrackStats {
        if (points.isEmpty()) {
            return ComputedTrackStats(emptyList(), emptyList(), 0.0, 0.0, 0.0, 0)
        }

        val enriched = mutableListOf<TrackPointEntity>()
        val splits = mutableListOf<LapSplitEntity>()

        var totalDistance = 0.0
        var activeElapsed = 0L
        var elevGain = 0.0
        var elevLoss = 0.0
        var totalCalories = 0.0

        var refAlt: Double? = null
        var lastSplitElapsed = 0L
        var lastSplitDistance = 0.0
        var lastSplitAlt: Double? = null

        points.forEachIndexed { idx, pt ->
            if (idx == 0) {
                refAlt = pt.altitudeMeters
                lastSplitAlt = pt.altitudeMeters
                enriched.add(pt.copy(elapsedMillis = 0L, distanceFromStartMeters = 0.0))
            } else {
                val prev = points[idx - 1]
                val prevDist = totalDistance
                val prevActiveElapsed = activeElapsed
                var segDist = 0.0
                var dtMillis = 0L
                if (pt.segmentIndex == prev.segmentIndex) {
                    segDist = GeoMath.haversineMeters(
                        prev.latitude,
                        prev.longitude,
                        pt.latitude,
                        pt.longitude
                    )
                    dtMillis = (pt.timestampMillis - prev.timestampMillis).coerceAtLeast(0L)
                    activeElapsed += dtMillis
                    totalDistance += segDist
                }

                val speedMps = if (pt.speedMps > 0f) {
                    pt.speedMps
                } else if (dtMillis > 0L) {
                    (segDist / (dtMillis / 1000.0)).toFloat()
                } else {
                    0f
                }

                val currentRef = refAlt ?: pt.altitudeMeters
                val altDiff = pt.altitudeMeters - currentRef
                if (abs(altDiff) >= 3.0) {
                    if (altDiff > 0) elevGain += altDiff else elevLoss += abs(altDiff)
                    refAlt = pt.altitudeMeters
                }

                if (dtMillis > 0L && segDist > 0.0) {
                    val met = GeoMath.calculateDynamicMet(activityType, speedMps.toDouble())
                    totalCalories += met * weightKg * (dtMillis / 3_600_000.0)
                }

                var nextThreshold = (splits.size + 1) * 1000.0
                while (totalDistance >= nextThreshold) {
                    val fraction = if (segDist > 0.0) {
                        ((nextThreshold - prevDist) / segDist).coerceIn(0.0, 1.0)
                    } else {
                        1.0
                    }
                    val splitCrossElapsed = prevActiveElapsed + (dtMillis * fraction).toLong()
                    val splitDuration = (splitCrossElapsed - lastSplitElapsed).coerceAtLeast(1000L)
                    val splitPace = (splitDuration / 1000.0) / 1.0
                    val elevDelta = pt.altitudeMeters - (lastSplitAlt ?: pt.altitudeMeters)
                    splits.add(
                        LapSplitEntity(
                            workoutId = 0L,
                            lapNumber = splits.size + 1,
                            distanceMeters = 1000.0,
                            durationMillis = splitDuration,
                            paceSecPerKm = splitPace,
                            elevationDeltaMeters = elevDelta
                        )
                    )
                    lastSplitDistance = nextThreshold
                    lastSplitElapsed = splitCrossElapsed
                    lastSplitAlt = pt.altitudeMeters
                    nextThreshold = (splits.size + 1) * 1000.0
                }

                enriched.add(
                    pt.copy(
                        elapsedMillis = activeElapsed,
                        speedMps = speedMps,
                        distanceFromStartMeters = totalDistance
                    )
                )
            }
        }

        val remainingDist = (totalDistance - lastSplitDistance).coerceAtLeast(0.0)
        val remainingTime = (activeElapsed - lastSplitElapsed).coerceAtLeast(0L)
        if (remainingDist >= 20.0 && remainingTime > 0L) {
            val pace = (remainingTime / 1000.0) / (remainingDist / 1000.0)
            val lastAlt = enriched.last().altitudeMeters
            splits.add(
                LapSplitEntity(
                    workoutId = 0L,
                    lapNumber = splits.size + 1,
                    distanceMeters = remainingDist,
                    durationMillis = remainingTime,
                    paceSecPerKm = pace,
                    elevationDeltaMeters = lastAlt - (lastSplitAlt ?: lastAlt)
                )
            )
        }

        return ComputedTrackStats(
            trackPoints = enriched,
            splits = splits,
            distanceMeters = totalDistance,
            elevationGainMeters = elevGain,
            elevationLossMeters = elevLoss,
            caloriesBurned = totalCalories.roundToInt()
        )
    }

    private fun computeActiveElapsedMillis(points: List<TrackPointEntity>): Long {
        if (points.size < 2) return 0L
        var total = 0L
        for (i in 1 until points.size) {
            if (points[i].segmentIndex == points[i - 1].segmentIndex) {
                total += (points[i].timestampMillis - points[i - 1].timestampMillis).coerceAtLeast(0L)
            }
        }
        return total
    }

    private fun parseJsonTrackPoints(array: JSONArray?, fallbackStartMillis: Long): List<TrackPointEntity> {
        if (array == null) return emptyList()
        val points = mutableListOf<TrackPointEntity>()
        for (i in 0 until array.length()) {
            val ptObj = array.optJSONObject(i)
                ?: throw IllegalArgumentException("Invalid track point in JSON at index $i.")
            val lat = ptObj.optDouble("lat", Double.NaN)
            val lon = ptObj.optDouble("lon", Double.NaN)
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                throw IllegalArgumentException("Invalid GPS coordinates in JSON track point $i.")
            }
            val ele = ptObj.optDouble("ele", 0.0).takeIf { !it.isNaN() } ?: 0.0
            val ts = ptObj.optLong("timestampMillis", -1L).takeIf { it > 0L }
                ?: parseIsoMillis(ptObj.optString("time", ""))
                ?: (fallbackStartMillis + i * 2000L)
            val speed = ptObj.optDouble("speedMps", 0.0).toFloat()
            val segment = ptObj.optInt("segment", 0).coerceAtLeast(0)
            points.add(
                TrackPointEntity(
                    id = 0L,
                    workoutId = 0L,
                    latitude = lat,
                    longitude = lon,
                    altitudeMeters = ele,
                    timestampMillis = ts,
                    elapsedMillis = (ts - fallbackStartMillis).coerceAtLeast(0L),
                    speedMps = speed,
                    distanceFromStartMeters = 0.0,
                    segmentIndex = segment
                )
            )
        }
        return points.sortedBy { it.timestampMillis }
    }

    private fun parseJsonSplits(array: JSONArray?): List<LapSplitEntity> {
        if (array == null) return emptyList()
        val splits = mutableListOf<LapSplitEntity>()
        for (i in 0 until array.length()) {
            val sObj = array.optJSONObject(i) ?: continue
            val lapNum = sObj.optInt("lapNumber", i + 1)
            val dist = sObj.optDouble("distanceMeters", 0.0).coerceAtLeast(0.0)
            val durMillis = (sObj.optDouble("durationSeconds", 0.0) * 1000.0).toLong().coerceAtLeast(0L)
            val pace = sObj.optDouble("paceSecPerKm", 0.0).coerceAtLeast(0.0)
            val elevDelta = sObj.optDouble("elevationDeltaMeters", 0.0)
            splits.add(
                LapSplitEntity(
                    id = 0L,
                    workoutId = 0L,
                    lapNumber = lapNum,
                    distanceMeters = dist,
                    durationMillis = durMillis,
                    paceSecPerKm = pace,
                    elevationDeltaMeters = elevDelta
                )
            )
        }
        return splits.sortedBy { it.lapNumber }
    }

    private fun parseStartMillis(obj: JSONObject): Long? {
        val exact = obj.optLong("startTimeMillis", -1L)
        if (exact > 0L) return exact
        return parseIsoMillis(obj.optString("startTime", ""))
    }

    private fun parseIsoMillis(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next ISO-8601 format
            }
        }
        return null
    }

    private fun queryDisplayName(contentResolver: ContentResolver, uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) cursor.getString(idx) else null
                    } else null
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildDefaultTitle(activityType: ActivityType, timestampMillis: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val period = when (cal.get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Morning"
            in 12..16 -> "Afternoon"
            in 17..20 -> "Evening"
            else -> "Night"
        }
        return "$period ${activityType.label}"
    }
}
