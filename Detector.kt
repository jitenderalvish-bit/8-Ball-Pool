package com.billiards.analyzer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Simple, fast, local pixel-scan detection. No ML models, no OpenCV — just color
 * clustering and connected-components on a small downscaled copy of the image.
 * This is intentionally approximate: manual correction is always available and expected.
 */
object Detector {

    data class DetectedBall(val x: Float, val y: Float, val radius: Float, val isCue: Boolean)

    data class Result(
        val tableRect: RectF?,
        val pockets: List<PointF>,
        val balls: List<DetectedBall>
    )

    private const val ANALYSIS_MAX_DIM = 240

    fun detect(source: Bitmap): Result {
        val scale = ANALYSIS_MAX_DIM.toFloat() / max(source.width, source.height)
        val aw = max(1, (source.width * scale).toInt())
        val ah = max(1, (source.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(source, aw, ah, true)
        val pixels = IntArray(aw * ah)
        small.getPixels(pixels, 0, aw, 0, 0, aw, ah)
        if (small !== source) small.recycle()
        val toOriginal = 1f / scale

        // 1) Dominant color (assumed felt) via coarse histogram.
        val buckets = HashMap<Int, Int>()
        for (p in pixels) {
            val key = ((Color.red(p) shr 4) shl 8) or ((Color.green(p) shr 4) shl 4) or (Color.blue(p) shr 4)
            buckets[key] = (buckets[key] ?: 0) + 1
        }
        val dominantKey = buckets.maxByOrNull { it.value }?.key ?: 0
        val domR = ((dominantKey shr 8) and 0xF) shl 4
        val domG = ((dominantKey shr 4) and 0xF) shl 4
        val domB = (dominantKey and 0xF) shl 4

        fun colorDist(p: Int): Double {
            val dr = Color.red(p) - domR
            val dg = Color.green(p) - domG
            val db = Color.blue(p) - domB
            return sqrt((dr * dr + dg * dg + db * db).toDouble())
        }

        // 2) Table bounding box = bounding box of felt-colored pixels.
        var minX = aw; var maxX = 0; var minY = ah; var maxY = 0
        var feltCount = 0
        for (y in 0 until ah) {
            for (x in 0 until aw) {
                val p = pixels[y * aw + x]
                if (colorDist(p) < 42) {
                    feltCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        val tableRect: RectF? = if (feltCount > pixels.size * 0.12) {
            RectF(minX * toOriginal, minY * toOriginal, maxX * toOriginal, maxY * toOriginal)
        } else null

        val tLeft = if (tableRect != null) minX else 0
        val tTop = if (tableRect != null) minY else 0
        val tRight = if (tableRect != null) maxX else aw - 1
        val tBottom = if (tableRect != null) maxY else ah - 1
        val tW = max(1, tRight - tLeft)
        val tH = max(1, tBottom - tTop)

        // 3) Pockets: snap default corner/mid-edge positions to the nearest dark cluster nearby.
        val defaults = listOf(
            PointF(tLeft.toFloat(), tTop.toFloat()),
            PointF((tLeft + tRight) / 2f, tTop.toFloat()),
            PointF(tRight.toFloat(), tTop.toFloat()),
            PointF(tLeft.toFloat(), tBottom.toFloat()),
            PointF((tLeft + tRight) / 2f, tBottom.toFloat()),
            PointF(tRight.toFloat(), tBottom.toFloat())
        )
        val window = max(4, (min(tW, tH) * 0.12f).toInt())
        val pockets = mutableListOf<PointF>()
        for (d in defaults) {
            val cx = d.x.toInt(); val cy = d.y.toInt()
            var sumX = 0.0; var sumY = 0.0; var count = 0
            for (y in max(0, cy - window)..min(ah - 1, cy + window)) {
                for (x in max(0, cx - window)..min(aw - 1, cx + window)) {
                    val p = pixels[y * aw + x]
                    val brightness = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                    if (brightness < 60) {
                        sumX += x; sumY += y; count++
                    }
                }
            }
            val snapped = if (count > 3) PointF((sumX / count).toFloat(), (sumY / count).toFloat()) else d
            pockets.add(PointF(snapped.x * toOriginal, snapped.y * toOriginal))
        }

        // 4) Balls: connected components of "not felt, not near-black" pixels inside the table area.
        val visited = BooleanArray(aw * ah)
        val candidate = BooleanArray(aw * ah)
        for (y in tTop..tBottom) {
            for (x in tLeft..tRight) {
                val idx = y * aw + x
                val p = pixels[idx]
                val brightness = (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
                if (colorDist(p) > 46 && brightness > 55) candidate[idx] = true
            }
        }

        val expectedRadiusPx = min(tW, tH) * 0.045f // rough guess relative to table size
        val minArea = max(3, (expectedRadiusPx * expectedRadiusPx * 0.9f).toInt())
        val maxArea = max(minArea + 1, (expectedRadiusPx * expectedRadiusPx * 6.5f).toInt())

        data class Blob(val cx: Float, val cy: Float, val area: Int, val brightness: Float)
        val blobs = mutableListOf<Blob>()
        val stackX = IntArray(aw * ah)
        val stackY = IntArray(aw * ah)

        for (y in tTop..tBottom) {
            for (x in tLeft..tRight) {
                val startIdx = y * aw + x
                if (!candidate[startIdx] || visited[startIdx]) continue
                var sp = 0
                stackX[sp] = x; stackY[sp] = y; sp++
                visited[startIdx] = true
                var sumX = 0.0; var sumY = 0.0; var sumB = 0.0; var area = 0
                while (sp > 0) {
                    sp--
                    val cx0 = stackX[sp]; val cy0 = stackY[sp]
                    val cIdx = cy0 * aw + cx0
                    val p = pixels[cIdx]
                    sumX += cx0; sumY += cy0; area++
                    sumB += (Color.red(p) + Color.green(p) + Color.blue(p)) / 3.0
                    val neighbors = intArrayOf(cx0 - 1, cy0, cx0 + 1, cy0, cx0, cy0 - 1, cx0, cy0 + 1)
                    var n = 0
                    while (n < neighbors.size) {
                        val nx = neighbors[n]; val ny = neighbors[n + 1]
                        n += 2
                        if (nx < tLeft || nx > tRight || ny < tTop || ny > tBottom) continue
                        val nIdx = ny * aw + nx
                        if (!visited[nIdx] && candidate[nIdx] && sp < stackX.size) {
                            visited[nIdx] = true
                            stackX[sp] = nx; stackY[sp] = ny; sp++
                        }
                    }
                }
                if (area in minArea..maxArea) {
                    blobs.add(Blob((sumX / area).toFloat(), (sumY / area).toFloat(), area, (sumB / area).toFloat()))
                }
            }
        }

        val topBlobs = blobs.sortedByDescending { it.area }.take(16)
        val cueBlob = topBlobs.maxByOrNull { it.brightness }

        val balls = topBlobs.map { b ->
            val r = sqrt(b.area / Math.PI).toFloat()
            DetectedBall(
                x = b.cx * toOriginal,
                y = b.cy * toOriginal,
                radius = r * toOriginal,
                isCue = b === cueBlob && b.brightness > 165
            )
        }

        return Result(tableRect, pockets, balls)
    }
}
