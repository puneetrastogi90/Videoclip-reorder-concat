package com.lb.video_trimmer_library.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.custom_toolbar.toolbar.ToolbarButtonClickListener
import com.lb.video_trimmer_library.R

import com.lb.video_trimmer_library.interfaces.VideoTrimmingListener
import com.lb.video_trimmer_library.utils.FileUtils
import com.lb.video_trimmer_library.view.VideoTrimmerView
import kotlinx.android.synthetic.main.video_trimmer.view.*
import java.io.File


/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [VideoTrimmerFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [VideoTrimmerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class VideoTrimmerFragment : Fragment(), VideoTrimmingListener, ToolbarButtonClickListener {


    override fun onBackButtonClick() {
        activity!!.onBackPressed()
    }

    override fun onActionButtonClick() {
        videoTrimmerView.initiateTrimming()
    }

    lateinit var videoTrimmerView: VideoTrimmerView
    override fun onVideoPrepared() {

    }

    override fun onTrimStarted() {
    }

    override fun onFinishedTrimming(uri: Uri?) {
        closeWithResultCode(uri, Activity.RESULT_OK)
    }

    private fun closeWithResultCode(uri: Uri?, resultCode: Int) {
        val intent = Intent().putExtra(VIDEO_URI, uri!!.path)
            .putExtra(POSITION, position)
        targetFragment!!.onActivityResult(
            targetRequestCode,
            resultCode,
            intent
        )
        activity!!.supportFragmentManager.popBackStack()
    }

    override fun onErrorWhileViewingVideo(what: Int, extra: Int) {
    }

    private var videoPath: String? = null
    private var position: Int? = null
    private lateinit var videoUri: Uri
    private var listener: OnFragmentInteractionListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            videoPath = it.getString(VIDEO_URI)
            videoUri = Uri.fromFile(File(videoPath))
            position = it.getInt(POSITION)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_video_trimmer, container, false)
        videoTrimmerView = view.findViewById(R.id.videoTrimmerView)
        videoTrimmerView.setMaxDurationInMs(10 * 1000)
        videoTrimmerView.setOnK4LVideoListener(this)
        val fileName = FileUtils.getVideoClipFilePath(activity)
        val trimmedVideoFile = File(fileName)
        videoTrimmerView.setDestinationFile(trimmedVideoFile)
        videoTrimmerView.setVideoURI(videoUri)
        videoTrimmerView.setVideoInformationVisibility(true)
        view.generic_toolbar.setToolbarClickListener(this)
        return view
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException(context.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments]
     * (http://developer.android.com/training/basics/fragments/communicating.html)
     * for more information.
     */
    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onVideoTrimmed(uri: Uri)
    }

    companion object {

        val VIDEO_URI = "VIDEO_URI"
        val POSITION = "POSITION"
        /**
         *
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment VideoTrimmerFragment.
         */
        @JvmStatic
        fun newInstance(param1: String, param2: Int) =
            VideoTrimmerFragment().apply {
                arguments = Bundle().apply {
                    putString(VIDEO_URI, param1)
                    putInt(POSITION, param2)
                }
            }
    }
}
