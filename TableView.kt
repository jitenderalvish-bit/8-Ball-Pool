package com.billiards.analyzer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

class TableView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---- state ----
    private var bitmap: Bitmap? = null
    private val drawMatrix = Matrix()
    private var imageScale = 1f
    private var imageDx = 0f
    private var imageDy = 0f

    var tableRect: RectF = RectF()
        private set
    private var tableInitialized = false

    val pockets = mutableListOf<Pocket>()
    val balls = mutableListOf<Ball>()

    var mode: Mode = Mode.TABLE
        set(value) {
            field = value
            ensurePocketsInitialized()
            invalidate()
        }

    var selectedPocketId: Int? = null
    var shotResult: ShotResult? = null

    var ballRadius = 24f
        private set

    private var nextBallId = 1
    private var nextPocketId = 1

    // drag state
    private var draggingCorner = -1 // 0 = TL, 1 = BR
    private var draggingBallIndex = -1
    private var draggingPocketIndex = -1
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var removeArmed = false

    fun armRemoveBall() {
        removeArmed = true
    }

    private val touchSlop = 16f
    private val cornerHandleRadius = 28f

    // ---- paints (reused, allocated once) ----
    private val paintTable = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 4f
        alpha = 180
    }
    private val paintHandle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.YELLOW
    }
    private val paintPocket = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }
    private val paintPocketStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
    }
    private val paintPocketSelected = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.CYAN
        strokeWidth = 6f
    }
    private val paintBall = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.LTGRAY
    }
    private val paintBallStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 3f
    }
    private val paintCue = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val paintTarget = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF7043")
    }
    private val paintGhost = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val paintFollow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.GRAY
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
    }
    private val paintObstruction = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.RED
        strokeWidth = 5f
    }
    private val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        isFakeBoldText = true
    }
    private val lineWidth = 6f

    private fun trajPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        this.color = color
        strokeWidth = lineWidth
        strokeCap = Paint.Cap.ROUND
    }

    // ---- public API ----

    fun setImage(bmp: Bitmap, onReady: (() -> Unit)? = null) {
        bitmap?.recycle()
        bitmap = bmp
        tableInitialized = false
        post {
            updateImageMapping()
            setupDefaultTable()
            onReady?.invoke()
        }
        invalidate()
    }

    fun releaseImage() {
        bitmap?.recycle()
        bitmap = null
    }

    fun resetAll() {
        pockets.clear()
        balls.clear()
        selectedPocketId = null
        shotResult = null
        nextBallId = 1
        nextPocketId = 1
        tableInitialized = false
        setupDefaultTable()
        invalidate()
    }

    fun clearShot() {
        shotResult = null
        invalidate()
    }

    /** Converts a point in original-bitmap pixel coordinates to on-screen view coordinates. */
    fun bitmapToView(x: Float, y: Float): PointF = PointF(x * imageScale + imageDx, y * imageScale + imageDy)

    fun hasImage(): Boolean = bitmap != null

    /** Applies auto-detection results (in bitmap pixel space) to the view. Any existing manual
     * edits are replaced since this is meant to run right after import. */
    fun applyDetection(detected: Detector.Result) {
        detected.tableRect?.let { r ->
            val tl = bitmapToView(r.left, r.top)
            val br = bitmapToView(r.right, r.bottom)
            tableRect = RectF(tl.x, tl.y, br.x, br.y)
            tableInitialized = true
        }
        if (detected.pockets.isNotEmpty()) {
            pockets.clear()
            for (p in detected.pockets) {
                val v = bitmapToView(p.x, p.y)
                pockets.add(Pocket(v.x, v.y, nextPocketId++))
            }
        } else {
            rebuildPockets()
        }
        if (detected.balls.isNotEmpty()) {
            balls.clear()
            for (b in detected.balls) {
                val v = bitmapToView(b.x, b.y)
                balls.add(Ball(v.x, v.y, nextBallId++, isCue = b.isCue))
            }
        }
        shotResult = null
        invalidate()
    }

    fun cueBall(): Ball? = balls.firstOrNull { it.isCue }
    fun targetBall(): Ball? = balls.firstOrNull { it.isTarget }
    fun selectedPocket(): Pocket? = pockets.firstOrNull { it.id == selectedPocketId }

    fun calculateShot(power: Power, powerSliderValue: Int, bankRequested: Boolean): ShotResult? {
        val cue = cueBall() ?: return null
        val target = targetBall() ?: return null
        val pocket = selectedPocket() ?: return null
        val powerFactor = Power.fromSlider(powerSliderValue)
        val result = Physics.computeShot(cue, target, pocket, balls, tableRect, ballRadius, power, powerFactor, bankRequested)
        shotResult = result
        invalidate()
        return result
    }

    // ---- setup ----

    private fun setupDefaultTable() {
        if (tableInitialized || width == 0 || height == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val marginX = w * 0.08f
        val marginY = h * 0.12f
        tableRect = RectF(marginX, marginY, w - marginX, h - marginY)
        ballRadius = min(tableRect.width(), tableRect.height()) * 0.028f
        tableInitialized = true
        pockets.clear()
        rebuildPockets()
    }

    private fun ensurePocketsInitialized() {
        if (mode == Mode.POCKETS && pockets.isEmpty()) {
            rebuildPockets()
        }
    }

    private fun rebuildPockets() {
        pockets.clear()
        val r = tableRect
        pockets.add(Pocket(r.left, r.top, nextPocketId++))
        pockets.add(Pocket((r.left + r.right) / 2f, r.top, nextPocketId++))
        pockets.add(Pocket(r.right, r.top, nextPocketId++))
        pockets.add(Pocket(r.left, r.bottom, nextPocketId++))
        pockets.add(Pocket((r.left + r.right) / 2f, r.bottom, nextPocketId++))
        pockets.add(Pocket(r.right, r.bottom, nextPocketId++))
    }

    // ---- touch handling ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; moved = false
                if (removeArmed) {
                    removeArmed = false
                    removeBallNear(x, y)
                    return true
                }
                when (mode) {
                    Mode.TABLE -> {
                        val tl = PointF(tableRect.left, tableRect.top)
                        val br = PointF(tableRect.right, tableRect.bottom)
                        draggingCorner = when {
                            dist(PointF(x, y), tl) < cornerHandleRadius -> 0
                            dist(PointF(x, y), br) < cornerHandleRadius -> 1
                            else -> -1
                        }
                    }
                    Mode.POCKETS -> {
                        draggingPocketIndex = pockets.indexOfFirst { dist(PointF(it.x, it.y), PointF(x, y)) < ballRadius * 1.8f }
                    }
                    Mode.BALLS -> {
                        draggingBallIndex = balls.indexOfFirst { dist(PointF(it.x, it.y), PointF(x, y)) < ballRadius * 1.4f }
                    }
                    Mode.CUE -> {
                        val idx = balls.indexOfFirst { dist(PointF(it.x, it.y), PointF(x, y)) < ballRadius * 1.4f }
                        if (idx >= 0) {
                            balls.forEach { it.isCue = false }
                            balls[idx].isCue = true
                            if (balls[idx].isTarget) balls[idx].isTarget = false
                            shotResult = null
                            invalidate()
                        }
                    }
                    Mode.TARGET -> {
                        val idx = balls.indexOfFirst { dist(PointF(it.x, it.y), PointF(x, y)) < ballRadius * 1.4f }
                        if (idx >= 0 && !balls[idx].isCue) {
                            balls.forEach { it.isTarget = false }
                            balls[idx].isTarget = true
                            shotResult = null
                            invalidate()
                        }
                    }
                    Mode.POCKET_SELECT -> {
                        val p = pockets.firstOrNull { dist(PointF(it.x, it.y), PointF(x, y)) < ballRadius * 1.8f }
                        if (p != null) {
                            selectedPocketId = p.id
                            shotResult = null
                            invalidate()
                        }
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (dist(PointF(x, y), PointF(downX, downY)) > touchSlop) moved = true
                when (mode) {
                    Mode.TABLE -> {
                        if (draggingCorner == 0) {
                            tableRect = RectF(x, y, tableRect.right, tableRect.bottom)
                            invalidate()
                        } else if (draggingCorner == 1) {
                            tableRect = RectF(tableRect.left, tableRect.top, x, y)
                            invalidate()
                        }
                    }
                    Mode.POCKETS -> {
                        if (draggingPocketIndex >= 0) {
                            pockets[draggingPocketIndex].x = x
                            pockets[draggingPocketIndex].y = y
                            invalidate()
                        }
                    }
                    Mode.BALLS -> {
                        if (draggingBallIndex >= 0) {
                            balls[draggingBallIndex].x = x
                            balls[draggingBallIndex].y = y
                            shotResult = null
                            invalidate()
                        }
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_UP -> {
                if (mode == Mode.BALLS && draggingBallIndex < 0 && !moved) {
                    // tap on empty space -> add new ball
                    balls.add(Ball(x, y, nextBallId++))
                    shotResult = null
                    invalidate()
                }
                draggingCorner = -1
                draggingBallIndex = -1
                draggingPocketIndex = -1
            }
        }
        return true
    }

    /** Long-press style removal: call from an external long-press detector if desired.
     * Kept simple here: double behavior isn't required for MVP; removal handled via RESET or by
     * dragging a ball off-screen edge is avoided—so provide direct removal helper instead. */
    fun removeBallNear(x: Float, y: Float): Boolean {
        val idx = balls.indexOfFirst { dist(PointF(it.x, it.y), PointF(x, y)) < ballRadius * 1.4f }
        if (idx >= 0) {
            balls.removeAt(idx)
            shotResult = null
            invalidate()
            return true
        }
        return false
    }

    // ---- drawing ----

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateImageMapping()
        setupDefaultTable()
    }

    private fun updateImageMapping() {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        imageScale = min(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
        imageDx = (width - bmp.width * imageScale) / 2f
        imageDy = (height - bmp.height * imageScale) / 2f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        bitmap?.let { bmp ->
            updateImageMapping()
            drawMatrix.reset()
            drawMatrix.postScale(imageScale, imageScale)
            drawMatrix.postTranslate(imageDx, imageDy)
            canvas.drawBitmap(bmp, drawMatrix, null)
        }

        // table boundary
        canvas.drawRoundRect(tableRect, 12f, 12f, paintTable)
        if (mode == Mode.TABLE) {
            canvas.drawCircle(tableRect.left, tableRect.top, 14f, paintHandle)
            canvas.drawCircle(tableRect.right, tableRect.bottom, 14f, paintHandle)
        }

        // pockets
        for (p in pockets) {
            canvas.drawCircle(p.x, p.y, ballRadius * 0.9f, paintPocket)
            canvas.drawCircle(p.x, p.y, ballRadius * 0.9f, paintPocketStroke)
            if (p.id == selectedPocketId) {
                canvas.drawCircle(p.x, p.y, ballRadius * 1.3f, paintPocketSelected)
            }
        }

        // balls
        for (b in balls) {
            val paint = when {
                b.isCue -> paintCue
                b.isTarget -> paintTarget
                else -> paintBall
            }
            canvas.drawCircle(b.x, b.y, ballRadius, paint)
            canvas.drawCircle(b.x, b.y, ballRadius, paintBallStroke)
        }

        // trajectory
        shotResult?.let { r -> drawTrajectory(canvas, r) }
    }

    private val paintSecondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#29B6F6")
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(9f, 9f), 0f)
    }
    private val paintFinalPos = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B0BEC5")
    }
    private val paintTextBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#B0000000")
    }

    private fun drawTrajectory(canvas: Canvas, r: ShotResult) {
        val cue = cueBall() ?: return
        val trajColor = when (r.verdict) {
            Verdict.GREEN -> Color.parseColor("#4CAF50")
            Verdict.YELLOW -> Color.parseColor("#FFEB3B")
            Verdict.RED -> Color.parseColor("#F44336")
        }
        val paintTraj = trajPaint(trajColor)

        // cue -> collision point (primary, solid, long)
        val cueP = PointF(cue.x, cue.y)
        val approachEnd = r.cueObstructionPoint ?: r.ghostBallPoint
        canvas.drawLine(cueP.x, cueP.y, approachEnd.x, approachEnd.y, paintTraj)

        if (r.cueObstructionPoint != null) {
            drawObstructionMarker(canvas, r.cueObstructionPoint)
            r.secondaryDeflection?.let { sec ->
                canvas.drawLine(r.cueObstructionPoint.x, r.cueObstructionPoint.y, sec.x, sec.y, paintSecondary)
            }
        } else {
            // ghost ball marker at collision point
            canvas.drawCircle(r.ghostBallPoint.x, r.ghostBallPoint.y, ballRadius, paintGhost)

            // cue follow-through (cosmetic, secondary/dashed)
            canvas.drawLine(
                r.ghostBallPoint.x, r.ghostBallPoint.y,
                r.cueFollowThrough.x, r.cueFollowThrough.y, paintFollow
            )
            canvas.drawCircle(r.cueFinalRest.x, r.cueFinalRest.y, 8f, paintFinalPos)

            // target ball path (through any bank cushion contact) to pocket — primary trajectory
            for (i in 0 until r.targetPath.size - 1) {
                val a = r.targetPath[i]
                val b = r.targetPath[i + 1]
                canvas.drawLine(a.x, a.y, b.x, b.y, paintTraj)
            }
            if (r.isBank && r.targetPath.size >= 3) {
                val contact = r.targetPath[1]
                canvas.drawCircle(contact.x, contact.y, 12f, paintHandle)
            }

            if (r.targetObstructionPoint != null) {
                drawObstructionMarker(canvas, r.targetObstructionPoint)
                r.secondaryDeflection?.let { sec ->
                    canvas.drawLine(r.targetObstructionPoint.x, r.targetObstructionPoint.y, sec.x, sec.y, paintSecondary)
                }
            } else {
                // final resting marker + entry-direction arrow at the last path point
                val last = r.targetPath.last()
                canvas.drawCircle(last.x, last.y, 7f, paintFinalPos)
                if (r.pocketReached) drawEntryArrow(canvas, r)
            }
        }

        drawHud(canvas, r)
    }

    private fun drawObstructionMarker(canvas: Canvas, p: PointF) {
        canvas.drawCircle(p.x, p.y, ballRadius, paintObstruction)
        canvas.drawLine(p.x - 14, p.y - 14, p.x + 14, p.y + 14, paintObstruction)
        canvas.drawLine(p.x - 14, p.y + 14, p.x + 14, p.y - 14, paintObstruction)
    }

    private fun drawEntryArrow(canvas: Canvas, r: ShotResult) {
        val pocket = selectedPocket() ?: return
        val p = PointF(pocket.x, pocket.y)
        val rad = Math.toRadians(r.entryAngleDeg.toDouble())
        val dir = PointF(kotlin.math.cos(rad).toFloat(), kotlin.math.sin(rad).toFloat())
        val tip = p.plus(dir.times(ballRadius * 1.6f))
        val back = p
        val leftWing = PointF(
            tip.x - dir.x * 16f + (-dir.y) * 8f,
            tip.y - dir.y * 16f + dir.x * 8f
        )
        val rightWing = PointF(
            tip.x - dir.x * 16f - (-dir.y) * 8f,
            tip.y - dir.y * 16f - dir.x * 8f
        )
        canvas.drawLine(back.x, back.y, tip.x, tip.y, paintHandle)
        canvas.drawLine(tip.x, tip.y, leftWing.x, leftWing.y, paintHandle)
        canvas.drawLine(tip.x, tip.y, rightWing.x, rightWing.y, paintHandle)
    }

    private fun drawHud(canvas: Canvas, r: ShotResult) {
        val verdictLabel = when (r.verdict) {
            Verdict.GREEN -> "LIKELY POCKET"
            Verdict.YELLOW -> "BORDERLINE"
            Verdict.RED -> "LIKELY MISS"
        }
        val lines = mutableListOf(
            "Cue angle: ${r.cueAngleDeg.toInt()}°   Object angle: ${r.objectAngleDeg.toInt()}°",
            "Entry: ${r.entryAngleDeg.toInt()}°   Confidence: ${r.confidence}% ($verdictLabel)",
            "Estimated prediction — not a guarantee"
        )
        if (r.isBank) lines.add(if (r.bankFailed) "No valid bank path found" else "Bank shot: 1 cushion contact")
        if (r.cueObstructionPoint != null) lines.add("Cue path blocked by another ball")
        if (r.targetObstructionPoint != null) lines.add("Target path blocked by another ball")

        val padding = 12f
        var ty = 34f
        var maxW = 0f
        for (line in lines) maxW = maxOf(maxW, paintText.measureText(line))
        canvas.drawRect(8f, 8f, 8f + maxW + padding * 2, 8f + lines.size * 40f, paintTextBg)
        paintText.color = Color.WHITE
        for (line in lines) {
            canvas.drawText(line, 8f + padding, ty, paintText)
            ty += 40f
        }
    }
}
