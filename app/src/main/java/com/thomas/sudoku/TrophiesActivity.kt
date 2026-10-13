package com.thomas.sudoku

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.thomas.sudoku.engine.Level
import java.time.LocalDate
import java.time.YearMonth

/** L'étagère : les trophées des 12 mois, le grand trophée de l'année, et les records. */
class TrophiesActivity : Activity() {

    private lateinit var store: Store
    private var year = LocalDate.now().year

    private lateinit var title: TextView
    private lateinit var prev: TextView
    private lateinit var next: TextView
    private lateinit var yearBox: LinearLayout
    private lateinit var shelf: LinearLayout
    private lateinit var records: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        store = Store(this)
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
        prev = arrow("‹") { move(-1) }
        next = arrow("›") { move(1) }
        title = text("", 22f, Colors.FG, Fonts.extra)
        nav.addView(prev, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        nav.addView(title, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        nav.addView(next, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        column.addView(nav, matchWrap())

        yearBox = vbox().apply {
            background = roundRect(Colors.GOLD_SOFT, dpf(20))
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        column.addView(yearBox, matchWrap())

        shelf = card()
        column.addView(shelf, matchWrap())

        records = card()
        column.addView(records, matchWrap())

        column.addView(text("Les trophées et les records sont gardés sur ce téléphone.", 13f, Colors.MUTED, Fonts.medium), matchWrap())

        setContentView(scroll)
        edgeToEdge(column)
    }

    private fun arrow(label: String, onClick: () -> Unit): TextView =
        text(label, 30f, Colors.SAGE, Fonts.extra).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(2), dp(16), dp(6))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }

    private fun move(delta: Int) {
        val target = year + delta
        if (target < store.start.year || target > LocalDate.now().year) return
        year = target
        render()
    }

    private fun render() {
        val pr = store.progress
        val today = LocalDate.now()
        val start = store.start
        title.text = "Mes trophées $year"
        prev.alpha = if (year > start.year) 1f else 0.25f
        next.alpha = if (year < today.year) 1f else 0.25f

        // Le grand trophée de l'année.
        yearBox.removeAllViews()
        val big = hbox(14)
        val trophy = pr.yearTrophy(year)
        big.addView(CupView(this, 60).apply { color = if (trophy != null) trophy.color() else 0x66C99A2E; star = true }, wrap())
        val texts = vbox(4)
        texts.addView(text("Grand trophée $year", 18f, Colors.FG, Fonts.extra), wrap())
        val sub = if (trophy != null) "Gagné : ${trophy.label}" else "${pr.monthsDone(year)} mois sur 12 terminés"
        texts.addView(text(sub, 14f, Colors.MUTED, Fonts.bold), wrap())
        big.addView(texts, weight1())
        yearBox.addView(big, matchWrap())

        // L'étagère des 12 mois.
        shelf.removeAllViews()
        for (row in 0 until 3) {
            val line = hbox()
            for (col in 0 until 4) {
                val m = row * 4 + col + 1
                val ym = YearMonth.of(year, m)
                val won = pr.monthTrophy(ym)
                val item = vbox(2).apply { gravity = Gravity.CENTER_HORIZONTAL }
                val current = ym == YearMonth.from(today)
                item.addView(CupView(this, 42).apply {
                    color = when {
                        won != null -> won.color()
                        current -> Colors.SAGE_SOFT
                        else -> Colors.LOCKED
                    }
                }, wrap())
                item.addView(text(Fr.shortMonth(m), 13f, if (won != null) Colors.FG else Colors.MUTED, Fonts.bold).apply { gravity = Gravity.CENTER }, wrap())
                val label = when {
                    won != null -> won.label
                    current -> "${pr.doneCount(ym)}/${ym.lengthOfMonth()}"
                    else -> " "
                }
                item.addView(text(label, 12f, Colors.MUTED, Fonts.medium).apply { gravity = Gravity.CENTER }, wrap())
                line.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
            shelf.addView(line, matchWrap())
        }

        // Les records par niveau.
        records.removeAllViews()
        records.addView(text("RECORDS", 12f, Colors.SAGE, Fonts.extra).apply { letterSpacing = 0.12f }, wrap())
        for (level in Level.values()) {
            val line = hbox(10)
            line.addView(text(level.label, 16f, Colors.FG, Fonts.extra), weight1())
            val best = store.best(level)
            line.addView(text(if (best > 0) Fr.clock(best) else "—", 15f, Colors.FG, Fonts.bold).apply { fontFeatureSettings = "tnum" }, wrap())
            val wins = store.wins(level)
            line.addView(text("$wins grille${if (wins > 1) "s" else ""}", 14f, Colors.MUTED, Fonts.medium).apply {
                gravity = Gravity.END
                minWidth = dp(84)
            }, wrap())
            records.addView(line, matchWrap())
        }
    }
}
