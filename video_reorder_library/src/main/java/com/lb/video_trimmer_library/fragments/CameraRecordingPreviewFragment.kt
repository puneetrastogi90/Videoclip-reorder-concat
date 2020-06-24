package com.lb.video_trimmer_library.fragments


import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector

import kotlinx.android.synthetic.main.fragment_camera_recording_preview.*
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.util.Util
import com.lb.video_trimmer_library.R


// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val VIDEO_PATH = "VIDEO_PATH"

/**
 * A simple [Fragment] subclass.
 * Use the [CameraRecordingPreviewFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class CameraRecordingPreviewFragment : Fragment() {
    private var videoPath: String? = null
    private var simpleExoPlayer: SimpleExoPlayer? = null
    private var playWhenReady = true
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            videoPath = it.getString(VIDEO_PATH)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_recording_preview, container, false)
    }

    private fun initializeExoPlayer() {

        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(
            context,
            DefaultTrackSelector(),
            DefaultLoadControl()
        )


        val mediaSource = buildMediaSourceForExoplayer(videoPath)
        simpleExoPlayer?.setPlayWhenReady(playWhenReady)
        simpleExoPlayer?.seekTo(currentWindow, playbackPosition)
        simpleExoPlayer?.prepare(mediaSource, false, false);
        exoplayer_view.setShowMultiWindowTimeBar(true)
        exoplayer_view.player = simpleExoPlayer

    }

    @SuppressLint
        ("InlinedApi")
    private fun hideSystemUi() {
        exoplayer_view.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        )
    }

    override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializeExoPlayer()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if ((Util.SDK_INT < 24 || simpleExoPlayer == null)) {
            initializeExoPlayer();
        }
    }

    override fun onPause() {
        super.onPause()
        if (Util.SDK_INT < 24) {
            releaseExoPlayer()
        }
    }

    override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= 24) {
            releaseExoPlayer()
        }
    }

    private fun buildMediaSourceForExoplayer(videoPath: String?): MediaSource {
        val dataSourceFactory = DefaultDataSourceFactory(context, "exoplayer-codelab")
        val mediaSource1 = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.parse(videoPath))
        return ConcatenatingMediaSource(mediaSource1)
    }


    private fun releaseExoPlayer() {
        if (simpleExoPlayer != null) {
            playWhenReady = simpleExoPlayer?.playWhenReady ?: false
            playbackPosition = simpleExoPlayer?.currentPosition ?: 0
            currentWindow = simpleExoPlayer?.currentWindowIndex ?: 0
            simpleExoPlayer?.release()
            simpleExoPlayer = null
        }
    }


    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment CameraRecordingPreviewFragment.
         */
        @JvmStatic
        fun newInstance(param1: String) =
            CameraRecordingPreviewFragment().apply {
                arguments = Bundle().apply {
                    putString(VIDEO_PATH, param1)
                }
            }
    }
}
