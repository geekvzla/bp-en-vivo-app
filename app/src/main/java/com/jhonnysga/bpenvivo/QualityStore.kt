package com.jhonnysga.bpenvivo

import android.content.Context
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Preferencia de calidad de video compartida por la guía TV, el preview
 * móvil y el reproductor: una sola elección ("auto" o altura en píxeles)
 * que se guarda una vez y aplica en todas las pantallas.
 */
object QualityStore {

    private const val PREFS = "bplay_prefs"
    private const val KEY_QUALITY = "video_quality"

    /** Alturas ofrecidas cuando el stream aún no reporta sus variantes. */
    val FALLBACK_HEIGHTS = listOf(1080, 720, 480, 360)

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QUALITY, "auto") ?: "auto"

    fun save(context: Context, quality: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_QUALITY, quality).apply()
    }

    /** Aplica la calidad a los parámetros: auto = sin restricción. */
    fun apply(
        params: DefaultTrackSelector.Parameters.Builder,
        quality: String
    ): DefaultTrackSelector.Parameters.Builder {
        val h = quality.toIntOrNull() ?: 0
        return if (h > 0) {
            params.setMaxVideoSize(Int.MAX_VALUE, h).setMinVideoSize(0, h)
        } else {
            params.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE).setMinVideoSize(0, 0)
        }
    }
}
