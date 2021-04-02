package org.schabi.newpipe.views.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.annotation.StyleRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.END
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.START
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.preference.PreferenceManager
import org.schabi.newpipe.MainActivity
import org.schabi.newpipe.R
import org.schabi.newpipe.player.event.DisplayPortion
import org.schabi.newpipe.player.event.DoubleTapListener

class PlayerSeekOverlay(context: Context, private val attrs: AttributeSet?) :
    ConstraintLayout(context, attrs), DoubleTapListener {

    private var secondsView: SecondsView
    private var circleClipTapView: CircleClipTapView
    private var rootConstraintLayout: ConstraintLayout

    private var isForwarding: Boolean? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.player_seek_overlay, this, true)

        secondsView = findViewById(R.id.seconds_view)
        circleClipTapView = findViewById(R.id.circle_clip_tap_view)
        rootConstraintLayout = findViewById(R.id.root_constraint_layout)

        initializeAttributes()
        secondsView.isForward = true
        isForwarding = null
        changeConstraints(true)
    }

    private fun initializeAttributes() {
        circleBackgroundColorInt(CircleClipTapView.COLOR_LIGHT_TRANSPARENT)
        iconAnimationDuration(SecondsView.ICON_ANIMATION_DURATION)
        icon(R.drawable.ic_play_seek_triangle)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val durationKey = context.getString(R.string.seek_duration_key)
        val seekValue = prefs.getString(
            durationKey, context.getString(R.string.seek_duration_default_value)
        )
        seekSeconds(seekValue?.toInt()?.div(1000) ?: 10)
    }

    private var performListener: PerformListener? = null

    fun performListener(listener: PerformListener) = apply {
        performListener = listener
    }

    var seekSeconds: Int = 0
        private set

    fun seekSeconds(seconds: Int) = apply {
        seekSeconds = seconds
    }

    var circleBackgroundColor: Int
        get() = circleClipTapView.circleBackgroundColor
        private set(value) {
            circleClipTapView.circleBackgroundColor = value
        }

    fun circleBackgroundColorRes(@ColorRes resId: Int) = apply {
        circleBackgroundColor = ContextCompat.getColor(context, resId)
    }

    fun circleBackgroundColorInt(@ColorInt color: Int) = apply {
        circleBackgroundColor = color
    }

    var arcSize: Float
        get() = circleClipTapView.arcSize
        internal set(value) {
            circleClipTapView.arcSize = value
        }

    fun arcSize(@DimenRes resId: Int) = apply {
        arcSize = context.resources.getDimension(resId)
    }

    fun arcSize(px: Float) = apply {
        arcSize = px
    }

    var showCircle: Boolean = true
        private set(value) {
            circleClipTapView.visibility = if (value) View.VISIBLE else View.GONE
            field = value
        }

    fun showCircle(show: Boolean) = apply {
        showCircle = show
    }

    var iconAnimationDuration: Long = SecondsView.ICON_ANIMATION_DURATION
        get() = secondsView.cycleDuration
        private set(value) {
            secondsView.cycleDuration = value
            field = value
        }

    fun iconAnimationDuration(duration: Long) = apply {
        iconAnimationDuration = duration
    }

    @DrawableRes
    var icon: Int = 0
        get() = secondsView.icon
        private set(value) {
            secondsView.stop()
            secondsView.icon = value
            field = value
        }

    fun icon(@DrawableRes resId: Int) = apply {
        icon = resId
    }

    @StyleRes
    var textAppearance: Int = 0
        private set(value) {
            TextViewCompat.setTextAppearance(secondsView.textView, value)
            field = value
        }

    fun textAppearance(@StyleRes resId: Int) = apply {
        textAppearance = resId
    }

    // Indicates whether this (double) tap is the first of a series
    // Decides whether to call performListener.onAnimationStart or not
    private var initTap: Boolean = false

    override fun onDoubleTapStarted(portion: DisplayPortion) {
        if (DEBUG)
            Log.d(TAG, "onDoubleTapStarted called with portion = [$portion]")

        initTap = false
        performListener?.onPrepare()

        changeConstraints(secondsView.isForward)
        if (showCircle) circleClipTapView.updatePosition(portion)

        isForwarding = null

        if (this.alpha == 0f)
            secondsView.stop()
    }

    override fun onDoubleTapProgressDown(portion: DisplayPortion) {
        val shouldForward: Boolean = performListener?.shouldFastForward(portion) ?: return

        if (DEBUG)
            Log.d(
                TAG,
                "onDoubleTapProgressDown called with " +
                    "shouldForward = [$shouldForward], " +
                    "isForwarding = [$isForwarding], " +
                    "secondsView#isForward = [${secondsView.isForward}], " +
                    "initTap = [$initTap], "
            )

        // Using this check prevents from fast switching (one touches)
        if (isForwarding != null && isForwarding != shouldForward) {
            isForwarding = shouldForward
            return
        }
        isForwarding = shouldForward

        if (this.visibility != View.VISIBLE || !initTap) {
            if (!initTap) {
                secondsView.seconds = 0
                performListener?.onAnimationStart()
                secondsView.start()
                initTap = true
            }
        }

        if (shouldForward)
            forwarding()
        else
            rewinding()
    }

    override fun onDoubleTapFinished() {
        if (DEBUG)
            Log.d(TAG, "onDoubleTapFinished called with initTap = [$initTap]")

        if (initTap) performListener?.onAnimationEnd()
        initTap = false
    }

    private fun forwarding() {
        if (DEBUG)
            Log.d(TAG, "forwarding called")

        // First time tap or switched
        if (!secondsView.isForward) {
            changeConstraints(true)
            if (showCircle) circleClipTapView.updatePosition(DisplayPortion.RIGHT)
            secondsView.apply {
                isForward = true
                seconds = 0
            }
        }
        secondsView.seconds += seekSeconds
        performListener?.seek(forward = true)
    }

    private fun rewinding() {
        if (DEBUG)
            Log.d(TAG, "rewinding called")

        // First time tap or switched
        if (secondsView.isForward) {
            changeConstraints(false)
            if (showCircle) circleClipTapView.updatePosition(DisplayPortion.LEFT)
            secondsView.apply {
                isForward = false
                seconds = 0
            }
        }
        secondsView.seconds += seekSeconds
        performListener?.seek(forward = false)
    }

    private fun changeConstraints(forward: Boolean) {
        val constraintSet = ConstraintSet()
        with(constraintSet) {
            clone(rootConstraintLayout)
            if (forward) {
                clear(secondsView.id, START)
                connect(
                    secondsView.id, END,
                    PARENT_ID, END
                )
            } else {
                clear(secondsView.id, END)
                connect(
                    secondsView.id, START,
                    PARENT_ID, START
                )
            }
            secondsView.start()
            applyTo(rootConstraintLayout)
        }
    }

    interface PerformListener {
        fun onPrepare() {}
        fun onAnimationStart()
        fun onAnimationEnd()
        fun shouldFastForward(portion: DisplayPortion): Boolean?
        fun seek(forward: Boolean)
    }

    companion object {
        private const val TAG = "PlayerSeekOverlay"
        private val DEBUG = MainActivity.DEBUG
    }
}
