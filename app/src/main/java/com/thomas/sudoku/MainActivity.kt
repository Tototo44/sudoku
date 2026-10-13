package com.thomas.sudoku

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.thomas.sudoku.engine.DayState
import com.thomas.sudoku.engine.Daily
import com.thomas.sudoku.engine.Game
import com.thomas.sudoku.engine.Level
import com.thomas.sudoku.engine.Status
import java.time.LocalDate
import java.time.YearMonth

/** L'accueil : la grille du jour, la partie libre et les trophées. */
class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        store = Store(this)
        val scroll = ScrollView(this).apply { setBackgroundColor(Colors.BG) }
        column = vbox(16).apply { setPadding(dp(16), 0, dp(16), 0) }
        scroll.addView(column)
        setContentView(scroll)
        edgeToEdge(column, extraTop = 24)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun label(value: String) = text(value, 12f, Colors.SAGE, Fonts.extra).apply { letterSpacing = 0.12f }

    private fun render() {
        column.removeAllViews()
        val today = LocalDate.now()
        val pr = store.progress

        // En-tête.
        val head = vbox(4)
        head.addView(text("Sudoku", 38f, Colors.FG, Fonts.extra), wrap())
        head.addView(text(Fr.longDay(today).replaceFirstChar { it.uppercase() }, 16f, Colors.MUTED, Fonts.medium), wrap())
        column.addView(head, matchWrap().apply { bottomMargin = dp(4) })

        // Grille du jour.
        val daily = card()
        daily.addView(label("GRILLE DU JOUR"), wrap())
        val row = hbox(10)
        row.addView(text(Fr.longDay(today).replaceFirstChar { it.uppercase() }, 20f, Colors.FG, Fonts.extra), wrap())
        row.addView(levelChip(Daily.level(today).label), wrap())
        daily.addView(row, matchWrap())
        val state = pr.state(today)
        val saved = store.loadGame(today.toString())?.takeIf { it.status == Status.PLAYING }
        when {
            state != DayState.TODO -> {
                daily.addView(text("Réussie en ${Fr.duration(store.dayTime(today))}. La pastille du jour a son étoile.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
                daily.addView(button("Voir le calendrier", primary = false) { startActivity(CalendarActivity.open(this)) }, matchWrap())
            }
            saved != null -> {
                daily.addView(text("Partie en cours : ${Fr.clock(saved.elapsedMs)}, ${saved.errors} erreur${if (saved.errors > 1) "s" else ""}.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
                daily.addView(button("Reprendre") { startActivity(GameActivity.daily(this, today)) }, matchWrap())
            }
            else -> {
                daily.addView(text("Une nouvelle grille t'attend. Réussis-la aujourd'hui pour gagner l'étoile.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
                daily.addView(button("Jouer") { startActivity(GameActivity.daily(this, today)) }, matchWrap())
            }
        }
        // Le mois en cours, en une ligne.
        val month = YearMonth.from(today)
        val monthLine = hbox(12).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { startActivity(CalendarActivity.open(this@MainActivity)) }
            setPadding(0, dp(4), 0, 0)
        }
        val won = pr.monthTrophy(month)
        val best = won ?: pr.bestPossible(month, today)
        monthLine.addView(CupView(this, 34).apply { color = best.color() }, wrap())
        val monthText = if (won != null) "${Fr.month(month)} : trophée ${won.label} gagné !"
        else "${Fr.month(month)} : ${pr.doneCount(month)} / ${month.lengthOfMonth()}, trophée visé ${best?.label ?: "—"}"
        monthLine.addView(text(monthText, 14f, Colors.MUTED, Fonts.bold), weight1())
        monthLine.addView(text("Calendrier ›", 14f, Colors.SAGE, Fonts.extra), wrap())
        daily.addView(monthLine, matchWrap())
        column.addView(daily, matchWrap())

        // Partie libre.
        val free = card()
        free.addView(label("PARTIE LIBRE"), wrap())
        free.addView(text("Une grille au niveau de ton choix, quand tu veux. Elle ne compte pas pour les trophées.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
        val freeSaved = store.loadGame(Game.FREE)?.takeIf { it.status == Status.PLAYING }
        if (freeSaved != null) {
            free.addView(button("Reprendre (${freeSaved.level.label}, ${Fr.clock(freeSaved.elapsedMs)})") {
                startActivity(GameActivity.resumeFree(this))
            }, matchWrap())
            free.addView(text("Ou commence une nouvelle partie :", 14f, Colors.MUTED, Fonts.medium), matchWrap())
        }
        val levels = Level.values()
        for (pair in listOf(0, 2)) {
            val line = hbox(10)
            for (k in pair..pair + 1) {
                val level = levels[k]
                line.addView(button(level.label, primary = false) { startFree(level, freeSaved != null) }, weight1())
            }
            free.addView(line, matchWrap())
        }
        column.addView(free, matchWrap())

        // Trophées.
        val cups = card()
        cups.addView(label("MES TROPHÉES"), wrap())
        val shelfLine = hbox(6)
        var count = 0
        var m = YearMonth.from(store.start)
        while (!m.isAfter(month)) {
            pr.monthTrophy(m)?.let { t ->
                shelfLine.addView(CupView(this, 30).apply { color = t.color() }, wrap())
                count++
            }
            m = m.plusMonths(1)
        }
        if (count == 0) {
            cups.addView(text("Pas encore de trophée. Réussis toutes les grilles d'un mois pour gagner le premier.", 15f, Colors.MUTED, Fonts.medium), matchWrap())
        } else {
            cups.addView(shelfLine, wrap())
        }
        cups.addView(button("Voir mes trophées et records", primary = false) {
            startActivity(Intent(this, TrophiesActivity::class.java))
        }, matchWrap())
        column.addView(cups, matchWrap())
    }

    private fun startFree(level: Level, replace: Boolean) {
        if (!replace) {
            startActivity(GameActivity.newFree(this, level))
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle("Nouvelle partie ?")
            .setMessage("La partie libre en cours sera perdue.")
            .setPositiveButton("Oui, commencer") { _, _ -> startActivity(GameActivity.newFree(this, level)) }
            .setNegativeButton("Non", null)
            .show()
    }
}
