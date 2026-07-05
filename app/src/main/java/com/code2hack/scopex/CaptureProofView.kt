package com.code2hack.scopex

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.code2hack.scopex.scopex.FloatPoint
import com.code2hack.scopex.scopex.FloatRect
import com.code2hack.scopex.scopex.IntSize
import com.code2hack.scopex.scopex.ScopeXCaptureProofCrosshairAnchor
import com.code2hack.scopex.scopex.ScopeXCaptureProofLayoutCalculator

class CaptureProofView(context: Context) : View(context) {
    private val frameStore = CaptureProofFrameStore<Bitmap>()
    private var crosshairAnchor = ScopeXCaptureProofCrosshairAnchor.Center
    private val framePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val paddingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(34, 43, 52)
        style = Paint.Style.FILL
    }
    private val logicalDisplayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(132, 196, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val physicalScopePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 214, 102)
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 92, 92)
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(190, 196, 204)
        textSize = 42f
        textAlign = Paint.Align.CENTER
    }

    fun replaceFrame(frame: Bitmap) {
        frameStore.latest?.takeIf { it !== frame }?.recycle()
        frameStore.replace(frame)
        invalidate()
    }

    fun clearFrame() {
        frameStore.latest?.recycle()
        frameStore.clear()
        invalidate()
    }

    fun setCrosshairAnchor(anchor: ScopeXCaptureProofCrosshairAnchor) {
        crosshairAnchor = anchor
        invalidate()
    }

    override fun onDetachedFromWindow() {
        clearFrame()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(15, 18, 22))

        val frame = frameStore.latest
        if (frame == null || frame.width < 2 || frame.height < 2 || width < 2 || height < 2) {
            canvas.drawText("No active capture", width / 2f, height / 2f, emptyPaint)
            return
        }

        val layout = ScopeXCaptureProofLayoutCalculator.layout(
            frameSize = IntSize(frame.width, frame.height),
            viewSize = IntSize(width, height),
            crosshairAnchor = crosshairAnchor,
        )

        canvas.drawRect(layout.paddedLogicalDisplayDrawRect.toRectF(), paddingPaint)
        canvas.drawBitmap(frame, null, layout.logicalDisplayDrawRect.toRectF(), framePaint)
        canvas.drawRect(layout.logicalDisplayDrawRect.toRectF(), logicalDisplayPaint)
        canvas.drawRect(layout.physicalScopeDrawRect.toRectF(), physicalScopePaint)
        canvas.drawCrosshair(layout.crosshairDrawPoint)
    }

    private fun Canvas.drawCrosshair(point: FloatPoint) {
        val radius = 22f
        drawLine(point.x - radius, point.y, point.x + radius, point.y, crosshairPaint)
        drawLine(point.x, point.y - radius, point.x, point.y + radius, crosshairPaint)
    }

    private fun FloatRect.toRectF(): RectF = RectF(left, top, right, bottom)
}
