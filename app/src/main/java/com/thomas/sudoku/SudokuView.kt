package com.thomas.sudoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import com.thomas.sudoku.engine.Cells
import com.thomas.sudoku.engine.Game
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * La grille de jeu, dessinée à la main, avec ses animations.
 * Chaque animation part de la case du dernier chiffre posé et s'étend dans toutes les directions,
 * lentement et sans rebond. On peut toujours jouer pendant qu'elle se déroule.
 */
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

    /**
     * Une étape d'animation, au moment [at] (de 0 à 1) :
     * [layer] la couleur posée sur la case, [text] celle du chiffre (null = sa couleur normale),
     * [lift] la case monte (1 = de 4 dp), [scale] sa taille, [ring] l'anneau doré (de 0 à 1).
     * Avant la première étape et après la dernière, la case est comme d'habitude.
     */
    private class Frame(val at: Float, val layer: Int, val text: Int?, val lift: Float, val scale: Float, val ring: Float)

    /** Les sortes d'animations : leurs étapes, leur durée pour une case et l'écart entre deux cases voisines. */
    private enum class Kind(val duration: Long, val step: Long, val frames: Array<Frame>) {
        /** Vague verte : ligne ou colonne terminée (et toute la grille quand elle est réussie). */
        WAVE(1700L, 95L, arrayOf(
            Frame(0.35f, Colors.WAVE, Colors.WHITE, 1f, 1.06f, 0f),
            Frame(0.70f, Colors.MINT, null, 0f, 1f, 0f),
        )),

        /** Éclat doré : carré terminé. */
        GOLD(1700L, 95L, arrayOf(
            Frame(0.32f, Colors.GOLD, Colors.WHITE, 0f, 1.12f, 0f),
            Frame(0.68f, Colors.GOLD_SOFT, null, 0f, 1f, 0f),
        )),

        /** Brillance dorée : les 9 exemplaires d'un même chiffre sont posés. */
        SHINE(1800L, 120L, arrayOf(
            Frame(0.40f, Colors.SHINE, null, 0f, 1.08f, 1f),
        )),
    }

    /** Une animation en cours : pour chaque case, le moment où elle démarre (-1 = case pas concernée). */
    private class Effect(val kind: Kind, val starts: LongArray) {
        val end: Long = (starts.maxOrNull() ?: 0L) + kind.duration
    }

    private val effects = mutableListOf<Effect>()

    /** Démarrage doux et arrivée douce, sans rebond. */
    private val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)

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
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Colors.GOLD
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
    private val liftMax = context.dpf(4)
    private val clip = Path()
    private val outer = RectF()
    private val cellRect = RectF()
    private val ringRect = RectF()
    private val animated = IntArray(81)

    /** Vague verte sur les cases des lignes et colonnes terminées, depuis la case [origin]. */
    fun wave(cells: IntArray, origin: Int) = add(Kind.WAVE, cells, origin)

    /** Éclat doré sur les cases d'un carré terminé, depuis la case [origin]. */
    fun pop(cells: IntArray, origin: Int) = add(Kind.GOLD, cells, origin)

    /** Les 9 cases d'un même chiffre brillent, depuis la case [origin], après [delay] ms. */
    fun shine(cells: IntArray, origin: Int, delay: Long) = add(Kind.SHINE, cells, origin, delay)

    /** Grille réussie : toute la grille fait la vague, depuis la dernière case remplie. */
    fun celebrate(origin: Int) = add(Kind.WAVE, IntArray(81) { it }, origin, step = 80L)

    /** Combien de temps (en ms) durent encore les animations en cours. */
    fun effectsRemaining(): Long {
        val end = effects.maxOfOrNull { it.end } ?: return 0L
        return (end - SystemClock.uptimeMillis()).coerceAtLeast(0L)
    }

    /** Chaque case démarre d'autant plus tard qu'elle est loin de [origin] : l'animation s'étend comme une vague dans l'eau. */
    private fun add(kind: Kind, cells: IntArray, origin: Int, delay: Long = 0L, step: Long = kind.step) {
        if (cells.isEmpty()) return
        val now = SystemClock.uptimeMillis() + delay
        val starts = LongArray(81) { -1L }
        for (i in cells) starts[i] = now + (distance(i, origin) * step).roundToLong()
        effects += Effect(kind, starts)
        postInvalidateOnAnimation()
    }

    private fun distance(a: Int, b: Int): Float =
        hypot((Cells.row(a) - Cells.row(b)).toFloat(), (Cells.col(a) - Cells.col(b)).toFloat())

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hMode = MeasureSpec.getMode(heightMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (hMode == MeasureSpec.UNSPECIFIED) w else min(w, h)
        setMeasuredDimension(size, size)
    }

    /** L'apparence d'une case animée à un instant donné. */
    private class Look {
        var layer = 0
        var text = 0
        var lift = 0f
        var scale = 1f
        var ring = 0f
    }

    private val look = Look()

    /** Vrai si une animation est en train de passer sur la case [i]. */
    private fun isAnimated(i: Int, now: Long): Boolean {
        for (k in effects.indices) {
            val e = effects[k]
            val s = e.starts[i]
            if (s >= 0 && now > s && now < s + e.kind.duration) return true
        }
        return false
    }

    /** Calcule l'apparence de la case [i] : les animations se posent l'une sur l'autre, la plus récente au-dessus. */
    private fun animate(i: Int, now: Long, baseText: Int): Look {
        look.layer = 0
        look.text = baseText
        look.lift = 0f
        look.scale = 1f
        look.ring = 0f
        for (k in effects.indices) {
            val e = effects[k]
            val s = e.starts[i]
            if (s < 0) continue
            val t = (now - s).toFloat() / e.kind.duration
            if (t <= 0f || t >= 1f) continue
            blend(e.kind.frames, ease.getInterpolation(t))
        }
        return look
    }

    /**
     * Applique une animation, arrivée au point [p], par-dessus ce que la case montre déjà.
     * Elle part de cet état et y revient : deux animations qui se croisent s'enchaînent sans à-coup.
     */
    private fun blend(frames: Array<Frame>, p: Float) {
        var k = 0
        while (k < frames.size && frames[k].at <= p) k++
        val a = if (k > 0) frames[k - 1] else null
        val b = if (k < frames.size) frames[k] else null
        val from = a?.at ?: 0f
        val to = b?.at ?: 1f
        val u = if (to > from) ((p - from) / (to - from)).coerceIn(0f, 1f) else 1f
        val l = look
        val layer0 = l.layer
        val text0 = l.text
        val lift0 = l.lift
        val scale0 = l.scale
        val ring0 = l.ring
        l.layer = mix(a?.layer ?: layer0, b?.layer ?: layer0, u)
        l.text = mix(a?.text ?: text0, b?.text ?: text0, u)
        l.lift = lerp(if (a != null) -liftMax * a.lift else lift0, if (b != null) -liftMax * b.lift else lift0, u)
        l.scale = lerp(a?.scale ?: scale0, b?.scale ?: scale0, u)
        l.ring = lerp(a?.ring ?: ring0, b?.ring ?: ring0, u)
    }

    private fun background(g: Game, i: Int, sel: Int, selValue: Int): Int {
        val wrong = g.isWrong(i)
        return when {
            i == sel && wrong -> Colors.ERROR_BG
            i == sel -> Colors.MINT
            selValue != 0 && g.value(i) == selValue && !wrong -> Colors.SAME
            sel >= 0 && (Cells.row(i) == Cells.row(sel) || Cells.col(i) == Cells.col(sel) || Cells.box(i) == Cells.box(sel)) -> Colors.PEER
            else -> Colors.CARD
        }
    }

    private fun textColor(g: Game, i: Int): Int = when {
        g.isWrong(i) -> Colors.ERROR
        g.isGiven(i) -> Colors.FG
        else -> Colors.SAGE
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
        note.textSize = cell * 0.27f

        canvas.save()
        canvas.clipPath(clip)
        fill.color = Colors.CARD
        canvas.drawRect(0f, 0f, size, size, fill)

        // 1. Le fond de toutes les cases, et le chiffre des cases au repos.
        var count = 0
        for (i in 0 until 81) {
            setCell(i, cell)
            val bg = background(g, i, sel, selValue)
            if (bg != Colors.CARD) {
                fill.color = bg
                canvas.drawRect(cellRect, fill)
            }
            if (isAnimated(i, now)) animated[count++] = i
            else drawContent(canvas, g, i, cell, textColor(g, i), 1f)
        }
        drawLines(canvas, size, cell)

        // 2. Les cases animées, par-dessus les traits : un calque arrondi, l'anneau doré, puis le chiffre.
        for (n in 0 until count) {
            val i = animated[n]
            val l = animate(i, now, textColor(g, i))
            setCell(i, cell)
            val inset = cell * (1f - l.scale) / 2f
            cellRect.inset(inset, inset)
            cellRect.offset(0f, l.lift)
            val corner = cell * 0.18f * l.scale
            if (Color.alpha(l.layer) > 0) {
                fill.color = l.layer
                canvas.drawRoundRect(cellRect, corner, corner, fill)
            }
            if (l.ring > 0.01f) {
                val w = ringPaint.strokeWidth / 2f
                ringRect.set(cellRect)
                ringRect.inset(w, w)
                ringPaint.alpha = (l.ring * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawRoundRect(ringRect, corner - w, corner - w, ringPaint)
            }
            drawContent(canvas, g, i, cell, l.text, l.scale)
        }
        canvas.restore()
        canvas.drawRoundRect(outer, radius, radius, border)

        effects.removeAll { now >= it.end }
        if (effects.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun setCell(i: Int, cell: Float) {
        val left = Cells.col(i) * cell
        val top = Cells.row(i) * cell
        cellRect.set(left, top, left + cell, top + cell)
    }

    /** Le chiffre (ou les petites notes du crayon) de la case [i], dans [cellRect]. */
    private fun drawContent(canvas: Canvas, g: Game, i: Int, cell: Float, color: Int, scale: Float) {
        val v = g.value(i)
        if (v != 0) {
            digit.color = color
            digit.typeface = if (g.isGiven(i)) Fonts.medium else Fonts.extra
            digit.textSize = cell * 0.56f * scale
            canvas.drawText(DIGITS[v], cellRect.centerX(), cellRect.centerY() - (digit.descent() + digit.ascent()) / 2f, digit)
        } else if (g.notes[i] != 0) {
            val sub = cell / 3f
            for (d in 1..9) {
                if (g.notes[i] and (1 shl d) == 0) continue
                val nx = cellRect.left + sub * ((d - 1) % 3) + sub / 2f
                val ny = cellRect.top + sub * ((d - 1) / 3) + sub / 2f - (note.descent() + note.ascent()) / 2f
                canvas.drawText(DIGITS[d], nx, ny, note)
            }
        }
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

    private companion object {
        val DIGITS = Array(10) { it.toString() }

        fun lerp(a: Float, b: Float, u: Float) = a + (b - a) * u

        /** Mélange deux couleurs, même transparentes, sans que le passage par le transparent ne fonce la couleur. */
        fun mix(from: Int, to: Int, u: Float): Int {
            if (u <= 0f) return from
            if (u >= 1f) return to
            val fa = Color.alpha(from) / 255f * (1f - u)
            val ta = Color.alpha(to) / 255f * u
            val a = fa + ta
            if (a <= 0f) return 0
            val r = (Color.red(from) * fa + Color.red(to) * ta) / a
            val g = (Color.green(from) * fa + Color.green(to) * ta) / a
            val b = (Color.blue(from) * fa + Color.blue(to) * ta) / a
            return Color.argb((a * 255f).roundToInt(), r.roundToInt(), g.roundToInt(), b.roundToInt())
        }
    }
}
