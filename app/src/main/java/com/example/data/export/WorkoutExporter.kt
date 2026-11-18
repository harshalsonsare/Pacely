package com.example.data.export

import com.example.data.local.WorkoutEntity
import com.example.data.model.WorkoutWithDetails
import com.example.tracking.GeoMath
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WorkoutExporter {

    private fun isoUtc(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    fun toJson(workouts: List<WorkoutWithDetails>): String {
        val root = JSONObject()
        root.put("app", "Pacely")
        root.put("exportedAt", isoUtc(System.currentTimeMillis()))
        root.put("workoutCount", workouts.size)

        val workoutArray = JSONArray()
        workouts.forEach { details ->
            val w = details.workout
            val obj = JSONObject().apply {
                put("id", w.id)
                put("title", w.title)
                put("activityType", w.activityType)
                put("startTime", isoUtc(w.startTimeMillis))
                put("endTime", isoUtc(w.endTimeMillis))
                put("startTimeMillis", w.startTimeMillis)
                put("endTimeMillis", w.endTimeMillis)
                put("elapsedSeconds", w.elapsedMillis / 1000.0)
                put("distanceMeters", w.distanceMeters)
                put("avgPaceSecPerKm", w.avgPaceSecPerKm)
                put("caloriesBurned", w.caloriesBurned)
                put("elevationGainMeters", w.elevationGainMeters)
                put("elevationLossMeters", w.elevationLossMeters)
            }

            val splitsArray = JSONArray()
            details.splits.forEach { split ->
                splitsArray.put(
                    JSONObject().apply {
                        put("lapNumber", split.lapNumber)
                        put("distanceMeters", split.distanceMeters)
                        put("durationSeconds", split.durationMillis / 1000.0)
                        put("paceSecPerKm", split.paceSecPerKm)
                        put("elevationDeltaMeters", split.elevationDeltaMeters)
                    }
                )
            }
            obj.put("splits", splitsArray)

            val pointsArray = JSONArray()
            details.trackPoints.forEach { pt ->
                pointsArray.put(
                    JSONObject().apply {
                        put("lat", pt.latitude)
                        put("lon", pt.longitude)
                        put("ele", pt.altitudeMeters)
                        put("time", isoUtc(pt.timestampMillis))
                        put("speedMps", pt.speedMps.toDouble())
                        put("segment", pt.segmentIndex)
                    }
                )
            }
            obj.put("trackPoints", pointsArray)
            workoutArray.put(obj)
        }
        root.put("workouts", workoutArray)
        return root.toString(2)
    }

    fun toCsv(workouts: List<WorkoutEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("id,title,activity_type,start_time_utc,duration_hhmmss,duration_sec,distance_km,avg_pace_min_per_km,calories_kcal,elevation_gain_m,elevation_loss_m")
        workouts.forEach { w ->
            val escapedTitle = "\"${w.title.replace("\"", "\"\"")}\""
            val durationSec = String.format(Locale.US, "%.1f", w.elapsedMillis / 1000.0)
            val distKm = String.format(Locale.US, "%.3f", w.distanceMeters / 1000.0)
            val pace = GeoMath.formatPace(w.avgPaceSecPerKm)
            val gain = String.format(Locale.US, "%.1f", w.elevationGainMeters)
            val loss = String.format(Locale.US, "%.1f", w.elevationLossMeters)
            sb.appendLine(
                "${w.id},$escapedTitle,${w.activityType},${isoUtc(w.startTimeMillis)},${GeoMath.formatElapsedTime(w.elapsedMillis)},$durationSec,$distKm,$pace,${w.caloriesBurned},$gain,$loss"
            )
        }
        return sb.toString()
    }

    fun toGpx(details: WorkoutWithDetails): String {
        val w = details.workout
        val safeTitle = escapeXml(w.title)
        val gpxType = if (w.activityType.equals("WALK", ignoreCase = true)) "walking" else "running"
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine(
            """<gpx version="1.1" creator="Pacely Android" xmlns="http://www.topografix.com/GPX/1/1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">"""
        )
        sb.appendLine("  <metadata>")
        sb.appendLine("    <name>$safeTitle</name>")
        sb.appendLine("    <time>${isoUtc(w.startTimeMillis)}</time>")
        sb.appendLine("  </metadata>")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>$safeTitle</name>")
        sb.appendLine("    <type>$gpxType</type>")

        val groupedSegments = details.trackPoints.groupBy { it.segmentIndex }.toSortedMap()
        if (groupedSegments.isEmpty()) {
            sb.appendLine("    <trkseg />")
        } else {
            groupedSegments.values.forEach { segmentPoints ->
                sb.appendLine("    <trkseg>")
                segmentPoints.forEach { pt ->
                    sb.appendLine(
                        String.format(
                            Locale.US,
                            """      <trkpt lat="%.7f" lon="%.7f">""",
                            pt.latitude,
                            pt.longitude
                        )
                    )
                    sb.appendLine(String.format(Locale.US, "        <ele>%.2f</ele>", pt.altitudeMeters))
                    sb.appendLine("        <time>${isoUtc(pt.timestampMillis)}</time>")
                    sb.appendLine("      </trkpt>")
                }
                sb.appendLine("    </trkseg>")
            }
        }

        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private fun escapeXml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
