package com.lb.video_trimmer_library.utils

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import com.googlecode.mp4parser.BasicContainer
import com.googlecode.mp4parser.authoring.Movie
import com.googlecode.mp4parser.authoring.Track
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AppendTrack
import com.lb.video_trimmer_library.models.VideoClipModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.*
import kotlin.collections.ArrayList


public const val PUBLIC_FOLDER_NAME = "Video-Reorder"
private const val TAG = "FileUtils"

object FileUtils {
    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.<br></br>
     * <br></br>
     * Callers should check whether the path is local before assuming it
     * represents a local file.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    @SuppressLint("NewApi")
    fun getPath(context: Context, uri: Uri): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && DocumentsContract.isDocumentUri(
                context,
                uri
            )
        ) {
            when {
                isExternalStorageDocument(uri) -> {
                    // DocumentProvider
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split =
                        docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    if ("primary".equals(type, ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                    }
                    return null
                    // TODO handle non-primary volumes
                }
                isDownloadsDocument(uri) -> {
                    // DownloadsProvider
                    val id = DocumentsContract.getDocumentId(uri)
                    try {
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            java.lang.Long.valueOf(id)
                        )
                        return getDataColumn(context, contentUri, null, null)
                    } catch (e: NumberFormatException) {
                        if (id.startsWith("raw:/")) {
                            val newPath = id.substring("raw:/".length)
                            val exists = File(newPath).exists()
                            if (exists)
                                return newPath
                        }
                    }
                }
                isMediaDocument(uri) -> {
                    // MediaProvider
                    val docId = DocumentsContract.getDocumentId(uri)
                    val split =
                        docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    val type = split[0]
                    var contentUri: Uri? = null
                    when (type) {
                        "image" -> contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        "video" -> contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        "audio" -> contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                    val selection = "_id=?"
                    val selectionArgs = arrayOf(split[1])
                    return getDataColumn(context, contentUri, selection, selectionArgs)
                }
            }
        }
        return when {
            "content".equals(uri.scheme!!, ignoreCase = true) -> // MediaStore (and general)
                // Return the remote address
                if (isGooglePhotosUri(uri)) uri.lastPathSegment else getDataColumn(
                    context,
                    uri,
                    null,
                    null
                )
            "file".equals(uri.scheme!!, ignoreCase = true) -> // File
                uri.path
            else -> null
        }
    }

    /**
     * Get the value of the data column for this Uri. This is useful for
     * MediaStore Uris, and other file-based ContentProviders.
     *
     * @param context       The context.
     * @param uri           The Uri to query.
     * @param selection     (Optional) Filter used in the query.
     * @param selectionArgs (Optional) Selection arguments used in the query.
     * @return The value of the _data column, which is typically a file path.
     * @author paulburke
     */
    private fun getDataColumn(
        context: Context,
        uri: Uri?,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)
        try {
            cursor =
                context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor.getString(columnIndex)
            }
        } finally {
            cursor?.close()
        }
        return null
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is Google Photos.
     */
    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    fun appendVideos(context: Context, videoClips: ArrayList<VideoClipModel>): String {
        var movies = ArrayList<Movie>(videoClips.size)
        val resultMovie = Movie()
        val videoTracks = LinkedList<Track>()
        val audioTracks = LinkedList<Track>()

        for (video in videoClips) {
            movies.add(MovieCreator.build(video.videoPath))
        }

        for (movie in movies) {
            for (track in movie.tracks) {
                if (track.handler.equals("soun")) {
                    audioTracks.add(track)
                }
                if (track.handler.equals("vide")) {
                    videoTracks.add(track)
                }
            }
        }
        if (audioTracks.size > 0) {
            resultMovie.addTrack(AppendTrack(*audioTracks.toTypedArray()))

        }
        if (videoTracks.size > 0) {
            resultMovie.addTrack(AppendTrack(*videoTracks.toTypedArray()));
        }

        val output: BasicContainer = DefaultMp4Builder().build(resultMovie) as BasicContainer
        val path = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
            ), PUBLIC_FOLDER_NAME
        );
        if (!path.exists())
            path.mkdirs()

        val fileName = UUID.randomUUID().toString()
        val file = File(path, String.format("/%s.mp4", fileName))
        file.createNewFile()
        val fc = RandomAccessFile(
            file.absolutePath,
            "rw"
        ).getChannel()
        output.writeContainer(fc)
        var mFileName = file.absolutePath
        val values = ContentValues(3)
        values.put(MediaStore.Video.Media.TITLE, fileName)
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        values.put(MediaStore.Video.Media.DATA, mFileName)
        context.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        return mFileName
    }

    fun deleteTempFiles(videoClips: ArrayList<VideoClipModel>) {
        for (video in videoClips) {
            File(video.videoPath).delete()
        }
    }

    fun getVideoClipFilePath(context: Context?): String {
        val filename = "${System.currentTimeMillis()}.mp4"
        val dir = context?.externalCacheDir

        return if (dir == null) {
            filename
        } else {
            "${dir.absolutePath}/$filename"
        }
    }

    fun saveTempBitmap(context: Context?, bitmap: Bitmap): String {
        val filename = "${System.currentTimeMillis()}.jpeg"
        val dir = context?.externalCacheDir
        val filePath = "${dir?.absolutePath}/$filename"

        try {
            FileOutputStream(filePath).use({ out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out) // bmp is your Bitmap instance
                // PNG is a lossless format, the compression factor (100) is ignored
            })
        } catch (e: IOException) {
            Log.e(TAG, e.toString())
        }

        return filePath
    }
}
