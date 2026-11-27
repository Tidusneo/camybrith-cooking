package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str

/** Origine d'une opération (data-model.md › Journal.origine, Mode du robot.origine). */
enum class Origine(val cle: String) {
    INTERFACE("interface"), ORDINATEUR("ordinateur"), SECOURS("secours"), GESTE("geste");

    companion object { fun of(s: String?): Origine? = values().firstOrNull { it.cle == s } }
}

enum class TypeOperation(val cle: String, val etapes: List<Etape>) {
    RETOUR_LIDL("retour-lidl", listOf(Etape.DEBUT, Etape.ARRET, Etape.ANNULATION, Etape.VERIFICATION, Etape.TERMINE)),
    REPRISE("reprise", listOf(Etape.DEBUT, Etape.REAPPLICATION, Etape.VERIFICATION, Etape.TERMINE)),
    RETRAIT("retrait", listOf(Etape.DEBUT, Etape.ARRET, Etape.ANNULATION, Etape.VERIFICATION, Etape.TERMINE));

    companion object { fun of(s: String?): TypeOperation? = values().firstOrNull { it.cle == s } }
}

enum class Etape(val cle: String) {
    DEBUT("debut"), ARRET("arret"), ANNULATION("annulation"), REAPPLICATION("reapplication"),
    VERIFICATION("verification"), TERMINE("termine");

    companion object { fun of(s: String?): Etape? = values().firstOrNull { it.cle == s } }
}

/** Journal d'opération (`op.json`), présent seulement pendant une opération. */
data class Journal(val type: TypeOperation, val origine: Origine, val etape: Etape = Etape.DEBUT, val date: String = "") {

    /** Étape suivante, dans l'ordre défini pour ce type ; toute autre transition est refusée. */
    fun vers(suivante: Etape): Journal {
        val i = type.etapes.indexOf(etape)
        val j = type.etapes.indexOf(suivante)
        if (j < 0 || j != i + 1) throw RegistreException("transition refusée : ${etape.cle} → ${suivante.cle} (${type.cle})")
        return copy(etape = suivante)
    }

    fun toJson(): String = Json.write(linkedMapOf("type" to type.cle, "origine" to origine.cle, "etape" to etape.cle, "date" to date))

    /**
     * Ce qu'il faut faire au démarrage si ce journal est encore là (data-model.md › Journal
     * d'opération › Au démarrage) : un retour ou un retrait interrompu est TERMINÉ ; une reprise
     * interrompue est abandonnée vers l'appli Lidl (le sens sûr).
     */
    fun repriseAuDemarrage(): ActionDemarrage = when {
        etape == Etape.TERMINE -> ActionDemarrage.EFFACER
        type == TypeOperation.REPRISE -> ActionDemarrage.RETOUR_LIDL
        type == TypeOperation.RETRAIT -> ActionDemarrage.TERMINER_RETRAIT
        else -> ActionDemarrage.TERMINER_RETOUR
    }

    companion object {
        fun parse(text: String): Journal {
            val m = try { Json.parse(text).obj() } catch (e: Json.ParseException) {
                throw RegistreException("journal illisible : ${e.message}")
            }
            return Journal(
                TypeOperation.of(m.str("type")) ?: throw RegistreException("type d'opération inconnu"),
                Origine.of(m.str("origine")) ?: throw RegistreException("origine inconnue"),
                Etape.of(m.str("etape")) ?: throw RegistreException("étape inconnue"),
                m.str("date") ?: "",
            )
        }
    }
}

enum class ActionDemarrage { EFFACER, TERMINER_RETOUR, TERMINER_RETRAIT, RETOUR_LIDL }

/** Résultat d'un contrôle de la vérification finale (research.md D6). */
data class Controle(val nom: String, val ok: Boolean, val detail: String = "") {
    fun toJson(): Map<String, Any?> = linkedMapOf("nom" to nom, "ok" to ok, "detail" to detail)

    companion object {
        fun fromJson(m: Map<String, Any?>) = Controle(m.str("nom") ?: "", m.bool("ok") ?: false, m.str("detail") ?: "")
    }
}

/** Bilan de retour (data-model.md › Bilan de retour). */
data class Bilan(
    val type: TypeOperation,
    val date: String,
    val origine: Origine,
    val annulees: List<Modification> = emptyList(),
    /** id → raison. */
    val echecs: Map<Int, String> = emptyMap(),
    val controles: List<Controle> = emptyList(),
    /** Garanties appliquées hors registre (modification essentielle absente du registre). */
    val corrections: List<String> = emptyList(),
    val memoirePleine: Boolean = false,
    /** Contrôle fait par `service.sh` après le redémarrage (appli Lidl au premier plan). */
    val apresDemarrage: Controle? = null,
) {
    val reussi: Boolean get() = echecs.isEmpty() && controles.all { it.ok }

    fun toJson(): String = Json.write(linkedMapOf(
        "type" to type.cle, "date" to date, "origine" to origine.cle,
        "annulees" to annulees.map { linkedMapOf("id" to it.id, "type" to it.type.cle, "cible" to it.cible) },
        "echecs" to echecs.map { (id, raison) -> linkedMapOf("id" to id, "erreur" to raison) },
        "controles" to controles.map { it.toJson() },
        "corrections" to corrections,
        "memoirePleine" to memoirePleine,
        "apresDemarrage" to apresDemarrage?.toJson(),
        "reussi" to reussi,
        "rappel" to RAPPEL,
    ), pretty = true)

    /** Texte lisible (commande `camybrith bilan`, script Mac). */
    fun texte(): String = buildString {
        appendLine("Bilan : ${type.cle} du $date (origine : ${origine.cle})")
        appendLine(if (reussi) "Résultat : réussi" else "Résultat : À VÉRIFIER")
        if (annulees.isEmpty()) appendLine("Modifications annulées : aucune")
        else {
            appendLine("Modifications annulées :")
            annulees.forEach { appendLine("  ${it.id}. ${it.type.cle} ${it.cible}") }
        }
        echecs.forEach { (id, raison) -> appendLine("  ÉCHEC $id : $raison") }
        corrections.forEach { appendLine("  Corrigé hors registre : $it") }
        appendLine("Vérifications :")
        controles.forEach { appendLine("  ${if (it.ok) "ok " else "NON"} ${it.nom}${if (it.detail.isNotEmpty()) " (${it.detail})" else ""}") }
        apresDemarrage?.let { appendLine("Après redémarrage : ${if (it.ok) "ok" else "NON"} ${it.nom} ${it.detail}".trimEnd()) }
        if (memoirePleine) appendLine("Mémoire pleine pendant l'opération : certaines traces n'ont pas pu être écrites.")
        append("Rappel : $RAPPEL")
    }

    companion object {
        const val RAPPEL = "Ne jamais accepter de mise à jour système Lidl tant que le robot est rooté."
        const val GARDES = 10

        fun parse(text: String): Bilan {
            val m = try { Json.parse(text).obj() } catch (e: Json.ParseException) {
                throw RegistreException("bilan illisible : ${e.message}")
            }
            return Bilan(
                type = TypeOperation.of(m.str("type")) ?: throw RegistreException("bilan : type inconnu"),
                date = m.str("date") ?: "",
                origine = Origine.of(m.str("origine")) ?: throw RegistreException("bilan : origine inconnue"),
                annulees = m["annulees"].list().mapNotNull { e ->
                    val o = e.obj()
                    val type = TypeModif.of(o.str("type")) ?: return@mapNotNull null
                    Modification(o.int("id") ?: return@mapNotNull null, type, o.str("cible") ?: "", null, "",
                        Portee.LIDL, "", "")
                },
                echecs = m["echecs"].list().mapNotNull { e -> e.obj().let { o -> o.int("id")?.let { it to (o.str("erreur") ?: "") } } }.toMap(),
                controles = m["controles"].list().map { Controle.fromJson(it.obj()) },
                corrections = m["corrections"].list().mapNotNull { it as? String },
                memoirePleine = m.bool("memoirePleine") ?: false,
                apresDemarrage = m["apresDemarrage"]?.let { Controle.fromJson(it.obj()) },
            )
        }

        /** Nom de fichier d'un bilan : `AAAAMMJJ-HHMMSS.json` à partir d'une date ISO. */
        fun nomFichier(dateIso: String): String =
            dateIso.take(19).replace("-", "").replace(":", "").replace("T", "-") + ".json"

        /** Bilans à supprimer pour n'en garder que [GARDES] (noms triables par date). */
        fun aSupprimer(noms: Collection<String>): List<String> =
            noms.filter { it.endsWith(".json") }.sorted().dropLast(GARDES)
    }
}
