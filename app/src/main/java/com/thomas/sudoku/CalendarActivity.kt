package com.thomas.sudoku

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.thomas.sudoku.engine.DayState
import com.thomas.sudoku.engine.Daily
import com.thomas.sudoku.engine.Status
import com.thomas.sudoku.engine.Trophy
import java.time.LocalDate
import java.time.YearMonth

/** Le calendrier des grilles du jour, en pastilles, avec le trophée du mois. */
class CalendarActivity : Activity() {

    companion object {
        private const val EXTRA_MONTH = "month"

        fun open(ctx: Context, month: YearMonth? = null): Intent =
            Intent(ctx, CalendarActivity::class.java).apply {
                if (month != null) putExtra(EXTRA_MONTH, month.toString())
            }
    }

    private lateinit var store: Store
    private lateinit var month: YearMonth
    private var selected: LocalDate? = null

    private lateinit var calendar: CalendarView
    private lateinit var title: TextView
    private lateinit var prev: TextView
    private lateinit var next: TextView
    private lateinit var trophyBox: LinearLayout
    private lateinit var dayBox: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        store = Store(this)
        month = intent.getStringExtra(EXTRA_MONTH)?.let { YearMonth.parse(it) } ?: YearMonth.now()
        build()
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun build() {
        val scroll = ScrollView(this).apply { setBackgroundColor(Colors.BG) }
        val column = vbox(14).apply { setPadding(dp(16), 0, dp(16), 0) }
        scroll.addView(column)

        column.addView(link("‹ Retour") { finish() }, wrap())

        val nav = FrameLayout(this)
        prev = navArrow("‹") { move(-1) }
        next = navArrow("›") { move(1) }
        title = text("", 22f, Colors.FG, Fonts.extra)
        nav.addView(prev, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        nav.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        nav.addView(next, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        column.addView(nav, matchWrap())

        calendar = CalendarView(this).apply {
            onDayTap = { day ->
                this@CalendarActivity.selected = day
                this@CalendarActivity.render()
            }
        }
        column.addView(calendar, matchWrap())

        val legend = vbox(6)
        legend.addView(legendRow(DayState.SAME_DAY, "Réussie le jour même"), wrap())
        legend.addView(legendRow(DayState.IN_MONTH, "Rattrapée dans le mois"), wrap())
        legend.addView(legendRow(DayState.LATE, "Rattrapée après le mois"), wrap())
        column.addView(legend, matchWrap())

        trophyBox = card()
        column.addView(trophyBox, matchWrap())
        dayBox = card()
        column.addView(dayBox, matchWrap())

        setContentView(scroll)
        edgeToEdge(column)
    }

    private fun navArrow(label: String, onClick: () -> Unit): TextView =
        text(label, 30f, Colors.SAGE, Fonts.extra).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(2), dp(16), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun legendRow(state: DayState, label: String): LinearLayout = hbox(10).apply {
        addView(DotView(this@CalendarActivity, state), wrap())
        addView(text(label, 14f, Colors.MUTED, Fonts.medium), wrap())
    }

    private fun firstMonth(): YearMonth = YearMonth.from(store.start)

    private fun move(delta: Int) {
        val target = month.plusMonths(delta.toLong())
        if (target.isBefore(firstMonth()) || target.isAfter(YearMonth.now())) return
        month = target
        selected = null
        render()
    }

    private fun render() {
        val today = LocalDate.now()
        val pr = store.progress
        if (month.isBefore(firstMonth())) month = firstMonth()
        if (month.isAfter(YearMonth.from(today))) month = YearMonth.from(today)

        title.text = Fr.month(month)
        prev.alpha = if (month.isAfter(firstMonth())) 1f else 0.25f
        next.alpha = if (month.isBefore(YearMonth.from(today))) 1f else 0.25f

        // Jour choisi par défaut : aujourd'hui, sinon le premier jour à rattraper.
        val sel = selected?.takeIf { YearMonth.from(it) == month }
            ?: today.takeIf { YearMonth.from(it) == month }
            ?: (1..month.lengthOfMonth()).map { month.atDay(it) }
                .firstOrNull { pr.isPlayable(it, today) && pr.state(it) == DayState.TODO }
            ?: month.atEndOfMonth()
        selected = sel

        calendar.today = today
        calendar.progress = pr
        calendar.month = month
        calendar.selected = sel

        renderTrophy(pr.monthTrophy(month), pr.bestPossible(month, today), pr.doneCount(month), today)
        renderDay(sel, pr.state(sel), pr.isPlayable(sel, today))
    }

    private fun renderTrophy(won: Trophy?, best: Trophy?, done: Int, today: LocalDate) {
        trophyBox.removeAllViews()
        val row = hbox(14)
        val cup = CupView(this, 52).apply { color = (won ?: best).color() }
        row.addView(cup, wrap())
        val texts = vbox(4)
        val total = month.lengthOfMonth()
        if (won != null) {
            texts.addView(text("Trophée gagné : ${won.label}", 18f, Colors.FG, Fonts.extra), wrap())
            texts.addView(text("Les $total grilles ${Fr.ofMonth(month)} sont réussies.", 14f, Colors.MUTED, Fonts.medium), wrap())
        } else if (best != null) {
            texts.addView(text("Trophée visé : ${best.label}", 18f, Colors.FG, Fonts.extra), wrap())
            texts.addView(text("$done / $total grilles réussies", 14f, Colors.MUTED, Fonts.bold), wrap())
            val hint = when {
                best == Trophy.OR -> "Réussis chaque grille le jour même pour l'or."
                best == Trophy.ARGENT && YearMonth.from(today) == month ->
                    "Rattrape les jours manqués avant le ${total} pour garder l'argent."
                else -> "Le mois est passé : rattrape les jours restants pour le bronze."
            }
            texts.addView(text(hint, 14f, Colors.MUTED, Fonts.regular), matchWrap())
        }
        row.addView(texts, weight1())
        trophyBox.addView(row, matchWrap())
    }

    private fun renderDay(day: LocalDate, state: DayState, playable: Boolean) {
        dayBox.removeAllViews()
        val head = hbox(10)
        head.addView(text(Fr.longDay(day).replaceFirstChar { it.uppercase() }, 17f, Colors.FG, Fonts.extra), wrap())
        head.addView(levelChip(Daily.level(day).label), wrap())
        dayBox.addView(head, matchWrap())

        val saved = store.loadGame(day.toString())?.takeIf { it.status == Status.PLAYING }
        when {
            !playable -> dayBox.addView(text("Cette grille n'est pas encore disponible.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
            state != DayState.TODO -> {
                val time = store.dayTime(day)
                val how = when (state) {
                    DayState.SAME_DAY -> "Réussie le jour même"
                    DayState.IN_MONTH -> "Rattrapée dans le mois"
                    else -> "Rattrapée après le mois"
                }
                val done = store.doneOn(day)
                val doneText = if (done != null && done != day) " (le ${done.dayOfMonth} ${Fr.monthName(done.monthValue)})" else ""
                dayBox.addView(text("$how$doneText, en ${Fr.duration(time)}.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
            }
            else -> {
                val label = when {
                    saved != null -> "Reprendre la grille du ${day.dayOfMonth}"
                    else -> "Jouer la grille du ${day.dayOfMonth}"
                }
                if (saved != null) {
                    dayBox.addView(text("Partie en cours : ${Fr.clock(saved.elapsedMs)}, ${saved.errors} erreur${if (saved.errors > 1) "s" else ""}.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
                }
                dayBox.addView(button(label) { startActivity(GameActivity.daily(this, day, fromCalendar = true)) }, matchWrap())
            }
        }
    }
}

/** Petite étiquette arrondie avec le niveau. */
fun Context.levelChip(label: String): TextView = text(label, 13f, Colors.DEEP_GREEN, Fonts.extra).apply {
    background = roundRect(Colors.MINT, dpf(99))
    setPadding(dp(10), dp(3), dp(10), dp(3))
}
