package com.jhonnysga.bpenvivo.tv

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.media3.exoplayer.ExoPlayer
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
import com.jhonnysga.bpenvivo.R
import com.jhonnysga.bpenvivo.StreamAdapter
import com.jhonnysga.bpenvivo.StreamItem
import com.jhonnysga.bpenvivo.ThundernetClient

/**
 * TV split-screen: guía a la izquierda, preview a la derecha.
 * D-pad arriba/abajo navega la guía y actualiza el preview.
 * OK en un canal lo maximiza a pantalla completa.
 */
class TvSplitActivity : FragmentActivity() {

    private lateinit var guideList: RecyclerView
    private lateinit var previewView: PlayerView
    private lateinit var previewChannel: TextView
    private lateinit var previewProgram: TextView
    private lateinit var maximizeBtn: Button

    private val adapter = StreamAdapter { openFullscreen(it) }
    private var items: List<StreamItem> = emptyList()
    private var previewPlayer: ExoPlayer? = null
    private var currentItem: StreamItem? = null

    private val isTrueno: Boolean
        get() = com.jhonnysga.bpenvivo.BuildConfig.FLAVOR == "truenotv"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tv_split)

        guideList = findViewById(R.id.tv_guide_list)
        previewView = findViewById(R.id.tv_preview_player)
        previewChannel = findViewById(R.id.tv_preview_channel)
        previewProgram = findViewById(R.id.tv_preview_program)
        maximizeBtn = findViewById(R.id.tv_preview_maximize)

        guideList.layoutManager = LinearLayoutManager(this)
        guideList.adapter = adapter

        maximizeBtn.setOnClickListener {
            currentItem?.let { openFullscreen(it) }
        }

        // Al enfocar un item de la guía, actualizar el preview
        // (el click también abre fullscreen vía el adapter)
        load()
    }

    private fun load() {
        Thread {
            items = if (!isTrueno) {
                runCatching { DailymotionClient.fetchLive() }.getOrDefault(emptyList())
            } else if (ThundernetClient.isLoggedIn()) {
                runCatching { ThundernetClient.getChannels() }.getOrDefault(emptyList())
            } else emptyList()
            runOnUiThread {
                adapter.submit(items)
                // Preview del primero automáticamente
                items.firstOrNull()?.let { previewChannel(it) }
            }
        }.start()
    }

    /** Muestra el canal en el preview de la derecha. */
    private fun previewChannel(item: StreamItem) {
        currentItem = item
        val chan = item.channel?.takeIf { it.isNotBlank() } ?: ""
        val prog = item.programa?.takeIf { it.isNotBlank() } ?: item.title
        previewChannel.text = chan
        previewProgram.text = prog

        previewPlayer?.release()
        previewPlayer = ExoPlayer.Builder(this).build().apply {
            volume = 0f
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

    override fun onStop() {
        super.onStop()
        previewPlayer?.release()
        previewPlayer = null
    }
}
