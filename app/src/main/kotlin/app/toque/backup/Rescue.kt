package app.toque.backup

import app.toque.core.backup.Cuisson
import app.toque.core.backup.Iso
import app.toque.core.backup.RobotId
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.machine.RobotModel
import app.toque.core.machine.RootShell

/**
 * Lien entre l'interface et le module de secours (spec 007) : tout passe par la commande
 * `camybrith` du module, lancée en root. L'interface ne touche jamais au registre elle-même.
 */
object Rescue {
    private const val DIR = "/data/adb/camybrith"
    private val COMMANDES = listOf(
        "/data/adb/modules/camybrith_secours/camybrith",
        // Juste après l'installation par Magisk, avant le redémarrage.
        "/data/adb/modules_update/camybrith_secours/camybrith",
    )

    @Volatile private var chemin: String? = null

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    /** Chemin de la commande si le module est installé, sinon null. */
    fun commande(): String? {
        chemin?.let { return it }
        val r = RootShell.run(COMMANDES.joinToString("; ") { "[ -f $it ] && echo $it" } + "; true", 8_000)
        return r.out.lines().firstOrNull { it.isNotBlank() }?.trim()?.also { chemin = it }
    }

    fun present(): Boolean = commande() != null

    private fun cam(vararg args: String, timeoutMs: Long = 30_000): RootShell.Result {
        val c = commande() ?: return RootShell.Result(-1, "", "module de secours absent")
        return RootShell.run("sh $c " + args.joinToString(" ") { q(it) }, timeoutMs)
    }

    /**
     * Inscrit une modification AVANT de la faire. Rend son id, ou null si le module est absent ou
     * refuse (dans ce cas, l'appelant ne fait pas la modification, sauf si ne pas la faire est
     * dangereux : voir LidlSync.setVendorHardware).
     */
    fun noter(type: String, cible: String, avant: String, apres: String, portee: String): Int? {
        val r = cam("noter", type, cible, avant, apres, portee, "interface")
        return if (r.ok) r.out.lines().lastOrNull { it.isNotBlank() }?.trim()?.toIntOrNull() else null
    }

    fun noterFin(id: Int, ok: Boolean) {
        cam("noter-fin", id.toString(), if (ok) "ok" else "echec")
    }

    class Etat(val lidl: Boolean, val depuis: String, val origine: String?, val aAnnuler: List<String>, val alerte: Boolean)

    fun etat(): Etat? {
        val r = cam("etat", "--json")
        if (!r.ok) return null
        val m = try { Json.parse(r.out.trim().lines().last()).obj() } catch (_: Exception) { return null }
        return Etat(
            m.str("mode") == "lidl", m.str("depuis") ?: "", m.str("origine"),
            m["lidl"].list().map { e -> e.obj().let { libelle(it.str("type") ?: "", it.str("cible") ?: "") } },
            m["alerte"] == true,
        )
    }

    /** Ce que représente une modification, en mots simples (écran de confirmation). */
    private fun libelle(type: String, cible: String): String = when {
        cible == RobotId.BOOT_RECEIVER -> tr("Camybrith Cooking ne s'ouvrira plus au démarrage")
        cible == RobotId.HOME_ALIAS -> tr("Camybrith Cooking ne sera plus l'écran d'accueil")
        type == "composant" && RobotModel.isHardwareService(cible.substringAfter('/')) -> tr("l'appli Lidl retrouve l'accès au moteur")
        else -> "$type $cible"
    }

    /** Retour à l'appli Lidl par le module : le robot redémarre si tout va bien. */
    class Resultat(val code: Int, val texte: String)

    fun retourLidl(): Resultat {
        val r = cam("retour-lidl", timeoutMs = 120_000)
        return Resultat(r.code, (r.out + r.err).trim())
    }

    /** Identité du robot lue par le module (robot.json, research.md D6). */
    fun ecrireRobot(model: RobotModel) {
        ecrire("robot.json", RobotId(model.vendorPackage, model.hardwareServiceSuffix).toJson())
    }

    /**
     * État de cuisson lu par le module (cuisson.json, research.md D12) : écrit directement, sans
     * lancer la commande (appelé toutes les 30 s pendant un programme : il doit rester léger).
     */
    fun ecrireCuisson(occupe: Boolean, programme: String) {
        ecrire("cuisson.json", Cuisson.json(occupe, programme.take(80), Iso.format(System.currentTimeMillis())))
    }

    private fun ecrire(nom: String, texte: String) {
        RootShell.run("[ -d $DIR ] && printf '%s' ${q(texte)} > $DIR/.$nom.tmp && mv $DIR/.$nom.tmp $DIR/$nom; true", 8_000)
    }
}
