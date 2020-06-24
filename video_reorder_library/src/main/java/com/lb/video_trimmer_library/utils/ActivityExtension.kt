package com.lb.video_trimmer_library.utils

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

fun Activity.showToast(string: String) {
    Toast.makeText(applicationContext, string, Toast.LENGTH_SHORT).show()
}

fun AppCompatActivity.showToast(string: String) {
    Toast.makeText(applicationContext, string, Toast.LENGTH_SHORT).show()
}