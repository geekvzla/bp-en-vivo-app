package com.jhonnysga.bpenvivo

/**
 * Mapeo de ID de video de Dailymotion → lo que está transmitiendo.
 * Los títulos de Dailymotion vienen ofuscados ("protection..."), así que
 * este mapeo se genera por análisis de miniaturas y se actualiza por jornada.
 *
 * Actualizado: 17/10/2026 (jornada LVBP sábado).
 */
object ProgramMapper {

    /** Video ID → "Equipo A vs Equipo B" o descripción del contenido. */
    private val PROGRAMAS = mapOf(
        // Navegantes vs Cardenales (4 PM) — estadio Barquisimeto en vivo
        "x97q86o" to "Navegantes vs Cardenales",
        // Juegos 6 PM en cuenta regresiva
        "x97q8a4" to "Por comenzar (6 PM)",
        "x97q84c" to "Por comenzar (6 PM)",
        "x97q88s" to "Por comenzar (6 PM)",
        // No son LVBP en vivo
        "x97q8ci" to "Estudio BeisbolPlay",
        "x97q85s" to "Análisis MLB",
        "x9s77t2" to "MLB en vivo",
        "x97q89k" to "Liga Mexicana",
        "x97q87u" to "Reposicion LVBP 2026"
    )

    /** Devuelve lo que transmite el video, o null si no se conoce. */
    fun programaPara(videoId: String): String? = PROGRAMAS[videoId]
}
