package app.toque.ui.screens

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import app.toque.Prefs
import app.toque.TimeSync
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.machine.TemperatureTable
import app.toque.core.machine.RootShell
import app.toque.core.machine.Snapshot
import app.toque.core.time.ZoneLookup
import app.toque.core.update.Release
import app.toque.recipes.FileImport
import app.toque.recipes.LidlSync
import app.toque.recipes.OnlineSync
import app.toque.ui.Icon
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import app.toque.ui.Section
import app.toque.ui.Ui
import app.toque.ui.tap
import app.toque.update.Updater
import app.toque.ui.KnobLight

class SettingsScreen(a: MainActivity) : Screen(a) {
    override val title = tr("Réglages")
    override val section = Section.SETTINGS
    private class Page(val title: String, val icon: Icon.Kind, val content: LinearLayout)
    private val pages = ArrayList<Page>()
    private var page = 0
    private lateinit var menu: LinearLayout
    private lateinit var pageBox: FrameLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var twinBox: LinearLayout
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var serialBox: LinearLayout
    private lateinit var serialDeviceField: EditText
    private lateinit var syncText: TextView
    private lateinit var bowlText: TextView
    private lateinit var linkText: TextView
    private lateinit var timeText: TextView
    private var settingTime = false
    private var mode = a.prefs.mode
    private var syncing = false

    private enum class Upd { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, DOWNLOADING, INSTALLING, ERROR }
    private val updater by lazy { Updater(a) }
    private lateinit var updText: TextView
    private lateinit var updButtons: LinearLayout
    private var upd = Upd.IDLE
    private var release: Release? = null
    private var updMessage = ""
    private var updPercent = 0

    override fun build(): View {
        val root = Ui.horizontal(c, Gravity.TOP)
        root.setPadding(Ui.u(24), Ui.u(2), Ui.u(22), Ui.u(16))
        menu = Ui.vertical(c)
        root.addView(menu, LinearLayout.LayoutParams(Ui.u(220), Ui.WRAP).apply { rightMargin = Ui.u(18) })
        pageBox = FrameLayout(c)
        pageBox.background = Ui.round(Ui.SURFACE, 24f)
        Ui.lift(pageBox)
        root.addView(pageBox, LinearLayout.LayoutParams(0, Ui.MATCH, 1f))

        // ---- Affichage
        val display = section(tr("Affichage et sons"), Icon.Kind.SLIDERS)
        display.addView(Ui.label(c, tr("Thème"), Ui.TEXT2, 12f), Ui.lp(t = 2, b = 8))
        val themes = listOf(Ui.Theme.LIGHT, Ui.Theme.DARK, Ui.Theme.AUTO)
        val current = Ui.Theme.of(a.prefs.theme)
        display.addView(Ui.segmented(c, listOf(null to tr("Clair"), null to tr("Sombre"), Icon.Kind.CLOCK to tr("Automatique")),
            themes.indexOf(current), 44) { i ->
            val t = themes[i]
            if (t != Ui.Theme.of(a.prefs.theme)) {
                a.prefs.theme = t.key
                a.applyTheme()
            }
        }, Ui.lp(Ui.WRAP, Ui.WRAP))
        display.addView(Ui.text(c, tr("Clair et chaleureux pour la journée, sombre et raffiné le soir. En automatique, l'appli passe au clair à 7 h et au sombre à 20 h."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 10))
        // Langue (titre bilingue : trouvable quelle que soit la langue affichée)
        display.addView(Ui.label(c, "Langue · Language", Ui.TEXT2, 12f), Ui.lp(t = 22, b = 8))
        val options = listOf(I18n.AUTO to tr("Automatique (langue du système)")) + I18n.languages.map { it.code to it.name }
        display.addView(dropdown(options, a.prefs.language) { code ->
            a.prefs.language = code
            a.applyLanguage()
        }, Ui.lp(Ui.MATCH, Ui.u(54)))
        // Mise en veille de l'écran
        display.addView(Ui.label(c, tr("Mise en veille de l'écran"), Ui.TEXT2, 12f), Ui.lp(t = 24, b = 8))
        val sleepChoices = listOf(0, 2, 5, 10, 30)
        val sleepNow = sleepChoices.indexOf(a.prefs.screenSleepMin).let { if (it < 0) 2 else it }
        display.addView(Ui.segmented(c, sleepChoices.map { m -> null to (if (m == 0) tr("Jamais") else tr("%d min", m)) },
            sleepNow, 44) { i -> a.prefs.screenSleepMin = sleepChoices[i] }, Ui.lp(Ui.WRAP, Ui.WRAP))
        display.addView(Ui.text(c, tr("Sans cuisson en cours, l'écran s'éteint après ce délai. Touchez l'écran ou tournez la molette pour le rallumer."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 10))
        // Anneau de la molette
        display.addView(Ui.label(c, tr("Molette"), Ui.TEXT2, 12f), Ui.lp(t = 24, b = 4))
        display.addView(toggle(tr("Couleurs sur la molette"), a.prefs.knobColours) {
            a.prefs.knobColours = it
            a.refreshKnob()
        }, Ui.lp(t = 6))
        display.addView(Ui.text(c, tr("Rouge : un problème. Orange : en pause. Vert : à vous de jouer. Cyan : en cours."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(t = 8))
        display.addView(Ui.label(c, tr("Molette pendant la veille"), Ui.TEXT2, 12f), Ui.lp(t = 16, b = 8))
        val knobModes = listOf("normal", "veilleuse", "eteint")
        display.addView(Ui.segmented(c, listOf(null to tr("Normale"), null to tr("Veilleuse"), null to tr("Éteinte")),
            knobModes.indexOf(a.prefs.knobStandby).let { if (it < 0) 1 else it }, 44) { i -> a.prefs.knobStandby = knobModes[i] },
            Ui.lp(Ui.WRAP, Ui.WRAP))
        display.addView(Ui.label(c, tr("Sons"), Ui.TEXT2, 12f), Ui.lp(t = 24, b = 4))
        display.addView(toggle(tr("Sons"), a.prefs.sound) { a.prefs.sound = it }, Ui.lp(t = 6))
        display.addView(toggle(tr("Son des touches"), a.prefs.keySound) { a.prefs.keySound = it }, Ui.lp(t = 12))
        display.addView(volumeRow(), Ui.lp(t = 12))
        val soundsText = Ui.text(c, soundsInfo(a.sounds.custom.size), 15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.15f) }
        display.addView(soundsText, Ui.lp(t = 12))
        display.addView(Ui.button(c, tr("Recharger les sons"), height = 48, textSize = 16f) {
            a.sounds.load()
            soundsText.text = soundsInfo(a.sounds.custom.size)
            a.toast(if (a.sounds.custom.isEmpty()) tr("Sons de l'appli") else tr("Sons personnels : %d").format(a.sounds.custom.size), Ui.OK)
        }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 10))

        // ---- Machine
        val machine = section(tr("Machine"), Icon.Kind.POT)
        modeRow = Ui.horizontal(c)
        machine.addView(modeRow, Ui.lp(t = 4))
        twinBox = Ui.horizontal(c)
        host = field(a.prefs.twinHost, tr("Hôte"))
        port = field(a.prefs.twinPort.toString(), "Port").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        twinBox.addView(host, LinearLayout.LayoutParams(0, Ui.u(58), 1f).apply { rightMargin = Ui.u(10) })
        twinBox.addView(port, LinearLayout.LayoutParams(Ui.u(120), Ui.u(58)))
        machine.addView(twinBox, Ui.lp(t = 12))
        serialBox = Ui.horizontal(c)
        serialDeviceField = field(a.prefs.serialDevice, "/dev/ttyS2, /dev/ttyMT1…")
        serialBox.addView(serialDeviceField, LinearLayout.LayoutParams(Ui.MATCH, Ui.u(58)))
        machine.addView(serialBox, Ui.lp(t = 12))
        linkText = Ui.text(c, "", 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }
        machine.addView(linkText, Ui.lp(t = 12))
        machine.addView(Ui.button(c, tr("Appliquer"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 58) { applyMode() }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 14))
        renderModes()

        // ---- Home Assistant
        val ha = section(tr("Home Assistant"), Icon.Kind.HOME)
        ha.addView(Ui.text(c, tr("Envoie l'état du robot à Home Assistant en MQTT : état, programme, température, vitesse, poids, temps restant, couvercle, alerte. " +
            "Les capteurs apparaissent tout seuls (découverte MQTT). Lecture seule : rien ne peut démarrer le robot à distance."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.15f) }, Ui.lp(b = 10))
        var mqttOn = a.prefs.mqttEnabled
        ha.addView(toggle(tr("Envoyer l'état en MQTT"), mqttOn) { mqttOn = it })
        val mqttRow = Ui.horizontal(c)
        val mqttHost = field(a.prefs.mqttHost, tr("Serveur MQTT (ex. 192.168.1.10)"))
        val mqttPort = field(a.prefs.mqttPort.toString(), "Port").apply { inputType = InputType.TYPE_CLASS_NUMBER }
        mqttRow.addView(mqttHost, LinearLayout.LayoutParams(0, Ui.u(58), 1f).apply { rightMargin = Ui.u(10) })
        mqttRow.addView(mqttPort, LinearLayout.LayoutParams(Ui.u(120), Ui.u(58)))
        ha.addView(mqttRow, Ui.lp(t = 12))
        val credRow = Ui.horizontal(c)
        val mqttUser = field(a.prefs.mqttUser, tr("Utilisateur"))
        val mqttPass = field(a.prefs.mqttPassword, tr("Mot de passe")).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        credRow.addView(mqttUser, LinearLayout.LayoutParams(0, Ui.u(58), 1f).apply { rightMargin = Ui.u(10) })
        credRow.addView(mqttPass, LinearLayout.LayoutParams(0, Ui.u(58), 1f))
        ha.addView(credRow, Ui.lp(t = 10))
        val mqttTopic = field(a.prefs.mqttTopic, tr("Sujet (camybrith)"))
        ha.addView(mqttTopic, Ui.lp(Ui.MATCH, Ui.u(58), t = 10))
        val mqttStatus = Ui.text(c, a.app.homeAssistant.status, 15f, Ui.TEXT2).apply { setLineSpacing(0f, 1.1f) }
        ha.addView(mqttStatus, Ui.lp(t = 10))
        a.app.homeAssistant.listener = { mqttStatus.text = it }
        ha.addView(Ui.button(c, tr("Enregistrer"), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 54) {
            a.prefs.mqttEnabled = mqttOn
            a.prefs.mqttHost = mqttHost.text.toString().trim()
            a.prefs.mqttPort = mqttPort.text.toString().trim().toIntOrNull() ?: 1883
            a.prefs.mqttUser = mqttUser.text.toString().trim()
            a.prefs.mqttPassword = mqttPass.text.toString()
            a.prefs.mqttTopic = mqttTopic.text.toString().trim().trimEnd('/').ifEmpty { "camybrith" }
            a.app.homeAssistant.restart()
            a.toast(if (mqttOn) tr("Home Assistant : réglages enregistrés") else tr("Envoi MQTT arrêté"), Ui.OK)
        }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 12))

        // ---- Sécurité
        val safety = section(tr("Sécurité"), Icon.Kind.WARNING)
        safety.addView(toggle(tr("Exiger la détection du bol"), a.prefs.requireBowl) { on ->
            a.prefs.requireBowl = on
            a.machine.reconfigure { a.refusal(it) }
        })
        bowlText = Ui.text(c, "", 15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }
        safety.addView(bowlText, Ui.lp(t = 6))
        safety.addView(toggle(tr("Montée progressive de la vitesse si le contenu est chaud"), a.prefs.hotRamp) { on ->
            a.prefs.hotRamp = on
            a.machine.reconfigure { a.refusal(it) }
        }, Ui.lp(t = 10))
        safety.addView(Ui.text(c, tr("À partir de 60 °C, la lame atteint sa vitesse en 2 à 3 secondes pour éviter les projections. À froid, elle y va directement."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 6))
        safety.addView(toggle(tr("Enchaîner les étapes machine des recettes"), a.prefs.chainSteps) { a.prefs.chainSteps = it }, Ui.lp(t = 10))
        safety.addView(Ui.text(c, tr("Les réglages qui se suivent sans geste à faire entre eux partent ensemble d'un seul appui sur Lancer : la lame passe directement à la vitesse et à la température suivantes."),
            15f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 6))

        // ---- Date et heure
        val time = section(tr("Date et heure"), Icon.Kind.CLOCK)
        val zones = listOf(ZoneLookup.AUTO to tr("Automatique (d'après la connexion Internet)")) +
            (ZoneLookup.COMMON + listOf(TimeSync.current(), a.prefs.timeZone))
                .filter { it != ZoneLookup.AUTO }.distinct().map { it to it.replace('_', ' ') }
        time.addView(dropdown(zones, a.prefs.timeZone) { z ->
            a.prefs.timeZone = z
            setClock()
        }, Ui.lp(Ui.MATCH, Ui.u(58), t = 4))
        timeText = Ui.text(c, "", 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }
        time.addView(timeText, Ui.lp(t = 10))
        time.addView(Ui.button(c, tr("Mettre à l'heure"), Ui.Style.SECONDARY, Icon.Kind.SYNC, height = 52) { setClock() },
            Ui.lp(Ui.WRAP, Ui.WRAP, t = 10))
        refreshTime()

        // ---- Recettes Lidl
        val lidl = section(tr("Recettes Lidl"), Icon.Kind.SYNC)
        lidl.addView(Ui.text(c, tr("Camybrith Cooking télécharge lui-même les recettes officielles du %s sur monsieur-cuisine.com (sans compte).", a.prefs.activeRobot.label),
            16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) })
        syncText = Ui.text(c, "", 16f, Ui.TEXT)
        lidl.addView(syncText, Ui.lp(t = 10))
        val b1 = Ui.horizontal(c)
        b1.addView(Ui.button(c, tr("Synchroniser"), Ui.Style.PRIMARY, Icon.Kind.SYNC, height = 58) { sync(online = true) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        lidl.addView(b1, Ui.lp(t = 12))
        lidl.addView(Ui.text(c, tr("Autre source : les recettes déjà présentes dans l'appli d'origine du robot (remplacent celles du site)."),
            14f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 12))
        val b2 = Ui.horizontal(c)
        b2.addView(Ui.button(c, tr("Importer de l'appli d'origine"), Ui.Style.SECONDARY, height = 52) { sync(online = false) }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
        b2.addView(Ui.button(c, tr("Ouvrir l'appli d'origine"), Ui.Style.GHOST, Icon.Kind.LINK, height = 52) { a.openVendorApp() })
        lidl.addView(b2, Ui.lp(t = 6))
        lidl.addView(toggle(tr("Synchro automatique chaque nuit"), a.prefs.autoSync) { a.prefs.autoSync = it }, Ui.lp(t = 10))

        // ---- Recettes d'autres robots
        val other = section(tr("Recettes d'autres robots"), Icon.Kind.SYNC)
        val imp = FileImport(a, a.app.recipes, a.app.log)
        other.addView(Ui.text(c, tr("Vos propres recettes écrites pour un autre robot (fichier JSON, réglages du type « 10 s/vitesse 5 ») ou au format Camybrith Cooking déposées dans %s sont converties pour le Monsieur Cuisine Smart : vitesses, températures, Varoma en vapeur, pétrin en malaxage. Les réglages modifiés sont à vérifier avant de lancer.",
            "Android/data/${c.packageName}/files/import/"), 15f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) })
        val importText = Ui.text(c, "", 16f, Ui.TEXT)
        fun paintImport() {
            val n = imp.pending().size
            importText.text = when {
                FileImport.isRunning -> tr("Import en cours… (une à deux minutes)")
                n == 0 -> tr("Aucun fichier en attente")
                else -> tr("Fichiers en attente : %d", n)
            }
        }
        paintImport()
        other.addView(importText, Ui.lp(t = 10))
        other.addView(Ui.button(c, tr("Importer les recettes"), Ui.Style.SECONDARY, Icon.Kind.SYNC, height = 52) {
            importText.text = tr("Import en cours… (une à deux minutes)")
            Thread {
                val r = imp.run()
                view.post {
                    paintImport()
                    a.toast(r.message, if (r.bad == 0) Ui.OK else Ui.WARN)
                }
            }.start()
        }, Ui.lp(Ui.WRAP, Ui.WRAP, t = 10))

        // ---- Mises à jour
        val updates = section(tr("Mises à jour"), Icon.Kind.INFO)
        updText = Ui.text(c, "", 16f, Ui.TEXT2).apply { setLineSpacing(0f, 1.15f) }
        updates.addView(updText)
        updButtons = Ui.horizontal(c)
        updates.addView(updButtons, Ui.lp(t = 12))
        Updater.onInstallError = { msg -> updateFailed(msg) }
        renderUpdate()

        // ---- Outils
        val tools = section(tr("Outils"), Icon.Kind.SETTINGS)
        tools.addView(link(tr("Diagnostic de la base moteur")) { a.push(DiagnosticsScreen(a)) })
        tools.addView(link(tr("Bouton rotatif")) { a.push(KnobScreen(a)) })
        tools.addView(link(tr("Journal")) { a.push(LogScreen(a)) })
        tools.addView(link(tr("Paramètres Android")) { a.openAndroidSettings() })
        tools.addView(link(tr("Revenir à l'appli d'origine")) { restoreVendor() })
        val version = try { a.packageManager.getPackageInfo(a.packageName, 0).versionName } catch (_: Exception) { "?" }
        tools.addView(Ui.text(c, tr("Camybrith Cooking %s · protocole d'après le Monsieur Cuisine Developer SDK (licence MIT). " +
            "Projet indépendant, non affilié à Lidl ni à SilverCrest.", version), 14f, Ui.TEXT3).apply { setLineSpacing(0f, 1.1f) }, Ui.lp(t = 12))

        renderMenu()
        return root
    }

    /** Une page de réglages : une entrée dans le menu de gauche, son contenu à droite. */
    private fun section(title: String, icon: Icon.Kind): LinearLayout {
        val content = Ui.vertical(c)
        content.setPadding(Ui.u(24), Ui.u(20), Ui.u(24), Ui.u(24))
        val head = Ui.horizontal(c)
        head.addView(Ui.badge(c, icon, Ui.ACCENT, Ui.ACCENT_SOFT, 40), LinearLayout.LayoutParams(Ui.u(40), Ui.u(40)).apply { rightMargin = Ui.u(12) })
        head.addView(Ui.title(c, title, 24f))
        content.addView(head, Ui.lp(b = 14))
        pages += Page(title, icon, content)
        return content
    }

    private fun renderMenu() {
        menu.removeAllViews()
        for ((i, p) in pages.withIndex()) {
            val on = i == page
            val row = Ui.horizontal(c)
            row.setPadding(Ui.u(14), 0, Ui.u(12), 0)
            row.minimumHeight = Ui.u(50)
            row.background = if (on) Ui.round(Ui.SURFACE, 16f) else Ui.pressable(Ui.round(0, 16f), 16f)
            if (on) Ui.lift(row, 1.5f)
            row.addView(Icon(c, p.icon, if (on) Ui.ACCENT else Ui.TEXT2), LinearLayout.LayoutParams(Ui.u(20), Ui.u(20)).apply { rightMargin = Ui.u(11) })
            row.addView(Ui.ellipsize(Ui.text(c, p.title, 16f, if (on) Ui.TEXT else Ui.TEXT2, if (on) Ui.MEDIUM else Ui.REGULAR)), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
            row.isClickable = true
            row.tap { page = i; renderMenu() }
            menu.addView(row, Ui.lp(b = 4))
        }
        pageBox.removeAllViews()
        val content = pages[page].content
        (content.parent as? android.view.ViewGroup)?.removeView(content)
        pageBox.addView(Ui.scroll(c, content), Ui.flp())
    }

    private fun field(value: String, hint: String) = EditText(c).apply {
        setText(value)
        this.hint = hint
        setTextSize(TypedValue.COMPLEX_UNIT_PX, Ui.uf(19f))
        setTextColor(Ui.TEXT)
        setHintTextColor(Ui.TEXT3)
        setSingleLine()
        background = Ui.round(Ui.SURFACE2, 16f, Ui.STROKE, 1f)
        setPadding(Ui.u(14), 0, Ui.u(14), 0)
    }

    private fun toggle(label: String, initial: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = Ui.horizontal(c)
        row.addView(Ui.text(c, label, 17f, Ui.TEXT).apply { setLineSpacing(0f, 1.1f) }, LinearLayout.LayoutParams(0, Ui.WRAP, 1f).apply { rightMargin = Ui.u(14) })
        var state = initial
        val track = FrameLayout(c)
        val knob = View(c)
        track.addView(knob, Ui.flp(Ui.u(26), Ui.u(26), Gravity.CENTER_VERTICAL))
        fun paint() {
            track.background = Ui.round(if (state) Ui.ACCENT else Ui.SURFACE3, 17f)
            knob.background = Ui.round(if (state) Ui.ON_ACCENT else Ui.SURFACE, 13f)
            (knob.layoutParams as FrameLayout.LayoutParams).leftMargin = if (state) Ui.u(26) else Ui.u(4)
            knob.requestLayout()
            track.contentDescription = label + " : " + if (state) tr("Oui") else tr("Non")
        }
        paint()
        track.isClickable = true
        track.tap { state = !state; paint(); onChange(state) }
        row.addView(track, LinearLayout.LayoutParams(Ui.u(56), Ui.u(34)))
        return row
    }

    private fun soundsInfo(count: Int): String {
        val dir = "Android/data/${c.packageName}/files/sons/"
        val base = tr("Pour mettre vos propres sons, déposez dans %s des fichiers .wav, .ogg ou .mp3 nommés demarrage, debut, fin, recette, alerte, pause, appui, pesee ou trop, puis rechargez.").format(dir)
        return if (count == 0) base else base + " " + tr("Sons personnels : %d").format(count)
    }

    /** Volume des médias du robot, celui des sons de l'appli. */
    private fun volumeRow(): LinearLayout {
        val row = Ui.horizontal(c)
        row.addView(Ui.text(c, tr("Volume"), 17f, Ui.TEXT), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        val value = Ui.text(c, "", 17f, Ui.TEXT, Ui.MEDIUM).apply { gravity = Gravity.CENTER }
        fun paint() {
            value.text = "${a.sounds.volume} / ${a.sounds.maxVolume}"
        }
        paint()
        row.addView(Ui.button(c, "", icon = Icon.Kind.MINUS, height = 44) {
            a.sounds.volume = a.sounds.volume - 1; paint(); a.sound(app.toque.Sounds.Kind.START)
        })
        row.addView(value, LinearLayout.LayoutParams(Ui.u(80), Ui.WRAP))
        row.addView(Ui.button(c, "", icon = Icon.Kind.PLUS, height = 44) {
            a.sounds.volume = a.sounds.volume + 1; paint(); a.sound(app.toque.Sounds.Kind.START)
        })
        row.addView(Ui.button(c, tr("Écouter"), height = 44, textSize = 16f) {
            a.sound(app.toque.Sounds.Kind.END)
        }, Ui.lp(Ui.WRAP, Ui.WRAP, l = 12))
        return row
    }

    private fun link(label: String, onClick: () -> Unit): LinearLayout {
        val row = Ui.horizontal(c)
        row.setPadding(Ui.u(4), Ui.u(14), Ui.u(4), Ui.u(14))
        row.addView(Ui.text(c, label, 17f, Ui.TEXT), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        row.addView(Icon(c, Icon.Kind.CHEVRON, Ui.TEXT3), LinearLayout.LayoutParams(Ui.u(20), Ui.u(20)))
        row.background = Ui.pressable(Ui.round(0, 8f), 8f)
        row.isClickable = true
        row.tap { onClick() }
        return row
    }

    /** Menu déroulant : la valeur choisie, et la liste qui s'ouvre en dessous. */
    private fun dropdown(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit): View {
        val box = Ui.horizontal(c)
        box.setPadding(Ui.u(16), 0, Ui.u(14), 0)
        box.background = Ui.pressable(Ui.round(Ui.SURFACE2, 16f, Ui.STROKE, 1f), 16f)
        val label = options.firstOrNull { it.first == selected }?.second ?: options.first().second
        box.addView(Ui.text(c, label, 19f, Ui.TEXT), LinearLayout.LayoutParams(0, Ui.WRAP, 1f))
        box.addView(Icon(c, Icon.Kind.CHEVRON, Ui.TEXT2).apply { rotation = 90f }, LinearLayout.LayoutParams(Ui.u(22), Ui.u(22)))
        box.isClickable = true
        box.tap {
            val list = Ui.vertical(c)
            list.setPadding(0, Ui.u(6), 0, Ui.u(6))
            val scroll = ScrollView(c).apply {
                addView(list)
                background = Ui.round(Ui.SURFACE3, 16f, Ui.STROKE, 1f)
            }
            val height = if (options.size > 7) Ui.u(400) else ViewGroup.LayoutParams.WRAP_CONTENT
            val popup = PopupWindow(scroll, box.width, height, false)
            for ((code, name) in options) {
                val row = Ui.text(c, name, 19f, if (code == selected) Ui.ACCENT else Ui.TEXT)
                row.setPadding(Ui.u(16), Ui.u(14), Ui.u(16), Ui.u(14))
                row.background = Ui.pressable(Ui.round(0, 0f), 0f)
                row.isClickable = true
                row.tap {
                    popup.dismiss()
                    if (code != selected) onPick(code)
                }
                list.addView(row, Ui.lp(Ui.MATCH, Ui.WRAP))
            }
            popup.setBackgroundDrawable(ColorDrawable(0))
            popup.isOutsideTouchable = true
            popup.elevation = Ui.uf(8f)
            popup.showAsDropDown(box, 0, Ui.u(6))
        }
        return box
    }

    private fun renderModes() {
        modeRow.removeAllViews()
        for (m in Prefs.Mode.values()) {
            modeRow.addView(Ui.toggleChip(c, when (m) {
                Prefs.Mode.DEMO -> tr("Démo")
                Prefs.Mode.TWIN -> tr("Jumeau (PC)")
                Prefs.Mode.HARDWARE -> tr("Machine réelle")
            }, mode == m) { mode = m; renderModes() }, Ui.lp(Ui.WRAP, Ui.WRAP, r = 8))
        }
        twinBox.visibility = if (mode == Prefs.Mode.TWIN) View.VISIBLE else View.GONE
        serialBox.visibility = if (mode == Prefs.Mode.HARDWARE) View.VISIBLE else View.GONE
        linkText.text = when (mode) {
            Prefs.Mode.DEMO -> tr("Base moteur simulée : pour essayer l'appli sur n'importe quel appareil Android.")
            Prefs.Mode.TWIN -> tr("Jumeau numérique du SDK lancé sur un PC (tools/twin.py). Avec « adb reverse tcp:8765 tcp:8765 », gardez 127.0.0.1.")
            Prefs.Mode.HARDWARE -> tr("Pilote le vrai moteur par le port série. Exige le root. L'appli d'origine est coupée du moteur (réversible).")
        } + if (mode == Prefs.Mode.TWIN) "" else "\n" + tr("Robot : %s", a.prefs.robotModel.label) + " (" + a.prefs.serialDevice + ")"
    }

    private fun applyMode() {
        if (a.snapshot.busy) {
            a.toast(tr("Arrêtez d'abord le programme en cours"), Ui.WARN)
            return
        }
        if (mode == Prefs.Mode.HARDWARE && a.prefs.mode != Prefs.Mode.HARDWARE) {
            a.confirm(
                tr("Piloter la vraie machine"),
                tr("Camybrith Cooking va prendre le contrôle du moteur et de la chauffe à la place de l'appli d'origine.\n\n" +
                    "Premier essai : bol vide, couvercle fermé, à portée de l'interrupteur principal. " +
                    "Si quoi que ce soit semble anormal, coupez l'interrupteur."),
                tr("Continuer"), danger = true,
            ) { prepareHardware() }
            return
        }
        save()
    }

    private fun save() {
        a.prefs.mode = mode
        val customDev = serialDeviceField.text.toString().trim()
        if (customDev.isNotEmpty()) a.prefs.serialDevice = customDev
        TemperatureTable.current = a.prefs.activeRobot.temperatures
        a.prefs.bootLaunch = true
        a.prefs.twinHost = host.text.toString().trim().ifEmpty { "127.0.0.1" }
        a.prefs.twinPort = port.text.toString().trim().toIntOrNull() ?: 8765
        a.machine.reconfigure { v -> if (v.ok) a.toast(tr("Mode : %s", mode.label), Ui.OK) else a.refusal(v) }
        a.refreshTitle()
    }

    private fun prepareHardware() {
        a.toast(tr("Vérifications…"))
        Thread {
            val problems = ArrayList<String>()
            if (!RootShell.available()) problems += tr("accès root refusé")
            val sync = LidlSync(a, a.prefs, a.app.recipes, a.app.log)
            if (problems.isEmpty() && sync.installed()) {
                if (sync.components().hardwareService != null && !sync.setVendorHardware(false))
                    problems += tr("impossible de couper l'appli d'origine du moteur")
                sync.forceStop()
            }
            view.post {
                if (problems.isNotEmpty()) {
                    a.confirm(tr("Machine réelle indisponible"), problems.joinToString("\n") { "• $it" }, "OK", tr("Fermer")) {}
                } else save()
            }
        }.start()
    }

    private fun restoreVendor() {
        a.confirm(
            tr("Revenir à l'appli d'origine"),
            tr("Camybrith Cooking libère le moteur, rend l'accès à l'appli Monsieur Cuisine d'origine et passe en mode Démo. " +
                "Camybrith Cooking ne s'ouvrira plus au démarrage du robot."),
            tr("Revenir"),
        ) {
            a.prefs.mode = Prefs.Mode.DEMO
            a.prefs.bootLaunch = false
            KnobLight.release()
            a.machine.reconfigure {
                Thread {
                    val sync = LidlSync(a, a.prefs, a.app.recipes, a.app.log)
                    sync.setVendorHardware(true)
                    val launch = sync.components().launch
                    view.post {
                        if (launch != null) a.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        else a.toast(tr("Appli d'origine introuvable"), Ui.WARN)
                    }
                }.start()
            }
        }
    }

    private fun sync(online: Boolean) {
        if (syncing) return
        if (!online && a.prefs.mode != Prefs.Mode.HARDWARE) {
            a.toast(tr("Disponible une fois Camybrith Cooking installé sur le robot (mode Machine réelle)"), Ui.WARN)
            return
        }
        syncing = true
        syncText.text = tr("Synchronisation en cours… (quelques minutes)")
        Thread {
            val r = if (online) OnlineSync(a, a.prefs, a.app.recipes, a.app.log).sync { msg -> view.post { syncText.text = msg } }
            else LidlSync(a, a.prefs, a.app.recipes, a.app.log).importNow { msg -> view.post { syncText.text = msg } }
            view.post {
                syncing = false
                refreshSync()
                a.toast(r.message, if (r.ok) Ui.OK else Ui.WARN)
            }
        }.start()
    }

    private fun refreshTime(message: String? = null) {
        val zone = TimeSync.current()
        val mode = if (a.prefs.timeZone == ZoneLookup.AUTO) tr("automatique") else tr("choisi à la main")
        timeText.text = tr("Il est %s · fuseau %s (%s)", TimeSync.clock(), zone.replace('_', ' '), mode) +
            (message?.let { "\n" + it } ?: "")
    }

    private fun setClock() {
        if (settingTime) return
        settingTime = true
        timeText.text = tr("Réglage de l'heure…")
        Thread {
            val r = try {
                TimeSync(a, a.prefs, a.app.log).run()
            } catch (e: Exception) {
                TimeSync.Result(false, TimeSync.current(), e.message ?: e.toString())
            }
            a.runOnUiThread {
                settingTime = false
                refreshTime(if (r.ok) null else r.message)
                if (r.ok) a.toast(r.message, Ui.OK)
            }
        }.start()
    }

    private fun refreshSync() {
        val last = a.prefs.lastSyncResult
        syncText.text = (if (last.isEmpty()) tr("Jamais synchronisé") else tr("Dernière synchro le %s", last)) +
            "\n" + tr("%d recettes dans Camybrith Cooking", a.app.recipes.count)
    }

    // ------------------------------------------------------------------ mises à jour

    private fun renderUpdate() {
        val r = release
        val installed = tr("Version installée : %s", updater.installedVersion)
        updText.setTextColor(if (upd == Upd.ERROR) Ui.WARN else if (upd == Upd.AVAILABLE) Ui.TEXT else Ui.TEXT2)
        updText.text = when (upd) {
            Upd.IDLE -> installed + "\n" + tr("Les nouvelles versions sont publiées sur GitHub.")
            Upd.CHECKING -> installed + "\n" + tr("Recherche sur GitHub…")
            Upd.UP_TO_DATE -> installed + "\n" + tr("Camybrith Cooking est à jour.")
            Upd.AVAILABLE -> installed + "\n" + tr("Nouvelle version disponible : %s", r?.version) + notes(r)
            Upd.DOWNLOADING -> tr("Téléchargement de la version %s… %d %%", r?.version, updPercent)
            Upd.INSTALLING -> tr("Installation de la version %s… Le robot va redémarrer tout seul, puis Camybrith Cooking se rouvre.", r?.version)
            Upd.ERROR -> installed + "\n" + updMessage
        }
        updButtons.removeAllViews()
        when (upd) {
            Upd.IDLE, Upd.UP_TO_DATE, Upd.ERROR ->
                updButtons.addView(Ui.button(c, tr("Rechercher une mise à jour"), Ui.Style.PRIMARY, Icon.Kind.SYNC, height = 58) { checkUpdate() })
            Upd.AVAILABLE -> {
                updButtons.addView(Ui.button(c, tr("Installer la version %s", r?.version), Ui.Style.PRIMARY, Icon.Kind.CHECK, height = 58) { installUpdate() },
                    Ui.lp(Ui.WRAP, Ui.WRAP, r = 10))
                updButtons.addView(Ui.button(c, tr("Plus tard"), Ui.Style.GHOST, height = 58) { upd = Upd.IDLE; renderUpdate() })
            }
            else -> {}
        }
    }

    /**
     * Résumé des nouveautés d'une version, tiré de ses notes (écrites depuis CHANGELOG.md) : la partie
     * française, ou la partie anglaise (après 🇬🇧) si l'appli n'est pas en français.
     */
    private fun notes(r: Release?): String {
        val all = r?.notes?.lines() ?: return ""
        val start = all.indexOfFirst { it.startsWith("## Nouveaut") }.let { if (it < 0) 0 else it + 1 }
        val section = all.drop(start).takeWhile { !it.startsWith("## ") }
        val flag = section.indexOfFirst { it.trim().startsWith("🇬🇧") }
        val french = I18n.current.code == "fr"
        val part = when {
            flag < 0 -> section
            french -> section.take(flag)
            else -> section.drop(flag + 1)
        }
        val text = part.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith(">") }
            .joinToString("\n") { it.replace(Regex("^#+ *"), "").replace("**", "").replace("`", "").replace(Regex("^\\* "), "• ").replace("*", "") }
            .replace(Regex("\n(?!• )"), " ")
            .lines().take(5).joinToString("\n")
        if (text.isEmpty()) return ""
        return "\n\n" + if (text.length > 420) text.substring(0, 420) + "…" else text
    }

    private fun checkUpdate() {
        if (upd == Upd.CHECKING || upd == Upd.DOWNLOADING || upd == Upd.INSTALLING) return
        upd = Upd.CHECKING
        renderUpdate()
        Thread {
            try {
                val r = updater.latest()
                view.post {
                    release = r
                    upd = if (updater.isNewer(r)) Upd.AVAILABLE else Upd.UP_TO_DATE
                    renderUpdate()
                    if (upd == Upd.AVAILABLE) installUpdate()
                }
            } catch (e: Updater.Failure) {
                view.post { upd = Upd.ERROR; updMessage = e.message ?: tr("Erreur"); renderUpdate() }
            } catch (e: Exception) {
                a.app.log.crash("maj", e)
                view.post { upd = Upd.ERROR; updMessage = tr("Erreur inattendue : %s", e.message); renderUpdate() }
            }
        }.start()
    }

    /** Propose l'installation dans une fenêtre (toujours visible, même si la carte est hors écran). */
    private fun installUpdate() {
        val r = release ?: return
        if (a.snapshot.busy) {
            a.toast(tr("Arrêtez d'abord le programme en cours"), Ui.WARN)
            return
        }
        a.confirm(
            tr("Version %s disponible", r.version),
            notes(r).trim().let { if (it.isEmpty()) "" else it + "\n\n" } +
                tr("Camybrith Cooking va la télécharger depuis GitHub, l'installer puis redémarrer le robot (une à deux minutes). " +
                    "Le moteur reste arrêté pendant l'opération."),
            tr("Installer"), tr("Plus tard"), icon = Icon.Kind.SYNC,
        ) {
            a.machine.suspendPort { v ->
                if (!v.ok) { a.refusal(v); return@suspendPort }
                upd = Upd.DOWNLOADING
                updPercent = 0
                renderUpdate()
                Thread {
                    try {
                        val file = updater.download(r) { p -> view.post { updPercent = p; renderUpdate() } }
                        view.post { upd = Upd.INSTALLING; renderUpdate() }
                        updater.install(file)
                    } catch (e: Updater.Failure) {
                        view.post { updateFailed(e.message ?: tr("Erreur")) }
                    } catch (e: Exception) {
                        a.app.log.crash("maj", e)
                        view.post { updateFailed(tr("Erreur inattendue : %s", e.message)) }
                    }
                }.start()
            }
        }
    }

    private fun updateFailed(message: String) {
        a.machine.resumePort()
        upd = Upd.ERROR
        updMessage = message
        renderUpdate()
        a.toast(message, Ui.WARN)
    }

    override fun onShow() {
        mode = a.prefs.mode
        renderModes()
        if (!syncing) refreshSync()
    }

    override fun onSnapshot(s: Snapshot) {
        bowlText.text = if (s.hasSample) tr("Capteur du bol en ce moment : %s. Vérifiez qu'il change quand vous retirez le bol avant d'activer cette option.",
            if (s.bowlDetected) tr("bol détecté") else tr("bol NON détecté")) else tr("Base non connectée")
    }
}
