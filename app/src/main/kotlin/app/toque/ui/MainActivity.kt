package app.toque.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.toque.Prefs
import app.toque.Sounds
import app.toque.core.machine.RootShell
import app.toque.recipes.FileImport
import app.toque.ToqueApp
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.EndCue
import app.toque.core.machine.EventType
import app.toque.core.machine.JobSpec
import app.toque.core.machine.MachineEvent
import app.toque.core.machine.PauseReason
import app.toque.core.machine.SafetyPolicy
import app.toque.core.machine.Snapshot
import app.toque.core.machine.Verdict
import app.toque.core.ring.RingColour
import app.toque.recipes.LidlSync
import app.toque.recipes.SyncService
import app.toque.update.Updater
import app.toque.ui.screens.HomeScreen
import app.toque.ui.screens.ManualScreen
import app.toque.ui.screens.ProgramsScreen
import app.toque.ui.screens.RecipesScreen
import app.toque.ui.screens.ScaleScreen
import app.toque.ui.screens.SettingsScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écran d'accueil du robot (lanceur) : une seule activité, des écrans empilés. */
class MainActivity : Activity() {
    lateinit var app: ToqueApp
        private set
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var overlay: FrameLayout
    private lateinit var topBar: TopBar
    private lateinit var rail: NavRail
    private lateinit var machineBar: MachineDock
    private val stack = ArrayList<Screen>()
    /** Écrans de base, un par partie de l'appli, gardés pour retrouver leur état. */
    private val roots = HashMap<Section, Screen>()
    private val main = Handler(Looper.getMainLooper())

    // ------------------------------------------------------------------ veille de l'écran
    private lateinit var sleepView: View
    private var lastActivityMs = System.currentTimeMillis()
    private var sleeping = false
    /** Le geste qui réveille l'écran ne doit pas aussi appuyer sur un bouton. */
    private var swallowGesture = false
    private var lastWeightG = Int.MIN_VALUE
    private var lastLidOpen: Boolean? = null
    /** Son « compte » : il part quand il reste sa durée, pour finir pile avec le programme. */
    private val endCue = EndCue()
    /** Le son « compte » est parti pendant le programme en cours. */
    private var cuePlayed = false
    private val sleepCheck = object : Runnable {
        override fun run() {
            val min = prefs.screenSleepMin
            val s = snapshot
            val keepAwake = s.busy || s.faultCode.isNotEmpty() || s.turboActive
            if (keepAwake) lastActivityMs = System.currentTimeMillis()
            if (!sleeping && min > 0 && System.currentTimeMillis() - lastActivityMs >= min * 60_000L) sleepScreen()
            main.postDelayed(this, 10_000)
        }
    }

    private fun noteActivity() {
        lastActivityMs = System.currentTimeMillis()
    }

    private fun sleepScreen() {
        if (sleeping) return
        sleeping = true
        app.log.write("veille", "écran éteint après ${prefs.screenSleepMin} min")
        sleepView.visibility = View.VISIBLE
        sleepView.bringToFront()
        window.attributes = window.attributes.apply { screenBrightness = 0f }
        when (prefs.knobStandby) {
            "veilleuse" -> KnobLight.start(KnobLight.NIGHT_LEVEL)
            "eteint" -> KnobLight.start(0)
        }
    }

    /** Dernière raison qui a empêché ou interrompu la veille, notée au journal une fois par minute. */
    private var lastWakeLog = 0L

    fun wakeScreen(why: String = "toucher") {
        noteActivity()
        val now = System.currentTimeMillis()
        if (sleeping || now - lastWakeLog > 60_000) {
            lastWakeLog = now
            app.log.write("veille", if (sleeping) "réveil : $why" else "activité : $why")
        }
        if (!sleeping) return
        sleeping = false
        sleepView.visibility = View.GONE
        KnobLight.stop()
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    /**
     * Code couleur de l'anneau de la molette : rouge (problème), orange (pause), vert (à vous :
     * programme terminé, ou recette guidée affichée qui attend un geste), cyan (en cours). Coupé
     * dans les réglages : l'anneau reprend son état d'origine.
     */
    fun refreshKnob(s: Snapshot = snapshot) {
        if (!prefs.knobColours) { KnobLight.show(null); return }
        KnobLight.show(RingColour.of(s, current?.cookWaiting == true))
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (sleeping && ev.actionMasked == MotionEvent.ACTION_DOWN) {
            wakeScreen()
            swallowGesture = true
        }
        if (swallowGesture) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swallowGesture = false
            return true
        }
        noteActivity()
        return super.dispatchTouchEvent(ev)
    }
    private var unsubscribe: (() -> Unit)? = null
    private var unsubscribeEvents: (() -> Unit)? = null
    lateinit var sounds: Sounds
        private set
    private var toastView: View? = null
    private var lastKnobMs = 0L
    var knobListener: ((KeyEvent) -> Unit)? = null

    val prefs: Prefs get() = app.prefs
    val machine get() = app.machine
    val snapshot: Snapshot get() = app.machine.snapshot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = ToqueApp.of(this)
        val dm = resources.displayMetrics
        Ui.init(dm.widthPixels, dm.heightPixels)
        Ui.loadFonts(this)
        Ui.apply(Ui.resolve(Ui.Theme.of(prefs.theme)))
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Ui.BG))

        root = FrameLayout(this)
        root.setBackgroundColor(Ui.BG)
        val row = Ui.horizontal(this, Gravity.TOP)
        rail = NavRail(this)
        row.addView(rail, LinearLayout.LayoutParams(Ui.u(90), Ui.MATCH))
        val column = Ui.vertical(this)
        topBar = TopBar(this)
        column.addView(topBar, Ui.lp(Ui.MATCH, Ui.u(74)))
        content = FrameLayout(this)
        column.addView(content, Ui.lp(Ui.MATCH, 0, 1f))
        machineBar = MachineDock(this)
        column.addView(machineBar, Ui.lp(Ui.MATCH, Ui.u(82), l = 16, t = 6, r = 16, b = 14))
        row.addView(column, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))
        root.addView(row, Ui.flp())
        overlay = FrameLayout(this)
        overlay.visibility = View.GONE
        root.addView(overlay, Ui.flp())
        sleepView = View(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            visibility = View.GONE
        }
        root.addView(sleepView, Ui.flp())
        setContentView(root)

        // Recettes déposées dans le dossier d'import (Thermomix…) : importées à l'ouverture, une fois
        // l'écran et le catalogue prêts (l'ouverture passe d'abord).
        main.postDelayed({
            Thread {
                val imp = FileImport(this, app.recipes, app.log)
                if (imp.pending().isNotEmpty()) {
                    val r = imp.run()
                    main.post { toast(r.message, if (r.bad == 0) Ui.OK else Ui.WARN) }
                }
            }.apply { priority = Thread.MIN_PRIORITY }.start()
        }, 4_000)

        KnobLight.log = { m -> app.log.write("molette", m) }
        sounds = Sounds(this, app.log)
        sounds.load()
        Ui.tapSound = { if (prefs.sound && prefs.keySound) sounds.play(Sounds.Kind.TAP) }
        val reopen = reopenSection
        reopenSection = null
        // Animation d'ouverture au lancement de l'appli (pas après un changement de langue ou de thème).
        if (savedInstanceState == null && reopen == null) {
            lateinit var splash: SplashView
            splash = SplashView(this) { main.post { root.removeView(splash) } }
            root.addView(splash, Ui.flp())
            if (prefs.sound && sounds.startupDue(prefs)) {
                // L'animation attend que le son parte (au démarrage du robot, le préparer peut prendre
                // un moment), au plus 1,5 s : les deux commencent ensemble.
                var started = false
                val go = Runnable {
                    if (!started) {
                        started = true
                        splash.start()
                    }
                }
                sounds.play(Sounds.Kind.STARTUP) { main.post(go) }
                main.postDelayed(go, 1500)
            } else splash.start()
        }

        app.machine.connect()
        // Le pilote moteur est une appli à part : prévenir s'il manque ou n'est pas de la même version.
        main.postDelayed({
            when {
                app.machine.pilotMissing -> toast(tr("Pilote moteur absent : installez Camybrith-pilote.apk avec l'appli"), Ui.DANGER)
                app.machine.pilotMismatch -> toast(tr("Pilote moteur d'une autre version : réinstallez les deux applis ensemble"), Ui.WARN)
            }
        }, 4_000)
        app.recipes.load()
        // Temps jusqu'au catalogue prêt, pour comparer les versions rapide et prudente.
        val t0 = android.os.SystemClock.uptimeMillis()
        var unsub: (() -> Unit)? = null
        unsub = app.recipes.onChange {
            if (app.recipes.loaded) {
                app.log.write("interface", "catalogue prêt en ${android.os.SystemClock.uptimeMillis() - t0} ms (${if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_VM_SAFE_MODE != 0) "prudente" else "rapide"})")
                unsub?.invoke()
            }
        }
        // Sentinelle du compilateur : au moindre écart, on prévient (voir CompilerWatch).
        app.compilerListener = { what -> main.post { toast(tr("Anomalie du compilateur détectée : installez la version prudente (%s)", what.take(60)), Ui.DANGER) } }
        if (app.compilerProblem.isNotEmpty()) app.compilerListener?.invoke(app.compilerProblem)
        // Programmation de la synchro : après l'ouverture, rien ne presse.
        main.postDelayed({ SyncService.schedule(this) }, 6_000)

        // Nouvelle version sur GitHub ? Vérifiée au plus toutes les 6 heures, jamais pendant un
        // programme ; on le signale seulement, l'installation se lance depuis Réglages › Mises à jour.
        main.postDelayed({
            if (snapshot.busy || System.currentTimeMillis() - prefs.lastUpdateCheck < 6 * 3_600_000L) return@postDelayed
            prefs.lastUpdateCheck = System.currentTimeMillis()
            Thread {
                try {
                    val r = Updater(this).check(prefs.betaUpdates)
                    if (r != null) main.post {
                        val name = if (r.beta) tr("%s bêta (%s)", r.version, r.commit.take(7)) else r.version
                        toast(tr("Nouvelle version disponible : %s", name) + " · " + tr("Réglages") + " › " + tr("Mises à jour"), Ui.OK)
                    }
                } catch (_: Exception) {
                }
            }.apply { priority = Thread.MIN_PRIORITY }.start()
        }, 20_000)
        open(reopen ?: Section.HOME)
    }

    /** Applique la langue choisie dans les réglages : l'interface est reconstruite, on revient aux réglages. */
    fun applyLanguage() {
        I18n.select(I18n.resolve(prefs.language, Locale.getDefault().language).code)
        reopenSection = Section.SETTINGS
        recreate()
    }

    /** Applique le thème choisi : l'interface est reconstruite, on revient aux réglages. */
    fun applyTheme() {
        reopenSection = Section.SETTINGS
        recreate()
    }

    /** Thème automatique : bascule clair / sombre à l'heure dite, seulement sur un écran de base et au repos. */
    private val themeCheck = object : Runnable {
        override fun run() {
            main.postDelayed(this, 30_000)
            if (Ui.Theme.of(prefs.theme) != Ui.Theme.AUTO) return
            if (Ui.resolve(Ui.Theme.AUTO) == Ui.theme) return
            if (stack.size != 1 || snapshot.busy || overlay.visibility == View.VISIBLE) return
            reopenSection = stack.first().section
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        unsubscribe = app.machine.onSnapshot { s -> dispatchSnapshot(s) }
        unsubscribeEvents = app.machine.onEvent { e -> onMachineEvent(e) }
        main.removeCallbacks(themeCheck)
        main.postDelayed(themeCheck, 30_000)
        noteActivity()
        main.removeCallbacks(sleepCheck)
        main.postDelayed(sleepCheck, 10_000)
        refreshKnob()
    }

    private fun dispatchSnapshot(s: Snapshot) {
        // Quelqu'un pèse, ouvre le couvercle, ou une cuisson tourne : ce n'est pas l'inactivité.
        // La balance bouge de quelques grammes au repos : seul un vrai ajout ou retrait compte.
        val weighing = lastWeightG != Int.MIN_VALUE && kotlin.math.abs(s.weightG - lastWeightG) >= WAKE_WEIGHT_G
        val lid = lastLidOpen != null && lastLidOpen != s.lidOpen
        lastWeightG = s.weightG
        lastLidOpen = s.lidOpen
        when {
            s.busy -> wakeScreen("cuisson en cours")
            s.faultCode.isNotEmpty() -> wakeScreen("alerte ${s.faultCode}")
            lid -> wakeScreen("couvercle")
            weighing -> wakeScreen("balance")
        }
        refreshKnob(s)
        topBar.update(s)
        machineBar.update(s)
        stack.lastOrNull()?.onSnapshot(s)
        countdown(s)
    }

    private fun countdown(s: Snapshot) {
        val length = if (prefs.sound) sounds.lengthMs(Sounds.Kind.COUNTDOWN) else null
        when (val act = endCue.update(s, length)) {
            is EndCue.Action.Start -> {
                cuePlayed = true
                app.log.write("sons", "compte : départ, reste ${s.remainingMs} ms sur ${s.totalMs} ms")
                sounds.playFrom(Sounds.Kind.COUNTDOWN, act.offsetMs)
            }
            EndCue.Action.Stop -> {
                app.log.write("sons", "compte : coupé (${s.jobState.ifEmpty { "plus de programme" }}, reste ${s.remainingMs} ms)")
                sounds.stop(Sounds.Kind.COUNTDOWN)
            }
            EndCue.Action.None -> Unit
        }
    }

    override fun onResume() {
        super.onResume()
        immersive()
        prefs.returnBubble = false
        ReturnBubble.stop(this)
        if (prefs.vendorSession) endVendorSession()
    }

    override fun onStop() {
        unsubscribe?.invoke()
        unsubscribeEvents?.invoke()
        main.removeCallbacks(themeCheck)
        main.removeCallbacks(sleepCheck)
        wakeScreen("appli en arrière-plan")
        KnobLight.release()
        super.onStop()
    }

    override fun onDestroy() {
        Ui.tapSound = {}
        sounds.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    // ------------------------------------------------------------------ navigation

    val current: Screen? get() = stack.lastOrNull()
    val depth: Int get() = stack.size

    /** Ouvre une partie de l'appli depuis la barre de navigation (la pile repart de son écran de base). */
    fun open(section: Section) {
        val top = stack.lastOrNull()
        if (top != null && stack.size == 1 && top.section == section) return
        if (top != null && !top.canLeave()) return
        hideKeyboard()
        closeOverlay()
        top?.onHide()
        stack.clear()
        val root = roots.getOrPut(section) {
            when (section) {
                Section.HOME -> HomeScreen(this)
                Section.RECIPES -> RecipesScreen(this)
                Section.THERMOMIX -> RecipesScreen(this, thermomix = true)
                Section.MANUAL -> ManualScreen(this)
                Section.PROGRAMS -> ProgramsScreen(this)
                Section.SCALE -> ScaleScreen(this)
                Section.SETTINGS -> SettingsScreen(this)
            }
        }
        stack += root
        show(root)
    }

    /** Écran de base d'une partie (créé au besoin). */
    fun root(section: Section): Screen? = roots[section]

    fun push(s: Screen) {
        hideKeyboard()
        stack.lastOrNull()?.onHide()
        stack += s
        show(s)
    }

    /** Remplace l'écran courant (pas de retour vers lui). */
    fun replace(s: Screen) {
        hideKeyboard()
        stack.removeLastOrNull()?.onHide()
        stack += s
        show(s)
    }

    fun back() {
        if (stack.size <= 1) return
        val top = stack.last()
        if (!top.canLeave()) return
        hideKeyboard()
        top.onHide()
        stack.removeAt(stack.size - 1)
        show(stack.last())
    }

    fun home() = open(Section.HOME)

    private fun show(s: Screen) {
        content.removeAllViews()
        val v = s.view
        (v.parent as? FrameLayout)?.removeView(v)
        content.addView(v, Ui.flp())
        topBar.visibility = if (s.showTopBar) View.VISIBLE else View.GONE
        topBar.setTitle(s.title, s.subtitle, stack.size > 1)
        rail.select(stack.firstOrNull()?.section)
        s.onShow()
        s.onSnapshot(snapshot)
    }

    fun refreshTitle() {
        current?.let { topBar.setTitle(it.title, it.subtitle, stack.size > 1) }
    }

    override fun onBackPressed() {
        if (overlay.visibility == View.VISIBLE) {
            closeOverlay()
            return
        }
        back()
    }

    // ------------------------------------------------------------------ bouton rotatif

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (sleeping) {
            if (event.action == KeyEvent.ACTION_DOWN) wakeScreen("molette")
            return true
        }
        noteActivity()
        knobListener?.let { l ->
            if (event.action == KeyEvent.ACTION_DOWN) l(event)
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && overlay.visibility != View.VISIBLE) {
            val k = event.keyCode
            val left = prefs.knobLeft.let { if (it >= 0) it == k else k == KeyEvent.KEYCODE_DPAD_LEFT }
            val right = prefs.knobRight.let { if (it >= 0) it == k else k == KeyEvent.KEYCODE_DPAD_RIGHT }
            val press = prefs.knobPress.let {
                if (it >= 0) it == k else k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
            }
            val s = current
            if (s != null && currentFocus !is android.widget.EditText) {
                if (left && s.onKnob(-1)) return true
                if (right && s.onKnob(1)) return true
                if (press && s.onKnobPress()) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (sleeping) {
            if (event.action == MotionEvent.ACTION_SCROLL) wakeScreen("molette")
            return true
        }
        noteActivity()
        if (event.action == MotionEvent.ACTION_SCROLL) {
            // Encodeur rotatif (AXIS_SCROLL) ou molette (AXIS_VSCROLL)
            val v = event.getAxisValue(AXIS_SCROLL).let { if (it == 0f) -event.getAxisValue(MotionEvent.AXIS_VSCROLL) else it }
            val now = System.currentTimeMillis()
            if (v != 0f && now - lastKnobMs > 40) {
                lastKnobMs = now
                if (current?.onKnob(if (v > 0) 1 else -1) == true) return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // ------------------------------------------------------------------ évènements machine

    private fun onMachineEvent(e: MachineEvent) {
        // Seuls les évènements qui demandent un regard rallument l'écran ; l'état de la liaison
        // (qui peut changer tout seul, robot au repos) ne compte pas comme une activité.
        if (e.type !in QUIET_EVENTS) wakeScreen(e.type.name)
        current?.onEvent(e)
        when (e.type) {
            EventType.JOB_STARTED -> { cuePlayed = false; sound(Sounds.Kind.START) }
            EventType.JOB_FINISHED -> {
                sound(Sounds.Kind.END)
                val length = sounds.lengthMs(Sounds.Kind.COUNTDOWN)
                if (!cuePlayed && Sounds.Kind.COUNTDOWN in sounds.custom)
                    app.log.write("sons", "compte : pas joué (son ${length ?: "?"} ms, programme ${snapshot.totalMs} ms, sons ${if (prefs.sound) "activés" else "coupés"})")
                cuePlayed = false
            }
            EventType.FAULT -> { sound(Sounds.Kind.ALERT); toast(e.message, Ui.DANGER) }
            EventType.LID_OPENED -> if (snapshot.busy || snapshot.state != null) toast(tr("Couvercle ouvert : pause"), Ui.WARN)
            EventType.JOB_PAUSED -> if (e.message != PauseReason.USER.label) { sound(Sounds.Kind.PAUSE); toast(e.message, Ui.WARN) }
            EventType.TURBO_END -> toast(e.message, Ui.TEXT2)
            else -> Unit
        }
    }

    /** Son court de la pesée : cible atteinte, ou dépassée. */
    fun beep(ok: Boolean) = sound(if (ok) Sounds.Kind.WEIGH_OK else Sounds.Kind.WEIGH_OVER)

    fun sound(kind: Sounds.Kind) {
        if (prefs.sound) sounds.play(kind)
    }

    // ------------------------------------------------------------------ dialogues et messages

    fun toast(message: String, color: Int = Ui.TEXT) {
        toastView?.let { root.removeView(it) }
        val t = Ui.horizontal(this, Gravity.CENTER_VERTICAL)
        t.setPadding(Ui.u(20), Ui.u(13), Ui.u(24), Ui.u(13))
        t.background = Ui.round(Ui.DOCK, 30f)
        t.elevation = Ui.uf(12f)
        val dotColor = when (color) {
            Ui.WARN -> MachineDock.DOCK_WARN
            Ui.DANGER -> MachineDock.DOCK_DANGER
            Ui.OK -> MachineDock.DOCK_OK
            else -> 0
        }
        if (dotColor != 0) t.addView(View(this).apply { background = Ui.round(dotColor, 5f) },
            LinearLayout.LayoutParams(Ui.u(10), Ui.u(10)).apply { rightMargin = Ui.u(12) })
        t.addView(Ui.text(this, message, 17f, Ui.DOCK_TEXT, Ui.MEDIUM).apply { maxWidth = Ui.u(700) })
        val lp = Ui.flp(Ui.WRAP, Ui.WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = Ui.u(if (machineBar.visibility == View.VISIBLE) 112 else 28)
        lp.leftMargin = Ui.u(90)
        root.addView(t, lp)
        toastView = t
        t.alpha = 0f
        t.animate().alpha(1f).setDuration(150).start()
        main.postDelayed({
            if (toastView === t) {
                t.animate().alpha(0f).setDuration(200).withEndAction { root.removeView(t); if (toastView === t) toastView = null }.start()
            }
        }, 3_200)
    }

    fun refusal(v: Verdict) {
        if (!v.ok) toast(v.message, Ui.WARN)
    }

    /** Affiche une carte modale au centre de l'écran. */
    fun showOverlay(card: View, width: Int = 600, dismissOnScrim: Boolean = true) {
        hideKeyboard()
        overlay.removeAllViews()
        overlay.setBackgroundColor(Ui.SCRIM)
        overlay.isClickable = true
        overlay.setOnClickListener { if (dismissOnScrim) closeOverlay() }
        card.isClickable = true
        card.elevation = Ui.uf(16f)
        overlay.addView(card, Ui.flp(Ui.u(width), Ui.WRAP, Gravity.CENTER))
        overlay.visibility = View.VISIBLE
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(120).start()
    }

    fun closeOverlay() {
        overlay.visibility = View.GONE
        overlay.removeAllViews()
    }

    val overlayShown: Boolean get() = overlay.visibility == View.VISIBLE

    /** Dialogue de confirmation. */
    fun confirm(title: String, message: String, yes: String, no: String = tr("Annuler"),
                danger: Boolean = false, icon: Icon.Kind = Icon.Kind.WARNING, onYes: () -> Unit) {
        val card = Ui.vertical(this)
        card.background = Ui.round(Ui.SURFACE, 26f)
        card.setPadding(Ui.u(28), Ui.u(24), Ui.u(28), Ui.u(22))
        val head = Ui.horizontal(this)
        head.addView(Ui.badge(this, icon, if (danger) Ui.DANGER else Ui.ACCENT, if (danger) Ui.DANGER_SOFT else Ui.ACCENT_SOFT, 44),
            LinearLayout.LayoutParams(Ui.u(44), Ui.u(44)).apply { rightMargin = Ui.u(14) })
        head.addView(Ui.title(this, title, 25f), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        card.addView(head)
        card.addView(Ui.text(this, message, 17f, Ui.TEXT2).apply { setLineSpacing(0f, 1.22f) }, Ui.lp(t = 14, b = 22))
        val row = Ui.horizontal(this, Gravity.END or Gravity.CENTER_VERTICAL)
        row.addView(Ui.button(this, no, Ui.Style.GHOST, height = 52) { closeOverlay() })
        row.addView(Ui.spacer(this, w = 12))
        row.addView(Ui.button(this, yes, if (danger) Ui.Style.DANGER else Ui.Style.PRIMARY, height = 52) {
            closeOverlay()
            onYes()
        })
        card.addView(row)
        showOverlay(card, 620)
    }

    /**
     * Démarre un programme ; si le contenu est chaud, demande d'abord la confirmation
     * de mixage (comme l'appli d'origine, mais avant tout mouvement).
     */
    fun startJob(spec: JobSpec, onHotConfirmed: () -> Unit = {}, done: (Verdict) -> Unit = {}) {
        machine.start(spec) { v ->
            if (!v.ok && v.has(SafetyPolicy.HOT_BLEND) && v.violations.size == 1) {
                confirm(
                    tr("Contenu chaud"),
                    tr("Le bol est à %d °C. Mixer chaud peut provoquer des projections.\n\n" +
                        "Vérifiez que le couvercle est verrouillé, que le gobelet est en place et que le bol n'est pas trop rempli.", snapshot.actualTempC) +
                        if (prefs.hotRamp) " " + tr("La vitesse montera progressivement.") else "",
                    tr("Mixer quand même"), icon = Icon.Kind.THERMO,
                ) {
                    onHotConfirmed()
                    machine.start(spec.copy(hotBlendConfirmed = true)) { v2 -> refusal(v2); done(v2) }
                }
            } else {
                refusal(v)
                done(v)
            }
        }
    }

    fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    // ------------------------------------------------------------------ appli d'origine

    /** Retour depuis l'appli d'origine : on la coupe du moteur, on reprend la main, on importe. */
    private fun endVendorSession() {
        prefs.vendorSession = false
        toast(tr("Reprise de la main sur le robot…"))
        Thread {
            val sync = LidlSync(this, prefs, app.recipes, app.log)
            sync.forceStop()
            sync.setVendorHardware(false)
            main.post { machine.resumePort() }
            val r = sync.importNow()
            main.post { toast(r.message, if (r.ok) Ui.OK else Ui.WARN) }
        }.start()
    }

    fun openVendorApp() {
        if (snapshot.busy) {
            toast(tr("Arrêtez d'abord le programme en cours"), Ui.WARN)
            return
        }
        confirm(
            tr("Ouvrir l'appli d'origine"),
            tr("Camybrith Cooking libère le moteur et lance l'appli Monsieur Cuisine d'origine, qui se synchronise avec les serveurs Lidl comme d'habitude.\n\n" +
                "Revenez ensuite à Camybrith Cooking avec le bouton « Retour à Camybrith Cooking » affiché par-dessus (vous pouvez le déplacer) : les recettes seront importées automatiquement."),
            tr("Ouvrir"), icon = Icon.Kind.SYNC,
        ) {
            machine.suspendPort { v ->
                if (!v.ok) { refusal(v); return@suspendPort }
                Thread {
                    // Sans bouton de retour, impossible de revenir à Toque : on n'ouvre pas.
                    if (!ReturnBubble.ensurePermission(this)) {
                        main.post {
                            toast(tr("Impossible d'afficher le bouton « Retour à Camybrith Cooking » (root ?) : ouverture annulée"), Ui.WARN)
                            machine.resumePort()
                        }
                        return@Thread
                    }
                    val sync = LidlSync(this, prefs, app.recipes, app.log)
                    val enabled = sync.setVendorHardware(true)
                    val launch = sync.components().launch
                    main.post {
                        if (!enabled || launch == null) {
                            toast(tr("Appli d'origine indisponible (root ?)"), Ui.WARN)
                            machine.resumePort()
                        } else {
                            prefs.vendorSession = true
                            prefs.returnBubble = true
                            ReturnBubble.start(this)
                            KnobLight.release()
                            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }.start()
            }
        }
    }

    /** Paramètres Android (accès caché), avec le bouton « Retour à Toque » pour en revenir. */
    /** Commandes d'Android, pour se passer de sa barre de navigation (appui long sur l'heure). */
    fun androidCommands() {
        val card = Ui.vertical(this)
        card.background = Ui.round(Ui.BG, 28f)
        card.setPadding(Ui.u(26), Ui.u(20), Ui.u(26), Ui.u(22))
        card.addView(Ui.title(this, tr("Commandes Android"), 25f))
        card.addView(Ui.text(this, tr("Envoyées à Android comme les boutons de sa barre de navigation."), 15f, Ui.TEXT3), Ui.lp(t = 6))
        fun key(code: Int) {
            closeOverlay()
            Thread {
                val r = RootShell.run("input keyevent $code", 5_000)
                if (!r.ok) main.post { toast(tr("Commande refusée (root ?)"), Ui.WARN) }
            }.start()
        }
        val row = Ui.horizontal(this)
        row.addView(Ui.button(this, tr("Retour"), Ui.Style.SECONDARY, Icon.Kind.BACK, height = 60) { key(4) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(10) })
        row.addView(Ui.button(this, tr("Accueil Android"), Ui.Style.SECONDARY, Icon.Kind.HOME, height = 60) { key(3) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(10) })
        row.addView(Ui.button(this, tr("Applis récentes"), Ui.Style.SECONDARY, Icon.Kind.GRID, height = 60) { key(187) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        card.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP, t = 18))
        card.addView(Ui.button(this, tr("Réglages Android"), Ui.Style.GHOST, Icon.Kind.SLIDERS, height = 54) { closeOverlay(); openAndroidSettings() }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 12))
        showOverlay(card, 760)
    }

    fun openAndroidSettings() {
        Thread {
            val ok = ReturnBubble.ensurePermission(this)
            main.post {
                if (!ok) {
                    toast(tr("Impossible d'afficher le bouton « Retour à Camybrith Cooking » (root ?) : ouverture annulée"), Ui.WARN)
                    return@post
                }
                prefs.returnBubble = true
                ReturnBubble.start(this)
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    prefs.returnBubble = false
                    ReturnBubble.stop(this)
                }
            }
        }.start()
    }

    companion object {
        /** Partie à rouvrir après une reconstruction (langue, thème). */
        private var reopenSection: Section? = null

        /** Variation de poids qui compte comme une pesée pour la veille de l'écran. */
        private const val WAKE_WEIGHT_G = 30

        /** Évènements qui ne rallument pas l'écran : rien à regarder. */
        private val QUIET_EVENTS = setOf(EventType.LINK_CHANGED, EventType.FAULT_CLEARED, EventType.PHASE_CHANGED)

        /** Température du bol telle qu'on l'affiche : sur le Smart, la base répond 0 sous 37 °C. */
        fun bowlTemperature(a: MainActivity, s: Snapshot): String =
            if (s.actualTempC < 37 && a.isSmart()) tr("< 37 °C") else "${s.actualTempC} °C"

        /** MotionEvent.AXIS_SCROLL (API 26), valeur fixe pour Android 6. */
        private const val AXIS_SCROLL = 26
        private val clockFormat = SimpleDateFormat("HH:mm", Locale.FRANCE)

        /** Heure affichée ; le fuseau peut changer en cours de route (réglage automatique). */
        fun now(): String {
            clockFormat.timeZone = java.util.TimeZone.getDefault()
            return clockFormat.format(Date())
        }
    }
}
