package com.lb.video_trimmer_library.utils

import com.google.android.exoplayer2.LoadControl
import com.google.android.exoplayer2.upstream.DefaultAllocator
import com.google.android.exoplayer2.DefaultLoadControl


class ExoplayerLoadControl {
    companion object {

        val MIN_BUFFER_DURATION = 2000
        val MAX_BUFFER_DURATION = 5000
        val MIN_PLAYBACK_START_BUFFER = 1500
        val MIN_PLAYBACK_RESUME_BUFFER = 2000

        var loadControl: LoadControl = DefaultLoadControl()/*.Builder()
            .setAllocator(DefaultAllocator(true, 16))
            .setBufferDurationsMs(
                MIN_BUFFER_DURATION,
                MAX_BUFFER_DURATION,
                MIN_PLAYBACK_START_BUFFER,
                MIN_PLAYBACK_RESUME_BUFFER
            )
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true).createDefaultLoadControl()*/
    }
}