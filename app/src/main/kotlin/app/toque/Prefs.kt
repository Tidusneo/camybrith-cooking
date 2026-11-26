package app.toque

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey
import app.toque.core.machine.RobotModel

/**
 * Réglages persistants, lisibles par les deux processus (interface et machine).
 * Écriture synchrone (commit) : le processus machine relit le fichier juste après.
 */
class Prefs(context: Context) {
    @Suppress("DEPRECATION")
    private val p: SharedPreferences = context.getSharedPreferences("toque", Context.MODE_MULTI_PROCESS)
    private val packages: PackageManager = context.applicationContext.packageManager

    enum class Mode(val key: String, private val fr: String) {
        DEMO("demo", trKey("Démo (base simulée)")),
        TWIN("twin", trKey("Jumeau numérique (PC)")),
        HARDWARE("hardware", trKey("Machine réelle"));

        val label: String get() = tr(fr)

        companion object {
            fun of(k: String?) = values().firstOrNull { it.key == k } ?: DEMO
        }
    }

    var mode: Mode
        get() = Mode.of(p.getString("mode", null))
        set(v) {
            p.edit().putString("mode", v.key).commit()
        }

    /** Langue de l'interface : « auto » (celle du système), « fr » ou « en ». */
    var language: String
        get() = p.getString("language", I18n.AUTO) ?: I18n.AUTO
        set(v) {
            p.edit().putString("language", v).commit()
        }

    /** Thème de l'interface : « auto » (clair de 7 h à 20 h, sombre le soir), « light » ou « dark ». */
    var theme: String
        get() = p.getString("theme", "auto") ?: "auto"
        set(v) {
            p.edit().putString("theme", v).commit()
        }

    var twinHost: String
        get() = p.getString("twinHost", "127.0.0.1") ?: "127.0.0.1"
        set(v) {
            p.edit().putString("twinHost", v).commit()
        }

    var twinPort: Int
        get() = p.getInt("twinPort", 8765)
        set(v) {
            p.edit().putInt("twinPort", v).commit()
        }

    /** Modèle de robot choisi : [RobotModel.AUTO] (selon l'appli d'origine installée) ou la clé d'un modèle. */
    var robot: String
        get() = p.getString("robot", RobotModel.AUTO) ?: RobotModel.AUTO
        set(v) {
            p.edit().putString("robot", v).commit()
        }

    /** Modèle effectif : celui choisi, sinon celui dont l'appli d'origine est installée ou la carte détectée. */
    val robotModel: RobotModel
        get() = RobotModel.of(robot) ?: RobotModel.detect({ pkg ->
            try {
                @Suppress("DEPRECATION")
                val flags = if (android.os.Build.VERSION.SDK_INT >= 24)
                    PackageManager.MATCH_DISABLED_COMPONENTS
                else
                    PackageManager.GET_DISABLED_COMPONENTS
                packages.getPackageInfo(pkg, flags)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }, {
            isR818Board()
        })

    /** Robot dont Toque parle le protocole : le jumeau numérique ne parle que celui du Connect. */
    val activeRobot: RobotModel
        get() = if (mode == Mode.TWIN) RobotModel.CONNECT else robotModel

    fun defaultSerialDevice(): String = robotModel.serialDevice

    var serialDevice: String
        get() = p.getString("serialDevice", null) ?: defaultSerialDevice()
        set(v) {
            p.edit().putString("serialDevice", v).commit()
        }

    var vendorPackage: String
        get() = p.getString("vendorPackage", null) ?: robotModel.vendorPackage
        set(v) {
            p.edit().putString("vendorPackage", v).commit()
        }

    var requireBowl: Boolean
        get() = p.getBoolean("requireBowl", false)
        set(v) {
            p.edit().putBoolean("requireBowl", v).commit()
        }

    /** Montée progressive de la vitesse quand le contenu est chaud (≥ 60 °C). */
    var hotRamp: Boolean
        get() = p.getBoolean("hotRamp", true)
        set(v) {
            p.edit().putBoolean("hotRamp", v).commit()
        }

    /** Recettes : enchaîner sans appui les étapes machine qui se suivent sans geste à faire. */
    var chainSteps: Boolean
        get() = p.getBoolean("chainSteps", true)
        set(v) {
            p.edit().putBoolean("chainSteps", v).commit()
        }

    var sound: Boolean
        get() = p.getBoolean("sound", true)
        set(v) {
            p.edit().putBoolean("sound", v).commit()
        }

    /** Petit son à chaque appui sur un bouton (si les sons sont activés). */
    var keySound: Boolean
        get() = p.getBoolean("keySound", true)
        set(v) {
            p.edit().putBoolean("keySound", v).commit()
        }

    /** Fuseau horaire : « auto » (d'après la connexion Internet) ou un identifiant IANA choisi à la main. */
    var timeZone: String
        get() = p.getString("timeZone", "auto") ?: "auto"
        set(v) {
            p.edit().putString("timeZone", v).commit()
        }

    /** Dernier fuseau appliqué avec succès (affiché dans les réglages). */
    var lastZone: String
        get() = p.getString("lastZone", "") ?: ""
        set(v) {
            p.edit().putString("lastZone", v).commit()
        }

    var autoSync: Boolean
        get() = p.getBoolean("autoSync", true)
        set(v) {
            p.edit().putBoolean("autoSync", v).commit()
        }

    var lastSyncMs: Long
        get() = p.getLong("lastSyncMs", 0)
        set(v) {
            p.edit().putLong("lastSyncMs", v).commit()
        }

    var lastSyncResult: String
        get() = p.getString("lastSyncResult", "") ?: ""
        set(v) {
            p.edit().putString("lastSyncResult", v).commit()
        }

    var vendorSession: Boolean
        get() = p.getBoolean("vendorSession", false)
        set(v) {
            p.edit().putBoolean("vendorSession", v).commit()
        }

    /** Bouton « Retour à Toque » à afficher : Toque a ouvert une autre appli (robot sans bouton Accueil). */
    var returnBubble: Boolean
        get() = p.getBoolean("returnBubble", false)
        set(v) {
            p.edit().putBoolean("returnBubble", v).commit()
        }

    /** Démarrage du robot pour lequel le son d'ouverture a déjà été joué (il ne l'est qu'une fois par démarrage). */
    var startupSoundBoot: String
        get() = p.getString("startupSoundBoot", "") ?: ""
        set(v) {
            p.edit().putString("startupSoundBoot", v).commit()
        }

    /** Ouvrir Toque au démarrage du robot (désactivé par « Revenir à l'appli d'origine »). */
    var bootLaunch: Boolean
        get() = p.getBoolean("bootLaunch", true)
        set(v) {
            p.edit().putBoolean("bootLaunch", v).commit()
        }

    var knobLeft: Int
        get() = p.getInt("knobLeft", -1)
        set(v) {
            p.edit().putInt("knobLeft", v).commit()
        }

    var knobRight: Int
        get() = p.getInt("knobRight", -1)
        set(v) {
            p.edit().putInt("knobRight", v).commit()
        }

    var knobPress: Int
        get() = p.getInt("knobPress", -1)
        set(v) {
            p.edit().putInt("knobPress", v).commit()
        }

    var lastCrashMs: Long
        get() = p.getLong("lastCrashMs", 0)
        set(v) {
            p.edit().putLong("lastCrashMs", v).commit()
        }

    var welcomeDone: Boolean
        get() = p.getBoolean("welcomeDone", false)
        set(v) {
            p.edit().putBoolean("welcomeDone", v).commit()
        }

    var builtinVersion: Int
        get() = p.getInt("builtinVersion", 0)
        set(v) {
            p.edit().putInt("builtinVersion", v).commit()
        }

    /** Recette guidée en cours (id + étape), pour reprendre après un redémarrage. */
    var guidedRecipe: String
        get() = p.getString("guidedRecipe", "") ?: ""
        set(v) {
            p.edit().putString("guidedRecipe", v).commit()
        }

    var guidedStep: Int
        get() = p.getInt("guidedStep", 0)
        set(v) {
            p.edit().putInt("guidedStep", v).commit()
        }

    companion object {
        fun isR818Board(): Boolean = try {
            val hw = android.os.Build.HARDWARE.lowercase()
            val platform = try {
                val c = Class.forName("android.os.SystemProperties")
                val get = c.getMethod("get", String::class.java)
                (get.invoke(null, "ro.board.platform") as? String)?.lowercase().orEmpty()
            } catch (_: Throwable) { "" }
            hw.startsWith("sun50i") || platform == "peony"
        } catch (_: Throwable) {
            false
        }
    }
}
