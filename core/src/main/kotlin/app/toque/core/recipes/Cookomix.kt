package app.toque.core.recipes

import app.toque.core.json.Json
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str

/**
 * Import d'une recette de cookomix.com (recettes écrites pour le Thermomix) vers le Smart.
 *
 * La page contient la recette au format schema.org (balise `application/ld+json`) : titre,
 * ingrédients (« Parmesan - 80 grammes »), étapes (« Cuire 3 min/90°C/vitesse 3. »). Les réglages
 * sont repérés dans le texte, mis au format que comprend [Cookidoo] (« <nobr>3 min/90°C/vitesse 3</nobr> »,
 * vitesse cuillère et sens inverse en symboles Thermomix), puis convertis par [Cookidoo.convert] :
 * Varoma → vapeur 120 °C, vitesse cuillère → vitesse 1 en sens inverse, vitesses plafonnées en
 * chauffe, etc. Toute étape adaptée est marquée « à vérifier ».
 *
 * La recette importée appartient à l'utilisateur ([RecipeSource.USER]) et porte l'étiquette « Cookomix ».
 */
object Cookomix {
    const val ID_PREFIX = "ck-"
    const val TAG = "Cookomix"

    private const val REVERSE = ''
    private const val SPOON = ''

    fun isCookomixUrl(url: String): Boolean = Regex("^(https?://)?(www\\.)?cookomix\\.com/recettes/[^\\s]+", RegexOption.IGNORE_CASE).matches(url.trim())

    /** Adresse complète, avec https:// et la barre finale. */
    fun normalizeUrl(url: String): String {
        var u = url.trim()
        if (!u.startsWith("http", true)) u = "https://$u"
        u = u.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
        return u.substringBefore('#').substringBefore('?')
    }

    /** Identifiant tiré de l'adresse : « …/recettes/crumble-poulet-courgettes-thermomix/ » → « ck-crumble-poulet-courgettes ». */
    fun idFor(url: String): String {
        val slug = normalizeUrl(url).trimEnd('/').substringAfterLast('/').removeSuffix("-thermomix").lowercase()
            .replace(Regex("[^a-z0-9-]"), "-").replace(Regex("-+"), "-").trim('-')
        return ID_PREFIX + slug.ifEmpty { "recette" }
    }

    /** La recette schema.org de la page, ou null (recette Premium, page sans recette…). */
    fun findRecipe(html: String): Map<String, Any?>? {
        for (m in Regex("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)).findAll(html)) {
            val root = try { Json.parse(m.groupValues[1].trim()) } catch (_: Exception) { continue }
            val candidates = when (root) {
                is List<*> -> root
                is Map<*, *> -> root.obj()["@graph"].list().ifEmpty { listOf(root) }
                else -> emptyList()
            }
            for (c in candidates) {
                val o = c.obj()
                val type = o["@type"]
                val isRecipe = type == "Recipe" || (type is List<*> && "Recipe" in type)
                if (isRecipe && o["recipeInstructions"].list().isNotEmpty()) return o
            }
        }
        return null
    }

    /** Adresse de la photo de la recette (la plus grande proposée). */
    fun imageUrl(schema: Map<String, Any?>): String? = when (val img = schema["image"]) {
        is String -> img
        is List<*> -> img.mapNotNull { (it as? String) ?: it.obj().str("url") }.lastOrNull()
        is Map<*, *> -> img.obj().str("url")
        else -> null
    }?.takeIf { it.startsWith("http") }

    /** Recette convertie pour le Smart, ou null si la page n'en contient pas. */
    fun parse(html: String, url: String): Recipe? {
        val schema = findRecipe(html) ?: return null
        val id = idFor(url)
        val title = cleanTitle(Cookidoo.clean(schema.str("name").orEmpty()))
        if (title.isEmpty()) return null
        val ingredients = schema["recipeIngredient"].list().mapNotNull { (it as? String)?.let { s -> ingredientLine(Cookidoo.clean(s)) } }
            .filter { it.isNotEmpty() }
        val instructions = schema["recipeInstructions"].list().map { st ->
            when (st) {
                is String -> markSettings(adaptText(Cookidoo.clean(st)))
                else -> {
                    val o = st.obj().toMutableMap()
                    o["text"] = markSettings(adaptText(Cookidoo.clean(o.str("text").orEmpty())))
                    o
                }
            }
        }
        val converted = Cookidoo.parse(
            mapOf(
                "id" to id.removePrefix(ID_PREFIX),
                "title" to title,
                "ingredients" to ingredients,
                "schema" to schema + ("recipeInstructions" to instructions),
            ),
        ) ?: return null
        val review = converted.steps.count { it.needsReview }
        val manual = converted.steps.count { it.text.contains(Cookidoo.MANUAL_MARK) }
        val notes = buildString {
            append("Recette Cookomix (écrite pour le Thermomix) adaptée automatiquement pour le Monsieur Cuisine Smart.")
            if (review > 0) append(" ").append(review).append(if (review > 1) " réglages adaptés sont à vérifier." else " réglage adapté est à vérifier.")
            if (manual > 0) append(" ").append(manual).append(if (manual > 1) " étapes sont à régler à la main." else " étape est à régler à la main.")
            append("\nSource : ").append(normalizeUrl(url))
        }
        // Les symboles Thermomix privés (cuillère, sens inverse) restent lisibles dans les explications.
        fun readable(t: String) = t.replace("vitesse $SPOON", "vitesse cuillère").replace(SPOON.toString(), "cuillère").replace(REVERSE.toString(), "sens inverse")
        return converted.copy(
            id = id,
            steps = converted.steps.map { it.copy(text = readable(it.text)) },
            tags = (converted.tags.filter { !it.equals("Thermomix", true) } + TAG).distinct(),
            servings = converted.servings.takeIf { it > 0 } ?: schema.str("recipeYield")?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
            totalMinutes = converted.totalMinutes.takeIf { it > 0 } ?: (Cookidoo.minutes(schema.str("prepTime")) + Cookidoo.minutes(schema.str("cookTime"))),
            source = RecipeSource.USER,
            image = "$id.jpg",
            notes = notes,
            author = TAG,
        )
    }

    /** « Crumble poulet, courgettes et parmesan au thermomix » → « Crumble poulet, courgettes et parmesan ». */
    fun cleanTitle(t: String): String = t.replace(Regex("\\s+(au|avec le|avec un|pour)\\s+thermomix\\b.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s*thermomix\\s*$", RegexOption.IGNORE_CASE), "").trim()

    /** « Parmesan - 80 grammes » → « 80 g Parmesan » ; « Sel - 1 pincée » → « 1 pincée Sel ». */
    fun ingredientLine(s: String): String {
        val m = Regex("^(.+?)\\s+-\\s+(.+?)\\s*$").find(s) ?: return s.trim()
        val name = m.groupValues[1].trim()
        var qty = m.groupValues[2].trim()
        qty = qty.replace(Regex("\\bgrammes?\\b", RegexOption.IGNORE_CASE), "g")
            .replace(Regex("\\bkilogrammes?\\b", RegexOption.IGNORE_CASE), "kg")
            .replace(Regex("\\bmillilitres?\\b", RegexOption.IGNORE_CASE), "ml")
            .replace(Regex("\\bcentilitres?\\b", RegexOption.IGNORE_CASE), "cl")
            .replace(Regex("\\bcuill[eè]res? à soupe\\b", RegexOption.IGNORE_CASE), "c. à soupe")
            .replace(Regex("\\bcuill[eè]res? à café\\b", RegexOption.IGNORE_CASE), "c. à café")
        return if (qty.isEmpty()) name else "$qty $name"
    }

    /** Le texte parle du Thermomix : on parle du robot. */
    fun adaptText(s: String): String = s
        .replace(Regex("\\s+(du|au|le|dans le)\\s+bol\\s+du\\s+thermomix", RegexOption.IGNORE_CASE)) { it.value.replace(Regex("\\s+du\\s+thermomix$", RegexOption.IGNORE_CASE), "") }
        .replace(Regex("\\bbol du thermomix\\b", RegexOption.IGNORE_CASE), "bol")
        .replace(Regex("\\bdu thermomix\\b", RegexOption.IGNORE_CASE), "du robot")
        .replace(Regex("\\ble thermomix\\b", RegexOption.IGNORE_CASE), "le robot")
        .replace(Regex("\\bthermomix\\b", RegexOption.IGNORE_CASE), "robot")

    // Un réglage commence par une durée et enchaîne des éléments séparés par « / ».
    private val PART = "(?:sens\\s+inverse|vitesse\\s*(?:cuill[eè]re|mijotage|\\d+(?:[.,]\\d+)?(?:\\s*-\\s*\\d+(?:[.,]\\d+)?)?)|varoma|\\d+\\s*°\\s*c|p[ée]trin|mode\\s+p[ée]trin|mixage|turbo|\\d+(?:\\s*-\\s*\\d+)?\\s*fois)"
    private val DURATION = "\\d+(?:[.,]\\d+)?(?:\\s*-\\s*\\d+(?:[.,]\\d+)?)?\\s*(?:sec|s|min|h)\\b(?:\\s*\\d+\\s*(?:sec|s|min)\\b)?"
    private val SETTING = Regex("(?:$DURATION|$PART)(?:\\s*/\\s*(?:$DURATION|$PART))+", RegexOption.IGNORE_CASE)

    /** Entoure chaque réglage de <nobr>…</nobr> et met cuillère / sens inverse en symboles Thermomix. */
    fun markSettings(text: String): String = SETTING.replace(text) { m ->
        val parts = m.value.split('/').map { it.trim() }.map { p ->
            val l = p.lowercase()
            when {
                l.startsWith("sens") -> REVERSE.toString()
                l.startsWith("vitesse") && (l.contains("cuill") || l.contains("mijot")) -> "vitesse $SPOON"
                l.contains("°") -> p.replace(Regex("\\s*°\\s*"), "°").replace("°c", "°C")
                l.startsWith("mode") -> "Pétrin"
                l == "s" -> p
                else -> p.replace(Regex("(\\d)\\s*s$"), "$1 sec")
            }
        }
        "<nobr>" + parts.joinToString("/") + "</nobr>"
    }
}
