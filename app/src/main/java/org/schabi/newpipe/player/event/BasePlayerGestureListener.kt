package org.schabi.newpipe.player.event

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.player.MainPlayer
import org.schabi.newpipe.player.Player
import org.schabi.newpipe.player.helper.PlayerHelper
import org.schabi.newpipe.player.helper.PlayerHelper.savePopupPositionAndSizeToPrefs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Base gesture handling for [Player]
 *
 * This class contains the logic for the player gestures like View preparations
 * and provides some abstract methods to make it easier separating the logic from the UI.
 */
abstract class BasePlayerGestureListener(
    @JvmField
    protected val player: Player,
    @JvmField
    protected val service: MainPlayer
) : GestureDetector.SimpleOnGestureListener(), View.OnTouchListener {

    // ///////////////////////////////////////////////////////////////////
    // Abstract methods for VIDEO and POPUP
    // ///////////////////////////////////////////////////////////////////

    abstract fun onDoubleTap(event: MotionEvent, portion: DisplayPortion)

    abstract fun onSingleTap(playerType: MainPlayer.PlayerType)

    abstract fun onScroll(
        playerType: MainPlayer.PlayerType,
        portion: DisplayPortion,
        initialEvent: MotionEvent,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    )

    abstract fun onScrollEnd(playerType: MainPlayer.PlayerType, event: MotionEvent)

    // ///////////////////////////////////////////////////////////////////
    // Abstract methods for POPUP (exclusive)
    // ///////////////////////////////////////////////////////////////////

    abstract fun onPopupResizingStart()

    abstract fun onPopupResizingEnd()

    private var initialPopupX: Int = -1
    private var initialPopupY: Int = -1

    private var isMovingInMain = false
    private var isMovingInPopup = false
    private var isResizing = false

    private val tossFlingVelocity = PlayerHelper.getTossFlingVelocity()

    // [popup] initial coordinates and distance between fingers
    private var initPointerDistance = -1.0
    private var initFirstPointerX = -1f
    private var initFirstPointerY = -1f
    private var initSecPointerX = -1f
    private var initSecPointerY = -1f

    // ///////////////////////////////////////////////////////////////////
    // onTouch implementation
    // ///////////////////////////////////////////////////////////////////

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return if (player.popupPlayerSelected()) {
            onTouchInPopup(v, event)
        } else {
            onTouchInMain(v, event)
        }
    }

    private fun onTouchInMain(v: View, event: MotionEvent): Boolean {
        player.gestureDetector.onTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP && isMovingInMain) {
            isMovingInMain = false
            onScrollEnd(MainPlayer.PlayerType.VIDEO, event)
        }
        return when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                v.parent.requestDisallowInterceptTouchEvent(player.isFullscreen)
                true
            }
            MotionEvent.ACTION_UP -> {
                v.parent.requestDisallowInterceptTouchEvent(false)
                false
            }
            else -> true
        }
    }

    private fun onTouchInPopup(v: View, event: MotionEvent): Boolean {
        player.gestureDetector.onTouchEvent(event)
        if (event.pointerCount == 2 && !isMovingInPopup && !isResizing) {
            if (DEBUG) {
                Log.d(TAG, "onTouch() 2 finger pointer detected, enabling resizing.")
            }
            onPopupResizingStart()

            // record coordinates of fingers
            initFirstPointerX = event.getX(0)
            initFirstPointerY = event.getY(0)
            initSecPointerX = event.getX(1)
            initSecPointerY = event.getY(1)
            // record distance between fingers
            initPointerDistance = hypot(
                initFirstPointerX - initSecPointerX.toDouble(),
                initFirstPointerY - initSecPointerY.toDouble()
            )

            isResizing = true
        }
        if (event.action == MotionEvent.ACTION_MOVE && !isMovingInPopup && isResizing) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onTouch() ACTION_MOVE > v = [$v], e1.getRaw =" +
                        "[${event.rawX}, ${event.rawY}]"
                )
            }
            return handleMultiDrag(event)
        }
        if (event.action == MotionEvent.ACTION_UP) {
            if (DEBUG) {
                Log.d(
                    TAG,
                    "onTouch() ACTION_UP > v = [$v], e1.getRaw =" +
                        " [${event.rawX}, ${event.rawY}]"
                )
            }
            if (isMovingInPopup) {
                isMovingInPopup = false
                onScrollEnd(MainPlayer.PlayerType.POPUP, event)
            }
            if (isResizing) {
                isResizing = false

                initPointerDistance = (-1).toDouble()
                initFirstPointerX = (-1).toFloat()
                initFirstPointerY = (-1).toFloat()
                initSecPointerX = (-1).toFloat()
                initSecPointerY = (-1).toFloat()

                onPopupResizingEnd()
                player.changeState(player.currentState)
            }
            if (!player.isPopupClosing) {
                savePopupPositionAndSizeToPrefs(player)
            }
        }

        v.performClick()
        return true
    }

    private fun handleMultiDrag(event: MotionEvent): Boolean {
        if (initPointerDistance != -1.0 && event.pointerCount == 2) {
            // get the movements of the fingers
            val firstPointerMove = hypot(
                event.getX(0) - initFirstPointerX.toDouble(),
                event.getY(0) - initFirstPointerY.toDouble()
            )
            val secPointerMove = hypot(
                event.getX(1) - initSecPointerX.toDouble(),
                event.getY(1) - initSecPointerY.toDouble()
            )

            // minimum threshold beyond which pinch gesture will work
            val minimumMove = ViewConfiguration.get(service).scaledTouchSlop

            if (max(firstPointerMove, secPointerMove) > minimumMove) {
                // calculate current distance between the pointers
                val currentPointerDistance = hypot(
                    event.getX(0) - event.getX(1).toDouble(),
                    event.getY(0) - event.getY(1).toDouble()
                )

                val popupWidth = player.popupLayoutParams!!.width.toDouble()
                // change co-ordinates of popup so the center stays at the same position
                val newWidth = popupWidth * currentPointerDistance / initPointerDistance
                initPointerDistance = currentPointerDistance
                player.popupLayoutParams!!.x += ((popupWidth - newWidth) / 2.0).toInt()

                player.checkPopupPositionBounds()
                player.updateScreenSize()
                player.changePopupSize(min(player.screenWidth.toDouble(), newWidth).toInt())
                return true
            }
        }
        return false
    }

    // ///////////////////////////////////////////////////////////////////
    // Simple gestures
    // ///////////////////////////////////////////////////////////////////

    override fun onDown(e: MotionEvent): Boolean {
        if (DEBUG)
            Log.d(TAG, "onDown called with e = [$e]")

        if (isDoubleTapping && isDoubleTapEnabled) {
            doubleTapControls?.onDoubleTapProgressDown(getDisplayPortion(e))
            return true
        }

        return if (player.popupPlayerSelected())
            onDownInPopup(e)
        else
            true
    }

    private fun onDownInPopup(e: MotionEvent): Boolean {
        // Fix popup position when the user touch it, it may have the wrong one
        // because the soft input is visible (the draggable area is currently resized).
        player.updateScreenSize()
        player.checkPopupPositionBounds()
        player.popupLayoutParams?.let {
            initialPopupX = it.x
            initialPopupY = it.y
        }
        return super.onDown(e)
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (DEBUG)
            Log.d(TAG, "onDoubleTap called with e = [$e]")

        onDoubleTap(e, getDisplayPortion(e))
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (DEBUG)
            Log.d(TAG, "onSingleTapConfirmed() called with: e = [$e]")

        if (isDoubleTapping)
            return true

        if (player.popupPlayerSelected()) {
            if (player.exoPlayerIsNull())
                return false

            onSingleTap(MainPlayer.PlayerType.POPUP)
            return true
        } else {
            super.onSingleTapConfirmed(e)
            if (player.currentState == Player.STATE_BLOCKED)
                return true

            onSingleTap(MainPlayer.PlayerType.VIDEO)
        }
        return true
    }

    override fun onLongPress(e: MotionEvent?) {
        if (player.popupPlayerSelected()) {
            player.updateScreenSize()
            player.checkPopupPositionBounds()
            player.changePopupSize(player.screenWidth.toInt())
        }
    }

    override fun onScroll(
        initialEvent: MotionEvent,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        return if (player.popupPlayerSelected()) {
            onScrollInPopup(initialEvent, movingEvent, distanceX, distanceY)
        } else {
            onScrollInMain(initialEvent, movingEvent, distanceX, distanceY)
        }
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return if (player.popupPlayerSelected()) {
            val absVelocityX = abs(velocityX)
            val absVelocityY = abs(velocityY)
            if (absVelocityX.coerceAtLeast(absVelocityY) > tossFlingVelocity) {
                if (absVelocityX > tossFlingVelocity) {
                    player.popupLayoutParams!!.x = velocityX.toInt()
                }
                if (absVelocityY > tossFlingVelocity) {
                    player.popupLayoutParams!!.y = velocityY.toInt()
                }
                player.checkPopupPositionBounds()
                player.windowManager!!.updateViewLayout(player.rootView, player.popupLayoutParams)
                return true
            }
            return false
        } else {
            true
        }
    }

    private fun onScrollInMain(
        initialEvent: MotionEvent,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {

        if (!player.isFullscreen) {
            return false
        }

        val isTouchingStatusBar: Boolean = initialEvent.y < getStatusBarHeight(service)
        val isTouchingNavigationBar: Boolean =
            initialEvent.y > (player.rootView.height - getNavigationBarHeight(service))
        if (isTouchingStatusBar || isTouchingNavigationBar) {
            return false
        }

        val insideThreshold = abs(movingEvent.y - initialEvent.y) <= MOVEMENT_THRESHOLD
        if (
            !isMovingInMain && (insideThreshold || abs(distanceX) > abs(distanceY)) ||
            player.currentState == Player.STATE_COMPLETED
        ) {
            return false
        }

        isMovingInMain = true

        onScroll(
            MainPlayer.PlayerType.VIDEO,
            getDisplayHalfPortion(initialEvent),
            initialEvent,
            movingEvent,
            distanceX,
            distanceY
        )

        return true
    }

    private fun onScrollInPopup(
        initialEvent: MotionEvent,
        movingEvent: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {

        if (isResizing) {
            return super.onScroll(initialEvent, movingEvent, distanceX, distanceY)
        }

        if (!isMovingInPopup) {
            player.closeOverlayButton.animate(true, 200)
        }

        isMovingInPopup = true

        val diffX: Float = (movingEvent.rawX - initialEvent.rawX)
        var posX: Float = (initialPopupX + diffX)
        val diffY: Float = (movingEvent.rawY - initialEvent.rawY)
        var posY: Float = (initialPopupY + diffY)

        if (posX > player.screenWidth - player.popupLayoutParams!!.width) {
            posX = (player.screenWidth - player.popupLayoutParams!!.width)
        } else if (posX < 0) {
            posX = 0f
        }

        if (posY > player.screenHeight - player.popupLayoutParams!!.height) {
            posY = (player.screenHeight - player.popupLayoutParams!!.height)
        } else if (posY < 0) {
            posY = 0f
        }

        player.popupLayoutParams!!.x = posX.toInt()
        player.popupLayoutParams!!.y = posY.toInt()

        onScroll(
            MainPlayer.PlayerType.POPUP,
            getDisplayHalfPortion(initialEvent),
            initialEvent,
            movingEvent,
            distanceX,
            distanceY
        )

        player.windowManager!!.updateViewLayout(player.rootView, player.popupLayoutParams)
        return true
    }

    // ///////////////////////////////////////////////////////////////////
    // Multi double tapping
    // ///////////////////////////////////////////////////////////////////

    var doubleTapControls: DoubleTapListener? = null
        private set

    val isDoubleTapEnabled: Boolean
        get() = doubleTapDelay > 0

    var isDoubleTapping = false
        private set

    fun doubleTapControls(listener: DoubleTapListener) = apply {
        doubleTapControls = listener
    }

    private var doubleTapDelay = DOUBLE_TAP_DELAY
    private val doubleTapHandler: Handler = Handler()
    private val doubleTapRunnable = Runnable {
        if (DEBUG)
            Log.d(TAG, "doubleTapRunnable called")

        isDoubleTapping = false
        doubleTapControls?.onDoubleTapFinished()
    }

    fun startMultiDoubleTap(e: MotionEvent) {
        if (!isDoubleTapping) {
            if (DEBUG)
                Log.d(TAG, "startMultiDoubleTap called with e = [$e]")

            keepInDoubleTapMode()
            doubleTapControls?.onDoubleTapStarted(getDisplayPortion(e))
        }
    }

    fun keepInDoubleTapMode() {
        if (DEBUG)
            Log.d(TAG, "keepInDoubleTapMode called")

        isDoubleTapping = true
        doubleTapHandler.removeCallbacks(doubleTapRunnable)
        doubleTapHandler.postDelayed(doubleTapRunnable, doubleTapDelay)
    }

    fun endMultiDoubleTap() {
        if (DEBUG)
            Log.d(TAG, "endMultiDoubleTap called")

        isDoubleTapping = false
        doubleTapHandler.removeCallbacks(doubleTapRunnable)
        doubleTapControls?.onDoubleTapFinished()
    }

    fun enableMultiDoubleTap(enable: Boolean) = apply {
        doubleTapDelay = if (enable) DOUBLE_TAP_DELAY else 0
    }

    // ///////////////////////////////////////////////////////////////////
    // Utils
    // ///////////////////////////////////////////////////////////////////

    private fun getDisplayPortion(e: MotionEvent): DisplayPortion {
        return if (player.playerType == MainPlayer.PlayerType.POPUP && player.popupLayoutParams != null) {
            when {
                e.x < player.popupLayoutParams!!.width / 3.0 -> DisplayPortion.LEFT
                e.x > player.popupLayoutParams!!.width * 2.0 / 3.0 -> DisplayPortion.RIGHT
                else -> DisplayPortion.MIDDLE
            }
        } else /* MainPlayer.PlayerType.VIDEO */ {
            when {
                e.x < player.rootView.width / 3.0 -> DisplayPortion.LEFT
                e.x > player.rootView.width * 2.0 / 3.0 -> DisplayPortion.RIGHT
                else -> DisplayPortion.MIDDLE
            }
        }
    }

    // Currently needed for scrolling since there is no action more the middle portion
    private fun getDisplayHalfPortion(e: MotionEvent): DisplayPortion {
        return if (player.playerType == MainPlayer.PlayerType.POPUP) {
            when {
                e.x < player.popupLayoutParams!!.width / 2.0 -> DisplayPortion.LEFT_HALF
                else -> DisplayPortion.RIGHT_HALF
            }
        } else /* MainPlayer.PlayerType.VIDEO */ {
            when {
                e.x < player.rootView.width / 2.0 -> DisplayPortion.LEFT_HALF
                else -> DisplayPortion.RIGHT_HALF
            }
        }
    }

    private fun getNavigationBarHeight(context: Context): Int {
        val resId = context.resources
            .getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resId > 0) {
            context.resources.getDimensionPixelSize(resId)
        } else 0
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resId = context.resources
            .getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) {
            context.resources.getDimensionPixelSize(resId)
        } else 0
    }

    companion object {
        private const val TAG = "BasePlayerGestListener"
        private val DEBUG = Player.DEBUG

        private const val DOUBLE_TAP_DELAY = 550L
        private const val MOVEMENT_THRESHOLD = 40
    }
}
