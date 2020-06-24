package com.lb.video_trimmer_library.activities

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.lb.video_trimmer_library.R
import com.lb.video_trimmer_library.fragments.CustomCameraFragment
import com.lb.video_trimmer_library.fragments.VideoTrimmerFragment

object VideoConstants {
    const val VIDEO_PATH_KEY = "VIDEO_PATH_KEY"
}

class VideoRecordingActivity : AppCompatActivity(),
    CustomCameraFragment.OnFragmentInteractionListener,
    VideoTrimmerFragment.OnFragmentInteractionListener {

    override fun onVideoTrimmed(uri: Uri) {
    }

    override fun onVideoRecordingCompleted(videoPath: String?) {
        val bundle = Bundle()
        bundle.putString(VideoConstants.VIDEO_PATH_KEY, videoPath)

        val mIntent = Intent()
        mIntent.putExtras(bundle)
        setResult(Activity.RESULT_OK, mIntent)
        Toast.makeText(this, "Video Saved at Location " + videoPath, Toast.LENGTH_LONG).show()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_recording)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, CustomCameraFragment.newInstance())
            .commit()


    }

}
