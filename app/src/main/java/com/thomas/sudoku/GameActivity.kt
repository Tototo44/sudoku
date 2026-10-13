package com.thomas.sudoku

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.thomas.sudoku.engine.Cells
import com.thomas.sudoku.engine.DayState
import com.thomas.sudoku.engine.Daily
import com.thomas.sudoku.engine.Game
import com.thomas.sudoku.engine.Generator
import com.thomas.sudoku.engine.Level
import com.thomas.sudoku.engine.Move
import com.thomas.sudoku.engine.Status
import com.thomas.sudoku.engine.Trophy
import java.time.LocalDate
import java.time.YearMonth

/** L'écran de jeu. */
class GameActivity : Activity() {

    companion object {
        private const val EXTRA_DAY = "day"
        private const val EXTRA_LEVEL = "level"
        private const val EXTRA_FROM_CALENDAR = "from_calendar"
        private const val STATE_KEY = "game_key"

        /** Grille du jour (reprend la partie en cours s'il y en a une). */
        fun daily(ctx: Context, day: LocalDate, fromCalendar: Boolean = false): Intent =
            Intent(ctx, GameActivity::class.java)
                .putExtra(EXTRA_DAY, day.toString())
                .putExtra(EXTRA_FROM_CALENDAR, fromCalendar)

        /** Nouvelle partie libre au niveau choisi. */
        fun newFree(ctx: Context, level: Level): Intent =
            Intent(ctx, GameActivity::class.java).putExtra(EXTRA_LEVEL, level.name)

        /** Reprendre la partie libre en cours. */
        fun resumeFree(ctx: Context): Intent = Intent(ctx, GameActivity::class.java)
    }

    private lateinit var store: Store
    private lateinit var game: Game

    private lateinit var grid: SudokuView
    private lateinit var pad: PadView
    private lateinit var levelText: TextView
    private lateinit var subtitle: TextView
    private lateinit var errorsText: TextView
    private lateinit var clockText: TextView
    private lateinit var pencilBox: LinearLayout
    private lateinit var pencilIcon: ImageView
    private lateinit var pencilLabel: TextView
    private lateinit var screen: FrameLayout
    private var overlay: View? = null

    private var selected = -1
    private var pencil = false

    private val handler = Handler(Looper.getMainLooper())
    private var lastTick = 0L
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        store = Store(this)
        // Si l'écran est recréé (téléphone qui manque de mémoire…), on reprend la partie gardée.
        val restored = savedInstanceState?.getString(STATE_KEY)?.let { key ->
            store.loadGame(key)?.takeIf { it.status == Status.PLAYING }
        }
        game = restored ?: loadOrCreate()
        build()
        refresh()
    }

    private fun loadOrCreate(): Game {
        intent.getStringExtra(EXTRA_DAY)?.let { iso ->
            val day = LocalDate.parse(iso)
            return store.loadGame(iso)?.takeIf { it.status == Status.PLAYING } ?: Daily.newGame(day)
        }
        intent.getStringExtra(EXTRA_LEVEL)?.let { name ->
            return newFreeGame(Level.valueOf(name))
        }
        return store.loadGame(Game.FREE)?.takeIf { it.status == Status.PLAYING } ?: newFreeGame(Level.MOYEN)
    }

    private fun newFreeGame(level: Level): Game {
        val p = Generator.generate(level, System.nanoTime())
        return Game(Game.FREE, level, p.givens, p.solution).also { store.saveGame(it) }
    }

    // ---------- Construction de l'écran ----------

    private fun build() {
        screen = FrameLayout(this).apply { setBackgroundColor(Colors.BG) }
        val column = vbox().apply { setPadding(dp(16), 0, dp(16), 0) }
        screen.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // Barre du haut : retour, niveau.
        val top = FrameLayout(this)
        top.addView(link("‹ Retour") { finish() }, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START or Gravity.CENTER_VERTICAL))
        val heading = vbox(2).apply { gravity = Gravity.CENTER_HORIZONTAL }
        levelText = text("", 17f, Colors.SAGE, Fonts.extra).apply { gravity = Gravity.CENTER }
        subtitle = text("", 13f, Colors.MUTED, Fonts.medium).apply { gravity = Gravity.CENTER }
        heading.addView(levelText, wrap())
        heading.addView(subtitle, wrap())
        top.addView(heading, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        column.addView(top, matchWrap())

        // Ligne d'infos : erreurs et chrono.
        val meta = hbox()
        errorsText = text("", 14f, Colors.MUTED, Fonts.bold)
        clockText = text("00:00", 14f, Colors.MUTED, Fonts.bold).apply {
            gravity = Gravity.END
            fontFeatureSettings = "tnum"
        }
        meta.addView(errorsText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        meta.addView(clockText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        column.addView(meta, matchWrap().apply { topMargin = dp(8) })

        grid = SudokuView(this).apply {
            game = this@GameActivity.game
            onCellTap = { i -> this@GameActivity.onCellTapped(i) }
        }
        // La grille prend la place disponible (plus petite sur un petit téléphone), toujours carrée.
        val gridBox = FrameLayout(this)
        gridBox.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        column.addView(gridBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(14) })

        // Outils : Effacer et Crayon (pas d'annuler, pas d'indice).
        val tools = hbox().apply { gravity = Gravity.CENTER }
        tools.addView(tool(R.drawable.ic_erase, "Effacer") { onErase() }.first, LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
        val p = tool(R.drawable.ic_pencil, "Crayon") { togglePencil() }
        pencilBox = p.first
        pencilIcon = p.second
        pencilLabel = p.third
        tools.addView(pencilBox, LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(tools, matchWrap().apply { topMargin = dp(16) })

        pad = PadView(this).apply { onDigit = { d -> this@GameActivity.onDigitTapped(d) } }
        column.addView(pad, matchWrap().apply { topMargin = dp(16) })

        setContentView(screen)
        edgeToEdge(column)
    }

    private fun tool(icon: Int, label: String, onClick: () -> Unit): Triple<LinearLayout, ImageView, TextView> {
        val box = vbox(6).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val img = ImageView(this).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(Colors.FG)
            background = roundRect(Colors.CARD, dpf(16))
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        box.addView(img, LinearLayout.LayoutParams(dp(48), dp(48)))
        val t = text(label, 13f, Colors.MUTED, Fonts.bold).apply { gravity = Gravity.CENTER }
        box.addView(t, wrap())
        return Triple(box, img, t)
    }

    // ---------- Actions du joueur ----------

    private fun onCellTapped(i: Int) {
        if (overlay != null) return
        selected = i
        grid.selected = i
    }

    private fun onDigitTapped(d: Int) {
        if (overlay != null || game.status != Status.PLAYING) return
        val i = selected
        if (i < 0) return
        if (pencil) {
            if (game.toggleNote(i, d)) refresh()
            return
        }
        when (val move = game.place(i, d)) {
            is Move.Ignored -> return
            is Move.Wrong -> {
                Buzz.error(this)
                refresh()
                if (move.lost) onLost()
            }
            is Move.Right -> {
                move.rows.forEach { grid.wave(Cells.rows[it]) }
                move.cols.forEach { grid.wave(Cells.cols[it]) }
                move.boxes.forEach { grid.pop(Cells.boxes[it]) }
                when {
                    move.won -> {}
                    move.boxes.isNotEmpty() -> Buzz.medium(this)
                    move.rows.isNotEmpty() || move.cols.isNotEmpty() -> Buzz.short(this)
                }
                refresh()
                if (move.won) onWon()
            }
        }
    }

    private fun onErase() {
        if (overlay != null || selected < 0) return
        if (game.erase(selected)) refresh()
    }

    private fun togglePencil() {
        if (overlay != null) return
        pencil = !pencil
        pencilIcon.background = roundRect(if (pencil) Colors.MINT else Colors.CARD, dpf(16))
        pencilIcon.imageTintList = ColorStateList.valueOf(if (pencil) Colors.SAGE else Colors.FG)
        pencilLabel.text = if (pencil) "Crayon activé" else "Crayon"
        pencilLabel.setTextColor(if (pencil) Colors.SAGE else Colors.MUTED)
        pad.pencil = pencil
    }

    private fun refresh() {
        levelText.text = game.level.label
        subtitle.text = if (game.isDaily) "Grille du ${Fr.longDay(LocalDate.parse(game.key))}" else "Partie libre"
        errorsText.text = "Erreurs ${game.errors} / ${Game.MAX_ERRORS}"
        errorsText.setTextColor(if (game.errors > 0) Colors.ERROR else Colors.MUTED)
        clockText.text = Fr.clock(game.elapsedMs)
        pad.remaining = IntArray(10) { if (it == 0) 0 else game.remaining(it) }
        grid.invalidate()
        if (game.status == Status.PLAYING) store.saveGame(game)
    }

    // ---------- Chronomètre ----------

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        if (game.status == Status.PLAYING && overlay == null) {
            game.elapsedMs += now - lastTick
            clockText.text = Fr.clock(game.elapsedMs)
        }
        lastTick = now
    }

    override fun onResume() {
        super.onResume()
        lastTick = SystemClock.uptimeMillis()
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        tick()
        handler.removeCallbacks(ticker)
        if (game.status == Status.PLAYING) store.saveGame(game)
    }

    // ---------- Fin de partie ----------

    private fun onLost() {
        tick()
        store.clearGame(game.key)
        handler.postDelayed({
            showResult(
                cup = null,
                title = "Partie terminée",
                lines = listOf("Tu as fait 3 erreurs.", "Tu peux recommencer la même grille depuis le début."),
                primary = "Recommencer" to { restart() },
                secondary = (if (game.isDaily) "Retour" else "Accueil") to { finish() },
            )
        }, 450L)
    }

    private fun restart() {
        hideResult()
        game = game.restart()
        selected = -1
        grid.selected = -1
        grid.game = game
        if (pencil) togglePencil()
        lastTick = SystemClock.uptimeMillis()
        refresh()
    }

    private fun onWon() {
        tick()
        val time = game.elapsedMs
        store.clearGame(game.key)
        val record = store.recordWin(game.level, time)
        val lines = mutableListOf("Grille réussie en ${Fr.duration(time)}.")
        var cup: Trophy? = null
        var yearCup = false
        if (game.isDaily) {
            val day = LocalDate.parse(game.key)
            val month = YearMonth.from(day)
            val before = store.progress
            val monthBefore = before.monthTrophy(month)
            val yearBefore = before.yearTrophy(day.year)
            store.markDone(day, LocalDate.now(), time)
            val after = store.progress
            lines += when (after.state(day)) {
                DayState.SAME_DAY -> "Réussie le jour même : la pastille a son étoile."
                DayState.IN_MONTH -> "Rattrapée dans le mois."
                else -> "Rattrapée après la fin du mois."
            }
            val monthNow = after.monthTrophy(month)
            if (monthBefore == null && monthNow != null) {
                cup = monthNow
                lines += "Tu gagnes le trophée ${monthNow.label} ${Fr.ofMonth(month)} ${month.year} !"
            }
            val yearNow = after.yearTrophy(day.year)
            if (yearBefore == null && yearNow != null) {
                cup = yearNow
                yearCup = true
                lines += "Et le grand trophée ${yearNow.label} de l'année ${day.year} !"
            }
        }
        if (record) lines += "Nouveau record en ${game.level.label} !"

        selected = -1
        grid.selected = -1
        grid.celebrate()
        Buzz.win(this)
        val fromCalendar = intent.getBooleanExtra(EXTRA_FROM_CALENDAR, false)
        val level = game.level
        val day = if (game.isDaily) LocalDate.parse(game.key) else null
        handler.postDelayed({
            if (day != null) {
                showResult(
                    cup = cup, bigStar = yearCup,
                    title = "Bravo !",
                    lines = lines,
                    primary = "Voir le calendrier" to {
                        if (!fromCalendar) startActivity(CalendarActivity.open(this, YearMonth.from(day)))
                        finish()
                    },
                    secondary = "Accueil" to { goHome() },
                )
            } else {
                showResult(
                    cup = null,
                    title = "Bravo !",
                    lines = lines,
                    primary = "Nouvelle partie" to { newFreeAgain(level) },
                    secondary = "Accueil" to { goHome() },
                )
            }
        }, 1500L)
    }

    private fun newFreeAgain(level: Level) {
        hideResult()
        game = newFreeGame(level)
        selected = -1
        grid.selected = -1
        grid.game = game
        if (pencil) togglePencil()
        lastTick = SystemClock.uptimeMillis()
        refresh()
    }

    private fun goHome() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun showResult(
        cup: Trophy?,
        bigStar: Boolean = false,
        title: String,
        lines: List<String>,
        primary: Pair<String, () -> Unit>,
        secondary: Pair<String, () -> Unit>,
    ) {
        hideResult()
        val dim = FrameLayout(this).apply {
            setBackgroundColor(0x992C3A33.toInt())
            isClickable = true
        }
        val box = card().apply { gravity = Gravity.CENTER_HORIZONTAL }
        if (cup != null) {
            val cupColor = cup.color()
            box.addView(CupView(this, 72).apply { color = cupColor; star = bigStar }, wrap())
        }
        box.addView(text(title, 26f, Colors.FG, Fonts.extra).apply { gravity = Gravity.CENTER }, matchWrap())
        lines.forEach { line ->
            box.addView(text(line, 16f, Colors.MUTED, Fonts.medium).apply { gravity = Gravity.CENTER }, matchWrap())
        }
        box.addView(button(primary.first) { primary.second() }, matchWrap().apply { topMargin = dp(8) })
        box.addView(button(secondary.first, primary = false) { secondary.second() }, matchWrap())
        dim.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
            leftMargin = dp(24)
            rightMargin = dp(24)
        })
        screen.addView(dim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dim.alpha = 0f
        dim.animate().alpha(1f).setDuration(220L).start()
        overlay = dim
    }

    private fun hideResult() {
        overlay?.let { screen.removeView(it) }
        overlay = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (game.status == Status.PLAYING) store.saveGame(game)
        outState.putString(STATE_KEY, game.key)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
