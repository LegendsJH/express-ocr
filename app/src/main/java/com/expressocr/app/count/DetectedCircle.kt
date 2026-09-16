package com.expressocr.app.count

data class DetectedCircle(
    val cx: Float,
    val cy: Float,
    val radius: Float
)

enum class CountPreset {
    CHOPSTICK,
    PIPE
}
