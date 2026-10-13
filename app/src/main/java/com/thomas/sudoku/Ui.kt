package com.thomas.sudoku

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Les couleurs du style « Galet ». */
object Colors {
    const val BG = 0xFFEEF0EA.toInt()
    const val FG = 0xFF2C3A33.toInt()
    const val MUTED = 0xFF6C7A72.toInt()
    const val FAINT = 0xFFAEB9B2.toInt()
    const val CARD = 0xFFFBFCF8.toInt()
    const val LINE = 0xFFDDE4DD.toInt()
    const val SAGE = 0xFF3F7A62.toInt()
    const val SAGE_SOFT = 0xFF8AA597.toInt()
    const val MINT = 0xFFBFE0CD.toInt()
    const val LIGHT_GREEN = 0xFFA9D6BF.toInt()
    const val DEEP_GREEN = 0xFF24453A.toInt()
    const val WAVE = 0xFF5F9E80.toInt()
    const val PEER = 0xFFEEF3EC.toInt()
    const val SAME = 0xFFDCEBE1.toInt()
    const val ERROR = 0xFFC4573F.toInt()
    const val ERROR_BG = 0xFFF7E3DC.toInt()
    const val GOLD = 0xFFE3B23C.toInt()
    const val GOLD_SOFT = 0xFFF3DFA6.toInt()
    const val GOLD_DEEP = 0xFF5B4310.toInt()
    const val OR = 0xFFD4A22A.toInt()
    const val ARGENT = 0xFF9AA5AE.toInt()
    const val BRONZE = 0xFFB0703C.toInt()
    const val LOCKED = 0xFFCFD6CF.toInt()
    const val STRIPE = 0xFFE5EAE4.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
}

/** La police Nunito (ronde), en plusieurs épaisseurs. */
object Fonts {
    lateinit var regular: Typeface
    lateinit var medium: Typeface
    lateinit var bold: Typeface
    lateinit var extra: Typeface
    private var ready = false

    fun init(ctx: Context) {
        if (ready) return
        fun weight(w: Int): Typeface = try {
            Typeface.Builder(ctx.assets, "nunito.ttf")
                .setFontVariationSettings("'wght' $w")
                .build()
        } catch (e: Exception) {
            Typeface.create(Typeface.SANS_SERIF, if (w >= 700) Typeface.BOLD else Typeface.NORMAL)
        }
        regular = weight(400)
        medium = weight(600)
        bold = weight(700)
        extra = weight(800)
        ready = true
    }
}

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density).roundToInt()
fun Context.dpf(v: Number): Float = v.toFloat() * resources.displayMetrics.density

fun roundRect(color: Int, radius: Float, stroke: Int = 0, strokeWidth: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (strokeWidth > 0) setStroke(strokeWidth, stroke)
}

fun Context.text(
    value: CharSequence,
    size: Float = 15f,
    color: Int = Colors.FG,
    font: Typeface = Fonts.regular,
): TextView = TextView(this).apply {
    text = value
    textSize = size
    setTextColor(color)
    typeface = font
    includeFontPadding = false
    setLineSpacing(0f, 1.15f)
}

fun Context.vbox(gap: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (gap > 0) {
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = GradientDrawable().apply { setSize(1, dp(gap)) }
    }
}

fun Context.hbox(gap: Int = 0): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    if (gap > 0) {
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
        dividerDrawable = GradientDrawable().apply { setSize(dp(gap), 1) }
    }
}

/** Une carte blanche aux coins arrondis. */
fun Context.card(): LinearLayout = vbox(12).apply {
    background = roundRect(Colors.CARD, dpf(20))
    setPadding(dp(18), dp(18), dp(18), dp(18))
}

/** Bouton plein (vert) ou léger (contour). */
fun Context.button(label: String, primary: Boolean = true, onClick: () -> Unit): TextView =
    text(label, 16f, if (primary) Colors.WHITE else Colors.SAGE, Fonts.extra).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = if (primary) roundRect(Colors.SAGE, dpf(16))
        else roundRect(Colors.CARD, dpf(16), Colors.SAGE_SOFT, dp(1.5f))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

/** Petit lien texte, par exemple « ‹ Accueil ». */
fun Context.link(label: String, onClick: () -> Unit): TextView =
    text(label, 15f, Colors.SAGE, Fonts.bold).apply {
        setPadding(dp(6), dp(10), dp(6), dp(10))
        isClickable = true
        isFocusable = true
        setOnClickListener { onClick() }
    }

fun matchWrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
fun wrap() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
fun weight1() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

/** L'appli dessine sous les barres du téléphone : on garde la place pour l'heure et les boutons. */
fun Activity.edgeToEdge(root: View, extraTop: Int = 12, extraBottom: Int = 16) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = 0
    window.navigationBarColor = 0
    WindowCompat.getInsetsController(window, root).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
    val left = root.paddingLeft
    val right = root.paddingRight
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        v.updatePadding(
            left = left + bars.left,
            right = right + bars.right,
            top = bars.top + dp(extraTop),
            bottom = bars.bottom + dp(extraBottom),
        )
        insets
    }
}

/** Petite vibration qui accompagne les animations. */
object Buzz {
    private fun vibrator(ctx: Context): Vibrator? = ctx.getSystemService(Vibrator::class.java)

    fun short(ctx: Context) = once(ctx, 25)
    fun medium(ctx: Context) = once(ctx, 45)
    fun error(ctx: Context) = pattern(ctx, longArrayOf(0, 40, 70, 40))
    fun win(ctx: Context) = pattern(ctx, longArrayOf(0, 40, 80, 40, 80, 90))

    private fun once(ctx: Context, ms: Long) = try {
        vibrator(ctx)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    } catch (e: Exception) {
    }

    private fun pattern(ctx: Context, p: LongArray) = try {
        vibrator(ctx)?.vibrate(VibrationEffect.createWaveform(p, -1))
    } catch (e: Exception) {
    }
}

/** Dates et durées en français. */
object Fr {
    private val locale = Locale.FRANCE

    fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
    fun monthName(m: Int) = java.time.Month.of(m).getDisplayName(TextStyle.FULL, locale)

    /** « mardi 13 octobre » */
    fun longDay(d: LocalDate) = "${dayName(d)} ${d.dayOfMonth} ${monthName(d.monthValue)}"

    /** « Octobre 2026 » */
    fun month(m: YearMonth) = "${monthName(m.monthValue).replaceFirstChar { it.uppercase() }} ${m.year}"

    /** « d'octobre » ou « de mars » */
    fun ofMonth(m: YearMonth): String {
        val name = monthName(m.monthValue)
        return if (name.first() in "aeiouyàâéèêîôû") "d'$name" else "de $name"
    }

    fun shortMonth(m: Int) = listOf("Janv.", "Févr.", "Mars", "Avril", "Mai", "Juin", "Juil.", "Août", "Sept.", "Oct.", "Nov.", "Déc.")[m - 1]

    /** 402000 ms -> « 06:42 » (ou « 1:06:42 » au-delà d'une heure). */
    fun clock(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(locale, "%d:%02d:%02d", h, m, sec)
        else String.format(locale, "%02d:%02d", m, sec)
    }

    /** 402000 ms -> « 6 min 42 s » */
    fun duration(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            h > 0 -> "$h h ${m} min"
            m > 0 -> "$m min ${sec} s"
            else -> "$sec s"
        }
    }
}
