package com.lb.video_trimmer_library.adapters

import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.lb.video_trimmer_library.R
import com.lb.video_trimmer_library.callbacks.ItemTouchHelperAdapter
import com.lb.video_trimmer_library.fragments.MediaSourceUpdateListener
import com.lb.video_trimmer_library.models.VideoClipModel
import com.lb.video_trimmer_library.utils.*
import kotlinx.android.synthetic.main.recording_timeline_single_view.view.*
import java.util.*
import kotlin.collections.ArrayList

class RecordingTimelineAdapter(val mListener: MediaSourceUpdateListener) :
    RecyclerView.Adapter<RecordingTimelineAdapter.ViewHolder>(),
    ItemTouchHelperAdapter {
    private lateinit var mRecyclerView: RecyclerView
    var videoClips: ArrayList<VideoClipModel> = ArrayList()

    private val mediaMetadataRetriever = MediaMetadataRetriever()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        mRecyclerView = recyclerView
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int) {
        mListener.onMediaSourceReorder(fromPosition, toPosition)
        Collections.swap(videoClips, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }


    override fun onItemDismiss(position: Int) {
        videoClips.removeAt(position)
        mListener.onMediaSourceDelete(position)
        notifyDataSetChanged()
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(
                R.layout.recording_timeline_single_view,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return videoClips.size
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        (holder.itemView.imageview as ImageView).setImageBitmap(
            mediaMetadataRetriever.getFullResolutionThumbnail(
                videoClips.get(position).videoPath
            )
        )


        val lp = holder.itemView.imageContainer.layoutParams
        if (lp is ViewGroup.LayoutParams) {
            (lp).width = (mediaMetadataRetriever.getThumbnailResizableWidth(
                position,
                videoClips
            ) * mRecyclerView.width).toInt()
            if (position == videoClips.size - 1) {
                (lp).width = (lp).width - dpToPx(10)
            }
        }


        holder.itemView.setOnClickListener(View.OnClickListener {
            mListener.onMediaSourceClick(position)
        })

    }


    public fun updateVideoClipsTimeline(videoClip: String) {
        videoClips.add(VideoClipModel(videoClip))
        notifyDataSetChanged()
    }


    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        init {

        }
    }
}