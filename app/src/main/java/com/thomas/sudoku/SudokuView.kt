package com.thomas.sudoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.ColorUtils
import com.thomas.sudoku.engine.Cells
import com.thomas.sudoku.engine.Game
import kotlin.math.min

/** La grille de jeu, dessinée à la main, avec les animations de ligne et de carré. */
@SuppressLint("ViewConstructor")
class SudokuView(context: Context) : View(context) {

    var game: Game? = null
        set(value) {
            field = value
            effects.clear()
            invalidate()
        }

    var selected: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    var onCellTap: ((Int) -> Unit)? = null

    private enum class Kind { WAVE, POP }

    private class Effect(val cells: IntArray, val kind: Kind, val start: Long, val step: Long)

    private val effects = mutableListOf<Effect>()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thin = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Colors.LINE
        strokeWidth = context.dpf(1)
    }
    private val thick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Colors.SAGE_SOFT
        strokeWidth = context.dpf(2)
    }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Colors.SAGE_SOFT
        style = Paint.Style.STROKE
        strokeWidth = context.dpf(2)
    }
    private val digit = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val note = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Colors.MUTED
        typeface = Fonts.bold
    }
    private val radius = context.dpf(16)
    private val clip = Path()
    private val outer = RectF()
    private val cellRect = RectF()

    /** Vague verte qui traverse une ligne ou une colonne. */
    fun wave(cells: IntArray) {
        effects += Effect(cells, Kind.WAVE, SystemClock.uptimeMillis(), 55L)
        postInvalidateOnAnimation()
    }

    /** Éclat doré sur les 9 cases d'un carré. */
    fun pop(cells: IntArray) {
        effects += Effect(cells, Kind.POP, SystemClock.uptimeMillis(), 30L)
        postInvalidateOnAnimation()
    }

    /** Toute la grille fait la vague, en diagonale, quand elle est réussie. */
    fun celebrate() {
        val order = (0 until 81).sortedBy { Cells.row(it) + Cells.col(it) }.toIntArray()
        effects += Effect(order, Kind.WAVE, SystemClock.uptimeMillis(), 12L)
        postInvalidateOnAnimation()
    }

    /** Durée de l'animation la plus longue en cours. */
    fun effectsDuration(): Long = effects.maxOfOrNull { it.step * it.cells.size + DURATION } ?: 0L

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (hMode == MeasureSpec.UNSPECIFIED) w else min(w, h)
        setMeasuredDimension(size, size)
    }

    private class Look(var bg: Int, var text: Int, var lift: Float, var scale: Float, var active: Boolean)

    private val look = Look(0, 0, 0f, 1f, false)

    /** Calcule l'apparence d'une case animée à l'instant [now]. */
    private fun animate(i: Int, now: Long, baseBg: Int, baseText: Int): Look {
        look.bg = baseBg; look.text = baseText; look.lift = 0f; look.scale = 1f; look.active = false
        for (e in effects) {
            val k = e.cells.indexOf(i)
            if (k < 0) continue
            val t = (now - e.start - k * e.step).toFloat() / DURATION
            if (t <= 0f || t >= 1f) continue
            look.active = true
            val liftMax = context.dpf(4)
            when (e.kind) {
                Kind.WAVE -> when {
                    t < 0.3f -> {
                        val u = t / 0.3f
                        look.bg = ColorUtils.blendARGB(baseBg, Colors.WAVE, u)
                        look.text = ColorUtils.blendARGB(baseText, Colors.WHITE, u)
                        look.lift = -liftMax * u
                        look.scale = 1f + 0.06f * u
                    }
                    t < 0.65f -> {
                        val u = (t - 0.3f) / 0.35f
                        look.bg = ColorUtils.blendARGB(Colors.WAVE, Colors.MINT, u)
                        look.text = ColorUtils.blendARGB(Colors.WHITE, baseText, u)
                        look.lift = -liftMax * (1f - u)
                        look.scale = 1.06f - 0.06f * u
                    }
                    else -> {
                        val u = (t - 0.65f) / 0.35f
                        look.bg = ColorUtils.blendARGB(Colors.MINT, baseBg, u)
                    }
                }
                Kind.POP -> when {
                    t < 0.3f -> {
                        val u = t / 0.3f
                        look.bg = ColorUtils.blendARGB(baseBg, Colors.GOLD, u)
                        look.text = ColorUtils.blendARGB(baseText, Colors.WHITE, u)
                        look.scale = 1f + 0.2f * u
                    }
                    t < 0.6f -> {
                        val u = (t - 0.3f) / 0.3f
                        look.bg = ColorUtils.blendARGB(Colors.GOLD, Colors.GOLD_SOFT, u)
                        look.text = ColorUtils.blendARGB(Colors.WHITE, baseText, u)
                        look.scale = 1.2f - 0.23f * u
                    }
                    else -> {
                        val u = (t - 0.6f) / 0.4f
                        look.bg = ColorUtils.blendARGB(Colors.GOLD_SOFT, baseBg, u)
                        look.scale = 0.97f + 0.03f * u
                    }
                }
            }
        }
        return look
    }

    override fun onDraw(canvas: Canvas) {
        val g = game ?: return
        val size = width.toFloat()
        val cell = size / 9f
        val now = SystemClock.uptimeMillis()
        val half = border.strokeWidth / 2f
        outer.set(half, half, size - half, size - half)
        clip.reset()
        clip.addRoundRect(outer, radius, radius, Path.Direction.CW)

        val sel = selected
        val selValue = if (sel >= 0 && !g.isWrong(sel)) g.value(sel) else 0
        digit.textSize = cell * 0.56f
        note.textSize = cell * 0.27f

        canvas.save()
        canvas.clipPath(clip)
        fill.color = Colors.CARD
        canvas.drawRect(0f, 0f, size, size, fill)

        val animated = ArrayList<Int>()
        for (pass in 0..1) {
            for (i in 0 until 81) {
                val r = Cells.row(i)
                val c = Cells.col(i)
                val v = g.value(i)
                val wrong = g.isWrong(i)
                val bg = when {
                    i == sel && wrong -> Colors.ERROR_BG
                    i == sel -> Colors.MINT
                    selValue != 0 && v == selValue && !wrong -> Colors.SAME
                    sel >= 0 && (r == Cells.row(sel) || c == Cells.col(sel) || Cells.box(i) == Cells.box(sel)) -> Colors.PEER
                    else -> Colors.CARD
                }
                val textColor = when {
                    wrong -> Colors.ERROR
                    g.isGiven(i) -> Colors.FG
                    else -> Colors.SAGE
                }
                val l = animate(i, now, bg, textColor)
                if (pass == 0 && l.active) {
                    animated += i
                    continue
                }
                if (pass == 1 && i !in animated) continue

                val left = c * cell
                val top = r * cell
                cellRect.set(left, top, left + cell, top + cell)
                if (l.active) {
                    val inset = cell * (1f - l.scale) / 2f
                    cellRect.inset(inset, inset)
                    cellRect.offset(0f, l.lift)
                    fill.color = l.bg
                    canvas.drawRoundRect(cellRect, cell * 0.18f, cell * 0.18f, fill)
                } else if (bg != Colors.CARD) {
                    fill.color = bg
                    canvas.drawRect(cellRect, fill)
                }

                val cx = cellRect.centerX()
                val cy = cellRect.centerY()
                if (v != 0) {
                    digit.color = l.text
                    digit.typeface = if (g.isGiven(i)) Fonts.medium else Fonts.extra
                    digit.textSize = cell * 0.56f * l.scale
                    canvas.drawText(v.toString(), cx, cy - (digit.descent() + digit.ascent()) / 2f, digit)
                } else if (g.notes[i] != 0) {
                    val sub = cell / 3f
                    for (d in 1..9) {
                        if (g.notes[i] and (1 shl d) == 0) continue
                        val nx = left + sub * ((d - 1) % 3) + sub / 2f
                        val ny = top + sub * ((d - 1) / 3) + sub / 2f - (note.descent() + note.ascent()) / 2f
                        canvas.drawText(d.toString(), nx, ny, note)
                    }
                }
            }
            if (pass == 0) drawLines(canvas, size, cell)
        }
        canvas.restore()
        canvas.drawRoundRect(outer, radius, radius, border)

        effects.removeAll { now - it.start > it.step * it.cells.size + DURATION }
        if (effects.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun drawLines(canvas: Canvas, size: Float, cell: Float) {
        for (k in 1..8) {
            if (k % 3 == 0) continue
            val x = k * cell
            canvas.drawLine(x, 0f, x, size, thin)
            canvas.drawLine(0f, x, size, x, thin)
        }
        for (k in listOf(3, 6)) {
            val x = k * cell
            canvas.drawLine(x, 0f, x, size, thick)
            canvas.drawLine(0f, x, size, x, thick)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val cell = width / 9f
                val c = (event.x / cell).toInt().coerceIn(0, 8)
                val r = (event.y / cell).toInt().coerceIn(0, 8)
                onCellTap?.invoke(r * 9 + c)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    companion object {
        const val DURATION = 1100L
    }
}
