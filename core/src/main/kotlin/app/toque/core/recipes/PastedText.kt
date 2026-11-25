package app.toque.core.recipes

/**
 * Recette collée en texte libre (page d'un blog, d'un livre, notes) : titre, portions,
 * ingrédients et étapes. Les réglages écrits à la manière Thermomix (« 5 sec/vit. 5 »,
 * « 20 min/100°C/sens inverse/vit. mijotage », « cuire 10 minutes à 100 degrés vitesse 2 »,
 * « Turbo/0.5 sec/3 fois ») sont repérés, remis dans la notation de l'importeur et entourés de
 * `<nobr>` : c'est [Cookidoo] qui les convertit ensuite pour le Monsieur Cuisine Smart, avec ses
 * règles et ses étapes « à vérifier ».
 *
 * Le découpage est une estimation : la recette passe toujours par un aperçu modifiable avant
 * d'être enregistrée.
 */
object PastedText {
    data class Result(
        val title: String,
        val servings: Int,
        val ingredients: List<String>,
        /** Une étape par réglage, réglages entourés de `<nobr>…</nobr>`. */
        val steps: List<String>,
    ) {
        val settings: Int get() = steps.sumOf { s -> NOBR.findAll(s).count() }
    }

    private val NOBR = Regex("<nobr>(.*?)</nobr>")

    // Un élément de réglage. Les durées n'avalent pas le point de fin de phrase.
    private const val DURATION = "(?:\\d+(?:[.,]\\d+)?\\s*(?:-|à)\\s*)?\\d+(?:[.,]\\d+)?\\s*(?:h|heures?|min(?:utes?)?|mn|sec(?:ondes?)?|s)\\b(?:\\s*\\d+\\s*(?:min|sec|s)\\b)?"
    private const val TOKEN = "$DURATION|\\d+\\s*°\\s*C?|\\d+\\s*degrés|varoma|sens\\s+inverse|inverse|⟲||p[ée]trin|mixage|turbo|" +
        "\\d+(?:\\s*-\\s*\\d+)?\\s*fois|vit(?:esse|\\.)?\\s*(?:\\d+(?:[.,]\\d+)?(?:\\s*-\\s*\\d+(?:[.,]\\d+)?)?|mijotage|cuill[eè]re|)"
    // Entre deux éléments : « / », une virgule, ou « à » et « pendant » (« 10 minutes à 100 degrés vitesse 2 »).
    private const val SEP = "(?:\\s*[/,]\\s*|\\s+(?:(?:à|pendant)\\s+)?)"
    private val SETTING = Regex("(?:$TOKEN)(?:$SEP(?:$TOKEN))+", RegexOption.IGNORE_CASE)
    private val TOKENS = Regex(TOKEN, RegexOption.IGNORE_CASE)

    /** Début d'une ligne d'ingrédient : une quantité puis une unité ou un mot. */
    private val QTY = Regex("^(?:\\d+(?:[.,]\\d+)?|[½¼¾⅓⅔])(?:\\s*[½¼¾⅓⅔])?(?:\\s*-\\s*\\d+)?\\s*\\p{L}", RegexOption.IGNORE_CASE)
    private val SERVINGS = Regex("(\\d+)\\s*(?:personnes?|portions?|parts?|pers\\.?)", RegexOption.IGNORE_CASE)
    private val SERVINGS_LINE = Regex("^(?:pour\\s+)?\\d+\\s*(?:personnes?|portions?|parts?|pers\\.?)\\s*\\.?$", RegexOption.IGNORE_CASE)
    private val INGREDIENTS = Regex("^ingr[ée]dients?\\s*:?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val METHOD = Regex("^(?:pr[ée]paration|[ée]tapes?|instructions?|recette)\\s*:?\\s*(.*)$", RegexOption.IGNORE_CASE)
    private val NUMBERED = Regex("^(?:étape\\s*)?\\d+\\s*[.)\\-:]\\s+", RegexOption.IGNORE_CASE)

    fun parse(text: String): Result {
        val lines = text.replace("\r", "").split('\n')
            .map { it.replace(Regex("^[\\s•*·\\-–—]+"), "").trim() }
            .filter { it.isNotEmpty() }
        var title = ""
        val servings = SERVINGS.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val ingredients = ArrayList<String>()
        val rawSteps = ArrayList<String>()
        var mode = Mode.AUTO
        for ((i, l) in lines.withIndex()) {
            val ing = INGREDIENTS.find(l)
            if (ing != null) {
                mode = Mode.INGREDIENTS
                val rest = ing.groupValues[1]
                if (rest.isNotEmpty()) ingredients += rest.split(Regex(",\\s*(?=\\d|[½¼¾])")).map { it.trim() }.filter { it.isNotEmpty() }
                continue
            }
            val method = METHOD.find(l)
            if (method != null && method.groupValues[1].length < 60) {
                mode = Mode.STEPS
                if (method.groupValues[1].isNotEmpty()) rawSteps += method.groupValues[1]
                continue
            }
            if (SERVINGS_LINE.matches(l)) continue
            val hasSetting = SETTING.containsMatchIn(l)
            if (i == 0 && !QTY.containsMatchIn(l) && !hasSetting) {
                title = l.replace(Regex("\\s*[,(].*$"), "").trim()
                continue
            }
            val numbered = NUMBERED.containsMatchIn(l)
            if (mode == Mode.INGREDIENTS && !numbered && l.length < 90 && !hasSetting) { ingredients += l; continue }
            if (mode == Mode.AUTO && QTY.containsMatchIn(l) && l.length < 90 && !hasSetting) { ingredients += l; continue }
            if (mode == Mode.INGREDIENTS) mode = Mode.STEPS
            rawSteps += l.replace(NUMBERED, "")
        }
        return Result(title, servings, ingredients, rawSteps.flatMap { split(it) })
    }

    private enum class Mode { AUTO, INGREDIENTS, STEPS }

    /**
     * Une ligne peut contenir plusieurs phrases, chacune avec son réglage : une étape par réglage.
     * Les phrases sans réglage restent avec le réglage qui suit, ou avec l'étape d'avant en fin de ligne.
     */
    private fun split(line: String): List<String> {
        // Les réglages contiennent des points (« vit. 5 », « 0.5 sec ») : mis de côté avant de couper.
        val kept = ArrayList<String>()
        val safe = SETTING.replace(line) { m -> kept += normalize(m.value); "\u0001${kept.size - 1}\u0002" }
        val sentences = safe.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var pending = ""
        for (s in sentences) {
            if (!s.contains('\u0001')) { pending = if (pending.isEmpty()) s else "$pending $s"; continue }
            out += restore(if (pending.isEmpty()) s else "$pending $s", kept)
            pending = ""
        }
        if (pending.isNotBlank()) {
            if (out.isNotEmpty() && !out.last().contains("<nobr>")) out[out.size - 1] = out.last() + " " + restore(pending, kept)
            else out += restore(pending, kept)
        }
        return out
    }

    private fun restore(s: String, kept: List<String>): String =
        s.replace(Regex("\u0001(\\d+)\u0002")) { m -> "<nobr>" + kept[m.groupValues[1].toInt()] + "</nobr>" }

    /** Un réglage repéré, dans la notation de l'importeur (« 20 min/100°C/⟲/vitesse 1 »). */
    fun normalize(setting: String): String =
        TOKENS.findAll(setting).map { it.value.trim() }.map { t ->
            val l = t.lowercase()
            when {
                // En toutes lettres : les notes « Adapté du Thermomix (…) » restent lisibles.
                Regex("^sens\\s+inverse$").matches(l) || l == "inverse" || t == "⟲" || t == "" -> "sens inverse"
                l.startsWith("vit") -> {
                    val v = l.replace(Regex("^vit(?:esse|\\.)?\\s*"), "")
                    if (v.startsWith("mijot") || v.startsWith("cuill") || v == "" || v.isEmpty()) "vitesse mijotage"
                    else "vitesse " + v.replace(" ", "")
                }
                l.endsWith("degrés") -> l.removeSuffix("degrés").trim() + "°C"
                l.contains("°") -> l.replace(Regex("\\s*°\\s*c?$"), "") + "°C"
                Regex("\\d").containsMatchIn(l) && !l.endsWith("fois") ->
                    l.replace(Regex("\\s*à\\s*"), "-")
                        .replace(Regex("heures?"), "h").replace(Regex("minutes?|mn"), "min").replace(Regex("secondes?"), "sec")
                else -> t
            }
        }.joinToString("/")
}
