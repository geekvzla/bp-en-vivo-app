package com.jhonnysga.bpenvivo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Guía estilo YouTube TV: canal a la izquierda, programa + hora + progreso.
 * Los items son focusables para D-pad en TV.
 *
 * Callbacks:
 *  - onClick: pulsación/OK sobre el ítem.
 *  - onFocus: el ítem ganó el foco del D-pad (TV lo usa para el preview).
 *  - onLongClick: pulsación larga (TV lo usa para abrir la calidad).
 */
class StreamAdapter(
    private val onClick: (StreamItem) -> Unit,
    private val onFocus: (StreamItem) -> Unit = {},
    private val onLongClick: (StreamItem) -> Unit = {}
) : RecyclerView.Adapter<StreamAdapter.VH>() {

    private val items = mutableListOf<StreamItem>()

    fun submit(newItems: List<StreamItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun getItem(position: Int): StreamItem? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_guide, parent, false)
        return VH(v, onClick, onFocus, onLongClick)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    class VH(
        view: View,
        private val onClick: (StreamItem) -> Unit,
        private val onFocus: (StreamItem) -> Unit,
        private val onLongClick: (StreamItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val channel: TextView = view.findViewById(R.id.guide_channel)
        private val program: TextView = view.findViewById(R.id.guide_program)
        private val time: TextView = view.findViewById(R.id.guide_time)
        private val progress: ProgressBar = view.findViewById(R.id.guide_progress)
        private val liveBadge: TextView = view.findViewById(R.id.guide_live)
        private var item: StreamItem? = null

        init {
            view.isFocusable = true
            view.isClickable = true
            view.setOnClickListener { item?.let(onClick) }
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) item?.let(onFocus)
            }
            view.setOnLongClickListener {
                item?.let(onLongClick)
                true
            }
        }

        fun bind(s: StreamItem) {
            item = s
            // Canal: nombre del canal o título limpio
            channel.text = s.channel?.takeIf { it.isNotBlank() } ?: s.title
            // Programa: lo que transmite (del ProgramMapper) o título
            program.text = s.programa?.takeIf { it.isNotBlank() } ?: s.title
            // Hora: por ahora genérico; el progreso es ilustrativo
            time.text = "En vivo"
            // Progreso: sin datos reales, mostramos barra parcial
            progress.progress = 30
            liveBadge.visibility = View.VISIBLE
        }
    }
}
