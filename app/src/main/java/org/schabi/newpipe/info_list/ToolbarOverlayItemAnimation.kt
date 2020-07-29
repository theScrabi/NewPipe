package org.schabi.newpipe.info_list

import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation

/**
 * @param duration The duration of the animation, in milliseconds
 * @param toolbar The view to animate
 * @param viewsUnderground The views under the toolbar to hide when the toolbar is shown and
 * vice versa
 */
class ToolbarOverlayItemAnimation(duration: Int,
                                  toolbar: View,
                                  vararg viewsUnderground: View) : Animation() {
    private val toolbar: View
    private val viewsUnderground: Array<out View>
    private val goingToShowToolbar: Boolean

    init {
        setDuration(duration.toLong())
        this.toolbar = toolbar
        this.viewsUnderground = viewsUnderground
        goingToShowToolbar = toolbar.visibility != View.VISIBLE
    }

    override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
        super.applyTransformation(interpolatedTime, t)

        if (goingToShowToolbar) {
            toolbar.alpha = interpolatedTime
            for (view in viewsUnderground) {
                view.alpha = 1.0f - (1.0f - MINIMUM_UNDERGROUND_ALPHA) * interpolatedTime
            }
            if (interpolatedTime >= 1.0f) {
                Log.e("hi", "hihi");
                toolbar.visibility = View.VISIBLE
                toolbar.isClickable = true // to prevent clicks on the underground views
            }

        } else {
            toolbar.alpha = 1.0f - interpolatedTime
            for (view in viewsUnderground) {
                view.alpha = (MINIMUM_UNDERGROUND_ALPHA
                        + (1.0f - MINIMUM_UNDERGROUND_ALPHA) * interpolatedTime)
            }
            if (interpolatedTime >= 1.0f) {
                resetToolbarOverlayItem(toolbar, *viewsUnderground)
            }
        }
    }

    companion object {
        private const val MINIMUM_UNDERGROUND_ALPHA = 0.4f

        @JvmStatic
        fun resetToolbarOverlayItem(toolbar: View, vararg viewsUnderground: View) {
            toolbar.visibility = View.INVISIBLE
            toolbar.isClickable = false
            for (view in viewsUnderground) {
                view.alpha = 1.0f
                view.visibility = View.VISIBLE
            }
        }
    }
}