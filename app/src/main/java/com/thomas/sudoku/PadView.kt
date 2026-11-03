package com.thomas.sudoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
import kotlin.math.roundToInt

/**
 * Le pavé de chiffres 1 à 9. Sous chaque chiffre : combien il en reste à placer.
 * Un chiffre terminé s'efface doucement en montant un peu.
 */
@SuppressLint("ViewConstructor")
class PadView(context: Context) : View(context) {

    var remaining = IntArray(10) { 9 }
        set(value) {
            field = value
            // Une touche de nouveau utile (nouvelle partie, recommencer) n'a plus de fondu en cours.
            for (d in 1..9) if (value[d] > 0) fadeStart[d] = 0L
            invalidate()
        }

    /** En mode crayon, les chiffres sont plus discrets pour bien voir le mode. */
    var pencil = false
        set(value) {
            field = value
            invalidate()
        }

    var onDigit: ((Int) -> Unit)? = null

    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD4DBD3.toInt() }
    private val digit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Fonts.extra
    }
    private val count = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Fonts.medium
        color = Colors.MUTED
    }
    private val gap = context.dpf(5)
    private val rect = RectF()
    private var pressed = -1

    /** Place gardée au-dessus des touches, pour qu'une touche terminée puisse monter en s'effaçant. */
    private val keysTop = context.dpf(RISE_DP)

    /** Pour chaque chiffre terminé, le moment où sa touche commence à s'effacer (0 = pas de fondu). */
    private val fadeStart = LongArray(10)
    private val ease = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    /** Le chiffre [d] vient d'être terminé : sa touche s'efface doucement au lieu de disparaître d'un coup. */
    fun fadeOut(d: Int) {
        fadeStart[d] = SystemClock.uptimeMillis() + FADE_DELAY
        postInvalidateOnAnimation()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, context.dp(66 + RISE_DP))
    }

    private fun keyAt(x: Float): Int {
        val keyW = (width - gap * 8) / 9f
        val k = (x / (keyW + gap)).toInt().coerceIn(0, 8)
        return k + 1
    }

    override fun onDraw(canvas: Canvas) {
        val keyW = (width - gap * 8) / 9f
        val h = height - keysTop - context.dpf(2)
        val radius = context.dpf(14)
        val shadowDrop = context.dpf(2)
        count.textSize = context.dpf(11)
        val now = SystemClock.uptimeMillis()
        var fading = false
        for (d in 1..9) {
            var alpha = 1f
            var up = 0f
            if (remaining[d] <= 0) {
                val start = fadeStart[d]
                if (start == 0L) continue
                val t = (now - start).toFloat() / FADE_DURATION
                if (t >= 1f) {
                    fadeStart[d] = 0L
                    continue
                }
                fading = true
                if (t > 0f) {
                    val p = ease.getInterpolation(t)
                    alpha = 1f - p
                    up = keysTop * p
                }
            }
            val left = (d - 1) * (keyW + gap)
            val top = keysTop - up
            // Une touche qui s'efface est dessinée d'un bloc, pour que son ombre ne transparaisse pas.
            val saved = if (alpha < 1f) {
                canvas.saveLayerAlpha(left, top, left + keyW, top + h + shadowDrop, (alpha * 255f).roundToInt())
            } else -1
            rect.set(left, top + shadowDrop, left + keyW, top + h + shadowDrop)
            canvas.drawRoundRect(rect, radius, radius, shadow)
            rect.set(left, top, left + keyW, top + h)
            keyPaint.color = if (d == pressed) Colors.MINT else Colors.CARD
            canvas.drawRoundRect(rect, radius, radius, keyPaint)
            digit.color = if (pencil) Colors.SAGE_SOFT else Colors.FG
            digit.textSize = context.dpf(if (pencil) 20 else 25)
            canvas.drawText(d.toString(), rect.centerX(), rect.top + h * 0.52f, digit)
            canvas.drawText(remaining[d].coerceAtLeast(0).toString(), rect.centerX(), rect.top + h * 0.84f, count)
            if (saved >= 0) canvas.restoreToCount(saved)
        }
        if (fading) postInvalidateOnAnimation()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // La place au-dessus des touches ne réagit pas, comme avant.
                if (event.y < keysTop) return false
                val d = keyAt(event.x)
                pressed = if (remaining[d] > 0) d else -1
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val d = keyAt(event.x)
                val hit = d == pressed
                pressed = -1
                invalidate()
                if (hit && remaining[d] > 0) onDigit?.invoke(d)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = -1
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private companion object {
        /** La touche monte de 14 dp en s'effaçant. */
        const val RISE_DP = 14

        /** Elle attend un peu (le temps de voir le « 0 »), puis s'efface en 0,9 seconde. */
        const val FADE_DELAY = 600L
        const val FADE_DURATION = 900L
    }
}
