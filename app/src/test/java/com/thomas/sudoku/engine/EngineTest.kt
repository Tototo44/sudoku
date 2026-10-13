package com.thomas.sudoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class EngineTest {

    @Test
    fun chaqueGrilleAUneSeuleSolution() {
        for (level in Level.values()) {
            for (seed in 1L..10L) {
                val p = Generator.generate(level, seed * 7919L)
                assertEquals(1, Solver.countSolutions(p.givens, 2))
                for (unit in Cells.rows + Cells.cols + Cells.boxes) {
                    assertEquals(9, unit.map { p.solution[it] }.toSet().size)
                }
                for (i in 0 until 81) if (p.givens[i] != 0) assertEquals(p.solution[i], p.givens[i])
                assertTrue(p.givens.count { it != 0 } <= level.clues + 3)
            }
        }
    }

    @Test
    fun laGrilleDuJourEstLaMemePourTous() {
        val day = LocalDate.of(2026, 10, 13)
        assertTrue(Daily.puzzle(day).givens.contentEquals(Daily.puzzle(day).givens))
        assertFalse(Daily.puzzle(day).givens.contentEquals(Daily.puzzle(day.plusDays(1)).givens))
        assertEquals(Level.FACILE, Daily.level(day))
        assertEquals(Level.EXPERT, Daily.level(LocalDate.of(2026, 10, 18)))
    }

    @Test
    fun reglesDeLaPartie() {
        val g = Daily.newGame(LocalDate.of(2026, 10, 13))
        val empty = (0 until 81).filter { g.givens[it] == 0 }
        val i = empty[0]
        val wrong = (1..9).first { it != g.solution[i] }
        assertTrue(g.toggleNote(i, 3))
        assertTrue(g.place(i, wrong) is Move.Wrong)
        assertEquals(1, g.errors)
        assertTrue(g.isWrong(i))
        assertEquals(Move.Ignored, g.place(i, wrong))
        assertEquals(1, g.errors)
        assertTrue(g.erase(i))
        assertTrue(g.place(i, g.solution[i]) is Move.Right)
        assertEquals(Move.Ignored, g.place(i, wrong))

        val copy = Game.decode(g.encode())
        assertNotNull(copy)
        assertEquals(g.encode(), copy!!.encode())

        var won = false
        for (k in empty.drop(1)) won = (g.place(k, g.solution[k]) as Move.Right).won
        assertTrue(won)
        assertEquals(Status.WON, g.status)
    }

    @Test
    fun troisErreursEtCestFini() {
        val g = Daily.newGame(LocalDate.of(2026, 10, 14))
        val empty = (0 until 81).filter { g.givens[it] == 0 }
        for (k in 0 until 3) {
            val i = empty[k]
            val m = g.place(i, (1..9).first { it != g.solution[i] }) as Move.Wrong
            assertEquals(k == 2, m.lost)
        }
        assertEquals(Status.LOST, g.status)
        assertEquals(0, g.restart().errors)
    }

    @Test
    fun trophees() {
        val done = HashMap<LocalDate, LocalDate>()
        val pr = Progress(LocalDate.of(2026, 10, 1)) { done[it] }
        val oct = YearMonth.of(2026, 10)
        for (d in 1..31) done[oct.atDay(d)] = oct.atDay(d)
        assertEquals(Trophy.OR, pr.monthTrophy(oct))
        done[oct.atDay(4)] = oct.atDay(9)
        assertEquals(DayState.IN_MONTH, pr.state(oct.atDay(4)))
        assertEquals(Trophy.ARGENT, pr.monthTrophy(oct))
        done[oct.atDay(7)] = LocalDate.of(2026, 11, 2)
        assertEquals(DayState.LATE, pr.state(oct.atDay(7)))
        assertEquals(Trophy.BRONZE, pr.monthTrophy(oct))
        done.remove(oct.atDay(7))
        assertNull(pr.monthTrophy(oct))
        assertEquals(Trophy.ARGENT, pr.bestPossible(oct, LocalDate.of(2026, 10, 13)))
        assertEquals(Trophy.BRONZE, pr.bestPossible(oct, LocalDate.of(2026, 11, 13)))
        assertNull(pr.monthTrophy(YearMonth.of(2026, 9)))
    }

    @Test
    fun grandTropheeDeLAnnee() {
        val done = HashMap<LocalDate, LocalDate>()
        val pr = Progress(LocalDate.of(2027, 1, 1)) { done[it] }
        var day = LocalDate.of(2027, 1, 1)
        while (day.year == 2027) {
            done[day] = day
            day = day.plusDays(1)
        }
        assertEquals(Trophy.OR, pr.yearTrophy(2027))
        done[LocalDate.of(2027, 3, 3)] = LocalDate.of(2027, 3, 20)
        assertEquals(Trophy.ARGENT, pr.yearTrophy(2027))
        done.remove(LocalDate.of(2027, 5, 5))
        assertNull(pr.yearTrophy(2027))
        assertEquals(11, pr.monthsDone(2027))
    }
}
