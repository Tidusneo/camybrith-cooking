package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str

/**
 * Registre des modifications faites au robot (spec 007, data-model.md › Modification, Registre).
 * Tout ce qui modifie le robot s'y inscrit AVANT d'agir ; le retour à l'appli Lidl annule les
 * modifications de portée `lidl`, dans l'ordre décroissant d'id. Liste fermée de types et de
 * formats de cible : le registre ne contient jamais de commande libre.
 */
enum class TypeModif(val cle: String) {
    COMPOSANT("composant"), REGLAGE("reglage"), DEMARRAGE("demarrage"), MODULE("module"), APPLI("appli");

    companion object { fun of(s: String?): TypeModif? = values().firstOrNull { it.cle == s } }
}

enum class Portee(val cle: String) {
    LIDL("lidl"), RETRAIT("retrait"), SOCLE("socle");

    companion object { fun of(s: String?): Portee? = values().firstOrNull { it.cle == s } }
}

enum class EtatModif(val cle: String) {
    ACTIVE("active"), ANNULEE("annulee"), ECHEC("echec");

    companion object { fun of(s: String?): EtatModif? = values().firstOrNull { it.cle == s } }
}

data class Modification(
    val id: Int,
    val type: TypeModif,
    val cible: String,
    /** État ou valeur d'avant : `enabled`, `disabled`, une valeur de réglage, ou `absent`. */
    val avant: String?,
    val apres: String,
    val portee: Portee,
    val origine: String,
    val date: String,
    val deduit: Boolean = false,
    val etat: EtatModif = EtatModif.ACTIVE,
    val erreur: String? = null,
    /** Faux entre `noter` et `noter-fin ok` pour une modification nouvelle (retirée si `echec`). */
    val confirme: Boolean = true,
) {
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id, "type" to type.cle, "cible" to cible, "avant" to avant, "apres" to apres,
        "portee" to portee.cle, "origine" to origine, "date" to date, "deduit" to deduit,
        "etat" to etat.cle, "erreur" to erreur, "confirme" to confirme,
    )

    /** Texte lisible pour les écrans et les bilans. */
    val libelle: String get() = "${type.cle} $cible ($avant → $apres)"

    companion object {
        fun fromJson(m: Map<String, Any?>): Modification? {
            val type = TypeModif.of(m.str("type")) ?: return null
            val cible = m.str("cible") ?: return null
            if (Cibles.erreur(type, cible) != null) return null
            return Modification(
                id = m.int("id") ?: return null,
                type = type,
                cible = cible,
                avant = m.str("avant"),
                apres = m.str("apres") ?: return null,
                portee = Portee.of(m.str("portee")) ?: return null,
                origine = m.str("origine") ?: "",
                date = m.str("date") ?: "",
                deduit = m.bool("deduit") ?: false,
                etat = EtatModif.of(m.str("etat")) ?: return null,
                erreur = m.str("erreur"),
                confirme = m.bool("confirme") ?: true,
            )
        }
    }
}

/** Formats de cible autorisés par type (data-model.md › Modification.cible). */
object Cibles {
    private val PAQUET = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val CLASSE = Regex("\\.?[A-Za-z][A-Za-z0-9_$]*(\\.[A-Za-z][A-Za-z0-9_$]*)*")
    private val NAMESPACES = setOf("system", "secure", "global")
    private val CLE = Regex("[A-Za-z0-9_.]{1,100}")
    private val MODULE = Regex("[A-Za-z][A-Za-z0-9._-]{0,63}")

    fun paquet(s: String): Boolean = s.length <= 200 && PAQUET.matches(s)

    /** Message d'erreur, ou null si la cible convient au type. */
    fun erreur(type: TypeModif, cible: String): String? = when (type) {
        TypeModif.COMPOSANT, TypeModif.DEMARRAGE -> {
            val i = cible.indexOf('/')
            if (i <= 0 || !paquet(cible.substring(0, i)) || !CLASSE.matches(cible.substring(i + 1)))
                "cible attendue : paquet/composant" else null
        }
        TypeModif.REGLAGE -> {
            val i = cible.indexOf('/')
            if (i <= 0 || cible.substring(0, i) !in NAMESPACES || !CLE.matches(cible.substring(i + 1)))
                "cible attendue : system|secure|global/clé" else null
        }
        TypeModif.MODULE -> if (MODULE.matches(cible)) null else "cible attendue : id de module"
        TypeModif.APPLI -> if (paquet(cible)) null else "cible attendue : nom de paquet"
    }

    /** Valeur `avant`/`apres` acceptable : pas de retour à la ligne ni de caractère de contrôle. */
    fun valeur(s: String): Boolean = s.length <= 2000 && s.none { it < ' ' }
}

class RegistreException(message: String) : Exception(message)

data class Registre(val robot: String? = null, val modifications: List<Modification> = emptyList()) {

    fun prochainId(): Int = (modifications.maxOfOrNull { it.id } ?: 0) + 1

    fun actives(portee: Portee): List<Modification> =
        modifications.filter { it.etat == EtatModif.ACTIVE && it.portee == portee }

    /**
     * Inscrit une modification (avant d'agir). Une modification active de même type et cible
     * existe déjà : elle est fusionnée (elle garde son `avant`, l'état d'origine) et son id est
     * rendu. Résultat : le registre et l'id concerné.
     */
    fun noter(
        type: TypeModif, cible: String, avant: String?, apres: String, portee: Portee,
        origine: String, date: String, deduit: Boolean = false,
    ): Pair<Registre, Int> {
        Cibles.erreur(type, cible)?.let { throw RegistreException(it) }
        if (!Cibles.valeur(apres) || (avant != null && !Cibles.valeur(avant)))
            throw RegistreException("valeur invalide")
        if (!Cibles.valeur(origine)) throw RegistreException("origine invalide")
        val existante = modifications.firstOrNull { it.etat == EtatModif.ACTIVE && it.type == type && it.cible == cible }
        if (existante != null) {
            val fusion = existante.copy(apres = apres, date = date, origine = origine)
            return copy(modifications = modifications.map { if (it.id == existante.id) fusion else it }) to existante.id
        }
        val m = Modification(prochainId(), type, cible, avant, apres, portee, origine, date, deduit,
            confirme = deduit)
        return copy(modifications = modifications + m) to m.id
    }

    /** Après la tentative : `ok` confirme ; `echec` retire une modification nouvelle. */
    fun noterFin(id: Int, ok: Boolean): Registre {
        val m = modifications.firstOrNull { it.id == id } ?: throw RegistreException("id inconnu : $id")
        return when {
            ok -> copy(modifications = modifications.map { if (it.id == id) it.copy(confirme = true) else it })
            !m.confirme -> copy(modifications = modifications.filter { it.id != id })
            else -> this // modification fusionnée : l'état d'origine reste celui de la première
        }
    }

    /** Plan d'annulation d'une portée : modifications actives, ordre décroissant d'id. */
    fun planAnnulation(portee: Portee): List<Modification> = actives(portee).sortedByDescending { it.id }

    /**
     * Plan de réapplication (reprise de Camybrith) : modifications de portée `lidl` annulées par le
     * dernier retour, ordre croissant d'id. [annuleesParDernierRetour] vient du dernier bilan.
     */
    fun planReapplication(annuleesParDernierRetour: Collection<Int>): List<Modification> =
        modifications.filter { it.id in annuleesParDernierRetour && it.portee == Portee.LIDL && it.etat == EtatModif.ANNULEE }
            .sortedBy { it.id }

    fun marquer(id: Int, etat: EtatModif, erreur: String? = null): Registre =
        copy(modifications = modifications.map { if (it.id == id) it.copy(etat = etat, erreur = erreur) else it })

    fun toJson(): String = Json.write(
        linkedMapOf("format" to FORMAT, "robot" to robot, "modifications" to modifications.map { it.toJson() }),
        pretty = true,
    )

    companion object {
        const val FORMAT = 1

        /** Lecture stricte : format inconnu ou élément invalide → exception (état inattendu). */
        fun parse(text: String): Registre {
            val root = try { Json.parse(text).obj() } catch (e: Json.ParseException) {
                throw RegistreException("registre illisible : ${e.message}")
            }
            if (root.int("format") != FORMAT) throw RegistreException("format de registre inconnu : ${root["format"]}")
            val mods = root["modifications"].list().map {
                Modification.fromJson(it.obj()) ?: throw RegistreException("élément de registre invalide : ${Json.write(it)}")
            }
            if (mods.map { it.id }.toSet().size != mods.size) throw RegistreException("id en double dans le registre")
            return Registre(root.str("robot"), mods)
        }
    }
}

/** Action concrète d'annulation d'une modification (aucune commande libre : construite ici). */
object Annulation {
    /** Commande à exécuter (liste d'arguments) pour remettre l'état d'avant. */
    fun commande(m: Modification): List<String> = when (m.type) {
        TypeModif.COMPOSANT, TypeModif.DEMARRAGE ->
            listOf("pm", if (m.avant == "enabled") "enable" else "disable", m.cible)
        TypeModif.REGLAGE -> {
            val (ns, cle) = m.cible.split('/', limit = 2)
            if (m.avant == null || m.avant == ABSENT) listOf("settings", "delete", ns, cle)
            else listOf("settings", "put", ns, cle, m.avant)
        }
        TypeModif.MODULE -> listOf("touch", "/data/adb/modules/${m.cible}/remove")
        TypeModif.APPLI -> if (m.avant == null || m.avant == ABSENT) listOf("pm", "uninstall", m.cible)
        else listOf("true") // appli déjà là avant : rien à défaire
    }

    /** Commande qui remet l'état `apres` (reprise de Camybrith). */
    fun reapplication(m: Modification): List<String> = when (m.type) {
        TypeModif.COMPOSANT, TypeModif.DEMARRAGE ->
            listOf("pm", if (m.apres == "enabled") "enable" else "disable", m.cible)
        TypeModif.REGLAGE -> {
            val (ns, cle) = m.cible.split('/', limit = 2)
            if (m.apres == ABSENT) listOf("settings", "delete", ns, cle) else listOf("settings", "put", ns, cle, m.apres)
        }
        TypeModif.MODULE, TypeModif.APPLI -> listOf("true")
    }

    const val ABSENT = "absent"
}
