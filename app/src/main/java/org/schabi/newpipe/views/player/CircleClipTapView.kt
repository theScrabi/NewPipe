package org.schabi.newpipe.views.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import org.schabi.newpipe.player.event.DisplayPortion

class CircleClipTapView(context: Context?, attrs: AttributeSet) : View(context, attrs) {

    companion object {
        const val COLOR_DARK = 0x45000000
        const val COLOR_DARK_TRANSPARENT = 0x30000000
        const val COLOR_LIGHT_TRANSPARENT = 0x25EEEEEE

        fun calculateArcSize(view: View): Float = view.height / 11.4f
    }

    private var backgroundPaint = Paint()

    private var widthPx = 0
    private var heightPx = 0

    // Background

    private var shapePath = Path()
    private var isLeft = true

    init {
        requireNotNull(context) { "Context is null." }

        backgroundPaint.apply {
            style = Paint.Style.FILL
            isAntiAlias = true
            color = COLOR_LIGHT_TRANSPARENT
        }

        val dm = context.resources.displayMetrics
        widthPx = dm.widthPixels
        heightPx = dm.heightPixels

        updatePathShape()
    }

    var arcSize: Float = 80f
        set(value) {
            field = value
            updatePathShape()
        }

    var circleBackgroundColor: Int
        get() = backgroundPaint.color
        set(value) {
            backgroundPaint.color = value
        }

    /*
        Background
     */

    fun updatePosition(portion: DisplayPortion) {
        val newIsLeft = portion == DisplayPortion.LEFT
        if (isLeft != newIsLeft) {
            isLeft = newIsLeft
            updatePathShape()
        }
    }

    private fun updatePathShape() {
        val halfWidth = widthPx * 0.5f

        shapePath.reset()

        val w = if (isLeft) 0f else widthPx.toFloat()
        val f = if (isLeft) 1 else -1

        shapePath.moveTo(w, 0f)
        shapePath.lineTo(f * (halfWidth - arcSize) + w, 0f)
        shapePath.quadTo(
            f * (halfWidth + arcSize) + w,
            heightPx.toFloat() / 2,
            f * (halfWidth - arcSize) + w,
            heightPx.toFloat()
        )
        shapePath.lineTo(w, heightPx.toFloat())

        shapePath.close()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        widthPx = w
        heightPx = h
        updatePathShape()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        canvas?.clipPath(shapePath)
        canvas?.drawPath(shapePath, backgroundPaint)
    }
}
