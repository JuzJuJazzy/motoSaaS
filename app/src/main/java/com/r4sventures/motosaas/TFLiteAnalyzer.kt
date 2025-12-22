package com.r4sventures.motosaas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log

@androidx.camera.core.ExperimentalGetImage
class TFLiteAnalyzer(
    private val context: Context,
    private val modelType: Int,
    private val onDetectionsReady: (
        people: List<Detection>,
        objects: List<Detection>,
        imageWidth: Float,
        imageHeight: Float
    ) -> Unit
) : ImageAnalysis.Analyzer {

    private val interpreter: Interpreter
    private val labels: List<String>

    // ใช้เก็บพื้นที่ของ object เดิมเอาไว้ เพื่อคิด growth
    private var prevArea = mutableMapOf<Int, Float>()

    private val notifyCooldownMs = 1000L
    private var lastNotifyTime = 0L
    private var thresholdScore = 0.6f

    init {
        val modelFile = "best_float32_0912.tflite"
        interpreter = Interpreter(FileUtil.loadMappedFile(context, modelFile))
        labels = FileUtil.loadLabels(context, "best_float32_0912.txt")
    }

    private fun rotateBitmap(src: Bitmap, rotationDegrees: Int): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    override fun analyze(imageProxy: ImageProxy) {
        try {

            val modelWidth = 640
            val modelHeight = 640

            // 1) Convert ImageProxy → Bitmap
            var bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) bitmap = rotateBitmap(bitmap, rotationDegrees)

            // 2) Resize to 640x640
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelWidth, modelHeight, true)

            // 3) Prepare input buffer
            val inputBuffer = ByteBuffer.allocateDirect(1 * modelWidth * modelHeight * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(modelWidth * modelHeight)
            resizedBitmap.getPixels(intValues, 0, modelWidth, 0, 0, modelWidth, modelHeight)

            for (pixel in intValues) {
                inputBuffer.putFloat((pixel shr 16 and 0xFF) / 255f)
                inputBuffer.putFloat((pixel shr 8 and 0xFF) / 255f)
                inputBuffer.putFloat((pixel and 0xFF) / 255f)
            }
            inputBuffer.rewind()

            // 4) Output buffer
            val outputShape = interpreter.getOutputTensor(0).shape()
            val outputBuffer = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }

            interpreter.run(inputBuffer, outputBuffer)

            val boxes = outputBuffer[0]
            val numBoxes = boxes[0].size

            val objectListRaw = mutableListOf<Detection>()

            // 5) Decode all bounding boxes
            for (i in 0 until numBoxes) {

                val cxNorm = boxes[0][i]
                val cyNorm = boxes[1][i]
                val wNorm  = boxes[2][i]
                val hNorm  = boxes[3][i]

                val cx = cxNorm * modelWidth
                val cy = cyNorm * modelHeight
                val w  = wNorm * modelWidth
                val h  = hNorm * modelHeight

                if (w <= 0f || h <= 0f) continue
                if (w < 8f || h < 8f) continue

                // class scores
                val boxScores = FloatArray(labels.size)
                for (c in labels.indices) boxScores[c] = boxes[4 + c][i]

                val maxScore = boxScores.maxOrNull() ?: 0f
                if (maxScore < thresholdScore) continue

                val labelIndex = boxScores.indices.maxByOrNull { boxScores[it] } ?: -1
                val label = labels.getOrElse(labelIndex) { "Unknown" }

                val finalBox = RectF(
                    cx - w / 2f,
                    cy - h / 2f,
                    cx + w / 2f,
                    cy + h / 2f
                )

                // Mirror X สำหรับกล้องหน้า
                val mirroredCenterX = bitmap.width.toFloat() - finalBox.centerX()

                // unique ID
                val id = (label.hashCode() +
                        finalBox.centerX().toInt() * 31 +
                        finalBox.centerY().toInt() * 17).hashCode()

                // ----------- AREA & GROWTH CALC -----------
                val area = w * h
                val lastArea = prevArea[id] ?: -1f

                // 1) growth-based
                val growth = if (lastArea > 0f) {
                    (area - lastArea) / lastArea
                } else 0f

                val expandedFast = growth > 0.10f    // 10%

                // 2) absolute size rule
                val imageArea = (modelWidth * modelHeight).toFloat()
                val areaPercent = area / imageArea
                val biggerThanLimit = areaPercent > 0.20f  // 20% ของภาพ

                // 3) final rule
                val approaching = expandedFast || biggerThanLimit

                prevArea[id] = area

                // LOG
                Log.e(
                    "APPROACHING",
                    "label=$label area=$area last=$lastArea growth=$growth expandedFast=$expandedFast " +
                            "areaPercent=$areaPercent biggerThanLimit=$biggerThanLimit approaching=$approaching"
                )

                objectListRaw.add(
                    Detection(
                        id = id,
                        centerX = mirroredCenterX,
                        centerY = finalBox.centerY(),
                        width = finalBox.width(),
                        height = finalBox.height(),
                        label = label,
                        approaching = approaching,
                        type = DetectionType.OBJECT
                    )
                )
            }

            // 6) NMS
            val objectList = nms(objectListRaw, 0.5f)

            // 7) Overlay notify
            val anyApproaching = objectList.any { it.approaching }
            val currentTime = System.currentTimeMillis()

            if (anyApproaching && currentTime - lastNotifyTime > notifyCooldownMs) {
                OverlayController.show(context)
                lastNotifyTime = currentTime
            } else if (!anyApproaching) {
                OverlayController.hide(context)
            }

            // 8) Push result to UI
            onDetectionsReady(emptyList(), objectList, modelWidth.toFloat(), modelHeight.toFloat())

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    // ---------- NMS ----------
    fun nms(detections: List<Detection>, iouThreshold: Float = 0.5f): List<Detection> {
        val result = mutableListOf<Detection>()
        val sorted = detections.sortedByDescending { it.width * it.height }
        val picked = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (picked[i]) continue
            val a = sorted[i]
            result.add(a)

            for (j in i + 1 until sorted.size) {
                if (picked[j]) continue
                val b = sorted[j]
                val iou = computeIoU(a, b)
                if (iou > iouThreshold) picked[j] = true
            }
        }
        return result
    }

    fun computeIoU(a: Detection, b: Detection): Float {
        val interLeft = maxOf(a.centerX - a.width / 2f, b.centerX - b.width / 2f)
        val interTop = maxOf(a.centerY - a.height / 2f, b.centerY - b.height / 2f)
        val interRight = minOf(a.centerX + a.width / 2f, b.centerX + b.width / 2f)
        val interBottom = minOf(a.centerY + a.height / 2f, b.centerY + b.height / 2f)

        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val unionArea = a.width * a.height + b.width * b.height - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    fun resetState() {
        prevArea.clear()
        lastNotifyTime = 0L
        OverlayController.hide(context)
    }
}
