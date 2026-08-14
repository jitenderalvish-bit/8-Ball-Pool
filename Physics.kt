package com.billiards.analyzer

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object Physics {

    /** Closest distance from point p to segment a-b. */
    fun pointSegmentDistance(p: PointF, a: PointF, b: PointF): Float {
        val ab = b.minus(a)
        val abLenSq = ab.x * ab.x + ab.y * ab.y
        if (abLenSq < 0.0001f) return dist(p, a)
        var t = dot(p.minus(a), ab) / abLenSq
        t = max(0f, min(1f, t))
        val closest = a.plus(ab.times(t))
        return dist(p, closest)
    }

    /** Ghost-ball position: point the cue ball's center must reach to send target ball toward pocket. */
    fun ghostBallPosition(target: PointF, pocket: PointF, radius: Float): PointF {
        val dir = target.minus(pocket).normalized()
        return target.plus(dir.times(2f * radius))
    }

    /** Cut angle in degrees between the cue's approach direction and the target's required travel direction.
     * 0 = straight in-line (easiest), close to 90 = extremely thin cut (hardest). */
    fun cutAngleDeg(cue: PointF, target: PointF, pocket: PointF): Float {
        val v1 = target.minus(cue).normalized()
        val v2 = pocket.minus(target).normalized()
        val cosA = max(-1f, min(1f, dot(v1, v2)))
        return Math.toDegrees(acos(cosA.toDouble())).toFloat()
    }

    /** Checks whether any of the given balls obstruct the segment a-b (within 2*radius collision distance).
     * Returns the closest obstruction point along the path, or null if clear. */
    fun findObstruction(a: PointF, b: PointF, others: List<Ball>, radius: Float): PointF? {
        var best: PointF? = null
        var bestT = Float.MAX_VALUE
        val ab = b.minus(a)
        val abLenSq = ab.x * ab.x + ab.y * ab.y
        if (abLenSq < 0.0001f) return null
        for (ball in others) {
            val center = PointF(ball.x, ball.y)
            var t = dot(center.minus(a), ab) / abLenSq
            if (t <= 0.02f || t >= 0.98f) continue // ignore near endpoints (the balls themselves)
            val d = pointSegmentDistance(center, a, b)
            if (d < radius * 2f) {
                if (t < bestT) {
                    bestT = t
                    best = a.plus(ab.times(t))
                }
            }
        }
        return best
    }

    /** Reflects point p across a horizontal line y = lineY. */
    private fun mirrorHorizontal(p: PointF, lineY: Float) = PointF(p.x, 2 * lineY - p.y)

    /** Reflects point p across a vertical line x = lineX. */
    private fun mirrorVertical(p: PointF, lineX: Float) = PointF(2 * lineX - p.x, p.y)

    data class BankOption(val contact: PointF, val totalDist: Float)

    /** Finds a single-cushion bank path from target to pocket using the mirror method.
     * Tries all 4 cushions and returns the shortest valid path, or null if none is geometrically valid. */
    fun findBankPath(target: PointF, pocket: PointF, table: RectF, radius: Float): BankOption? {
        val margin = radius * 1.2f
        val options = mutableListOf<BankOption>()

        // Top cushion
        run {
            val mirrored = mirrorHorizontal(pocket, table.top)
            val denom = mirrored.y - target.y
            if (kotlin.math.abs(denom) > 0.0001f) {
                val t = (table.top - target.y) / denom
                if (t in 0.05f..0.95f) {
                    val contact = target.plus(mirrored.minus(target).times(t))
                    if (contact.x in (table.left + margin)..(table.right - margin)) {
                        options.add(BankOption(contact, dist(target, contact) + dist(contact, pocket)))
                    }
                }
            }
        }
        // Bottom cushion
        run {
            val mirrored = mirrorHorizontal(pocket, table.bottom)
            val denom = mirrored.y - target.y
            if (kotlin.math.abs(denom) > 0.0001f) {
                val t = (table.bottom - target.y) / denom
                if (t in 0.05f..0.95f) {
                    val contact = target.plus(mirrored.minus(target).times(t))
                    if (contact.x in (table.left + margin)..(table.right - margin)) {
                        options.add(BankOption(contact, dist(target, contact) + dist(contact, pocket)))
                    }
                }
            }
        }
        // Left cushion
        run {
            val mirrored = mirrorVertical(pocket, table.left)
            val denom = mirrored.x - target.x
            if (kotlin.math.abs(denom) > 0.0001f) {
                val t = (table.left - target.x) / denom
                if (t in 0.05f..0.95f) {
                    val contact = target.plus(mirrored.minus(target).times(t))
                    if (contact.y in (table.top + margin)..(table.bottom - margin)) {
                        options.add(BankOption(contact, dist(target, contact) + dist(contact, pocket)))
                    }
                }
            }
        }
        // Right cushion
        run {
            val mirrored = mirrorVertical(pocket, table.right)
            val denom = mirrored.x - target.x
            if (kotlin.math.abs(denom) > 0.0001f) {
                val t = (table.right - target.x) / denom
                if (t in 0.05f..0.95f) {
                    val contact = target.plus(mirrored.minus(target).times(t))
                    if (contact.y in (table.top + margin)..(table.bottom - margin)) {
                        options.add(BankOption(contact, dist(target, contact) + dist(contact, pocket)))
                    }
                }
            }
        }

        return options.minByOrNull { it.totalDist }
    }

    /** Absolute travel-direction angle in degrees (0 = pointing right, measured clockwise on screen). */
    fun absoluteAngleDeg(from: PointF, to: PointF): Float {
        val v = to.minus(from)
        var deg = Math.toDegrees(kotlin.math.atan2(v.y.toDouble(), v.x.toDouble())).toFloat()
        if (deg < 0) deg += 360f
        return deg
    }

    /** Approximate deflection direction for a ball knocked out of a straight path:
     * pushed away from the line, roughly toward the obstructing ball's center. */
    private fun deflectionPoint(lineA: PointF, lineB: PointF, obstructionCenter: PointF, radius: Float): PointF {
        val onLine = run {
            val ab = lineB.minus(lineA)
            val abLenSq = ab.x * ab.x + ab.y * ab.y
            val t = if (abLenSq > 0.0001f) (dot(obstructionCenter.minus(lineA), ab) / abLenSq).coerceIn(0f, 1f) else 0f
            lineA.plus(ab.times(t))
        }
        val pushDir = obstructionCenter.minus(onLine).normalized()
        val travelDir = lineB.minus(lineA).normalized()
        // Deflected ball moves mostly along the push direction, with a bit of forward momentum.
        val blended = PointF(pushDir.x * 0.8f + travelDir.x * 0.5f, pushDir.y * 0.8f + travelDir.y * 0.5f).normalized()
        return obstructionCenter.plus(blended.times(radius * 6f))
    }

    /**
     * Computes the full shot prediction.
     */
    fun computeShot(
        cue: Ball,
        target: Ball,
        pocket: Pocket,
        allBalls: List<Ball>,
        table: RectF,
        radius: Float,
        power: Power,
        powerFactor: Float,
        bankRequested: Boolean
    ): ShotResult {
        val cueP = PointF(cue.x, cue.y)
        val targetP = PointF(target.x, target.y)
        val pocketP = PointF(pocket.x, pocket.y)

        val ghost = ghostBallPosition(targetP, pocketP, radius)

        val others = allBalls.filter { it.id != cue.id && it.id != target.id }
        val cueObstruction = findObstruction(cueP, ghost, others, radius)
        val cueObstructionBall = cueObstruction?.let { p ->
            others.minByOrNull { dist(PointF(it.x, it.y), p) }
        }

        val objectAngle = cutAngleDeg(cueP, targetP, pocketP)
        val cueAngle = absoluteAngleDeg(cueP, ghost)

        val diag = sqrt(
            ((table.right - table.left) * (table.right - table.left) +
                (table.bottom - table.top) * (table.bottom - table.top)).toDouble()
        ).toFloat()
        val maxTravel = diag * powerFactor

        var isBank = false
        var bankFailed = false
        val targetPath = mutableListOf<PointF>()
        targetPath.add(targetP)

        var totalTargetDist: Float
        if (bankRequested) {
            val bank = findBankPath(targetP, pocketP, table, radius)
            if (bank != null) {
                isBank = true
                targetPath.add(bank.contact)
                targetPath.add(pocketP)
                totalTargetDist = bank.totalDist
            } else {
                bankFailed = true
                targetPath.add(pocketP)
                totalTargetDist = dist(targetP, pocketP)
            }
        } else {
            targetPath.add(pocketP)
            totalTargetDist = dist(targetP, pocketP)
        }

        // Check whether another ball blocks the target ball's own route to the pocket.
        var targetObstruction: PointF? = null
        var targetObstructionBall: Ball? = null
        if (cueObstruction == null) {
            for (i in 0 until targetPath.size - 1) {
                val a = targetPath[i]
                val b = targetPath[i + 1]
                val remaining = others.filter { it.id != target.id }
                val hit = findObstruction(a, b, remaining, radius)
                if (hit != null) {
                    targetObstruction = hit
                    targetObstructionBall = remaining.minByOrNull { dist(PointF(it.x, it.y), hit) }
                    break
                }
            }
        }

        val hasEnoughPower = totalTargetDist <= maxTravel
        var pocketReached = hasEnoughPower && cueObstruction == null && targetObstruction == null && !bankFailed

        // If underpowered, shorten the final path segment to show where the ball actually stops.
        var finalPath = targetPath.toList()
        if (!hasEnoughPower && cueObstruction == null) {
            val overrun = totalTargetDist - maxTravel
            val lastIdx = finalPath.size - 1
            val segStart = finalPath[lastIdx - 1]
            val segEnd = finalPath[lastIdx]
            val segLen = dist(segStart, segEnd)
            val travelable = max(0f, segLen - overrun)
            val stopPoint = if (segLen > 0.001f) {
                segStart.plus(segEnd.minus(segStart).normalized().times(travelable))
            } else segStart
            finalPath = finalPath.toMutableList().also { it[lastIdx] = stopPoint }
        }
        // If target's own path is blocked, truncate path at the obstruction point.
        if (targetObstruction != null) {
            finalPath = listOf(targetP, targetObstruction)
        }

        // Secondary (approximate) deflection path of whichever ball was struck out of line.
        var secondaryDeflection: PointF? = null
        if (cueObstruction != null && cueObstructionBall != null) {
            secondaryDeflection = deflectionPoint(cueP, ghost, PointF(cueObstructionBall.x, cueObstructionBall.y), radius)
        } else if (targetObstruction != null && targetObstructionBall != null) {
            secondaryDeflection = deflectionPoint(targetP, pocketP, PointF(targetObstructionBall.x, targetObstructionBall.y), radius)
        }

        // Confidence scoring
        var confidence = 95f
        confidence -= objectAngle * 0.75f
        if (cueObstruction != null) confidence -= 35f
        if (targetObstruction != null) confidence -= 30f
        if (isBank) confidence -= 15f
        if (bankFailed) confidence -= 40f
        if (!hasEnoughPower) confidence -= 30f
        confidence = confidence.coerceIn(5f, 97f)

        if (cueObstruction != null || targetObstruction != null || bankFailed || !hasEnoughPower) pocketReached = false

        val verdict = when {
            confidence >= 70 && pocketReached -> Verdict.GREEN
            confidence >= 40 -> Verdict.YELLOW
            else -> Verdict.RED
        }

        val followDir = ghost.minus(cueP).normalized()
        val followLen = 18f + 30f * powerFactor
        val followThrough = ghost.plus(followDir.times(followLen))
        val cueFinalRest = if (cueObstruction != null) cueObstruction else followThrough

        val entryAngle = if (finalPath.size >= 2) {
            absoluteAngleDeg(finalPath[finalPath.size - 2], finalPath[finalPath.size - 1])
        } else objectAngle

        return ShotResult(
            ghostBallPoint = ghost,
            cueObstructionPoint = cueObstruction,
            targetObstructionPoint = targetObstruction,
            secondaryDeflection = secondaryDeflection,
            targetPath = finalPath,
            pocketReached = pocketReached,
            cueAngleDeg = cueAngle,
            objectAngleDeg = objectAngle,
            entryAngleDeg = entryAngle,
            confidence = confidence.toInt(),
            isBank = isBank,
            bankFailed = bankFailed,
            cueFollowThrough = followThrough,
            cueFinalRest = cueFinalRest,
            verdict = verdict
        )
    }
}
