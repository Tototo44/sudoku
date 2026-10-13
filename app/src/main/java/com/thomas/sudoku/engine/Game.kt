package com.thomas.sudoku.engine

/** Où en est une partie. */
enum class Status { PLAYING, WON, LOST }

/** Ce qui s'est passé quand on pose un chiffre. */
sealed class Move {
    /** Rien n'a changé (case donnée ou déjà juste). */
    object Ignored : Move()

    /** Chiffre faux : il s'affiche en rouge et compte une erreur. */
    data class Wrong(val errors: Int, val lost: Boolean) : Move()

    /** Chiffre juste, avec les lignes, colonnes et carrés qu'il vient de terminer. */
    data class Right(
        val rows: List<Int>,
        val cols: List<Int>,
        val boxes: List<Int>,
        val won: Boolean,
    ) : Move()
}

/**
 * Une partie de sudoku.
 * [key] range la partie : "free" pour la partie libre, ou la date "2026-10-13" pour une grille du jour.
 */
class Game(
    val key: String,
    val level: Level,
    val givens: IntArray,
    val solution: IntArray,
) {
    /** Chiffres posés par le joueur (0 = rien). Un chiffre faux reste affiché en rouge. */
    val entries = IntArray(81)

    /** Petits chiffres du crayon : un bit par chiffre (bit 1 = chiffre 1, etc.). */
    val notes = IntArray(81)

    var errors = 0
    var elapsedMs = 0L
    var status = Status.PLAYING

    val isDaily: Boolean get() = key != FREE

    fun value(i: Int): Int = if (givens[i] != 0) givens[i] else entries[i]
    fun isGiven(i: Int) = givens[i] != 0
    fun isWrong(i: Int) = givens[i] == 0 && entries[i] != 0 && entries[i] != solution[i]

    /** Case bien remplie (donnée au départ, ou chiffre juste du joueur) : on ne peut plus la changer. */
    fun isSolved(i: Int) = value(i) != 0 && value(i) == solution[i]

    /** Combien de fois il reste à placer le chiffre [d]. */
    fun remaining(d: Int): Int = 9 - (0 until 81).count { isSolved(it) && value(it) == d }

    fun place(i: Int, d: Int): Move {
        // Retaper le même chiffre faux ne compte pas une deuxième erreur.
        if (status != Status.PLAYING || isSolved(i) || d !in 1..9 || entries[i] == d) return Move.Ignored
        notes[i] = 0
        entries[i] = d
        if (d != solution[i]) {
            errors++
            if (errors >= MAX_ERRORS) status = Status.LOST
            return Move.Wrong(errors, status == Status.LOST)
        }
        // Le chiffre est juste : on l'efface des petites notes voisines.
        val bit = 1 shl d
        for (p in Cells.peers[i]) notes[p] = notes[p] and bit.inv()

        val rows = listOf(Cells.row(i)).filter { r -> Cells.rows[r].all { isSolved(it) } }
        val cols = listOf(Cells.col(i)).filter { c -> Cells.cols[c].all { isSolved(it) } }
        val boxes = listOf(Cells.box(i)).filter { b -> Cells.boxes[b].all { isSolved(it) } }
        val won = (0 until 81).all { isSolved(it) }
        if (won) status = Status.WON
        return Move.Right(rows, cols, boxes, won)
    }

    /** Ajoute ou retire un petit chiffre au crayon. Seulement dans une case vide. */
    fun toggleNote(i: Int, d: Int): Boolean {
        if (status != Status.PLAYING || value(i) != 0 || d !in 1..9) return false
        notes[i] = notes[i] xor (1 shl d)
        return true
    }

    /** Efface un chiffre faux, ou les petites notes d'une case. */
    fun erase(i: Int): Boolean {
        if (status != Status.PLAYING || isSolved(i)) return false
        if (entries[i] != 0) {
            entries[i] = 0
            return true
        }
        if (notes[i] != 0) {
            notes[i] = 0
            return true
        }
        return false
    }

    /** La même grille, depuis le début (après 3 erreurs). */
    fun restart(): Game = Game(key, level, givens, solution)

    /** Transforme la partie en texte, pour la garder en mémoire quand l'appli est fermée. */
    fun encode(): String = listOf(
        "v1",
        key,
        level.name,
        givens.joinToString(""),
        solution.joinToString(""),
        entries.joinToString(""),
        notes.joinToString(","),
        errors.toString(),
        elapsedMs.toString(),
        status.name,
    ).joinToString("|")

    companion object {
        const val FREE = "free"
        const val MAX_ERRORS = 3

        fun decode(text: String): Game? = try {
            val p = text.split("|")
            require(p.size == 10 && p[0] == "v1")
            fun digits(s: String): IntArray {
                require(s.length == 81)
                return IntArray(81) { s[it] - '0' }
            }
            Game(p[1], Level.valueOf(p[2]), digits(p[3]), digits(p[4])).apply {
                digits(p[5]).copyInto(entries)
                val n = p[6].split(",").map { it.toInt() }
                require(n.size == 81)
                n.toIntArray().copyInto(notes)
                errors = p[7].toInt()
                elapsedMs = p[8].toLong()
                status = Status.valueOf(p[9])
            }
        } catch (e: Exception) {
            null
        }
    }
}
