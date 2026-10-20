package com.jhonnysga.bpenvivo.tv

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jhonnysga.bpenvivo.DailymotionClient
import com.jhonnysga.bpenvivo.MainActivity.Companion.EXTRA_CHANNEL_IDS
import com.jhonnysga.bpenvivo.MainActivity.Companion.EXTRA_CHANNEL_INDEX
import com.jhonnysga.bpenvivo.MainActivity.Companion.EXTRA_CHANNEL_TITLES
import com.jhonnysga.bpenvivo.MainActivity.Companion.EXTRA_SOURCE
import com.jhonnysga.bpenvivo.MainActivity.Companion.EXTRA_STREAM_ID
import com.jhonnysga.bpenvivo.MainActivity.Companion.EXTRA_STREAM_TITLE
import com.jhonnysga.bpenvivo.MainActivity.Source
import com.jhonnysga.bpenvivo.PlayerActivity
import com.jhonnysga.bpenvivo.QualityDialog
import com.jhonnysga.bpenvivo.QualityStore
import com.jhonnysga.bpenvivo.R
import com.jhonnysga.bpenvivo.StreamAdapter
import com.jhonnysga.bpenvivo.StreamItem
import com.jhonnysga.bpenvivo.ThundernetClient

/**
 * TV split-screen: guía a la izquierda, preview a la derecha.
 *
 * Navegación (sin botones en pantalla):
 *  - D-pad arriba/abajo recorre la guía; el canal enfocado se resalta y
 *    el preview cambia a ese canal (con una pausa breve al pasar rápido).
 *  - OK una vez sobre un canal lo carga en el preview; OK otra vez sobre
 *    el mismo canal lo maximiza a pantalla completa.
 *  - MENU (☰) u OK sostenido abre el selector de calidad.
 *  - ⏪ (rebobinar) recarga toda la lista; también se recarga sola si
 *    al volver a la guía pasaron más de 10 minutos.
 */
class TvSplitActivity : AppCompatActivity() {

    companion object {
        /** Pausa tras enfocar un canal antes de cambiar el preview. */
        private const val PREVIEW_DEBOUNCE_MS = 350L
        /** Antigüedad máxima de la lista antes de recargarla al volver. */
        private const val RELOAD_AFTER_MS = 10 * 60 * 1000L
    }

    private lateinit var guideList: RecyclerView
    private lateinit var guideStatus: TextView
    private lateinit var previewView: PlayerView
    private lateinit var previewChannel: TextView
    private lateinit var previewProgram: TextView

    private val adapter = StreamAdapter(
        onClick = { onChannelClick(it) },
        onFocus = { onChannelFocused(it) },
        onLongClick = { showQualityDialog() }
    )
    private var items: List<StreamItem> = emptyList()
    private var previewPlayer: ExoPlayer? = null
    private var previewSelector: DefaultTrackSelector? = null
    private var currentItem: StreamItem? = null
    private var lastLoadMs: Long = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPreview: Runnable? = null

    private val isTrueno: Boolean
        get() = com.jhonnysga.bpenvivo.BuildConfig.FLAVOR == "truenotv"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_split)

        guideList = findViewById(R.id.tv_guide_list)
        guideStatus = findViewById(R.id.tv_guide_status)
        previewView = findViewById(R.id.tv_preview_player)
        previewChannel = findViewById(R.id.tv_preview_channel)
        previewProgram = findViewById(R.id.tv_preview_program)

        guideList.layoutManager = LinearLayoutManager(this)
        guideList.adapter = adapter

        load()
    }

    private fun load() {
        Thread {
            val result = runCatching {
                if (!isTrueno) {
                    DailymotionClient.fetchLive()
                } else if (ThundernetClient.isLoggedIn()) {
                    ThundernetClient.getChannels()
                } else emptyList()
            }
            runOnUiThread {
                lastLoadMs = System.currentTimeMillis()
                val loaded = result.getOrDefault(emptyList())
                items = loaded
                adapter.submit(loaded)
                if (loaded.isEmpty()) {
                    guideStatus.text = getString(R.string.tv_guide_empty)
                    guideStatus.visibility = View.VISIBLE
                    return@runOnUiThread
                }
                guideStatus.visibility = View.GONE
                // Mantener el canal del preview si sigue en la lista; si no, el primero.
                val target = loaded.firstOrNull { it.id == currentItem?.id } ?: loaded.first()
                previewChannel(target)
                focusItem(loaded.indexOf(target).coerceAtLeast(0))
            }
        }.start()
    }

    /** Pone el foco del D-pad en la posición indicada de la guía. */
    private fun focusItem(position: Int) {
        guideList.post {
            val lm = guideList.layoutManager as? LinearLayoutManager ?: return@post
            lm.findViewByPosition(position)?.requestFocus() ?: run {
                guideList.scrollToPosition(position)
                guideList.post { lm.findViewByPosition(position)?.requestFocus() }
            }
        }
    }

    /** Al enfocar un canal con el D-pad, el preview lo sigue (con pausa). */
    private fun onChannelFocused(item: StreamItem) {
        if (item.id == currentItem?.id) return
        pendingPreview?.let { handler.removeCallbacks(it) }
        val r = Runnable {
            if (item.id != currentItem?.id) previewChannel(item)
        }
        pendingPreview = r
        handler.postDelayed(r, PREVIEW_DEBOUNCE_MS)
    }

    /** OK: si el canal ya está en el preview, maximiza; si no, lo previsualiza. */
    private fun onChannelClick(item: StreamItem) {
        if (item.id == currentItem?.id) {
            openFullscreen(item)
        } else {
            pendingPreview?.let { handler.removeCallbacks(it) }
            previewChannel(item)
        }
    }

    /** Muestra el canal en el preview de la derecha. */
    private fun previewChannel(item: StreamItem) {
        currentItem = item
        val chan = item.channel?.takeIf { it.isNotBlank() } ?: ""
        val prog = item.programa?.takeIf { it.isNotBlank() } ?: item.title
        previewChannel.text = chan
        previewProgram.text = prog

        previewPlayer?.release()
        // Selector con la calidad guardada: la misma que usa el fullscreen.
        previewSelector = DefaultTrackSelector(this).apply {
            setParameters(
                QualityStore.apply(buildUponParameters(), QualityStore.current(this@TvSplitActivity))
                    .build()
            )
        }
        previewPlayer = ExoPlayer.Builder(this)
            .setTrackSelector(previewSelector!!)
            .build().apply {
                previewView.player = this
            }
        Thread {
            val url = runCatching {
                if (!isTrueno) DailymotionClient.extractStreamUrl(item.id)
                else ThundernetClient.getChannelStream(item.id).url
            }.getOrNull() ?: return@Thread
            runOnUiThread {
                val exo = previewPlayer ?: return@runOnUiThread
                exo.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                exo.prepare()
                exo.play()
            }
        }.start()
    }

    private fun showQualityDialog() {
        QualityDialog.show(this, previewSelector)
    }

    private fun openFullscreen(item: StreamItem) {
        val ids = ArrayList(items.map { it.id })
        val titles = ArrayList(items.map { it.title })
        val index = items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        startActivity(
            Intent(this, PlayerActivity::class.java).apply {
                putExtra(EXTRA_STREAM_ID, item.id)
                putExtra(EXTRA_STREAM_TITLE, item.title)
                putExtra(EXTRA_SOURCE, if (isTrueno) Source.THUNDERNET.name else Source.BEISBOLPLAY.name)
                putStringArrayListExtra(EXTRA_CHANNEL_IDS, ids)
                putStringArrayListExtra(EXTRA_CHANNEL_TITLES, titles)
                putExtra(EXTRA_CHANNEL_INDEX, index)
            }
        )
    }

    // MENU (☰) = calidad; ⏪ = recargar la lista completa.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MENU -> {
                showQualityDialog()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                load()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        // Al volver del fullscreen el preview quedó liberado: reactivarlo.
        // Si la lista envejeció (>10 min), recargarla completa.
        if (items.isNotEmpty() &&
            System.currentTimeMillis() - lastLoadMs > RELOAD_AFTER_MS
        ) {
            load()
        } else if (previewPlayer == null) {
            currentItem?.let { previewChannel(it) }
        }
    }

    override fun onStop() {
        super.onStop()
        pendingPreview?.let { handler.removeCallbacks(it) }
        pendingPreview = null
        previewPlayer?.release()
        previewPlayer = null
        previewSelector = null
    }
}
