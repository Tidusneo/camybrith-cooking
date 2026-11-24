package app.toque.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.Prefs
import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey
import app.toque.core.machine.JobState
import app.toque.core.machine.LinkState
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.RobotModel
import app.toque.core.machine.Snapshot
import app.toque.core.machine.TemperatureTable

/** Grandes parties de l'appli, une par entrée de la barre de navigation. */
enum class Section(val icon: Icon.Kind, private val fr: String) {
    HOME(Icon.Kind.HOME, trKey("Accueil")),
    RECIPES(Icon.Kind.BOOK, trKey("Recettes")),
    THERMOMIX(Icon.Kind.HAT, trKey("Thermomix")),
    MANUAL(Icon.Kind.POT, trKey("Cuisson")),
    PROGRAMS(Icon.Kind.GRID, trKey("Programmes")),
    SCALE(Icon.Kind.SCALE, trKey("Balance")),
    SETTINGS(Icon.Kind.SLIDERS, trKey("Réglages"));

    val label: String get() = tr(fr)
}

/** Écran de l'appli (vue construite une fois, réutilisée). */
abstract class Screen(val a: MainActivity) {
    abstract val title: String
    /** Partie de l'appli mise en avant dans la barre de navigation quand cet écran est à la base de la pile. */
    open val section: Section = Section.HOME
    /** Petite ligne sous le titre (« ÉTAPE 4 SUR 9 »). */
    open val subtitle: String = ""
    /** L'accueil dessine son propre en-tête. */
    open val showTopBar: Boolean = true
    /** Une recette guidée affichée attend un geste de la personne : l'anneau de la molette passe au vert. */
    open val cookWaiting: Boolean get() = false
    val view: View by lazy { build() }
    val c: Context get() = a

    protected abstract fun build(): View
    open fun onShow() {}
    open fun onHide() {}
    open fun onSnapshot(s: Snapshot) {}
    open fun onEvent(e: MachineEvent) {}
    open fun onKnob(delta: Int): Boolean = false
    open fun onKnobPress(): Boolean = false
    open fun canLeave(): Boolean = true
}

/** Barre de navigation, à gauche de tous les écrans. */
class NavRail(private val a: MainActivity) : LinearLayout(a) {
    private val items = HashMap<Section, LinearLayout>()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Ui.RAIL)
        setPadding(0, Ui.u(16), 0, Ui.u(12))
        // Marque : la cocotte orange et le nom de l'appli, comme le logo de démarrage.
        val logo = Ui.vertical(a, Gravity.CENTER_HORIZONTAL)
        logo.addView(BrandMark(a), LayoutParams(Ui.u(34), Ui.u(30)))
        val name = try { a.applicationInfo.loadLabel(a.packageManager).toString() } catch (_: Exception) { "Camybrith Cooking" }
        val words = name.trim().split(Regex("\\s+"))
        logo.addView(Ui.text(a, words.first(), 13f, Ui.TEXT, Ui.BRAND_BOLD).apply { gravity = Gravity.CENTER; maxLines = 1 }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 4))
        if (words.size > 1) logo.addView(Ui.text(a, words.drop(1).joinToString(" ").uppercase(), 7.5f, Ui.ACCENT, Ui.BRAND_LIGHT).apply {
            gravity = Gravity.CENTER
            letterSpacing = 0.3f
            maxLines = 1
        }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 1))
        addView(logo, Ui.lp(Ui.WRAP, Ui.WRAP, b = 12))
        for (s in Section.values()) {
            if (s == Section.SETTINGS) addView(View(a), LayoutParams(1, 0, 1f))
            val item = item(s)
            items[s] = item
            // Sept entrées dans 600 unités de haut : 60 + 3 chacune.
            addView(item, LayoutParams(Ui.u(74), Ui.u(60)).apply { topMargin = Ui.u(3) })
        }
    }

    private fun item(s: Section): LinearLayout {
        val v = Ui.vertical(a, Gravity.CENTER)
        v.addView(Icon(a, s.icon, Ui.TEXT2), LayoutParams(Ui.u(24), Ui.u(24)))
        v.addView(Ui.text(a, s.label, 12.5f, Ui.TEXT2, Ui.REGULAR).apply { gravity = Gravity.CENTER; maxLines = 1 }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 5))
        v.isClickable = true
        v.contentDescription = s.label
        v.tap { a.open(s) }
        return v
    }

    fun select(sel: Section?) {
        for ((s, v) in items) {
            val on = s == sel
            val col = if (on) Ui.ACCENT else Ui.TEXT2
            v.background = if (on) Ui.round(Ui.ACCENT_SOFT, 18f) else Ui.pressable(Ui.round(0, 18f), 18f)
            (v.getChildAt(0) as Icon).color = col
            (v.getChildAt(1) as TextView).apply {
                setTextColor(col)
                typeface = if (on) Ui.MEDIUM else Ui.REGULAR
            }
        }
    }
}

/** Pastilles d'état du robot et heure, en haut à droite (et dans l'en-tête de l'accueil). */
class StatusPills(private val a: MainActivity, showClock: Boolean = true) : LinearLayout(a) {
    private val lid = Ui.chip(a, tr("Couvercle ouvert"), Icon.Kind.LID, Ui.WARN, Ui.WARN_SOFT)
    private val temp = Ui.chip(a, "", Icon.Kind.THERMO, Ui.TEXT2)
    private val link = Ui.chip(a, "", null, Ui.TEXT)
    private val dot = View(a)
    private val clock = Ui.text(a, "", 21f, Ui.TEXT, Ui.REGULAR)
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            clock.text = MainActivity.now()
            handler.postDelayed(this, 10_000)
        }
    }
    private var adminTaps = 0
    private var adminFirst = 0L

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(lid, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        addView(temp, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        link.addView(dot, 0, LayoutParams(Ui.u(9), Ui.u(9)).apply { rightMargin = Ui.u(7) })
        addView(link, Ui.lp(Ui.WRAP, Ui.WRAP))
        if (showClock) addView(clock, Ui.lp(Ui.WRAP, Ui.WRAP, l = 14))
        lid.visibility = GONE
        temp.visibility = GONE
        // Appui long sur l'heure : commandes d'Android (Retour, Accueil, Applis récentes), utiles
        // quand la barre de navigation d'Android est retirée (module Magisk camybrith_sans_barre).
        clock.isLongClickable = true
        clock.setOnLongClickListener { Ui.tapSound(); a.androidCommands(); true }
        // Accès caché aux réglages Android : 5 appuis rapides sur l'heure.
        clock.tap {
            val now = System.currentTimeMillis()
            if (now - adminFirst > 3_000) { adminFirst = now; adminTaps = 0 }
            if (++adminTaps >= 5) {
                adminTaps = 0
                a.openAndroidSettings()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        handler.removeCallbacks(tick)
        tick.run()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        super.onDetachedFromWindow()
    }

    fun update(s: Snapshot) {
        lid.visibility = if (s.hasSample && s.lidOpen) VISIBLE else GONE
        if (s.hasSample) {
            temp.visibility = VISIBLE
            Ui.chipText(temp).text = MainActivity.bowlTemperature(a, s)
            val col = if (s.actualTempC >= 40) Ui.tempColor(s.actualTempC) else Ui.TEXT2
            Ui.chipText(temp).setTextColor(col)
            Ui.chipIcon(temp)?.color = col
        } else temp.visibility = GONE
        val demo = a.prefs.mode == Prefs.Mode.DEMO
        val (label, color) = when {
            s.hasFault -> tr("Alerte") to Ui.DANGER
            s.link == LinkState.READY -> (if (demo) tr("Démo") else tr("Prête")) to (if (demo) Ui.COOL else Ui.OK)
            s.link == LinkState.ARMING -> tr("Connexion…") to Ui.WARN
            s.link == LinkState.LOST -> tr("Liaison perdue") to Ui.DANGER
            else -> tr("Base absente") to Ui.TEXT3
        }
        Ui.chipText(link).text = label
        dot.background = Ui.round(color, 5f)
    }
}

/** Barre du haut : retour, titre, état du robot, heure. */
class TopBar(private val a: MainActivity) : LinearLayout(a) {
    private val back = FrameLayout(a)
    private val titleView = Ui.ellipsize(Ui.title(a, "", 29f))
    private val subtitleView = Ui.label(a, "", Ui.TEXT2, 12.5f)
    val status = StatusPills(a)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(Ui.u(24), 0, Ui.u(22), 0)
        back.background = Ui.pressable(Ui.round(Ui.SURFACE2, 21f), 21f)
        back.addView(Icon(a, Icon.Kind.BACK, Ui.TEXT).apply { strokeWidth = 2.2f }, Ui.flp(Ui.u(22), Ui.u(22), Gravity.CENTER))
        back.isClickable = true
        back.contentDescription = tr("Retour")
        back.tap { a.back() }
        addView(back, LayoutParams(Ui.u(42), Ui.u(42)).apply { rightMargin = Ui.u(12) })
        val col = Ui.vertical(a)
        col.addView(titleView)
        col.addView(subtitleView, Ui.lp(t = 1))
        addView(col, LayoutParams(0, Ui.WRAP, 1f))
        addView(status, Ui.lp(Ui.WRAP, Ui.WRAP, l = 12))
    }

    fun setTitle(t: String, subtitle: String, canGoBack: Boolean) {
        titleView.text = t
        subtitleView.text = subtitle.uppercase()
        subtitleView.visibility = if (subtitle.isEmpty()) GONE else VISIBLE
        back.visibility = if (canGoBack) VISIBLE else GONE
    }

    fun update(s: Snapshot) = status.update(s)
}

/** Fine barre de progression arrondie. */
class ProgressLine(c: Context) : View(c) {
    var progress = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var color = Ui.ACCENT
    var track = Ui.SURFACE3
    var rounded = true
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val rad = if (rounded) h / 2 else 0f
        if (track != 0) {
            paint.color = track
            r.set(0f, 0f, width.toFloat(), h)
            canvas.drawRoundRect(r, rad, rad, paint)
        }
        paint.color = color
        r.set(0f, 0f, maxOf(h, width * progress), h)
        if (progress > 0f) canvas.drawRoundRect(r, rad, rad, paint)
    }
}

/**
 * Barre de cuisson : visible sur tous les écrans dès qu'un programme tourne, est en pause, vient
 * de finir, ou qu'une alerte est active.
 */
class MachineDock(private val a: MainActivity) : FrameLayout(a) {
    private val jobRow = Ui.horizontal(a)
    private val faultRow = Ui.horizontal(a)
    private val label = Ui.ellipsize(Ui.text(a, "", 17f, Ui.DOCK_TEXT, Ui.MEDIUM))
    private val state = Ui.ellipsize(Ui.text(a, "", 14f, Ui.DOCK_TEXT2))
    private val time = Ui.text(a, "", 44f, Ui.DOCK_TEXT, Ui.LIGHT)
    private val progress = ProgressLine(a).apply { track = 0; rounded = false }
    private val chips = Ui.horizontal(a, Gravity.CENTER_VERTICAL or Gravity.END)
    private val speedChip = Ui.chip(a, "", Icon.Kind.SPEED, Ui.DOCK_TEXT, Ui.DOCK_SURFACE)
    private val tempChip = Ui.chip(a, "", Icon.Kind.THERMO, Ui.DOCK_TEXT, Ui.DOCK_SURFACE)
    private val dirChip = Ui.chip(a, tr("Inverse"), Icon.Kind.REVERSE, Ui.DOCK_TEXT, Ui.DOCK_SURFACE)
    private val pauseBtn: LinearLayout
    private val stopBtn: LinearLayout
    private val okBtn: LinearLayout
    private val faultText = Ui.text(a, "", 18f, Ui.ON_DANGER, Ui.MEDIUM)
    private var last: Snapshot? = null

    init {
        background = Ui.round(Ui.DOCK, 26f)
        clipToOutline = true
        elevation = Ui.uf(10f)
        // Programme
        jobRow.setPadding(Ui.u(22), 0, Ui.u(12), 0)
        val left = Ui.vertical(a)
        left.addView(label)
        left.addView(state, Ui.lp(t = 3))
        jobRow.addView(left, LinearLayout.LayoutParams(Ui.u(190), Ui.WRAP))
        jobRow.addView(time, Ui.lp(Ui.WRAP, Ui.WRAP, l = 12))
        chips.addView(speedChip, Ui.lp(Ui.WRAP, Ui.WRAP, l = 7))
        chips.addView(tempChip, Ui.lp(Ui.WRAP, Ui.WRAP, l = 7))
        chips.addView(dirChip, Ui.lp(Ui.WRAP, Ui.WRAP, l = 7))
        jobRow.addView(chips, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(14) })
        pauseBtn = Ui.button(a, "", Ui.Style.DOCK, Icon.Kind.PAUSE, height = 56, textSize = 21f) { onPause() }
        stopBtn = Ui.button(a, "", Ui.Style.DANGER, Icon.Kind.STOP, height = 56, textSize = 21f) { a.machine.stop() }
        okBtn = Ui.button(a, "OK", Ui.Style.DOCK, Icon.Kind.CHECK, height = 56) { a.machine.clearFinished() }
        jobRow.addView(pauseBtn, Ui.lp(Ui.WRAP, Ui.WRAP))
        jobRow.addView(stopBtn, Ui.lp(Ui.WRAP, Ui.WRAP, l = 10))
        jobRow.addView(okBtn, Ui.lp(Ui.WRAP, Ui.WRAP, l = 10))
        addView(jobRow, Ui.flp(Ui.MATCH, Ui.MATCH))
        addView(progress, Ui.flp(Ui.MATCH, Ui.u(4), Gravity.TOP))
        // Alerte
        faultRow.setPadding(Ui.u(22), 0, Ui.u(12), 0)
        faultRow.setBackgroundColor(Ui.DANGER)
        faultRow.addView(Icon(a, Icon.Kind.WARNING, Ui.ON_DANGER), LinearLayout.LayoutParams(Ui.u(30), Ui.u(30)).apply { rightMargin = Ui.u(14) })
        faultRow.addView(faultText, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val ack = Ui.button(a, tr("Acquitter"), Ui.Style.DOCK, Icon.Kind.CHECK, height = 52) {
            a.machine.acknowledge { v -> a.refusal(v) }
        }
        faultRow.addView(ack)
        addView(faultRow, Ui.flp(Ui.MATCH, Ui.MATCH))
        visibility = View.GONE
    }

    private fun onPause() {
        val s = last ?: return
        if (s.state == JobState.PAUSED) {
            a.machine.resume(s.hotBlendConfirmed) { v ->
                if (!v.ok && v.has(app.toque.core.machine.SafetyPolicy.HOT_BLEND)) {
                    a.confirm(tr("Contenu chaud"), tr("Le bol est à %d °C. Reprendre le mixage ?", s.actualTempC), tr("Reprendre"), icon = Icon.Kind.THERMO) {
                        a.machine.resume(true) { a.refusal(it) }
                    }
                } else a.refusal(v)
            }
        } else a.machine.pause { a.refusal(it) }
    }

    fun update(s: Snapshot) {
        last = s
        val st = s.state
        val showJob = s.jobActive || s.turboActive
        visibility = if (showJob || s.hasFault) View.VISIBLE else View.GONE
        faultRow.visibility = if (s.hasFault) View.VISIBLE else View.GONE
        jobRow.visibility = if (!s.hasFault && showJob) View.VISIBLE else View.GONE
        progress.visibility = jobRow.visibility
        if (s.hasFault) {
            faultText.text = s.faultMessage
            return
        }
        if (!showJob) return
        if (s.turboActive) {
            label.text = "Turbo"
            state.text = tr("Relâchez pour arrêter")
            time.text = "●"
            progress.progress = 0f
            setButtons(running = false, ended = false, turbo = true)
            return
        }
        label.text = s.jobLabel
        state.text = when (st) {
            JobState.STARTING -> if (s.elapsedMs == 0L) tr("Démarrage…") else tr("Reprise…")
            JobState.RUNNING -> if (s.waitingStop) tr("Changement de sens…") else if (s.phaseCount > 1) tr("En cours · phase %d/%d", s.phaseIndex + 1, s.phaseCount) else tr("En cours")
            JobState.STOPPING -> tr("Arrêt du moteur…")
            JobState.PAUSED -> when (s.pauseReason) {
                "LID" -> tr("Pause : couvercle ouvert")
                "SAFETY" -> tr("Interrompu par sécurité")
                else -> tr("En pause")
            }
            JobState.FINISHED -> tr("Terminé")
            JobState.ABORTED -> s.endReason
            null -> ""
        }
        state.setTextColor(when (st) {
            JobState.PAUSED -> DOCK_WARN
            JobState.FINISHED -> DOCK_OK
            JobState.ABORTED -> DOCK_DANGER
            else -> Ui.DOCK_TEXT2
        })
        time.text = if (st == JobState.FINISHED) "00:00" else Ui.clock(s.remainingMs)
        progress.progress = if (st == JobState.FINISHED) 1f else if (s.totalMs > 0) s.elapsedMs.toFloat() / s.totalMs else 0f
        progress.color = if (s.jobTempLevel > 0) DOCK_ACCENT else DOCK_COOL
        Ui.chipText(speedChip).text = if (s.jobKind == "STEAM") tr("Vapeur") else tr("Vitesse %d", s.jobSpeed)
        Ui.chipIcon(speedChip)?.color = DOCK_COOL
        tempChip.visibility = if (s.jobTempLevel > 0) View.VISIBLE else View.GONE
        Ui.chipText(tempChip).text = "${TemperatureTable.current.celsius(s.jobTempLevel)} °C"
        Ui.chipIcon(tempChip)?.color = DOCK_ACCENT
        dirChip.visibility = if (s.jobReverse && s.jobKind == "MANUAL") View.VISIBLE else View.GONE
        Ui.chipIcon(dirChip)?.color = DOCK_COOL
        val ended = st == JobState.FINISHED || st == JobState.ABORTED
        setButtons(running = st != JobState.PAUSED, ended = ended, turbo = false)
    }

    private fun setButtons(running: Boolean, ended: Boolean, turbo: Boolean) {
        pauseBtn.visibility = if (ended || turbo) View.GONE else View.VISIBLE
        stopBtn.visibility = if (ended) View.GONE else View.VISIBLE
        okBtn.visibility = if (ended) View.VISIBLE else View.GONE
        Ui.buttonIcon(pauseBtn)?.set(if (running) Icon.Kind.PAUSE else Icon.Kind.PLAY)
        chips.visibility = if (turbo) View.INVISIBLE else View.VISIBLE
    }

    companion object {
        // Couleurs vives lisibles sur le fond foncé de la barre, dans les deux thèmes.
        const val DOCK_OK = 0xFF6FCF97.toInt()
        const val DOCK_WARN = 0xFFE9B860.toInt()
        const val DOCK_DANGER = 0xFFF06A5E.toInt()
        const val DOCK_ACCENT = 0xFFE0915F.toInt()
        const val DOCK_COOL = 0xFF7CC4B4.toInt()
    }
}

/** Modèle de robot (pour l'affichage). */
fun MainActivity.isSmart(): Boolean = try { prefs.robotModel == RobotModel.SMART } catch (_: Exception) { false }

/** Cocotte de la marque Camybrith Cooking (même dessin que l'icône et le logo de démarrage). */
class BrandMark(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        // Dessin d'origine sur une grille 200 × 200 (tools/smart/marque/marque.py), recadré sur la cocotte.
        val s = minOf(width / 160f, height / 112f)
        val ox = (width - 160f * s) / 2 - 20f * s
        val oy = (height - 112f * s) / 2 - 56f * s
        fun x(v: Float) = ox + v * s
        fun y(v: Float) = oy + v * s
        p.style = Paint.Style.FILL
        p.color = ORANGE
        r.set(x(20f), y(104f), x(42f), y(120f)); canvas.drawRoundRect(r, 7 * s, 7 * s, p)
        r.set(x(158f), y(104f), x(180f), y(120f)); canvas.drawRoundRect(r, 7 * s, 7 * s, p)
        r.set(x(38f), y(96f), x(162f), y(168f)); canvas.drawRoundRect(r, 26 * s, 26 * s, p)
        canvas.drawRect(x(38f), y(96f), x(162f), y(120f), p)
        r.set(x(28f), y(82f), x(172f), y(98f)); canvas.drawRoundRect(r, 8 * s, 8 * s, p)
        r.set(x(86f), y(68f), x(114f), y(84f)); canvas.drawRoundRect(r, 7 * s, 7 * s, p)
        p.color = SHADE
        canvas.drawRect(x(38f), y(98f), x(162f), y(104f), p)
    }

    companion object {
        const val ORANGE = 0xFFFF8A3D.toInt()
        const val SHADE = 0xFFA65A2A.toInt()
    }
}
