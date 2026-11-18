package com.example.tracking

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import com.example.data.model.LapSplit
import java.util.Locale
import kotlin.math.roundToInt

interface SplitFeedbackListener {
    fun announceSplit(split: LapSplit, cumulativeDistanceMeters: Double)
    fun shutdown()
}

class SplitFeedbackEngine(context: Context) : SplitFeedbackListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    @Volatile
    private var isTtsReady = false

    init {
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val engine = tts ?: return@TextToSpeech
                    val langResult = engine.setLanguage(Locale.US)
                    isTtsReady = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED
                }
            }
        } catch (_: Exception) {
            isTtsReady = false
        }
    }

    override fun announceSplit(split: LapSplit, cumulativeDistanceMeters: Double) {
        triggerHapticSplitCue()
        speakSplitAnnouncement(split, cumulativeDistanceMeters)
    }

    private fun triggerHapticSplitCue() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(180L, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(180L)
            }
        } catch (_: Exception) {
            // Safe no-op on devices without vibrator hardware
        }
    }

    private fun speakSplitAnnouncement(split: LapSplit, cumulativeDistanceMeters: Double) {
        if (!isTtsReady) return
        val utterance = formatSplitUtterance(split, cumulativeDistanceMeters)
        try {
            tts?.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, "pacely_split_${split.lapNumber}")
        } catch (_: Exception) {
            // Ignore TTS transient failures
        }
    }

    override fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
    }

    companion object {
        fun formatKmForSpeech(distanceMeters: Double): String {
            val km = (distanceMeters / 1000.0).coerceAtLeast(0.0)
            val rounded2 = (km * 100.0).roundToInt() / 100.0
            val whole = rounded2.roundToInt()
            return if (kotlin.math.abs(rounded2 - whole) < 0.005) {
                whole.toString()
            } else {
                String.format(Locale.US, "%.2f", rounded2).trimEnd('0').trimEnd('.')
            }
        }

        fun formatSplitUtterance(split: LapSplit, cumulativeDistanceMeters: Double): String {
            val totalSeconds = split.paceSecPerKm.roundToInt().coerceAtLeast(1)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val minWord = if (minutes == 1) "minute" else "minutes"
            val secWord = if (seconds == 1) "second" else "seconds"
            val kmText = formatKmForSpeech(cumulativeDistanceMeters)
            return "$kmText kilometers completed, split pace $minutes $minWord $seconds $secWord"
        }
    }
}
