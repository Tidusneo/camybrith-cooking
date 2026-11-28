package app.toque.editeur

import app.toque.core.json.Json
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.recipes.Cookidoo
import java.io.File
import java.text.Normalizer

/**
 * Recettes de l'éditeur : un fichier data.json au format documenté (docs/RECIPES.en.md) et un
 * dossier images/ (une photo JPEG par recette), dans [dir]. Au démarrage, les fichiers déjà
 * déposés dans le dossier d'import de l'appli ([importDir], renommés .importe une fois importés)
 * plus récents que la dernière lecture y sont repris : les recettes envoyées par adb restent
 * modifiables. Les champs que l'éditeur ne connaît pas sont gardés tels quels.
 */
class RecipeStore(private val dir: File, private val importDir: File? = null, private val clock: () -> Long = System::currentTimeMillis) {
    private val file = File(dir, "data.json")
    private val marker = File(dir, ".lu")
    val imagesDir: File get() = File(dir, "images").apply { mkdirs() }

    /** Entrées data.json par identifiant, dans l'ordre du fichier. */
    private val entries = LinkedHashMap<String, Map<String, Any?>>()

    @Synchronized
    fun load() {
        dir.mkdirs()
        entries.clear()
        if (file.isFile) for (e in Json.parse(file.readText()).list()) add(e.obj())
        if (mergeImports()) write()
    }

    private fun add(e: Map<String, Any?>) {
        val id = e.str("id")?.trim().orEmpty()
        if (id.isNotEmpty()) entries[id] = e
    }

    /** Reprend les fichiers du dossier d'import modifiés depuis la dernière lecture. */
    private fun mergeImports(): Boolean {
        val src = importDir ?: return false
        val since = if (marker.isFile) marker.lastModified() else 0L
        val files = src.listFiles { f -> f.isFile && (f.name.endsWith(".json", true) || f.name.endsWith(".json.importe", true)) && f.lastModified() > since }
            ?.sortedBy { it.lastModified() }.orEmpty()
        var changed = false
        for (f in files) {
            val root = try { Json.parse(f.readText()) } catch (_: Exception) { continue }
            if (!Cookidoo.isExport(root)) continue
            for (e in root.list()) {
                val id = e.obj().str("id")?.trim().orEmpty()
                if (id.isEmpty()) continue
                add(e.obj())
                val photo = File(File(src, "images"), "$id.jpg")
                if (safeId(id) && photo.isFile) photo.copyTo(File(imagesDir, "$id.jpg"), overwrite = true)
                changed = true
            }
        }
        if (files.isNotEmpty()) {
            if (!marker.exists()) marker.createNewFile()
            marker.setLastModified(maxOf(clock(), files.maxOf { it.lastModified() }))
        }
        return changed
    }

    @Synchronized
    fun list(): List<EditorRecipe> = entries.values.map { decode(it) }

    @Synchronized
    fun get(id: String): EditorRecipe? = entries[id]?.let { decode(it) }

    /** Entrée data.json de la recette (pour l'importeur). */
    @Synchronized
    fun entry(id: String): Map<String, Any?>? = entries[id]

    /** Enregistre (nouvel identifiant si [EditorRecipe.id] est vide) et renvoie la recette relue. */
    @Synchronized
    fun save(r: EditorRecipe): EditorRecipe {
        val id = r.id.ifBlank { newId(r.title) }
        require(safeId(id)) { "identifiant invalide" }
        entries[id] = encode(r.copy(id = id, updated = clock()), entries[id])
        write()
        return decode(entries[id]!!)
    }

    /** Entrée data.json d'un brouillon, sans l'enregistrer (aperçu de la conversion). */
    @Synchronized
    fun entryOf(r: EditorRecipe): Map<String, Any?> = encode(r.copy(id = r.id.ifBlank { "apercu" }), entries[r.id])

    @Synchronized
    fun delete(id: String): Boolean {
        if (entries.remove(id) == null) return false
        write()
        imageFile(id)?.delete()
        return true
    }

    fun imageFile(id: String): File? = if (safeId(id)) File(imagesDir, "$id.jpg").takeIf { it.isFile } else null

    @Synchronized
    fun setImage(id: String, bytes: ByteArray) {
        require(safeId(id) && entries.containsKey(id)) { "recette inconnue" }
        val tmp = File(imagesDir, "$id.jpg.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(File(imagesDir, "$id.jpg"))) { tmp.copyTo(File(imagesDir, "$id.jpg"), true); tmp.delete() }
    }

    private fun write() {
        val tmp = File(dir, "data.json.tmp")
        tmp.writeText(Json.write(entries.values.toList(), pretty = true))
        if (!tmp.renameTo(file)) { tmp.copyTo(file, true); tmp.delete() }
    }

    private fun newId(title: String): String {
        val base = Normalizer.normalize(title, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trim('-').ifEmpty { "recette" }
        var id = base
        var n = 2
        while (entries.containsKey(id)) id = "$base-${n++}"
        return id
    }

    // ------------------------------------------------------------------ data.json ⇄ éditeur

    private fun decode(e: Map<String, Any?>): EditorRecipe {
        val schema = e["schema"].obj()
        val sections = ArrayList<EditorSection>()
        var loose = ArrayList<EditorStep>()
        fun flushLoose() {
            if (loose.isNotEmpty()) sections += EditorSection("", loose)
            loose = ArrayList()
        }
        for (s in schema["recipeInstructions"].list()) {
            when (s) {
                is String -> loose += EditorStep.fromText(s)
                is Map<*, *> -> {
                    val o = s.obj()
                    val items = o["itemListElement"].list()
                    if (o.str("@type") == "HowToSection" || items.isNotEmpty()) {
                        flushLoose()
                        sections += EditorSection(o.str("name").orEmpty(), items.mapNotNull { stepOf(it) })
                    } else stepOf(o)?.let { loose += it }
                }
            }
        }
        flushLoose()
        val id = e.str("id").orEmpty()
        return EditorRecipe(
            id = id,
            title = e.str("title") ?: schema.str("name").orEmpty(),
            author = e.str("author").orEmpty(),
            servings = Regex("\\d+").find(schema.str("recipeYield").orEmpty())?.value?.toIntOrNull() ?: 0,
            prepMinutes = Cookidoo.minutes(schema.str("prepTime")),
            totalMinutes = Cookidoo.minutes(schema.str("totalTime")),
            category = when (val c = schema["recipeCategory"]) {
                is List<*> -> c.firstOrNull()?.toString().orEmpty()
                null -> ""
                else -> c.toString()
            },
            tags = e["tags"].list().mapNotNull { it as? String },
            ingredients = e["ingredients"].list().mapNotNull { it as? String }.ifEmpty { schema["recipeIngredient"].list().mapNotNull { it as? String } },
            sections = sections.ifEmpty { listOf(EditorSection()) },
            hasImage = imageFile(id) != null,
            updated = (e["updated"] as? Number)?.toLong() ?: 0L,
        )
    }

    private fun stepOf(s: Any?): EditorStep? {
        if (s is String) return EditorStep.fromText(s)
        val o = (s as? Map<*, *>)?.obj() ?: return null
        val text = o.str("text").orEmpty()
        val machine = (o["machine"] as? Map<*, *>)?.obj()?.let { m ->
            val program = Program.of(m.str("program")) ?: return@let null
            Machine(
                program = program,
                seconds = (m["seconds"] as? Number)?.toInt() ?: 0,
                speed = (m["speed"] as? Number)?.toInt() ?: 0,
                tempC = (m["tempC"] as? Number)?.toInt() ?: 0,
                reverse = m["reverse"] == true,
                whisk = m.str("accessory").equals("whisk", true),
                pulses = (m["pulses"] as? Number)?.toInt() ?: 0,
            )
        }
        val weigh = (o["weigh"] as? Map<*, *>)?.obj()?.let { w ->
            Weighing(w.str("label").orEmpty(), (w["grams"] as? Number)?.toInt() ?: 0)
        }
        return when {
            machine != null -> EditorStep(text.trim(), machine = machine)
            weigh != null -> EditorStep(text.trim(), weigh = weigh)
            else -> EditorStep.fromText(text)
        }
    }

    private fun stepEntry(s: EditorStep): Map<String, Any?>? {
        val text = s.text.trim()
        val out = linkedMapOf<String, Any?>("@type" to "HowToStep", "text" to text)
        when (s.kind) {
            StepKind.MACHINE -> {
                val m = Rules.normalize(s.machine!!)
                out["machine"] = linkedMapOf(
                    "program" to m.program.key, "seconds" to m.seconds, "speed" to m.speed, "tempC" to m.tempC,
                    "reverse" to m.reverse, "accessory" to if (m.whisk) "whisk" else "blade", "pulses" to m.pulses,
                )
            }
            StepKind.WEIGH -> {
                val w = s.weigh!!
                out["weigh"] = linkedMapOf("label" to w.label.trim(), "grams" to w.grams.coerceAtLeast(0))
            }
            else -> if (text.isEmpty()) return null
        }
        return out
    }

    private fun encode(r: EditorRecipe, old: Map<String, Any?>?): Map<String, Any?> {
        val ingredients = r.ingredients.map { it.trim() }.filter { it.isNotEmpty() }
        val tags = r.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val instructions = ArrayList<Any?>()
        for (sec in r.sections) {
            val steps = sec.steps.mapNotNull { stepEntry(it) }
            if (sec.name.isBlank()) instructions.addAll(steps)
            else instructions += linkedMapOf("@type" to "HowToSection", "name" to sec.name.trim(), "itemListElement" to steps)
        }
        val schema = LinkedHashMap(old?.get("schema").obj())
        schema["name"] = r.title.trim()
        fun put(key: String, v: Any?) { if (v == null) schema.remove(key) else schema[key] = v }
        put("recipeYield", if (r.servings > 0) "${r.servings} portions" else null)
        put("prepTime", if (r.prepMinutes > 0) "PT${r.prepMinutes}M" else null)
        put("totalTime", if (r.totalMinutes > 0) "PT${r.totalMinutes}M" else null)
        put("recipeCategory", r.category.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) })
        schema["recipeIngredient"] = ingredients
        schema["recipeInstructions"] = instructions
        val out = LinkedHashMap<String, Any?>()
        out["id"] = r.id
        out["title"] = r.title.trim()
        if (r.author.isNotBlank()) out["author"] = r.author.trim()
        out["ingredients"] = ingredients
        out["tags"] = tags
        old?.forEach { (k, v) -> if (k !in out && k != "author" && k != "schema" && k != "updated") out[k] = v }
        out["updated"] = r.updated
        // Recette de l'éditeur : l'appli la range sous « Éditeur » dans l'onglet Thermomix.
        out["editor"] = true
        out["schema"] = schema
        return out
    }

    companion object {
        private val SAFE = Regex("^[A-Za-z0-9_-][A-Za-z0-9._-]{0,79}$")
        fun safeId(id: String) = SAFE.matches(id)
    }
}
