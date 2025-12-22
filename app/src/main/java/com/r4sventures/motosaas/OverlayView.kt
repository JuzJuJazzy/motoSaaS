package com.r4sventures.motosaas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var detections: MutableList<Detection> = mutableListOf()
    private var imageWidth: Float = 0f
    private var imageHeight: Float = 0f


    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val approachingPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        strokeWidth = 2f
    }

    fun setDetections(objects: List<Detection>, imageWidth: Float, imageHeight: Float) {
        this.detections.clear()
        this.detections.addAll(objects)
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageWidth == 0f || imageHeight == 0f) return

        val viewAspect = width.toFloat() / height
        val imageAspect = imageWidth / imageHeight

        val scale: Float
        val dx: Float
        val dy: Float

        if (imageAspect > viewAspect) {
            scale = height / imageHeight
            dx = (width - imageWidth * scale) / 2f
            dy = 0f
        } else {
            scale = width / imageWidth
            dx = 0f
            dy = (height - imageHeight * scale) / 2f
        }

        detections.forEach { det ->
            val left = det.centerX - det.width / 2
            val top = det.centerY - det.height / 2
            val right = det.centerX + det.width / 2
            val bottom = det.centerY + det.height / 2

            val l = left * scale + dx
            val t = top * scale + dy
            val r = right * scale + dx
            val b = bottom * scale + dy

            val paint = if (det.approaching) approachingPaint else boxPaint
            canvas.drawRect(l, t, r, b, paint)
            //canvas.drawText(det.label ?: "?", l, t - 10, textPaint)
        }
    }

    fun clearDetections() {
        detections.clear()  // เคลียร์ list ของ bounding box
        invalidate()        // รีเฟรช view
    }

}
