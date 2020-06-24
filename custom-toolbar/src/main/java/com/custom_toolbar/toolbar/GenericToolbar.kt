package com.custom_toolbar.toolbar

import android.content.Context
import android.content.res.TypedArray
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.custom_toolbar.R
import kotlinx.android.synthetic.main.generic_toolbar.view.*

class GenericToolbar : ConstraintLayout, View.OnClickListener {
    override fun onClick(view: View?) {
        when (view?.id) {
            R.id.ib_toolbar_back -> mListener?.onBackButtonClick()
            R.id.toolbar_action_button -> mListener?.onActionButtonClick()
        }
    }

    lateinit var mListener: ToolbarButtonClickListener

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initView(attrs)
    }

    public fun setToolbarClickListener(listener: ToolbarButtonClickListener) {
        mListener = listener
    }

    public fun getActionBarButton(): AppCompatButton {
        return findViewById<AppCompatButton>(R.id.toolbar_action_button)
    }

    public fun getToobarBackButton(): ImageButton {
        return findViewById(R.id.ib_toolbar_back) as ImageButton
    }

    fun getToolBarTitle(): TextView{
        return findViewById<TextView>(R.id.tv_toolbar_title)
    }

    fun initView(attrs: AttributeSet?) {
        val inflater: LayoutInflater
        val view: View
        val toolbarTitle: AppCompatTextView
        val backBtn: ImageButton
        val toolbarActionButton: AppCompatButton
        val gradientDrawable: GradientDrawable
        lateinit var typedArray: TypedArray

        typedArray = context.obtainStyledAttributes(attrs, R.styleable.GenericToolbar)
        inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        view = inflater.inflate(R.layout.generic_toolbar, this)
        toolbarTitle = view.tv_toolbar_title as AppCompatTextView
        toolbarActionButton = view.toolbar_action_button as AppCompatButton
        backBtn = view.ib_toolbar_back as ImageButton
        toolbarTitle.text =
            typedArray?.getText(R.styleable.GenericToolbar_title)
        toolbarTitle.setTextColor(typedArray.getColorStateList(R.styleable.GenericToolbar_titleColor))
        toolbarActionButton.text =
            typedArray?.getText(R.styleable.GenericToolbar_actionButtonText)
        if (toolbarActionButton.text == null || TextUtils.isEmpty(toolbarActionButton.text)) {
            toolbarActionButton.visibility = View.GONE
        }
        toolbarActionButton.setTextColor(typedArray.getColorStateList(R.styleable.GenericToolbar_actionButtonTextColor))
        toolbarActionButton.setOnClickListener(this)
        gradientDrawable = GradientDrawable()
        gradientDrawable.cornerRadius = 10f

        toolbarActionButton.background = gradientDrawable

        var color = typedArray.getColor(R.styleable.GenericToolbar_backButtonBgColor, -1)
        backBtn.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        backBtn.setOnClickListener(this)
        typedArray.recycle()
    }


}

open interface ToolbarButtonClickListener {
    fun onBackButtonClick()
    fun onActionButtonClick()
}

