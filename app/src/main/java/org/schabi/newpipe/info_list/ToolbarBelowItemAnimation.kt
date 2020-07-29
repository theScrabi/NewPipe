package org.schabi.newpipe.info_list

import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.LinearLayout

/**
 * @param duration The duration of the animation, in milliseconds
 * @param toolbar The view to animate
 */
class ToolbarBelowItemAnimation(duration: Int, toolbar: View) : Animation() {
    private val toolbar: View
    private val toolbarLayoutParams: LinearLayout.LayoutParams
    private val marginStart: Int
    private val marginEnd: Int

    init {
        setDuration(duration.toLong())
        this.toolbar = toolbar
        toolbarLayoutParams = toolbar.layoutParams as LinearLayout.LayoutParams
        marginStart = toolbarLayoutParams.bottomMargin
        marginEnd = if (marginStart == 0) -(toolbar.height + toolbarLayoutParams.topMargin) else 0
        toolbar.visibility = View.VISIBLE
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        super.applyTransformation(interpolatedTime, t)
        toolbarLayoutParams.bottomMargin = (marginStart
                + ((marginEnd - marginStart) * interpolatedTime).toInt())

        if (marginStart == 0) {
            toolbar.alpha = 1.0f - interpolatedTime
            if (interpolatedTime >= 1.0f) {
                toolbar.visibility = View.GONE
            }

        } else {
            toolbar.alpha = interpolatedTime
            if (interpolatedTime >= 1.0f) {
                toolbar.visibility = View.VISIBLE
            }
        }

        toolbar.requestLayout()
    }

    companion object {
        @JvmStatic
        fun resetToolbarBelowItem(toolbar: View) {
            toolbar.visibility = View.GONE
            val layoutParams = toolbar.layoutParams as LinearLayout.LayoutParams
            if (layoutParams.bottomMargin == 0) {
                layoutParams.bottomMargin = -(toolbar.height + layoutParams.topMargin)
            }
        }
    }
}