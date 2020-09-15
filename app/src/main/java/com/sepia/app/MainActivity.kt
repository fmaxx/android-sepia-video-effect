package com.sepia.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.daasuu.gpuv.egl.filter.GlSepiaFilter
import com.daasuu.gpuv.player.GPUPlayerView
import com.daasuu.gpuv.player.PlayerScaleType
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import kotlinx.android.synthetic.main.activity_main.*
import timber.log.Timber
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private var player: SimpleExoPlayer? = null
    private var currentWindow = 0
    private val videoRequestCode = 1
    private var videoUri: Uri? = null
    private var gpuPlayerView: GPUPlayerView? = null

    // region Lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.plant(Timber.DebugTree())
        setContentView(R.layout.activity_main)
        choseButton.setOnClickListener {
            selectVideo()
        }
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (Util.SDK_INT < 24 || player == null) {
            initializePlayer()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != videoRequestCode || resultCode != RESULT_OK || data == null || data.data == null) return
        videoUri = data.data
    }
    // endregion

    private fun selectVideo() {
        val intent = Intent()
        intent.type = "video/*"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        startActivityForResult(Intent.createChooser(intent, "Select Video"), videoRequestCode)
    }

    private fun initializePlayer() {
        val mediaSource = buildMediaSource(videoUri)
        val player = SimpleExoPlayer.Builder(this).build()
        playerView.player = player
        player.playWhenReady = true
        player.addListener(object : Player.EventListener {
            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == ExoPlayer.STATE_READY) {
                    playerView.visibility = View.VISIBLE
                    player.videoFormat?.let {
                        var bottomMargin = 12
                        val scaleType = if (it.width > it.height) {
                            PlayerScaleType.RESIZE_FIT_WIDTH
                        } else {
                            bottomMargin = 120
                            PlayerScaleType.RESIZE_FIT_HEIGHT
                        }
                        gpuPlayerView?.setPlayerScaleType(scaleType)
                        val newLayout: RelativeLayout.LayoutParams =
                            effectsPlayerLayout.layoutParams as RelativeLayout.LayoutParams
                        newLayout.bottomMargin = dpToPixels(bottomMargin).roundToInt()
                        effectsPlayerLayout.layoutParams = newLayout
                        effectsPlayerLayout.requestLayout()
                        effectsPlayerLayout.visibility = View.VISIBLE
                    }
                }
            }
        })

        mediaSource?.let {
            player.seekTo(currentWindow, 0)
            player.prepare(mediaSource, false, false)
            this.player = player
            this.gpuPlayerView = initEffectsSurface(player, this)
            playerView.visibility = View.INVISIBLE
            effectsPlayerLayout.visibility = View.INVISIBLE
        }

        demoImageView.visibility = if (mediaSource == null) {
            View.VISIBLE
        } else {
            View.GONE
        }
    }

    private fun initEffectsSurface(player: SimpleExoPlayer, context: Context): GPUPlayerView {
        val gpuPlayerView = GPUPlayerView(context)
        gpuPlayerView.setPlayerScaleType(PlayerScaleType.RESIZE_FIT_WIDTH)
        gpuPlayerView.setSimpleExoPlayer(player)
        gpuPlayerView.layoutParams = layoutParamsForEffectsSurface()
        effectsPlayerLayout.removeAllViews()
        effectsPlayerLayout.addView(gpuPlayerView)
        gpuPlayerView.setGlFilter(GlSepiaFilter())
        gpuPlayerView.onResume()
        return gpuPlayerView
    }

    private fun layoutParamsForEffectsSurface(): RelativeLayout.LayoutParams {
        val params = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
        return params
    }

    private fun releasePlayer() {
        try {
            currentWindow = player?.currentWindowIndex ?: 0
            player?.release()
            player = null
        } catch (e: Exception) {
        }
    }

    private fun hideSystemUi() {
        playerView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    private fun buildMediaSource(uri: Uri?): MediaSource? {
        if (uri == null) return null
        val dataSourceFactory = DefaultDataSourceFactory(this, Util.getUserAgent(this, "ExoTest"))
        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri)
    }

    private fun dpToPixels(dp: Int): Float {
        return (dp * resources.displayMetrics.density)
    }
}