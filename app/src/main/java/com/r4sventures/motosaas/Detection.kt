package com.r4sventures.motosaas

data class Detection(
    val id: Int,
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float,
    val label: String? = null,
    val approaching: Boolean = false,
    val type: DetectionType = DetectionType.OBJECT,
    val isFace: Boolean = false

)

enum class DetectionType {
    FACE, OBJECT
}
