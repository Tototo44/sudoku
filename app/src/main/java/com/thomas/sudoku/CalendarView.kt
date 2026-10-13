package com.thomas.sudoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.thomas.sudoku.engine.Progress
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/** Le mois en pastilles : une par jour, du lundi au dimanche. */
@SuppressLint("ViewConstructor")
class CalendarView(context: Context) : View(context) {

    var month: YearMonth = YearMonth.now()
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }
    var progress: Progress? = null
        set(value) {
            field = value
            invalidate()
        }
    var today: LocalDate = LocalDate.now()
    var selected: LocalDate? = null
        set(value) {
            field = value
            invalidate()
        }
    var onDayTap: ((LocalDate) -> Unit)? = null

    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Colors.MUTED
        typeface = Fonts.bold
    }
    private val headH get() = context.dpf(26)

    private fun offset() = month.atDay(1).dayOfWeek.value - 1
    private fun weeks() = (offset() + month.lengthOfMonth() + 6) / 7

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val cellH = min(w / 7f, context.dpf(50))
        setMeasuredDimension(w, (headH + cellH * weeks()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val pr = progress ?: return
        val d = resources.displayMetrics.density
        val cellW = width / 7f
        val cellH = min(cellW, context.dpf(50))
        head.textSize = context.dpf(12)
        listOf("L", "M", "M", "J", "V", "S", "D").forEachIndexed { k, s ->
            canvas.drawText(s, cellW * k + cellW / 2f, context.dpf(14), head)
        }
        val radius = min(cellW, cellH) * 0.40f
        val off = offset()
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val pos = off + day - 1
            val cx = cellW * (pos % 7) + cellW / 2f
            val cy = headH + cellH * (pos / 7) + cellH / 2f
            Shapes.dot(
                canvas, cx, cy, radius, day,
                pr.state(date),
                today = date == today,
                playable = pr.isPlayable(date, today),
                selected = date == selected,
                density = d,
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val cellW = width / 7f
                val cellH = min(cellW, context.dpf(50))
                if (event.y < headH) return true
                val col = (event.x / cellW).toInt().coerceIn(0, 6)
                val row = ((event.y - headH) / cellH).toInt()
                val day = row * 7 + col - offset() + 1
                if (day in 1..month.lengthOfMonth()) {
                    val date = month.atDay(day)
                    if (progress?.isPlayable(date, today) == true) onDayTap?.invoke(date)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
