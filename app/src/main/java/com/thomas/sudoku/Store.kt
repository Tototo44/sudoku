package com.thomas.sudoku

import android.content.Context
import com.thomas.sudoku.engine.Game
import com.thomas.sudoku.engine.Level
import com.thomas.sudoku.engine.Progress
import java.time.LocalDate

/** Tout ce que l'appli garde en mémoire sur le téléphone. */
class Store(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("sudoku", Context.MODE_PRIVATE)

    /** Le calendrier commence au 1er du mois où l'appli a été ouverte pour la première fois. */
    val start: LocalDate
        get() {
            p.getString("start", null)?.let { return LocalDate.parse(it) }
            val first = LocalDate.now().withDayOfMonth(1)
            p.edit().putString("start", first.toString()).apply()
            return first
        }

    val progress: Progress get() = Progress(start) { doneOn(it) }

    /** Le jour où la grille du jour [day] a été réussie (ou null). */
    fun doneOn(day: LocalDate): LocalDate? = p.getString("done:$day", null)?.let { LocalDate.parse(it) }

    fun dayTime(day: LocalDate): Long = p.getLong("time:$day", 0L)

    /** Note la grille du jour comme réussie (la première réussite compte). */
    fun markDone(day: LocalDate, on: LocalDate, timeMs: Long) {
        if (doneOn(day) != null) return
        p.edit().putString("done:$day", on.toString()).putLong("time:$day", timeMs).apply()
    }

    fun saveGame(g: Game) {
        p.edit().putString("game:${g.key}", g.encode()).apply()
    }

    fun loadGame(key: String): Game? = p.getString("game:$key", null)?.let { Game.decode(it) }

    fun clearGame(key: String) {
        p.edit().remove("game:$key").apply()
    }

    /** Meilleur temps pour un niveau (0 = pas encore de record). */
    fun best(level: Level): Long = p.getLong("best:${level.name}", 0L)

    fun wins(level: Level): Int = p.getInt("wins:${level.name}", 0)

    /** Compte une grille réussie ; renvoie vrai si c'est un nouveau record. */
    fun recordWin(level: Level, timeMs: Long): Boolean {
        val old = best(level)
        val record = old == 0L || timeMs < old
        p.edit().apply {
            putInt("wins:${level.name}", wins(level) + 1)
            if (record) putLong("best:${level.name}", timeMs)
        }.apply()
        return record
    }
}
