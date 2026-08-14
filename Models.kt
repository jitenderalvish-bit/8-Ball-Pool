package com.billiards.analyzer

import android.graphics.PointF

data class Ball(
    var x: Float,
    var y: Float,
    val id: Int,
    var isCue: Boolean = false,
    var isTarget: Boolean = false
)

data class Pocket(
    var x: Float,
    var y: Float,
    val id: Int
)

enum class Mode {
    TABLE, POCKETS, BALLS, CUE, TARGET, POCKET_SELECT
}

enum class Power(val factor: Float, val label: String) {
    LOW(0.35f, "LOW"),
    MEDIUM(0.65f, "MEDIUM"),
    HIGH(1.0f, "HIGH");

    companion object {
        /** Maps a 0-100 slider value to a continuous power factor (0.2 - 1.0). */
        fun fromSlider(value: Int): Float = 0.2f + (value.coerceIn(0, 100) / 100f) * 0.8f

        fun nearestPreset(value: Int): Power = when {
            value < 40 -> LOW
            value < 75 -> MEDIUM
            else -> HIGH
        }
    }
}

enum class Verdict { GREEN, YELLOW, RED }

data class ShotResult(
    val ghostBallPoint: PointF,
    val cueObstructionPoint: PointF?,          // blocks cue -> target approach
    val targetObstructionPoint: PointF?,        // blocks target -> pocket path
    val secondaryDeflection: PointF?,           // approximate deflected path of the ball that was hit
    val targetPath: List<PointF>,               // target -> [cushion contact] -> pocket (or short stop point)
    val pocketReached: Boolean,
    val cueAngleDeg: Float,                     // cue ball's absolute travel direction
    val objectAngleDeg: Float,                  // deflection/cut angle at contact
    val entryAngleDeg: Float,                   // pocket entry direction
    val confidence: Int,
    val isBank: Boolean,
    val bankFailed: Boolean,
    val cueFollowThrough: PointF,
    val cueFinalRest: PointF,
    val verdict: Verdict
)

// --- small PointF math helpers ---
fun PointF.minus(o: PointF) = PointF(x - o.x, y - o.y)
fun PointF.plus(o: PointF) = PointF(x + o.x, y + o.y)
fun PointF.times(s: Float) = PointF(x * s, y * s)
fun PointF.length(): Float = kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
fun PointF.normalized(): PointF {
    val l = length()
    return if (l < 0.0001f) PointF(0f, 0f) else PointF(x / l, y / l)
}
fun dot(a: PointF, b: PointF): Float = a.x * b.x + a.y * b.y
fun dist(a: PointF, b: PointF): Float = a.minus(b).length()
