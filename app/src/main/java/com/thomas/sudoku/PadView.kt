package com.thomas.sudoku

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** Le pavé de chiffres 1 à 9. Sous chaque chiffre : combien il en reste à placer. Un chiffre terminé disparaît. */
@SuppressLint("ViewConstructor")
class PadView(context: Context) : View(context) {

    var remaining = IntArray(10) { 9 }
        set(value) {
            field = value
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, context.dp(66))
    }

    private fun keyAt(x: Float): Int {
        val keyW = (width - gap * 8) / 9f
        val k = (x / (keyW + gap)).toInt().coerceIn(0, 8)
        return k + 1
    }

    override fun onDraw(canvas: Canvas) {
        val keyW = (width - gap * 8) / 9f
        val h = height.toFloat() - context.dpf(2)
        val radius = context.dpf(14)
        digit.textSize = context.dpf(25)
        count.textSize = context.dpf(11)
        for (d in 1..9) {
            if (remaining[d] <= 0) continue
            val left = (d - 1) * (keyW + gap)
            rect.set(left, context.dpf(2), left + keyW, h + context.dpf(2))
            canvas.drawRoundRect(rect, radius, radius, shadow)
            rect.set(left, 0f, left + keyW, h)
            keyPaint.color = if (d == pressed) Colors.MINT else Colors.CARD
            canvas.drawRoundRect(rect, radius, radius, keyPaint)
            digit.color = if (pencil) Colors.SAGE_SOFT else Colors.FG
            digit.textSize = context.dpf(if (pencil) 20 else 25)
            canvas.drawText(d.toString(), rect.centerX(), rect.top + h * 0.52f, digit)
            canvas.drawText(remaining[d].toString(), rect.centerX(), rect.top + h * 0.84f, count)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
}
