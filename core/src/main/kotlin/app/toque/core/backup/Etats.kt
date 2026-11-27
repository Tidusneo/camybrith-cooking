package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.obj
import app.toque.core.json.str
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dates ISO 8601 avec décalage (« 2026-11-27T14:03:00+01:00 »), sans java.time (Android 6). */
object Iso {
    fun format(ms: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)
        f.timeZone = zone
        val s = f.format(Date(ms))
        return s.substring(0, s.length - 2) + ":" + s.substring(s.length - 2)
    }

    /** Millisecondes, ou null si illisible. Accepte « +01:00 », « +0100 » et « Z ». */
    fun parse(s: String?): Long? {
        if (s == null || s.length < 19) return null
        val t = when {
            s.endsWith("Z") -> s.dropLast(1) + "+0000"
            s.length >= 6 && s[s.length - 3] == ':' && (s[s.length - 6] == '+' || s[s.length - 6] == '-') ->
                s.substring(0, s.length - 3) + s.substring(s.length - 2)
            else -> s
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT).apply { isLenient = false }.parse(t)?.time
        } catch (_: ParseException) {
            null
        }
    }
}

/** État de cuisson (`cuisson.json`, écrit par l'interface ; data-model.md › État de cuisson). */
object Cuisson {
    enum class Etat { LIBRE, EN_COURS, INCONNU }

    const val FRAICHEUR_MS = 60_000L

    fun json(occupe: Boolean, programme: String, maj: String): String =
        Json.write(linkedMapOf("occupe" to occupe, "programme" to if (occupe) programme else "", "maj" to maj))

    /**
     * Pilote arrêté → pas de cuisson, quel que soit le fichier. Pilote actif : `occupe` vrai et
     * `maj` de moins de 60 s → cuisson ; `occupe` faux → libre ; fichier absent, illisible ou
     * `occupe` vrai mais périmé → inconnu (interface plantée pendant un programme).
     */
    fun evaluer(fichier: String?, piloteActif: Boolean, maintenant: Long): Etat {
        if (!piloteActif) return Etat.LIBRE
        val m = try { fichier?.let { Json.parse(it).obj() } } catch (_: Json.ParseException) { null } ?: return Etat.INCONNU
        val occupe = m.bool("occupe") ?: return Etat.INCONNU
        if (!occupe) return Etat.LIBRE
        val maj = Iso.parse(m.str("maj")) ?: return Etat.INCONNU
        return if (maintenant - maj in 0 until FRAICHEUR_MS) Etat.EN_COURS else Etat.INCONNU
    }

    fun programme(fichier: String?): String =
        try { fichier?.let { Json.parse(it).obj().str("programme") } } catch (_: Json.ParseException) { null } ?: ""
}

/** Mode du robot (`mode.json`, source unique ; data-model.md › Mode du robot). */
data class Mode(val lidl: Boolean, val depuis: String = "", val origine: Origine? = null) {
    fun toJson(): String = Json.write(linkedMapOf(
        "mode" to if (lidl) "lidl" else "camybrith", "depuis" to depuis, "origine" to origine?.cle))

    companion object {
        /** Absent ou illisible = Camybrith (data-model : absent = `camybrith`). */
        fun parse(text: String?): Mode {
            val m = try { text?.let { Json.parse(it).obj() } } catch (_: Json.ParseException) { null } ?: return Mode(false)
            return Mode(m.str("mode") == "lidl", m.str("depuis") ?: "", Origine.of(m.str("origine")))
        }
    }
}

/** Identité du robot pour le module (`robot.json`, écrit par l'interface ; research.md D6). */
data class RobotId(
    val vendorPackage: String,
    val hardwareServiceSuffix: String,
    val camybrithBootReceiver: String = BOOT_RECEIVER,
    val camybrithHomeAlias: String = HOME_ALIAS,
) {
    fun toJson(): String = Json.write(linkedMapOf(
        "vendorPackage" to vendorPackage, "hardwareServiceSuffix" to hardwareServiceSuffix,
        "camybrithBootReceiver" to camybrithBootReceiver, "camybrithHomeAlias" to camybrithHomeAlias))

    companion object {
        const val APP = "app.toque"
        const val PILOTE = "app.toque.pilote"
        const val BOOT_RECEIVER = "app.toque/app.toque.recipes.BootReceiver"
        const val HOME_ALIAS = "app.toque/app.toque.ui.Accueil"

        /** Lecture stricte : valeurs hors format refusées (elles finissent dans des commandes `pm`). */
        fun parse(text: String?): RobotId? {
            val m = try { text?.let { Json.parse(it).obj() } } catch (_: Json.ParseException) { null } ?: return null
            val pkg = m.str("vendorPackage") ?: return null
            val suffix = m.str("hardwareServiceSuffix") ?: return null
            val boot = m.str("camybrithBootReceiver") ?: BOOT_RECEIVER
            val home = m.str("camybrithHomeAlias") ?: HOME_ALIAS
            if (!Cibles.paquet(pkg) || !Regex("[A-Za-z][A-Za-z0-9_]{0,80}").matches(suffix)) return null
            if (Cibles.erreur(TypeModif.DEMARRAGE, boot) != null || Cibles.erreur(TypeModif.DEMARRAGE, home) != null) return null
            return RobotId(pkg, suffix, boot, home)
        }
    }
}
