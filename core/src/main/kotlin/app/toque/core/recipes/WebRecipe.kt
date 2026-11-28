package app.toque.core.recipes

import app.toque.core.json.Json

/**
 * Recette d'une page web, lue dans ses données structurées schema.org (balise
 * `<script type="application/ld+json">`, que publient la plupart des sites et des blogs de
 * cuisine, dont les cartes de recette WordPress). Rien n'est deviné dans la mise en page : sans
 * ces données, la page est refusée et l'on passe par « Coller une recette ».
 *
 * Les étapes repassent par [PastedText] : mêmes règles pour les réglages Thermomix
 * (« 20 min/100°C/vit. 1 ») que pour un texte collé. Les variantes pour un autre modèle entre
 * parenthèses (« (TM31 : 90°C) ») sont retirées : elles feraient croire à un second réglage.
 */
object WebRecipe {
    data class Result(
        val title: String,
        val servings: Int,
        val ingredients: List<String>,
        /** Réglages entourés de `<nobr>…</nobr>`, comme [PastedText.Result.steps]. */
        val steps: List<String>,
        val category: String,
        val prepMinutes: Int,
        val totalMinutes: Int,
        val author: String,
        val rating: Double,
        val ratingCount: Int,
        val imageUrl: String,
    )

    private val LD = Regex("<script[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val OTHER_MODEL = Regex("\\(\\s*TM\\s?\\d+[^)]*\\)", RegexOption.IGNORE_CASE)

    /** La recette de la page, ou null si la page n'en décrit aucune. */
    fun parse(html: String): Result? {
        for (m in LD.findAll(html)) {
            val data = try { Json.parse(m.groupValues[1].trim()) } catch (_: Exception) { continue }
            val recipe = find(data) ?: continue
            return convert(recipe)
        }
        return null
    }

    /** Cherche un objet `"@type": "Recipe"`, y compris dans un `@graph` ou une liste. */
    private fun find(node: Any?): Map<*, *>? = when (node) {
        is Map<*, *> -> if (isRecipe(node["@type"])) node else find(node["@graph"]) ?: node.values.firstNotNullOfOrNull { if (it is List<*>) find(it) else null }
        is List<*> -> node.firstNotNullOfOrNull { find(it) }
        else -> null
    }

    private fun isRecipe(type: Any?): Boolean = when (type) {
        is String -> type.equals("Recipe", ignoreCase = true)
        is List<*> -> type.any { it is String && it.equals("Recipe", ignoreCase = true) }
        else -> false
    }

    private fun text(v: Any?): String = when (v) {
        is String -> clean(v)
        is Number -> v.toString()
        is List<*> -> v.firstOrNull()?.let { text(it) }.orEmpty()
        is Map<*, *> -> text(v["name"] ?: v["text"] ?: v["@value"])
        else -> ""
    }

    private fun clean(s: String): String {
        val hex = Regex("&#[xX]([0-9a-fA-F]+);").replace(s) { it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: "" }
        return Cookidoo.clean(hex.replace("&nbsp;", " "))
    }

    private fun convert(r: Map<*, *>): Result {
        val title = text(r["name"])
        val ingredients = (r["recipeIngredient"] ?: r["ingredients"]).asList().map { text(it) }.filter { it.isNotEmpty() }

        // Étapes : texte simple, HowToStep, ou HowToSection (« La pâte ») avec ses étapes.
        val lines = ArrayList<String>()
        fun add(s: Any?) {
            when (s) {
                is String -> clean(s).split(Regex("(?<=[.!?])\\s+(?=\\p{Lu})|\\n")).map { it.trim() }.filter { it.isNotEmpty() }.forEach { lines += it }
                is Map<*, *> -> {
                    val items = s["itemListElement"].asList()
                    if (items.isNotEmpty()) {
                        val name = text(s["name"])
                        if (name.isNotEmpty()) lines += "$name :"
                        items.forEach { add(it) }
                    } else add((s["text"] ?: s["name"]) as? String)
                }
                is List<*> -> s.forEach { add(it) }
            }
        }
        add(r["recipeInstructions"])
        val stepsText = lines.map { OTHER_MODEL.replace(it, "").replace(Regex("\\s{2,}"), " ").trim() }.filter { it.isNotEmpty() }
        val pasted = if (stepsText.isEmpty()) emptyList() else PastedText.parse("Préparation :\n" + stepsText.joinToString("\n")).steps

        val rating = r["aggregateRating"] as? Map<*, *>
        return Result(
            title = title,
            servings = Regex("\\d+").find(text(r["recipeYield"]))?.value?.toIntOrNull()?.takeIf { it in 1..99 } ?: 0,
            ingredients = ingredients,
            steps = pasted,
            category = text(r["recipeCategory"]),
            prepMinutes = Cookidoo.minutes(text(r["prepTime"])),
            totalMinutes = Cookidoo.minutes(text(r["totalTime"])),
            author = text(r["author"]),
            rating = text(rating?.get("ratingValue")).replace(',', '.').toDoubleOrNull()?.coerceIn(0.0, 5.0) ?: 0.0,
            ratingCount = text(rating?.get("ratingCount") ?: rating?.get("reviewCount")).filter { it.isDigit() }.toIntOrNull() ?: 0,
            imageUrl = imageUrl(r["image"]),
        )
    }

    private fun imageUrl(v: Any?): String = when (v) {
        is String -> v
        is List<*> -> v.firstOrNull()?.let { imageUrl(it) }.orEmpty()
        is Map<*, *> -> (v["url"] as? String).orEmpty()
        else -> ""
    }.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty()

    private fun Any?.asList(): List<Any?> = when (this) {
        is List<*> -> this
        null -> emptyList()
        else -> listOf(this)
    }
}
