package com.thomas.sudoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.thomas.sudoku.engine.DayState
import com.thomas.sudoku.engine.Trophy

fun Trophy?.color(): Int = when (this) {
    Trophy.OR -> Colors.OR
    Trophy.ARGENT -> Colors.ARGENT
    Trophy.BRONZE -> Colors.BRONZE
    null -> Colors.LOCKED
}

/** Dessins partagés : la coupe des trophées et les pastilles du calendrier. */
object Shapes {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val r = RectF()

    /** Une coupe dessinée dans un carré de 48 × 48, ramené à [box]. Avec [star], une étoile sur la coupe. */
    fun cup(canvas: Canvas, box: RectF, color: Int, star: Boolean = false) {
        val s = box.width() / 48f
        canvas.save()
        canvas.translate(box.left, box.top)
        canvas.scale(s, s)
        fill.color = color
        path.reset()
        path.moveTo(14f, 8f)
        path.lineTo(34f, 8f)
        path.lineTo(34f, 16f)
        r.set(14f, 6f, 34f, 26f)
        path.arcTo(r, 0f, 180f, false)
        path.close()
        canvas.drawPath(path, fill)
        stroke.color = color
        stroke.strokeWidth = 3f
        path.reset()
        path.moveTo(14f, 11f)
        path.lineTo(8f, 11f)
        path.quadTo(8f, 18f, 15f, 18f)
        path.moveTo(34f, 11f)
        path.lineTo(40f, 11f)
        path.quadTo(40f, 18f, 33f, 18f)
        canvas.drawPath(path, stroke)
        canvas.drawRect(21f, 25f, 27f, 33f, fill)
        r.set(15f, 33f, 33f, 39f)
        canvas.drawRoundRect(r, 2f, 2f, fill)
        if (star) {
            fill.color = 0xFFFFF6DC.toInt()
            star(canvas, 24f, 15f, 5.2f, fill)
        }
        canvas.restore()
    }

    fun star(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        path.reset()
        for (k in 0 until 10) {
            val a = Math.PI / 2 + k * Math.PI / 5
            val rr = if (k % 2 == 0) radius else radius * 0.45f
            val x = cx + (rr * Math.cos(a)).toFloat()
            val y = cy - (rr * Math.sin(a)).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    /**
     * Une pastille du calendrier.
     * [today] = entourée ; [playable] = faux pour les jours à venir ou avant le début ; [selected] = anneau autour.
     */
    fun dot(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        day: Int?,
        state: DayState,
        today: Boolean,
        playable: Boolean,
        selected: Boolean,
        density: Float,
    ) {
        if (selected) {
            stroke.color = Colors.SAGE_SOFT
            stroke.strokeWidth = 2f * density
            canvas.drawCircle(cx, cy, radius + 3.5f * density, stroke)
        }
        var textColor = Colors.FG
        when {
            state == DayState.SAME_DAY -> {
                fill.color = Colors.SAGE
                canvas.drawCircle(cx, cy, radius, fill)
                textColor = Colors.WHITE
            }
            state == DayState.IN_MONTH -> {
                fill.color = Colors.LIGHT_GREEN
                canvas.drawCircle(cx, cy, radius, fill)
                textColor = Colors.DEEP_GREEN
            }
            state == DayState.LATE -> {
                fill.color = Colors.CARD
                canvas.drawCircle(cx, cy, radius, fill)
                stroke.color = Colors.SAGE_SOFT
                stroke.strokeWidth = 2f * density
                canvas.drawCircle(cx, cy, radius - density, stroke)
                textColor = Colors.SAGE
            }
            today -> {
                fill.color = Colors.CARD
                canvas.drawCircle(cx, cy, radius, fill)
                stroke.color = Colors.SAGE
                stroke.strokeWidth = 2.5f * density
                canvas.drawCircle(cx, cy, radius - 1.25f * density, stroke)
                textColor = Colors.SAGE
            }
            playable -> {
                // Pas encore faite : rayures grises.
                canvas.save()
                path.reset()
                path.addCircle(cx, cy, radius, Path.Direction.CW)
                canvas.clipPath(path)
                stroke.color = Colors.STRIPE
                stroke.strokeWidth = 2f * density
                var x = cx - radius * 2
                while (x < cx + radius * 2) {
                    canvas.drawLine(x, cy + radius, x + radius * 2, cy - radius, stroke)
                    x += 5f * density
                }
                canvas.restore()
                stroke.color = 0xFFB9C4BD.toInt()
                stroke.strokeWidth = 1.5f * density
                canvas.drawCircle(cx, cy, radius - 0.75f * density, stroke)
                textColor = Colors.MUTED
            }
            else -> textColor = Colors.FAINT
        }
        if (day != null) {
            text.textSize = radius * 0.78f
            text.typeface = if (state != DayState.TODO || today) Fonts.extra else Fonts.medium
            text.color = textColor
            canvas.drawText(day.toString(), cx, cy - (text.descent() + text.ascent()) / 2f, text)
        }
        // Petit badge : étoile, coche ou flèche retour.
        if (state != DayState.TODO) {
            val br = radius * 0.42f
            val bx = cx + radius * 0.72f
            val by = cy - radius * 0.72f
            fill.color = Colors.BG
            canvas.drawCircle(bx, by, br + 1.5f * density, fill)
            fill.color = when (state) {
                DayState.SAME_DAY -> Colors.GOLD
                DayState.IN_MONTH -> Colors.SAGE
                else -> Colors.SAGE_SOFT
            }
            canvas.drawCircle(bx, by, br, fill)
            when (state) {
                DayState.SAME_DAY -> {
                    fill.color = Colors.GOLD_DEEP
                    star(canvas, bx, by + br * 0.05f, br * 0.7f, fill)
                }
                DayState.IN_MONTH -> {
                    stroke.color = Colors.WHITE
                    stroke.strokeWidth = br * 0.3f
                    path.reset()
                    path.moveTo(bx - br * 0.45f, by)
                    path.lineTo(bx - br * 0.1f, by + br * 0.38f)
                    path.lineTo(bx + br * 0.5f, by - br * 0.35f)
                    canvas.drawPath(path, stroke)
                }
                else -> {
                    stroke.color = Colors.WHITE
                    stroke.strokeWidth = br * 0.28f
                    r.set(bx - br * 0.48f, by - br * 0.48f, bx + br * 0.48f, by + br * 0.48f)
                    path.reset()
                    path.addArc(r, -60f, 280f)
                    canvas.drawPath(path, stroke)
                    // pointe de la flèche
                    fill.color = Colors.WHITE
                    path.reset()
                    val ax = bx + br * 0.24f
                    val ay = by - br * 0.42f
                    path.moveTo(ax + br * 0.32f, ay - br * 0.22f)
                    path.lineTo(ax + br * 0.30f, ay + br * 0.30f)
                    path.lineTo(ax - br * 0.18f, ay + br * 0.10f)
                    path.close()
                    canvas.drawPath(path, fill)
                }
            }
        }
    }
}

/** Une coupe seule, pour l'étagère et les messages. */
@SuppressLint("ViewConstructor")
class CupView(context: Context, private val sizeDp: Int) : View(context) {
    var color: Int = Colors.LOCKED
        set(value) {
            field = value
            invalidate()
        }
    var star = false
        set(value) {
            field = value
            invalidate()
        }
    private val box = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = context.dp(sizeDp)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        Shapes.cup(canvas, box, color, star)
    }
}

/** Une pastille seule, pour la légende du calendrier. */
@SuppressLint("ViewConstructor")
class DotView(context: Context, private val state: DayState, private val sizeDp: Int = 22) : View(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val s = context.dp(sizeDp)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        val radius = width / 2f - 3f * d
        Shapes.dot(canvas, width / 2f - 2f * d, height / 2f + 2f * d, radius, null, state, false, true, false, d)
    }
}
