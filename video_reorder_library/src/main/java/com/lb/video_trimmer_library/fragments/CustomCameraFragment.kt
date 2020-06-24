package com.lb.video_trimmer_library.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.android.camera.utils.OrientationLiveData
import com.example.android.camera2video.CompareSizesByArea
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayerFactory
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.util.Util
import com.custom_toolbar.toolbar.ToolbarButtonClickListener
import com.lb.video_trimmer_library.R
import com.lb.video_trimmer_library.adapters.RecordingTimelineAdapter
import com.lb.video_trimmer_library.callbacks.VideoClipItemTouchHelperCallback
import com.lb.video_trimmer_library.utils.FileUtils
import com.lb.video_trimmer_library.utils.MarginItemDecoration
import com.lb.video_trimmer_library.utils.getPreviewOutputSize
import com.lb.video_trimmer_library.utils.showToast
import com.lb.video_trimmer_library.view.AutoFitSurfaceView.AutoFitSurfaceView
import com.lb.video_trimmer_library.view.BitmapTimeBar
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.fragment_custom_camera.*
import kotlinx.android.synthetic.main.fragment_custom_camera.view.*
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class CustomCameraFragment : Fragment(), View.OnClickListener, MediaSourceUpdateListener,
    Player.EventListener, ToolbarButtonClickListener {
    private lateinit var bitmapTimeBar: BitmapTimeBar
    private val TAG = "CustomCameraFragment"
    private var simpleExoPlayer: SimpleExoPlayer? = null
    private var playWhenReady = false
    private var currentWindow = 0
    private var playbackPosition: Long = 0
    private lateinit var tempFile: File
    private var concatenatingMediaSource: ConcatenatingMediaSource? = null
    private val TRIMMER_REQUEST_CODE = 23
    private lateinit var previewSize: Size
    private lateinit var videoSize: Size
    private var isRecordingVideo = false
    private var timerDisposable: Disposable? = null
    private lateinit var cameraId: String
    private lateinit var timelineAdapter: RecordingTimelineAdapter
    private var listener: OnFragmentInteractionListener? = null

    /** Detects, characterizes, and connects to a CameraDevice (used for all camera operations) */
    private val cameraManager: CameraManager by lazy {
        val context = requireContext().applicationContext
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    /** [CameraCharacteristics] corresponding to the provided Camera ID */
    private val characteristics: CameraCharacteristics by lazy {
        cameraManager.getCameraCharacteristics(cameraId)
    }

    /** File where the recording will be saved */
    private lateinit var outputFile: File

    /**
     * Setup a persistent [Surface] for the recorder so we can use it as an output target for the
     * camera session without preparing the recorder
     */
    private val recorderSurface: Surface by lazy {

        // Get a persistent Surface from MediaCodec, don't forget to release when done
        val surface = MediaCodec.createPersistentInputSurface()

        // Prepare and release a dummy MediaRecorder with our new surface
        // Required to allocate an appropriately sized buffer before passing the Surface as the
        //  output target to the capture session
        tempFile = createFile(requireContext(), "tmp")
        recorder = createRecorder(surface, tempFile).apply {
            prepare()
            release()
        }
        tempFile.delete()

        surface
    }

    /** Saves the video recording */
    private lateinit var recorder: MediaRecorder

    /** [HandlerThread] where all camera operations run */
    private val cameraThread = HandlerThread("CameraThread").apply { start() }

    /** [Handler] corresponding to [cameraThread] */
    private val cameraHandler = Handler(cameraThread.looper)


    /** Where the camera preview is displayed */
    private lateinit var texture_view: AutoFitSurfaceView


    /** Captures frames from a [CameraDevice] for our video recording */
    private lateinit var session: CameraCaptureSession

    /** The [CameraDevice] that will be opened in this fragment */
    private lateinit var camera: CameraDevice

    /** Requests used for preview only in the [CameraCaptureSession] */
    private val previewRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            // Add the preview surface target
            addTarget(texture_view.holder.surface)
        }.build()
    }

    /** Requests used for preview and recording in the [CameraCaptureSession] */
    private val recordRequest: CaptureRequest by lazy {
        // Capture request holds references to target surfaces
        session.device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            // Add the preview and recording surface targets
            addTarget(texture_view.holder.surface)
            addTarget(recorderSurface)
            // Sets user requested FPS for all targets
        }.build()
    }

    private var recordingStartMillis: Long = 0L

    /** Live data listener for changes in the device orientation relative to the camera */
    private lateinit var relativeOrientation: OrientationLiveData

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.capture_action -> {
                if (isRecordingVideo) stopRecordingVideo() else startRecordingVideo()
            }
            R.id.stop_action -> {
                if (isRecordingVideo) stopRecordingVideo() else startRecordingVideo()
            }

            R.id.texture_view -> {
            }


        }
    }


    private fun startRecordingVideo() {
        lifecycleScope.launch(Dispatchers.IO) {
            outputFile = createFile(requireContext(), "mp4")
            recorder = createRecorder(recorderSurface, outputFile)
            // Prevents screen rotation during the video recording
            requireActivity().requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_LOCKED

            // Start recording repeating requests, which will stop the ongoing preview
            //  repeating requests without having to explicitly call `session.stopRepeating`
            session.setRepeatingRequest(recordRequest, null, cameraHandler)

            // Finalizes recorder setup and starts recording
            recorder.apply {
                // Sets output orientation based on current sensor value at start time
                relativeOrientation.value?.let { setOrientationHint(it) }
                prepare()
                start()

            }
            withContext(Dispatchers.Main) {
                isRecordingVideo = true
                toggleCapturebutton()
                setupRecordingUI()
            }
            recordingStartMillis = System.currentTimeMillis()
            Log.d("CustomCameraFragment", "Recording started")

            // Starts recording animation
        }

    }


    private fun stopRecordingVideo() {
        lifecycleScope.launch(Dispatchers.IO) {

            // Unlocks screen rotation after recording finished
            requireActivity().requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

            // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
            val elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis
            if (elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                delay(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis)
            }

            Log.d(TAG, "Recording stopped. Output file: $outputFile")
            recorder.stop()

            // Removes recording animation

            // Broadcasts the media file to the rest of the system
            MediaScannerConnection.scanFile(
                context, arrayOf(outputFile.absolutePath), null, null
            )


            withContext(Dispatchers.Main) {
                addMediaSourceForExoplayer(outputFile.absolutePath)
                isRecordingVideo = false
                toggleCapturebutton()
                stopTimer()
                activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            TRIMMER_REQUEST_CODE -> if (resultCode == Activity.RESULT_OK) {
                setTrimmedVideo(data)
            }
        }
    }

    private fun setTrimmedVideo(data: Intent?) {
        val videoPath = data?.extras?.getString(VideoTrimmerFragment.VIDEO_URI)
        val position = data?.extras?.getInt(VideoTrimmerFragment.POSITION)

        if (File(timelineAdapter.videoClips[position!!].videoPath).delete()) {
            timelineAdapter.videoClips[position!!].videoPath = videoPath!!
            changeMediaSource(videoPath, position)
            timelineAdapter.notifyDataSetChanged()
        } else {
            activity!!.showToast("Could not trim the video")
        }


    }

    private fun initializeExoPlayer() {

        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(
            context,
            DefaultTrackSelector(),
            DefaultLoadControl()
        )

        bitmapTimeBar = player_control.findViewById<BitmapTimeBar>(R.id.exo_progress)
        val mediaSource = buildMediaSourceForExoplayer()
        simpleExoPlayer?.setPlayWhenReady(playWhenReady)
        simpleExoPlayer?.seekTo(currentWindow, playbackPosition)
        simpleExoPlayer?.prepare(mediaSource, false, false);
        exoplayer_view.setShowMultiWindowTimeBar(true)
        player_control.setShowMultiWindowTimeBar(true)
        exoplayer_view.player = simpleExoPlayer
        simpleExoPlayer?.addListener(this)
        player_control.player = simpleExoPlayer

    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            exoplayerPlaying()
            Log.i(TAG, "playing")
        } else {
            Log.i(TAG, "paused")
            exoplayerPaused()
        }
    }

    private fun exoplayerPlaying() {
        video_timeline_recyclerview.visibility = View.INVISIBLE
        bitmapTimeBar.visibility = View.VISIBLE
        capture_action.visibility = View.INVISIBLE
        video_time_text.visibility = View.INVISIBLE
        //   texture_view.visibility = View.INVISIBLE
        exoplayer_view.visibility = View.VISIBLE
    }

    private fun exoplayerPaused() {
        video_timeline_recyclerview.visibility = View.VISIBLE
        bitmapTimeBar.visibility = View.INVISIBLE
        capture_action.visibility = View.VISIBLE
        video_time_text.visibility = View.VISIBLE
        //  texture_view.visibility = View.VISIBLE
        exoplayer_view.visibility = View.INVISIBLE
    }

    private fun buildMediaSourceForExoplayer(): ConcatenatingMediaSource {
        if (concatenatingMediaSource == null) {
            concatenatingMediaSource = ConcatenatingMediaSource()
        }
        return concatenatingMediaSource as ConcatenatingMediaSource
    }

    private fun addMediaSourceForExoplayer(videoPath: String?) {
        val dataSourceFactory = DefaultDataSourceFactory(context, "exoplayer")
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.parse(videoPath))
        concatenatingMediaSource?.addMediaSource(mediaSource)
        timelineAdapter.updateVideoClipsTimeline(outputFile.absolutePath)
        setProgressBarBackground()
    }


    private fun setProgressBarBackground() {
        if (timelineAdapter.videoClips.size > 0) {
            bitmapTimeBar.setBitmap(timelineAdapter.videoClips[0].videoPath)
        } else {
            bitmapTimeBar.setBitmap(null)
        }

    }


    private fun changeMediaSource(videoPath: String, position: Int) {
        val dataSourceFactory = DefaultDataSourceFactory(context, "exoplayer")
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(Uri.parse(videoPath))
        concatenatingMediaSource?.removeMediaSource(position)
        concatenatingMediaSource?.addMediaSource(position, mediaSource)
    }

    override fun onMediaSourceReorder(oldPosition: Int, newPosition: Int) {
        val mediaSource = concatenatingMediaSource?.getMediaSource(oldPosition)
        concatenatingMediaSource?.removeMediaSource(oldPosition)
        concatenatingMediaSource?.addMediaSource(newPosition, mediaSource)
        simpleExoPlayer!!.prepare(concatenatingMediaSource)
    }

    override fun onMediaSourceDelete(position: Int) {
        concatenatingMediaSource?.removeMediaSource(position)
        if (concatenatingMediaSource?.size!! <= 0) {
            player_control.visibility = View.INVISIBLE
            generic_toolbar.visibility = View.INVISIBLE
            video_time_text.visibility = View.INVISIBLE
        }
        setProgressBarBackground()
    }

    override fun onMediaSourceClick(position: Int) {
        var videoClipPath = timelineAdapter.videoClips[position].videoPath
        val trimmerFragment: VideoTrimmerFragment =
            VideoTrimmerFragment.newInstance(videoClipPath, position)
        trimmerFragment.setTargetFragment(this, TRIMMER_REQUEST_CODE)
        activity!!.supportFragmentManager.beginTransaction()
            .add(R.id.container, trimmerFragment).addToBackStack(null)
            .commit()

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

    override fun onBackButtonClick() {
        activity!!.onBackPressed()
    }

    override fun onActionButtonClick() {
        val fileName = stitchVideo()
        listener!!.onVideoRecordingCompleted(fileName)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_custom_camera, container, false)
        setupRecyclerView(view)
        return view
    }

    private fun setupRecyclerView(view: View) {
        timelineAdapter = RecordingTimelineAdapter(this)
        view.video_timeline_recyclerview.adapter = timelineAdapter
        view.video_timeline_recyclerview.setHasFixedSize(true)
        view.video_timeline_recyclerview.addItemDecoration(
            MarginItemDecoration(
                resources.getDimension(
                    R.dimen.space_listing_recycler_padding
                ).toInt()
            )
        )
        view.video_timeline_recyclerview.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        var callback: ItemTouchHelper.Callback =
            VideoClipItemTouchHelperCallback(timelineAdapter);
        var touchHelper: ItemTouchHelper = ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(view.video_timeline_recyclerview);
    }


    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        texture_view = view.findViewById(R.id.texture_view)
        texture_view.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) = Unit

            override fun surfaceCreated(holder: SurfaceHolder) {
                val rect = holder.surfaceFrame
                // Selects appropriate preview size and configures view finder
                val previewSize = getPreviewOutputSize(
                    texture_view.display, characteristics, SurfaceHolder::class.java
                )
                Log.d(TAG, "View finder size: ${texture_view.width} x ${texture_view.height}")
                Log.d(TAG, "Selected preview size: $previewSize")
                texture_view.setAspectRatio(previewSize.width, previewSize.height)

                // To ensure that size is set, initialize camera in the view's thread
                texture_view.post { initializeCamera(rect.width(), rect.height()) }
            }
        })
        cameraId = cameraManager.cameraIdList[0]
        // Used to rotate the output media to match device orientation
        relativeOrientation = OrientationLiveData(requireContext(), characteristics).apply {
            observe(viewLifecycleOwner, Observer { orientation ->
                Log.d(TAG, "Orientation changed: $orientation")
            })
        }

        texture_view.setOnClickListener(this)
        capture_action.setOnClickListener(this)
        stop_action.setOnClickListener(this)
        generic_toolbar.setToolbarClickListener(this)
    }


    override fun onResume() {
        super.onResume()


        if ((Util.SDK_INT < 24 || simpleExoPlayer == null)) {
            initializeExoPlayer()
        }
    }

    override fun onPause() {
        if (Util.SDK_INT < 24) {
            releaseExoPlayer()
        }
        super.onPause()
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }


    /** Creates a [MediaRecorder] instance using the provided [Surface] as input */
    private fun createRecorder(surface: Surface, file: File) = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(file.absolutePath)
        setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE)
        setVideoFrameRate(30)
        setVideoSize(videoSize.width, videoSize.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setInputSurface(surface)
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating request
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initializeCamera(width: Int, height: Int) =
        lifecycleScope.launch(Dispatchers.Main) {

            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?: throw RuntimeException("Cannot get available preview/video sizes")

            //   sensorOrientation = characteristics.get(SENSOR_ORIENTATION)
            videoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            previewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture::class.java),
                width, height, videoSize
            )
            // Open the selected camera
            camera = openCamera(cameraManager, cameraId, cameraHandler)
            // Creates list of Surfaces where the camera will output frames
            val targets = listOf(texture_view.holder.surface, recorderSurface)

            // Start a capture session using our open camera and list of Surfaces where frames will go
            session = createCaptureSession(camera, targets, cameraHandler)

            // Sends the capture request as frequently as possible until the session is torn down or
            //  session.stopRepeating() is called
            session.setRepeatingRequest(previewRequest, null, cameraHandler)

        }

    private fun toggleCapturebutton() {
        if (isRecordingVideo) {
            capture_action.visibility = View.INVISIBLE
            generic_toolbar.visibility = View.INVISIBLE
            player_control.visibility = View.INVISIBLE
            stop_action.visibility = View.VISIBLE
            video_time_text.visibility = View.VISIBLE
            video_timeline_recyclerview.visibility = View.INVISIBLE

        } else {
            capture_action.visibility = View.VISIBLE
            generic_toolbar.visibility = View.VISIBLE
            player_control.visibility = View.VISIBLE
            stop_action.visibility = View.INVISIBLE
            video_time_text.visibility = View.INVISIBLE
            video_timeline_recyclerview.visibility = View.VISIBLE
        }
    }

    private fun setupRecordingUI() {
        startTimer()
        exoplayer_view.visibility = View.INVISIBLE
        texture_view.visibility = View.VISIBLE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun startTimer() {
        timerDisposable = Observable.interval(0, 1, TimeUnit.SECONDS)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                changeToMinutes(it)
            }
    }

    private fun changeToMinutes(seconds: Long?) {
        var minute = seconds?.div(60)
        var sec = seconds?.rem(60)
        video_time_text.setText(String.format("%02d:%02d", minute, sec))
    }


    private fun chooseVideoSize(choices: Array<Size>) = choices.firstOrNull {
        it.width == 1920 && it.height == 1080
    } ?: choices[choices.size - 1]

    private fun chooseOptimalSize(
        choices: Array<Size>,
        width: Int,
        height: Int,
        aspectRatio: Size
    ): Size {

        // Collect the supported resolutions that are at least as big as the preview Surface
        val w = aspectRatio.width
        val h = aspectRatio.height
        val bigEnough = choices.filter {
            it.height == it.width * h / w && it.width >= width && it.height >= height
        }

        // Pick the smallest of those, assuming we found any
        return if (bigEnough.isNotEmpty()) {
            Collections.max(bigEnough, CompareSizesByArea())
        } else {
            choices[0]
        }
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    @SuppressLint("MissingPermission")
    private suspend fun openCamera(
        manager: CameraManager,
        cameraId: String,
        handler: Handler? = null
    ): CameraDevice = suspendCancellableCoroutine { cont ->
        manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) = cont.resume(device)

            override fun onDisconnected(device: CameraDevice) {
                Log.w(TAG, "Camera $cameraId has been disconnected")
                requireActivity().finish()
            }

            override fun onError(device: CameraDevice, error: Int) {
                val msg = when (error) {
                    ERROR_CAMERA_DEVICE -> "Fatal (device)"
                    ERROR_CAMERA_DISABLED -> "Device policy"
                    ERROR_CAMERA_IN_USE -> "Camera in use"
                    ERROR_CAMERA_SERVICE -> "Fatal (service)"
                    ERROR_MAX_CAMERAS_IN_USE -> "Maximum cameras in use"
                    else -> "Unknown"
                }
                val exc = RuntimeException("Camera $cameraId error: ($error) $msg")
                Log.e(TAG, exc.message, exc)
                if (cont.isActive) cont.resumeWithException(exc)
            }
        }, handler)
    }

    /**
     * Creates a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine)
     */
    private suspend fun createCaptureSession(
        device: CameraDevice,
        targets: List<Surface>,
        handler: Handler? = null
    ): CameraCaptureSession = suspendCoroutine { cont ->

        // Creates a capture session using the predefined targets, and defines a session state
        // callback which resumes the coroutine once the session is configured
        device.createCaptureSession(targets, object : CameraCaptureSession.StateCallback() {

            override fun onConfigured(session: CameraCaptureSession) = cont.resume(session)

            override fun onConfigureFailed(session: CameraCaptureSession) {
                val exc = RuntimeException("Camera ${device.id} session configuration failed")
                Log.e(TAG, exc.message, exc)
                cont.resumeWithException(exc)
            }
        }, handler)
    }

    override fun onStop() {
        super.onStop()
        try {
            camera.close()
        } catch (exc: Throwable) {
            Log.e(TAG, "Error closing camera", exc)
        }
        if (Util.SDK_INT >= 24) {
            releaseExoPlayer()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraThread.quitSafely()
        recorder?.release()
        recorderSurface?.release()
        if (timerDisposable != null)
            timerDisposable?.dispose()
    }

    companion object {
        private val TAG = CustomCameraFragment::class.java.simpleName

        private const val RECORDER_VIDEO_BITRATE: Int = 10_000_000
        private const val MIN_REQUIRED_RECORDING_TIME_MILLIS: Long = 1000L

        @JvmStatic
        fun newInstance() =
            CustomCameraFragment().apply {
                arguments = Bundle().apply {
                }
            }

        /** Creates a [File] named with the current date and time */
        private fun createFile(context: Context, extension: String): File {
            val sdf = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US)
            return File(context.externalCacheDir, "VID_${sdf.format(Date())}.$extension")
        }
    }


    private fun stitchVideo(): String? {
        try {
            val fileName = FileUtils.appendVideos(activity as Context, timelineAdapter.videoClips)
            FileUtils.deleteTempFiles(timelineAdapter.videoClips)
            return fileName
        } catch (exception: IOException) {
            activity!!.showToast("Something went wrong")
            Log.e(TAG, "ERROR:", exception)
        }

        return null
    }


    private fun stopTimer() {
        video_time_text.setText("00:00")
        timerDisposable?.dispose()
    }


    interface OnFragmentInteractionListener {
        fun onVideoRecordingCompleted(videoPath: String?)

    }
}

interface MediaSourceUpdateListener {
    fun onMediaSourceReorder(oldPosition: Int, newPosition: Int)


    fun onMediaSourceDelete(position: Int)

    fun onMediaSourceClick(position: Int)

}
