package app.toque.core.backup

import app.toque.core.json.Json
import app.toque.core.json.bool
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str

/**
 * Registry of every change made to the robot (spec 007, data-model.md › Modification, Registre).
 * Anything that modifies the robot is recorded here BEFORE acting; going back to the Lidl app
 * undoes the `lidl`-scoped changes, by decreasing id. Closed list of types and target formats:
 * the registry never holds a free-form command.
 *
 * On-disk keys and values are the French ones defined by data-model.md (they are a contract with
 * the shell scripts and the specs); only the Kotlin names are in English.
 */
enum class ChangeType(val key: String) {
    COMPONENT("composant"), SETTING("reglage"), STARTUP("demarrage"), MODULE("module"), APP("appli");

    companion object { fun of(s: String?): ChangeType? = values().firstOrNull { it.key == s } }
}

enum class Scope(val key: String) {
    /** Undone when going back to the Lidl app. */
    LIDL("lidl"),
    /** Undone only by a full removal of Camybrith. */
    REMOVAL("retrait"),
    /** Never undone (root and Wi-Fi access stay). */
    BASE("socle");

    companion object { fun of(s: String?): Scope? = values().firstOrNull { it.key == s } }
}

enum class ChangeState(val key: String) {
    ACTIVE("active"), UNDONE("annulee"), FAILED("echec");

    companion object { fun of(s: String?): ChangeState? = values().firstOrNull { it.key == s } }
}

data class Change(
    val id: Int,
    val type: ChangeType,
    val target: String,
    /** State or value before: `enabled`, `disabled`, a setting value, or `absent`. */
    val before: String?,
    val after: String,
    val scope: Scope,
    val origin: String,
    val date: String,
    val inferred: Boolean = false,
    val state: ChangeState = ChangeState.ACTIVE,
    val error: String? = null,
    /** False between `noter` and `noter-fin ok` for a new change (removed on `echec`). */
    val confirmed: Boolean = true,
) {
    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id, "type" to type.key, "cible" to target, "avant" to before, "apres" to after,
        "portee" to scope.key, "origine" to origin, "date" to date, "deduit" to inferred,
        "etat" to state.key, "erreur" to error, "confirme" to confirmed,
    )

    /** Readable text for screens and reports. */
    val label: String get() = "${type.key} $target ($before → $after)"

    companion object {
        fun fromJson(m: Map<String, Any?>): Change? {
            val type = ChangeType.of(m.str("type")) ?: return null
            val target = m.str("cible") ?: return null
            if (Targets.error(type, target) != null) return null
            return Change(
                id = m.int("id") ?: return null,
                type = type,
                target = target,
                before = m.str("avant"),
                after = m.str("apres") ?: return null,
                scope = Scope.of(m.str("portee")) ?: return null,
                origin = m.str("origine") ?: "",
                date = m.str("date") ?: "",
                inferred = m.bool("deduit") ?: false,
                state = ChangeState.of(m.str("etat")) ?: return null,
                error = m.str("erreur"),
                confirmed = m.bool("confirme") ?: true,
            )
        }
    }
}

/** Allowed target formats per type (data-model.md › Modification.cible). */
object Targets {
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    private val CLASS = Regex("\\.?[A-Za-z][A-Za-z0-9_$]*(\\.[A-Za-z][A-Za-z0-9_$]*)*")
    private val NAMESPACES = setOf("system", "secure", "global")
    private val KEY = Regex("[A-Za-z0-9_.]{1,100}")
    private val MODULE = Regex("[A-Za-z][A-Za-z0-9._-]{0,63}")

    fun isPackage(s: String): Boolean = s.length <= 200 && PACKAGE.matches(s)

    /** Error message (French, shown to the user), or null if the target fits the type. */
    fun error(type: ChangeType, target: String): String? = when (type) {
        ChangeType.COMPONENT, ChangeType.STARTUP -> {
            val i = target.indexOf('/')
            if (i <= 0 || !isPackage(target.substring(0, i)) || !CLASS.matches(target.substring(i + 1)))
                "cible attendue : paquet/composant" else null
        }
        ChangeType.SETTING -> {
            val i = target.indexOf('/')
            if (i <= 0 || target.substring(0, i) !in NAMESPACES || !KEY.matches(target.substring(i + 1)))
                "cible attendue : system|secure|global/clé" else null
        }
        ChangeType.MODULE -> if (MODULE.matches(target)) null else "cible attendue : id de module"
        ChangeType.APP -> if (isPackage(target)) null else "cible attendue : nom de paquet"
    }

    /** Acceptable `before`/`after` value: no line break or control character. */
    fun isValue(s: String): Boolean = s.length <= 2000 && s.none { it < ' ' }
}

class RegistryException(message: String) : Exception(message)

data class Registry(val robot: String? = null, val changes: List<Change> = emptyList()) {

    fun nextId(): Int = (changes.maxOfOrNull { it.id } ?: 0) + 1

    fun active(scope: Scope): List<Change> = changes.filter { it.state == ChangeState.ACTIVE && it.scope == scope }

    /**
     * Records a change (before acting). If an active change with the same type and target already
     * exists, it is merged (it keeps its `before`, the original state) and its id is returned.
     * Result: the new registry and the id concerned.
     */
    fun note(
        type: ChangeType, target: String, before: String?, after: String, scope: Scope,
        origin: String, date: String, inferred: Boolean = false,
    ): Pair<Registry, Int> {
        Targets.error(type, target)?.let { throw RegistryException(it) }
        if (!Targets.isValue(after) || (before != null && !Targets.isValue(before)))
            throw RegistryException("valeur invalide")
        if (!Targets.isValue(origin)) throw RegistryException("origine invalide")
        val existing = changes.firstOrNull { it.state == ChangeState.ACTIVE && it.type == type && it.target == target }
        if (existing != null) {
            val merged = existing.copy(after = after, date = date, origin = origin)
            return copy(changes = changes.map { if (it.id == existing.id) merged else it }) to existing.id
        }
        val c = Change(nextId(), type, target, before, after, scope, origin, date, inferred, confirmed = inferred)
        return copy(changes = changes + c) to c.id
    }

    /** After the attempt: `ok` confirms; a failure removes a new change. */
    fun noteDone(id: Int, ok: Boolean): Registry {
        val c = changes.firstOrNull { it.id == id } ?: throw RegistryException("id inconnu : $id")
        return when {
            ok -> copy(changes = changes.map { if (it.id == id) it.copy(confirmed = true) else it })
            !c.confirmed -> copy(changes = changes.filter { it.id != id })
            else -> this // merged change: the original state stays the one of the first record
        }
    }

    /** Undo plan for a scope: active changes, decreasing id. */
    fun undoPlan(scope: Scope): List<Change> = active(scope).sortedByDescending { it.id }

    /**
     * Reapply plan (resuming Camybrith): `lidl` changes undone by the last return, increasing id.
     * [undoneByLastReturn] comes from the last report.
     */
    fun reapplyPlan(undoneByLastReturn: Collection<Int>): List<Change> =
        changes.filter { it.id in undoneByLastReturn && it.scope == Scope.LIDL && it.state == ChangeState.UNDONE }
            .sortedBy { it.id }

    fun mark(id: Int, state: ChangeState, error: String? = null): Registry =
        copy(changes = changes.map { if (it.id == id) it.copy(state = state, error = error) else it })

    fun toJson(): String = Json.write(
        linkedMapOf("format" to FORMAT, "robot" to robot, "modifications" to changes.map { it.toJson() }),
        pretty = true,
    )

    companion object {
        const val FORMAT = 1

        /** Strict reading: unknown format or invalid entry → exception (unexpected state). */
        fun parse(text: String): Registry {
            val root = try { Json.parse(text).obj() } catch (e: Json.ParseException) {
                throw RegistryException("registre illisible : ${e.message}")
            }
            if (root.int("format") != FORMAT) throw RegistryException("format de registre inconnu : ${root["format"]}")
            val changes = root["modifications"].list().map {
                Change.fromJson(it.obj()) ?: throw RegistryException("élément de registre invalide : ${Json.write(it)}")
            }
            if (changes.map { it.id }.toSet().size != changes.size) throw RegistryException("id en double dans le registre")
            return Registry(root.str("robot"), changes)
        }
    }
}

/** Concrete command that undoes or reapplies a change (never free-form: built here). */
object Undo {
    const val ABSENT = "absent"

    /** Command (argument list) that restores the `before` state. */
    fun command(c: Change): List<String> = when (c.type) {
        ChangeType.COMPONENT, ChangeType.STARTUP ->
            listOf("pm", if (c.before == "enabled") "enable" else "disable", c.target)
        ChangeType.SETTING -> {
            val (ns, key) = c.target.split('/', limit = 2)
            if (c.before == null || c.before == ABSENT) listOf("settings", "delete", ns, key)
            else listOf("settings", "put", ns, key, c.before)
        }
        ChangeType.MODULE -> listOf("touch", "/data/adb/modules/${c.target}/remove")
        ChangeType.APP -> if (c.before == null || c.before == ABSENT) listOf("pm", "uninstall", c.target)
        else listOf("true") // the app was already there before: nothing to undo
    }

    /** Command that restores the `after` state (resuming Camybrith). */
    fun reapply(c: Change): List<String> = when (c.type) {
        ChangeType.COMPONENT, ChangeType.STARTUP ->
            listOf("pm", if (c.after == "enabled") "enable" else "disable", c.target)
        ChangeType.SETTING -> {
            val (ns, key) = c.target.split('/', limit = 2)
            if (c.after == ABSENT) listOf("settings", "delete", ns, key) else listOf("settings", "put", ns, key, c.after)
        }
        ChangeType.MODULE, ChangeType.APP -> listOf("true")
    }
}
