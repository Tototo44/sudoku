package com.thomas.sudoku.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** État d'une pastille du calendrier. */
enum class DayState {
    /** Pas encore réussie. */
    TODO,

    /** Réussie le jour même. */
    SAME_DAY,

    /** Rattrapée plus tard, mais dans le même mois. */
    IN_MONTH,

    /** Rattrapée après la fin du mois. */
    LATE,
}

/** Les trois trophées, du meilleur au moins bon. */
enum class Trophy(val label: String) {
    OR("Or"), ARGENT("Argent"), BRONZE("Bronze");
}

/** La grille du jour : même date = même grille, pour tout le monde. */
object Daily {
    /** Lundi et mardi Facile, mercredi et jeudi Moyen, vendredi et samedi Difficile, dimanche Expert. */
    fun level(day: LocalDate): Level = when (day.dayOfWeek) {
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY -> Level.FACILE
        DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY -> Level.MOYEN
        DayOfWeek.FRIDAY, DayOfWeek.SATURDAY -> Level.DIFFICILE
        else -> Level.EXPERT
    }

    fun seed(day: LocalDate): Long =
        (day.year * 10_000L + day.monthValue * 100L + day.dayOfMonth) * 1_000_003L + 7_919L

    fun puzzle(day: LocalDate): Puzzle = Generator.generate(level(day), seed(day))

    fun newGame(day: LocalDate): Game {
        val p = puzzle(day)
        return Game(day.toString(), level(day), p.givens, p.solution)
    }
}

/**
 * Le calendrier et les trophées.
 * [start] = premier jour jouable (le 1er du mois où l'appli a été installée).
 * [doneOn] = pour une date de grille, le jour où elle a été réussie (ou null).
 */
class Progress(
    val start: LocalDate,
    private val doneOn: (LocalDate) -> LocalDate?,
) {
    fun isPlayable(day: LocalDate, today: LocalDate) = !day.isBefore(start) && !day.isAfter(today)

    fun state(day: LocalDate): DayState {
        val done = doneOn(day) ?: return DayState.TODO
        return when {
            !done.isAfter(day) -> DayState.SAME_DAY
            YearMonth.from(done) == YearMonth.from(day) -> DayState.IN_MONTH
            else -> DayState.LATE
        }
    }

    private fun days(month: YearMonth): List<LocalDate> = (1..month.lengthOfMonth()).map { month.atDay(it) }

    /** Le trophée gagné pour ce mois, ou null si le mois n'est pas complet. */
    fun monthTrophy(month: YearMonth): Trophy? {
        if (month.atEndOfMonth().isBefore(start)) return null
        val states = days(month).map { state(it) }
        return when {
            states.any { it == DayState.TODO } -> null
            states.all { it == DayState.SAME_DAY } -> Trophy.OR
            states.none { it == DayState.LATE } -> Trophy.ARGENT
            else -> Trophy.BRONZE
        }
    }

    /** Le meilleur trophée encore possible pour ce mois (null si plus aucun n'est possible). */
    fun bestPossible(month: YearMonth, today: LocalDate): Trophy? {
        monthTrophy(month)?.let { return it }
        if (month.atEndOfMonth().isBefore(start)) return null
        val past = days(month).filter { it.isBefore(today) }
        return when {
            YearMonth.from(today).isAfter(month) -> Trophy.BRONZE
            past.all { state(it) == DayState.SAME_DAY } -> Trophy.OR
            else -> Trophy.ARGENT
        }
    }

    /** Nombre de grilles réussies dans le mois. */
    fun doneCount(month: YearMonth): Int = days(month).count { state(it) != DayState.TODO }

    /** Le grand trophée de l'année : il faut les 12 mois, et il prend le niveau du moins bon. */
    fun yearTrophy(year: Int): Trophy? {
        val months = (1..12).map { monthTrophy(YearMonth.of(year, it)) ?: return null }
        return months.maxByOrNull { it.ordinal }
    }

    fun monthsDone(year: Int): Int = (1..12).count { monthTrophy(YearMonth.of(year, it)) != null }
}
