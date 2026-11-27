package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str

/** Where an operation was started from (data-model.md › Journal.origine, Mode du robot.origine). */
enum class Origin(val key: String) {
    INTERFACE("interface"), COMPUTER("ordinateur"), RESCUE("secours"), GESTURE("geste");

    companion object { fun of(s: String?): Origin? = values().firstOrNull { it.key == s } }
}

enum class OperationType(val key: String, val steps: List<Step>) {
    BACK_TO_LIDL("retour-lidl", listOf(Step.START, Step.STOP, Step.UNDO, Step.CHECK, Step.DONE)),
    RESUME("reprise", listOf(Step.START, Step.REAPPLY, Step.CHECK, Step.DONE)),
    REMOVAL("retrait", listOf(Step.START, Step.STOP, Step.UNDO, Step.CHECK, Step.DONE));

    companion object { fun of(s: String?): OperationType? = values().firstOrNull { it.key == s } }
}

enum class Step(val key: String) {
    START("debut"), STOP("arret"), UNDO("annulation"), REAPPLY("reapplication"), CHECK("verification"), DONE("termine");

    companion object { fun of(s: String?): Step? = values().firstOrNull { it.key == s } }
}

/** Operation journal (`op.json`), present only while an operation runs. */
data class Journal(val type: OperationType, val origin: Origin, val step: Step = Step.START, val date: String = "") {

    /** Next step, in the order defined for this type; any other transition is refused. */
    fun next(step: Step): Journal {
        val i = type.steps.indexOf(this.step)
        val j = type.steps.indexOf(step)
        if (j < 0 || j != i + 1) throw RegistryException("transition refusée : ${this.step.key} → ${step.key} (${type.key})")
        return copy(step = step)
    }

    fun toJson(): String = Json.write(linkedMapOf("type" to type.key, "origine" to origin.key, "etape" to step.key, "date" to date))

    /**
     * What to do at boot if this journal is still there (data-model.md › Journal d'opération ›
     * Au démarrage): an interrupted return or removal is FINISHED; an interrupted resume is
     * abandoned towards the Lidl app (the safe direction).
     */
    fun bootAction(): BootAction = when {
        step == Step.DONE -> BootAction.CLEAR
        type == OperationType.RESUME -> BootAction.BACK_TO_LIDL
        type == OperationType.REMOVAL -> BootAction.FINISH_REMOVAL
        else -> BootAction.FINISH_RETURN
    }

    companion object {
        fun parse(text: String): Journal {
            val m = try { Json.parse(text).obj() } catch (e: Json.ParseException) {
                throw RegistryException("journal illisible : ${e.message}")
            }
            return Journal(
                OperationType.of(m.str("type")) ?: throw RegistryException("type d'opération inconnu"),
                Origin.of(m.str("origine")) ?: throw RegistryException("origine inconnue"),
                Step.of(m.str("etape")) ?: throw RegistryException("étape inconnue"),
                m.str("date") ?: "",
            )
        }
    }
}

enum class BootAction { CLEAR, FINISH_RETURN, FINISH_REMOVAL, BACK_TO_LIDL }

/** Result of one check of the final verification (research.md D6). Name and detail are French (shown). */
data class Check(val name: String, val ok: Boolean, val detail: String = "") {
    fun toJson(): Map<String, Any?> = linkedMapOf("nom" to name, "ok" to ok, "detail" to detail)

    companion object {
        fun fromJson(m: Map<String, Any?>) = Check(m.str("nom") ?: "", m.bool("ok") ?: false, m.str("detail") ?: "")
    }
}

/** Return report (data-model.md › Bilan de retour). */
data class Report(
    val type: OperationType,
    val date: String,
    val origin: Origin,
    val undone: List<Change> = emptyList(),
    /** id → reason. */
    val failures: Map<Int, String> = emptyMap(),
    val checks: List<Check> = emptyList(),
    /** Guarantees applied outside the registry (an essential change missing from it). */
    val fixes: List<String> = emptyList(),
    val storageFull: Boolean = false,
    /** Check made by `service.sh` after the restart (Lidl app in the foreground). */
    val afterBoot: Check? = null,
) {
    val succeeded: Boolean get() = failures.isEmpty() && checks.all { it.ok }

    fun toJson(): String = Json.write(linkedMapOf(
        "type" to type.key, "date" to date, "origine" to origin.key,
        "annulees" to undone.map { linkedMapOf("id" to it.id, "type" to it.type.key, "cible" to it.target) },
        "echecs" to failures.map { (id, reason) -> linkedMapOf("id" to id, "erreur" to reason) },
        "controles" to checks.map { it.toJson() },
        "corrections" to fixes,
        "memoirePleine" to storageFull,
        "apresDemarrage" to afterBoot?.toJson(),
        "reussi" to succeeded,
        "rappel" to REMINDER,
    ), pretty = true)

    /** Readable text, in French (command `camybrith bilan`, computer script). */
    fun text(): String = buildString {
        appendLine("Bilan : ${type.key} du $date (origine : ${origin.key})")
        appendLine(if (succeeded) "Résultat : réussi" else "Résultat : À VÉRIFIER")
        val what = if (type == OperationType.RESUME) "Modifications remises" else "Modifications annulées"
        if (undone.isEmpty()) appendLine("$what : aucune")
        else {
            appendLine("$what :")
            undone.forEach { appendLine("  ${it.id}. ${it.type.key} ${it.target}") }
        }
        failures.forEach { (id, reason) -> appendLine("  ÉCHEC $id : $reason") }
        fixes.forEach { appendLine("  Corrigé hors registre : $it") }
        appendLine("Vérifications :")
        checks.forEach { appendLine("  ${if (it.ok) "ok " else "NON"} ${it.name}${if (it.detail.isNotEmpty()) " (${it.detail})" else ""}") }
        afterBoot?.let { appendLine("Après redémarrage : ${if (it.ok) "ok" else "NON"} ${it.name} ${it.detail}".trimEnd()) }
        if (storageFull) appendLine("Mémoire pleine pendant l'opération : certaines traces n'ont pas pu être écrites.")
        append("Rappel : $REMINDER")
    }

    companion object {
        /** FR-009, shown to the user. */
        const val REMINDER = "Ne jamais accepter de mise à jour système Lidl tant que le robot est rooté."
        const val KEPT = 10

        fun parse(text: String): Report {
            val m = try { Json.parse(text).obj() } catch (e: Json.ParseException) {
                throw RegistryException("bilan illisible : ${e.message}")
            }
            return Report(
                type = OperationType.of(m.str("type")) ?: throw RegistryException("bilan : type inconnu"),
                date = m.str("date") ?: "",
                origin = Origin.of(m.str("origine")) ?: throw RegistryException("bilan : origine inconnue"),
                undone = m["annulees"].list().mapNotNull { e ->
                    val o = e.obj()
                    val type = ChangeType.of(o.str("type")) ?: return@mapNotNull null
                    Change(o.int("id") ?: return@mapNotNull null, type, o.str("cible") ?: "", null, "", Scope.LIDL, "", "")
                },
                failures = m["echecs"].list().mapNotNull { e -> e.obj().let { o -> o.int("id")?.let { it to (o.str("erreur") ?: "") } } }.toMap(),
                checks = m["controles"].list().map { Check.fromJson(it.obj()) },
                fixes = m["corrections"].list().mapNotNull { it as? String },
                storageFull = m.bool("memoirePleine") ?: false,
                afterBoot = m["apresDemarrage"]?.let { Check.fromJson(it.obj()) },
            )
        }

        /** Report file name: `AAAAMMJJ-HHMMSS.json` from an ISO date. */
        fun fileName(isoDate: String): String =
            isoDate.take(19).replace("-", "").replace(":", "").replace("T", "-") + ".json"

        /** Reports to delete to keep only [KEPT] (names sort by date). */
        fun toDelete(names: Collection<String>): List<String> =
            names.filter { it.endsWith(".json") }.sorted().dropLast(KEPT)
    }
}
