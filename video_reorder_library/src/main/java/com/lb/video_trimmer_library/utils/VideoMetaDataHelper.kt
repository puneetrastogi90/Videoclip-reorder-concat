package com.lb.video_trimmer_library.utils

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import com.lb.video_trimmer_library.models.VideoClipModel

/**
 * returns duration of video in seconds
 */
fun MediaMetadataRetriever.getVideoDuration(videoPath: String): Int {
    setDataSource(videoPath)
    return extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toInt() / 1000
}

fun MediaMetadataRetriever.getThumbnail(videoPath: String): Bitmap {
    setDataSource(videoPath)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(0, 0, dpToPx(100), dpToPx(100))
    } else {
        getFrameAtTime(0)
    }
}

fun MediaMetadataRetriever.getFullResolutionThumbnail(videoPath: String): Bitmap {
    setDataSource(videoPath)
    return getFrameAtTime(0)
}

fun MediaMetadataRetriever.getThumbnailResizableWidth(
    position: Int,
    videosList: ArrayList<VideoClipModel>
): Double {
    val mVideoLength = getVideoDuration(videosList.get(position).videoPath)
    var totalLength = 0
    for (video in videosList) {
        totalLength = totalLength + getVideoDuration(video.videoPath)
    }
    return (mVideoLength.toDouble() / totalLength)
}