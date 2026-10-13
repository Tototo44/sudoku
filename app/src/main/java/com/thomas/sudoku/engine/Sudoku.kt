package com.thomas.sudoku.engine

import kotlin.random.Random

/** Les 4 niveaux. [clues] = nombre de chiffres donnés au départ (moins il y en a, plus c'est dur). */
enum class Level(val label: String, val clues: Int) {
    FACILE("Facile", 40),
    MOYEN("Moyen", 34),
    DIFFICILE("Difficile", 29),
    EXPERT("Expert", 25);
}

/** Une grille : les chiffres donnés (0 = case vide) et la solution complète. */
class Puzzle(val givens: IntArray, val solution: IntArray)

/** Outils sur les cases : une grille est un tableau de 81 cases, ligne par ligne. */
object Cells {
    fun row(i: Int) = i / 9
    fun col(i: Int) = i % 9
    fun box(i: Int) = (row(i) / 3) * 3 + col(i) / 3

    val rows: List<IntArray> = List(9) { r -> IntArray(9) { r * 9 + it } }
    val cols: List<IntArray> = List(9) { c -> IntArray(9) { it * 9 + c } }
    val boxes: List<IntArray> = List(9) { b ->
        val r0 = (b / 3) * 3
        val c0 = (b % 3) * 3
        IntArray(9) { (r0 + it / 3) * 9 + c0 + it % 3 }
    }

    /** Les 20 cases qui partagent la ligne, la colonne ou le carré de la case [i]. */
    val peers: List<IntArray> = List(81) { i ->
        (0 until 81).filter { j ->
            j != i && (row(j) == row(i) || col(j) == col(i) || box(j) == box(i))
        }.toIntArray()
    }
}

/** Résolution rapide (retour en arrière), pour vérifier qu'une grille n'a qu'une seule solution. */
object Solver {
    /** Compte les solutions, en s'arrêtant dès qu'on atteint [limit]. */
    fun countSolutions(grid: IntArray, limit: Int = 2): Int {
        val g = grid.copyOf()
        val rowMask = IntArray(9)
        val colMask = IntArray(9)
        val boxMask = IntArray(9)
        for (i in 0 until 81) {
            val v = g[i]
            if (v != 0) {
                val bit = 1 shl v
                val r = Cells.row(i); val c = Cells.col(i); val b = Cells.box(i)
                if (rowMask[r] and bit != 0 || colMask[c] and bit != 0 || boxMask[b] and bit != 0) return 0
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
            }
        }
        var count = 0
        fun search(): Boolean {
            // Choisit la case vide qui a le moins de possibilités.
            var best = -1
            var bestFree = 0
            var bestCount = 10
            for (i in 0 until 81) {
                if (g[i] != 0) continue
                val used = rowMask[Cells.row(i)] or colMask[Cells.col(i)] or boxMask[Cells.box(i)]
                val free = 0x3FE and used.inv()
                val n = Integer.bitCount(free)
                if (n < bestCount) {
                    best = i; bestFree = free; bestCount = n
                    if (n <= 1) break
                }
            }
            if (best == -1) {
                count++
                return count >= limit
            }
            if (bestCount == 0) return false
            val r = Cells.row(best); val c = Cells.col(best); val b = Cells.box(best)
            var free = bestFree
            while (free != 0) {
                val bit = free and -free
                free = free xor bit
                g[best] = Integer.numberOfTrailingZeros(bit)
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit
                if (search()) return true
                rowMask[r] = rowMask[r] xor bit
                colMask[c] = colMask[c] xor bit
                boxMask[b] = boxMask[b] xor bit
                g[best] = 0
            }
            return false
        }
        search()
        return count
    }
}

/** Fabrique des grilles. Avec la même graine, on obtient toujours la même grille (grille du jour). */
object Generator {
    fun generate(level: Level, seed: Long): Puzzle {
        val rnd = Random(seed)
        val solution = IntArray(81)
        fill(solution, 0, rnd)
        val givens = solution.copyOf()
        var clues = 81
        for (i in (0 until 81).shuffled(rnd)) {
            if (clues <= level.clues) break
            val keep = givens[i]
            givens[i] = 0
            if (Solver.countSolutions(givens, 2) == 1) clues-- else givens[i] = keep
        }
        return Puzzle(givens, solution)
    }

    private fun fill(g: IntArray, pos: Int, rnd: Random): Boolean {
        if (pos == 81) return true
        for (d in (1..9).shuffled(rnd)) {
            if (Cells.peers[pos].none { g[it] == d }) {
                g[pos] = d
                if (fill(g, pos + 1, rnd)) return true
            }
        }
        g[pos] = 0
        return false
    }
}
