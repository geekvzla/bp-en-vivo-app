package com.jhonnysga.bpenvivo

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.C
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Selector de calidad compartido (guía TV y reproductor a pantalla completa).
 *
 * SIEMPRE abre: si el stream ya reportó variantes usa las reales; si aún
 * está cargando o reconectando, ofrece las alturas estándar y la elección
 * queda guardada (se aplica en cuanto el video entra). Antes el diálogo
 * salía en silencio en ese caso y la tecla MENU parecía no funcionar.
 */
object QualityDialog {

    fun show(
        activity: Activity,
        trackSelector: DefaultTrackSelector?,
        onChosen: (() -> Unit)? = null
    ) {
        val real = collectHeights(trackSelector)
        val heights = if (real.isNotEmpty()) real else QualityStore.FALLBACK_HEIGHTS

        data class Opt(val height: Int, val label: String)

        val opts = mutableListOf(Opt(-1, activity.getString(R.string.quality_auto)))
        heights.sortedDescending().forEach { h -> opts.add(Opt(h, "${h}p")) }

        val cur = QualityStore.current(activity)
        val checked = opts.indexOfFirst {
            if (cur == "auto") it.height == -1 else it.height.toString() == cur
        }.coerceAtLeast(0)

        AlertDialog.Builder(activity, R.style.QualityDialogTheme)
            .setTitle(R.string.quality_title)
            .setSingleChoiceItems(opts.map { it.label }.toTypedArray(), checked) { dialog, which ->
                val opt = opts[which]
                val q = if (opt.height == -1) "auto" else opt.height.toString()
                QualityStore.save(activity, q)
                trackSelector?.let { ts ->
                    ts.setParameters(QualityStore.apply(ts.buildUponParameters(), q).build())
                }
                onChosen?.invoke()
                dialog.dismiss()
            }
            .show()
    }

    /** Alturas reales del stream según el selector de pistas (vacío si no hay). */
    private fun collectHeights(trackSelector: DefaultTrackSelector?): List<Int> {
        val mapped = trackSelector?.currentMappedTrackInfo ?: return emptyList()
        val seen = mutableSetOf<Int>()
        for (r in 0 until mapped.rendererCount) {
            if (mapped.getRendererType(r) != C.TRACK_TYPE_VIDEO) continue
            val groups = mapped.getTrackGroups(r)
            for (g in 0 until groups.length) {
                val group = groups.get(g)
                for (t in 0 until group.length) {
                    val h = group.getFormat(t).height
                    if (h > 0) seen.add(h)
                }
            }
        }
        return seen.toList()
    }
}
