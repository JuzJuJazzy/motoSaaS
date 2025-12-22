package com.r4sventures.motosaas

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

class CameraAnalyzer(
    private val context: Context,
    private val onDetections: (objects: List<Detection>, faces: List<Detection>, camWidth: Float, camHeight: Float) -> Unit
) : ImageAnalysis.Analyzer {

    // Object Detection options
    private val objOptions = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableMultipleObjects()
        .enableClassification()
        .build()
    private val objDetector = ObjectDetection.getClient(objOptions)

    // Face Detection options
    private val faceOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build()
    private val faceDetector = FaceDetection.getClient(faceOptions)

    private val tracker = mutableMapOf<Int, MutableList<Float>>()

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotation = imageProxy.imageInfo.rotationDegrees
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

            // Object detection
            objDetector.process(inputImage)
                .addOnSuccessListener { detectedObjects ->
                    val objectList = mutableListOf<Detection>()
                    for (obj in detectedObjects) {
                        val id = obj.trackingId ?: continue
                        val box = obj.boundingBox
                        val label = obj.labels.firstOrNull()?.text ?: "Unknown"

                        // กรองเฉพาะรถ
                        if (true || label == "Car" || label == "Vehicle") {
                            // คำนวณอัตราส่วนของกล่องกับภาพ
                            val boxAreaRatio = (box.width() * box.height()).toFloat() / (imageProxy.width * imageProxy.height).toFloat()
                            val isClose = boxAreaRatio > 0.1f // ถ้าเกิน 30% ถือว่าใกล้

                            // ใช้ tracker เพื่อดูแนวโน้มการเข้าใกล้
                            val history = tracker.getOrPut(id) { mutableListOf() }
                            history.add(boxAreaRatio)
                            if (history.size > 8) history.removeAt(0)

                            var approaching = false
                            if (history.size >= 3) {
                                val first = history.first()
                                val last = history.last()
                                val growth = if (first > 0f) (last - first) / first else 0f
                                if (growth > 0.15f) approaching = true
                            }

                            objectList.add(
                                Detection(
                                    id = id,
                                    centerX = box.centerX().toFloat(),
                                    centerY = box.centerY().toFloat(),
                                    width = box.width().toFloat(),
                                    height = box.height().toFloat(),
                                    label = label,
                                    approaching = approaching,
                                    isFace = false
                                )
                            )
                        }
                    }


                    // Face detection
                    faceDetector.process(inputImage)
                        .addOnSuccessListener { faces ->
                            val faceList = mutableListOf<Detection>()
                            for (face in faces) {
                                val box = face.boundingBox
                                faceList.add(
                                    Detection(
                                        id = face.hashCode(),
                                        centerX = box.centerX().toFloat(),
                                        centerY = box.centerY().toFloat(),
                                        width = box.width().toFloat(),
                                        height = box.height().toFloat(),
                                        label = "Face",
                                        approaching = false,
                                        isFace = true
                                    )
                                )
                            }
                            // ส่งกลับ OverlayView
                            val camWidth = imageProxy.width.toFloat()
                            val camHeight = imageProxy.height.toFloat()

                            // ส่งกลับ OverlayView พร้อมขนาดกล้อง
                            onDetections(objectList, faceList, camWidth, camHeight)
                            imageProxy.close()
                        }
                        .addOnFailureListener { e ->
                            e.printStackTrace()
                            imageProxy.close()
                        }
                }
                .addOnFailureListener { e ->
                    e.printStackTrace()
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
