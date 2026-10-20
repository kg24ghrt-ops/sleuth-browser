package com.sleuth.browser.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * The evidence lab canvas. A captured page can be marked up, boxed, circled,
 * arrowed, labelled or redacted before it is filed.
 */
class AnnotationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Tool { PEN, MARKER, BOX, CIRCLE, ARROW, TEXT, REDACT }

    interface Listener {
        fun onTextRequested(viewX: Float, viewY: Float)
    }

    var listener: Listener? = null

    private var original: Bitmap? = null
    private var working: Bitmap? = null
    private val marks = ArrayList<Mark>()
    private val redactions = ArrayList<RectF>()

    var tool: Tool = Tool.PEN
    var strokeColor: Int = Color.RED

    private var activePath: Path? = null
    private var activeRect: RectF? = null
    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private sealed class Mark {
        class Freehand(val path: Path, val color: Int, val width: Float) : Mark()
        class Shape(val kind: Tool, val rect: RectF, val color: Int, val width: Float) : Mark()
        class Label(val text: String, val x: Float, val y: Float, val color: Int, val size: Float) : Mark()
    }

    fun setBitmap(bitmap: Bitmap) {
        original = bitmap
        marks.clear()
        redactions.clear()
        rebuildWorking()
        updateGeometry()
        invalidate()
    }

    fun hasEdits(): Boolean = marks.isNotEmpty() || redactions.isNotEmpty()

    fun undo() {
        if (marks.isNotEmpty()) {
            marks.removeAt(marks.lastIndex)
        } else if (redactions.isNotEmpty()) {
            redactions.removeAt(redactions.lastIndex)
            rebuildWorking()
        }
        invalidate()
    }

    fun clearAll() {
        marks.clear()
        redactions.clear()
        rebuildWorking()
        invalidate()
    }

    fun addLabel(text: String, viewX: Float, viewY: Float) {
        val bitmap = original ?: return
        if (text.isBlank()) return
        val x = viewToBitmapX(viewX)
        val y = viewToBitmapY(viewY)
        marks.add(Mark.Label(text, x, y, strokeColor, bitmap.width * 0.032f))
        invalidate()
    }

    fun export(): Bitmap? {
        val base = working ?: return null
        val out = base.copy(Bitmap.Config.ARGB_8888, true) ?: return null
        val canvas = Canvas(out)
        for (mark in marks) {
            drawMark(canvas, mark)
        }
        return out
    }

    // ------------------------------------------------------------- internals

    private fun strokeWidth(): Float {
        val bitmap = original ?: return 8f
        val base = bitmap.width * 0.005f
        return if (tool == Tool.MARKER) base * 3.2f else base
    }

    private fun rebuildWorking() {
        val source = original ?: return
        val copy = source.copy(Bitmap.Config.ARGB_8888, true) ?: return
        for (region in redactions) {
            pixelate(copy, region)
        }
        working = copy
    }

    private fun pixelate(bitmap: Bitmap, region: RectF) {
        val left = region.left.toInt().coerceIn(0, bitmap.width - 1)
        val top = region.top.toInt().coerceIn(0, bitmap.height - 1)
        val right = region.right.toInt().coerceIn(left + 1, bitmap.width)
        val bottom = region.bottom.toInt().coerceIn(top + 1, bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width <= 0 || height <= 0) return
        val patch = Bitmap.createBitmap(bitmap, left, top, width, height)
        val block = (bitmap.width * 0.012f).toInt().coerceAtLeast(6)
        val small = Bitmap.createScaledBitmap(
            patch,
            (width / block).coerceAtLeast(1),
            (height / block).coerceAtLeast(1),
            true
        )
        val scaled = Bitmap.createScaledBitmap(small, width, height, false)
        Canvas(bitmap).drawBitmap(scaled, left.toFloat(), top.toFloat(), null)
    }

    private fun updateGeometry() {
        val bitmap = original ?: return
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return
        scale = min(viewWidth / bitmap.width, viewHeight / bitmap.height)
        offsetX = (viewWidth - bitmap.width * scale) / 2f
        offsetY = (viewHeight - bitmap.height * scale) / 2f
    }

    private fun viewToBitmapX(x: Float): Float = (x - offsetX) / scale
    private fun viewToBitmapY(y: Float): Float = (y - offsetY) / scale

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateGeometry()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val bitmap = working ?: return
        updateGeometry()
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        for (mark in marks) {
            drawMark(canvas, mark)
        }
        activePath?.let { drawFreehand(canvas, it, strokeColor, strokeWidth()) }
        activeRect?.let { drawShape(canvas, tool, it, strokeColor, strokeWidth()) }
        canvas.restore()
    }

    private fun drawMark(canvas: Canvas, mark: Mark) {
        when (mark) {
            is Mark.Freehand -> drawFreehand(canvas, mark.path, mark.color, mark.width)
            is Mark.Shape -> drawShape(canvas, mark.kind, mark.rect, mark.color, mark.width)
            is Mark.Label -> drawLabel(canvas, mark)
        }
    }

    private fun drawFreehand(canvas: Canvas, path: Path, color: Int, width: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = width
        paint.color = color
        canvas.drawPath(path, paint)
    }

    private fun drawShape(canvas: Canvas, kind: Tool, rect: RectF, color: Int, width: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = width
        paint.color = color
        when (kind) {
            Tool.CIRCLE -> canvas.drawOval(rect, paint)
            Tool.ARROW -> {
                canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, paint)
                drawArrowHead(canvas, rect, paint)
            }
            else -> canvas.drawRect(rect, paint)
        }
    }

    private fun drawArrowHead(canvas: Canvas, rect: RectF, paint: Paint) {
        val angle = Math.atan2((rect.bottom - rect.top).toDouble(), (rect.right - rect.left).toDouble())
        val size = paint.strokeWidth * 4f
        val x = rect.right
        val y = rect.bottom
        val left = (x - size * Math.cos(angle - Math.PI / 6)).toFloat()
        val leftY = (y - size * Math.sin(angle - Math.PI / 6)).toFloat()
        val right = (x - size * Math.cos(angle + Math.PI / 6)).toFloat()
        val rightY = (y - size * Math.sin(angle + Math.PI / 6)).toFloat()
        val head = Path().apply {
            moveTo(x, y)
            lineTo(left, leftY)
            lineTo(right, rightY)
            close()
        }
        val fill = Paint(paint)
        fill.style = Paint.Style.FILL
        canvas.drawPath(head, fill)
    }

    private fun drawLabel(canvas: Canvas, label: Mark.Label) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = label.color
        paint.textSize = label.size
        paint.style = Paint.Style.FILL
        val textWidth = paint.measureText(label.text)
        val background = RectF(
            label.x - label.size * 0.2f,
            label.y - label.size * 1.05f,
            label.x + textWidth + label.size * 0.2f,
            label.y + label.size * 0.25f
        )
        val backing = Paint(Paint.ANTI_ALIAS_FLAG)
        backing.color = Color.argb(190, 13, 17, 23)
        canvas.drawRect(background, backing)
        canvas.drawText(label.text, label.x, label.y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (original == null) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (tool == Tool.TEXT) {
                    listener?.onTextRequested(event.x, event.y)
                    return true
                }
                val startX = viewToBitmapX(event.x)
                val startY = viewToBitmapY(event.y)
                activePath = Path().apply { moveTo(startX, startY) }
                activeRect = RectF(startX, startY, startX, startY)
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val x = viewToBitmapX(event.x)
                val y = viewToBitmapY(event.y)
                if (tool == Tool.PEN || tool == Tool.MARKER) {
                    activePath?.lineTo(x, y)
                } else {
                    val start = activeRect
                    if (start != null) {
                        activeRect = RectF(
                            min(start.left, x),
                            min(start.top, y),
                            maxOf(start.right, x),
                            maxOf(start.bottom, y)
                        )
                    }
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                commit()
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun commit() {
        val path = activePath
        val rect = activeRect
        activePath = null
        activeRect = null

        when (tool) {
            Tool.PEN, Tool.MARKER -> {
                if (path != null) {
                    marks.add(Mark.Freehand(path, strokeColor, strokeWidth()))
                }
            }

            Tool.REDACT -> {
                if (rect != null && rect.width() > 4f && rect.height() > 4f) {
                    redactions.add(RectF(rect))
                    rebuildWorking()
                }
            }

            Tool.BOX, Tool.CIRCLE, Tool.ARROW -> {
                if (rect != null && rect.width() > 4f && rect.height() > 4f) {
                    marks.add(Mark.Shape(tool, RectF(rect), strokeColor, strokeWidth()))
                }
            }

            Tool.TEXT -> Unit
        }
    }
}
