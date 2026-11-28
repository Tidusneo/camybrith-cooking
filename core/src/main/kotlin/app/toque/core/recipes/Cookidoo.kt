package app.toque.core.recipes

import app.toque.core.i18n.tr
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.machine.Accessory
import app.toque.core.machine.Limits
import app.toque.core.machine.TemperatureTable
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Recettes personnelles écrites pour un Thermomix (fichier data.json, format décrit dans
 * docs/RECETTES.md) converties pour le Monsieur Cuisine Smart. Les recettes restent sur les
 * appareils de l'utilisateur : rien n'est publié ni joint au dépôt.
 *
 * Réglages Thermomix, entre <nobr>…</nobr> dans le texte des étapes, séparés par « / » :
 * « 15-30 sec/vitesse 7 », « 10 min/100°C/⟲/vitesse 1 » (⟲ = U+E003, sens inverse),
 * « vitesse ⌒ » (U+E002, mijotage, 40 tr/min), « 20 min/Varoma/vitesse 1 », « Pétrin ⌁/2 min »
 * (U+E001), « mixage ⌁/1 min » (U+E01E, mode mixage du TM6), « Turbo/0.5 sec/3-5 fois ».
 *
 * Adaptation au Smart (mode d'emploi SKMS 1200 A1 et limites de [Limits]) :
 * - vitesses 1 à 10 de même usage sur les deux robots ; les demi-vitesses (3,5) sont arrondies
 *   au-dessus sans chauffe, et la vitesse mijotage devient vitesse 1 en sens inverse ;
 * - en chauffe, vitesse 3 au plus ; en sens inverse, vitesse 3 au plus ; au-delà de la vitesse 6,
 *   10 min au plus (mode d'emploi du Smart) ;
 * - températures ramenées à la graduation du Smart juste en dessous (98 °C → 95 °C) ;
 * - Varoma → programme Vapeur (120 °C) ; Pétrin → Malaxer, 2 min par fois, deux fois au plus
 *   d'affilée ; mode mixage → programme Mixer ; Turbo → impulsions Turbo ;
 * - fourchette de temps (« 25-30 min ») : la plus courte, l'autre est indiquée dans le texte ;
 * - réglage sans durée : laissé à faire à la main (pas de durée inventée).
 * Toute étape dont le réglage a été modifié est marquée « à vérifier » avant lancement.
 *
 * Une étape (HowToStep) peut aussi donner directement le réglage du Monsieur Cuisine et une
 * pesée, avec les champs du format de l'appli ([RecipeCodec]) : `"machine": {program, seconds,
 * speed, tempC, reverse, accessory, pulses}` et `"weigh": {label, grams}`. Ces étapes ne sont
 * pas converties ; elles ne sont « à vérifier » que si le réglage enfreint une règle de sécurité.
 */
object Cookidoo {
    const val ID_PREFIX = "tm-"

    private const val REVERSE = ''
    private const val SPOON = ''
    private const val DOUGH = ''
    private const val BLEND = ''

    /** Fichier « data.json » : liste d'objets avec un identifiant et une recette schema.org (« schema »). */
    fun isExport(root: Any?): Boolean {
        val list = root as? List<*> ?: return false
        val first = list.firstOrNull() as? Map<*, *> ?: return false
        return first["schema"] is Map<*, *> && first["id"] != null
    }

    fun imageName(recipeId: String) = "$recipeId.jpg"

    fun parseAll(root: Any?): Pair<List<Recipe>, Int> {
        val out = ArrayList<Recipe>()
        var bad = 0
        for (e in root as? List<*> ?: emptyList<Any?>()) {
            val r = try {
                parse(e.obj())
            } catch (_: Exception) {
                null
            }
            if (r == null) bad++ else out += r
        }
        return out to bad
    }

    fun parse(m: Map<String, Any?>): Recipe? {
        val schema = m["schema"]?.obj() ?: emptyMap()
        val title = clean(m.str("title") ?: schema.str("name") ?: "")
        val sourceId = m.str("id")?.trim().orEmpty()
        if (title.isEmpty() || sourceId.isEmpty()) return null
        val id = ID_PREFIX + sourceId

        val ingredientLines = m["ingredients"].list().mapNotNull { it as? String }
            .ifEmpty { schema["recipeIngredient"].list().mapNotNull { it as? String } }
        val ingredients = ingredientLines.map { clean(it) }.filter { it.isNotEmpty() }.map { ingredient(it) }

        val steps = ArrayList<Step>()
        var thermomix = false
        fun add(s: Any?) {
            when (s) {
                is String -> {
                    if (SETTING.containsMatchIn(s)) thermomix = true
                    steps += steps(s)
                }
                is Map<*, *> -> {
                    val o = s.obj()
                    val items = o["itemListElement"].list()
                    if (items.isNotEmpty()) {
                        // Section (« La sauce ») : son titre devient une étape sans réglage.
                        val name = clean(o.str("name").orEmpty())
                        if (name.isNotEmpty()) steps += Step("$name :")
                        items.forEach { add(it) }
                    } else {
                        val native = nativeStep(o)
                        if (native != null) steps += native
                        else {
                            val text = o.str("text").orEmpty()
                            if (SETTING.containsMatchIn(text)) thermomix = true
                            steps += steps(text)
                        }
                    }
                }
            }
        }
        schema["recipeInstructions"].list().forEach { add(it) }
        val review = steps.count { it.needsReview }
        val manual = steps.count { it.text.contains(MANUAL_MARK) }

        val notes = listOfNotNull(
            if (thermomix) tr("Recette Thermomix adaptée automatiquement pour le Monsieur Cuisine Smart.") else null,
            when {
                review > 1 -> tr("%d réglages sont à vérifier.", review)
                review == 1 -> tr("1 réglage est à vérifier.")
                else -> null
            },
            when {
                manual > 1 -> tr("%d étapes sont à régler à la main.", manual)
                manual == 1 -> tr("1 étape est à régler à la main.")
                else -> null
            },
        ).joinToString(" ")
        val tags = (m["tags"].list().mapNotNull { (it as? String)?.trim() } + if (thermomix) listOf("Thermomix") else emptyList())
            .filter { it.isNotEmpty() }.distinct()
        return Recipe(
            id = id,
            title = title,
            category = schema["recipeCategory"].list().firstOrNull()?.let { clean(it.toString()) }.orEmpty(),
            tags = tags,
            servings = servings(schema.str("recipeYield")),
            prepMinutes = minutes(schema.str("prepTime")),
            totalMinutes = minutes(schema.str("totalTime")),
            ingredients = if (ingredients.isEmpty()) emptyList() else listOf(IngredientGroup("", ingredients)),
            steps = steps,
            source = if (m["editor"] == true) RecipeSource.USER else RecipeSource.IMPORTED,
            image = imageName(id),
            notes = notes,
            author = m.str("author")?.trim().orEmpty(),
            rating = (m.str("rating_score")?.replace(',', '.')?.toDoubleOrNull() ?: 0.0).coerceIn(0.0, 5.0),
            ratingCount = m.str("rating_count")?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
        )
    }

    // ------------------------------------------------------------------ étapes

    /** Début de la note d'une étape à régler à la main, dans la langue de l'appli. */
    val MANUAL_MARK: String get() = tr("À régler à la main")

    /** Étape au format du Monsieur Cuisine (champs « machine » et « weigh »), null sinon. */
    fun nativeStep(o: Map<String, Any?>): Step? {
        val mc = (o["machine"] as? Map<*, *>)?.let { RecipeCodec.parseMachine(it.obj()) }
        val w = (o["weigh"] as? Map<*, *>)?.let { RecipeCodec.parseWeigh(it.obj()) }
        if (mc == null && w == null) return null
        var text = clean(o.str("text").orEmpty())
        if (text.isEmpty() && w != null) text = if (w.label.isEmpty()) tr("Peser %d g", w.grams) else tr("Peser %d g de %s", w.grams, w.label)
        return Step(text, mc, w, needsReview = mc != null && mc.violations().isNotEmpty())
    }

    /** Un paragraphe Thermomix peut contenir plusieurs réglages : une étape Toque par réglage. */
    fun steps(html: String): List<Step> {
        val out = ArrayList<Step>()
        val sentences = splitSentences(html)
        val text = StringBuilder()
        var conv: Conversion? = null
        fun flush() {
            var t = text.toString().replace(Regex("\\s+([.,;:!?)])"), "$1").replace(Regex("\\s+"), " ").trim()
            // Morceau coupé après un réglage : « et mixer … » devient « Mixer … », et l'étape d'avant finit par un point.
            t = t.replace(Regex("^(?:et|puis|,)\\s+(?:puis\\s+)?"), "").replaceFirstChar { it.uppercase() }
            if (t.isNotEmpty() && t.last().isLetterOrDigit()) t += "."
            val c = conv
            if (t.isEmpty() && c == null) return
            when {
                c == null -> out += Step(t)
                c.machines.isEmpty() -> out += Step(listOf(t, c.note).filter { it.isNotEmpty() }.joinToString("\n"))
                else -> c.machines.forEachIndexed { i, mc ->
                    val body = if (i == 0) t else tr("Encore une fois : %s", mc.summary)
                    out += Step(listOf(body, if (i == 0) c.note else "").filter { it.isNotEmpty() }.joinToString("\n"), mc, needsReview = c.review)
                }
            }
            text.setLength(0)
            conv = null
        }
        for (s in sentences) {
            val settings = SETTING.findAll(s).toList()
            if (settings.isEmpty()) {
                text.append(clean(s)).append(' ')
                continue
            }
            // Une phrase avec réglage : on clôt l'étape en cours si elle a déjà le sien.
            if (conv != null) flush()
            val whisk = s.contains("fouet", ignoreCase = true)
            var from = 0
            for ((k, match) in settings.withIndex()) {
                val c = convert(match.groupValues[1], whisk)
                // Deux réglages dans une phrase (« chauffer … et mixer … ») : on coupe juste après le
                // premier ; « et mixer » part avec le second.
                if (k > 0) flush()
                conv = c
                text.append(clean(s.substring(from, match.range.first))).append(' ').append(c.label).append(' ')
                from = match.range.last + 1
            }
            text.append(clean(s.substring(from))).append(' ')
        }
        flush()
        return out
    }

    private val SETTING = Regex("<nobr>(.*?)</nobr>", RegexOption.DOT_MATCHES_ALL)

    private fun splitSentences(html: String): List<String> {
        // Les réglages contiennent des points (« 0.5 sec ») : on les protège avant de couper.
        val out = ArrayList<String>()
        var depth = 0
        val cur = StringBuilder()
        var i = 0
        while (i < html.length) {
            if (html.startsWith("<nobr>", i)) depth++
            if (html.startsWith("</nobr>", i)) depth = maxOf(0, depth - 1)
            val ch = html[i]
            cur.append(ch)
            if (depth == 0 && (ch == '.' || ch == '!' || ch == '?') && (i + 1 == html.length || html[i + 1].isWhitespace())) {
                out += cur.toString()
                cur.setLength(0)
            }
            i++
        }
        if (cur.isNotBlank()) out += cur.toString()
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    // ------------------------------------------------------------------ réglages

    class Conversion(
        /** Réglage(s) pour le Smart ; vide si l'étape est à régler à la main. */
        val machines: List<StepMachine>,
        /** Texte qui remplace le réglage Thermomix dans la phrase. */
        val label: String,
        /** Explication des changements (vide si traduction exacte). */
        val note: String,
        val review: Boolean,
    )

    fun convert(rawSetting: String, whisk: Boolean = false): Conversion {
        val raw = clean(rawSetting)
        val parts = raw.split('/').map { it.trim() }.filter { it.isNotEmpty() }
        val changes = ArrayList<String>()
        var seconds: IntRange? = null
        var tempC = 0
        var varoma = false
        var reverse = false
        var speed: Double? = null
        var spoon = false
        var progressive = false
        var dough = false
        var blend = false
        var turbo = false
        var pulses: IntRange? = null
        for (p0 in parts) {
            val p = p0.lowercase()
            when {
                p == REVERSE.toString() || p == "sens inverse" -> reverse = true
                p.startsWith("pétrin") || p.startsWith("petrin") || p.contains(DOUGH) -> dough = true
                p.startsWith("mixage") || p.contains(BLEND) -> blend = true
                p == "turbo" -> turbo = true
                p == "varoma" -> varoma = true
                p.endsWith("°c") -> tempC = p.removeSuffix("°c").trim().replace(',', '.').toDoubleOrNull()?.toInt() ?: 0
                p.startsWith("vitesse") -> {
                    val v = p.removePrefix("vitesse").trim()
                    if (v.isEmpty() || v.contains(SPOON) || v.startsWith("mijot")) spoon = true
                    else {
                        // « vitesse 5-10 » : monter progressivement ; le Smart démarre doucement tout seul.
                        val r = range(v)
                        speed = r?.endInclusive
                        if (r != null && r.start != r.endInclusive) progressive = true
                    }
                }
                p.endsWith("fois") -> pulses = range(p.removeSuffix("fois"))?.let { it.start.toInt()..it.endInclusive.toInt() }
                else -> duration(p)?.let { seconds = it }
            }
        }

        fun result(machines: List<StepMachine>, label: String): Conversion {
            val note = if (changes.isEmpty()) "" else tr("Adapté du Thermomix (%s) : %s.", raw, changes.joinToString(" ; "))
            return Conversion(machines, label, note, changes.isNotEmpty())
        }

        val secs = seconds
        val rangeNote = secs?.takeIf { it.first != it.last }?.let { " " + tr("(jusqu'à %s)", fmt(it.last)) }.orEmpty()

        // Turbo : impulsions.
        if (turbo) {
            val n = pulses?.first ?: 1
            if (pulses != null && pulses.first != pulses.last) changes += tr("%d impulsions (jusqu'à %d)", pulses.first, pulses.last)
            val mc = StepMachine(program = StepProgram.TURBO, pulses = n)
            return result(listOf(mc), "Turbo × $n")
        }
        if (secs == null) {
            return Conversion(emptyList(), raw, MANUAL_MARK + " : " + tr("la recette ne donne pas de durée (%s).", raw), true)
        }
        val chosen = secs.first
        if (secs.first != secs.last) changes += tr("durée la plus courte, %s", fmt(secs.first))

        // Pétrin : Malaxer, 2 min par fois, 2 fois d'affilée au plus.
        if (dough) {
            val max = (Limits.MAX_MS_KNEAD / 1000).toInt()
            if (chosen > 2 * max) {
                changes += tr("le Smart malaxe 2 × 2 min au plus d'affilée")
                return result(listOf(StepMachine(StepProgram.KNEAD, max), StepMachine(StepProgram.KNEAD, max)), tr("Malaxer 2 × 2 min") + rangeNote)
            }
            if (chosen > max) {
                changes += tr("en deux fois (2 min au plus par fois)")
                return result(listOf(StepMachine(StepProgram.KNEAD, max), StepMachine(StepProgram.KNEAD, chosen - max)), tr("Malaxer %s en deux fois", fmt(chosen)) + rangeNote)
            }
            return result(listOf(StepMachine(StepProgram.KNEAD, chosen)), tr("Malaxer %s", fmt(chosen)) + rangeNote)
        }

        // Mode mixage du TM6 : programme Mixer du Smart (5 s à 3 min).
        if (blend) {
            changes += tr("programme Mixer du Smart")
            val s = ((chosen + 4) / 5 * 5).coerceIn(5, 180)
            if (s != chosen) changes += tr("durée %s", fmt(s))
            return result(listOf(StepMachine(StepProgram.PUREE, s)), tr("Mixer %s", fmt(s)) + rangeNote)
        }

        // Varoma : programme Vapeur.
        if (varoma) {
            if (reverse || spoon || (speed ?: 1.0) > 1.0) changes += tr("le programme Vapeur remue lui-même doucement")
            return result(listOf(StepMachine(StepProgram.STEAM, chosen, tempC = 120)), fmt(chosen) + "/" + tr("vapeur") + rangeNote)
        }

        // Cuisson ou mixage manuel.
        var t = 0
        if (tempC >= 37) {
            t = TemperatureTable.current.let { table -> table.celsius(table.levelAtMost(tempC)) }
            if (t != tempC) changes += tr("%d °C (graduation du Smart)", t)
        } else if (tempC > 0) changes += tr("sans chauffe (le Smart chauffe à partir de 37 °C)")
        val heated = t > 0
        var sp: Int
        if (spoon) {
            sp = 1
            if (!reverse) changes += tr("vitesse 1 en sens inverse à la place de la vitesse mijotage")
            reverse = true
        } else {
            val v = speed ?: 0.0
            sp = if (v % 1.0 != 0.0) (if (heated) floor(v) else ceil(v)).toInt() else v.toInt()
            if (v % 1.0 != 0.0) changes += tr("vitesse %d (pas de demi-vitesse)", sp)
        }
        if (progressive) changes += tr("vitesse %d directement (montée progressive au Thermomix)", sp)
        if (sp <= 0) return Conversion(emptyList(), raw, MANUAL_MARK + " : " + tr("vitesse non précisée (%s).", raw), true)
        if (heated && sp > Limits.MAX_SPEED_HEATED) {
            sp = Limits.MAX_SPEED_HEATED
            changes += tr("vitesse %d au plus en chauffe", Limits.MAX_SPEED_HEATED)
        }
        if (reverse && sp > Limits.MAX_SPEED_REVERSE) {
            sp = Limits.MAX_SPEED_REVERSE
            changes += tr("vitesse %d au plus en sens inverse", Limits.MAX_SPEED_REVERSE)
        }
        val accessory = if (whisk) Accessory.WHISK else Accessory.BLADE
        if (whisk && sp > Accessory.WHISK.maxSpeed) {
            sp = Accessory.WHISK.maxSpeed
            changes += tr("vitesse %d au plus avec le fouet", Accessory.WHISK.maxSpeed)
        }
        var dur = chosen
        if (sp > 6 && dur > 600) {
            dur = 600
            changes += tr("10 min au plus au-delà de la vitesse 6")
        }
        if (dur < 1) dur = 1
        val label = buildString {
            append(fmt(dur))
            if (heated) append("/").append(t).append(" °C")
            if (reverse) append("/").append(tr("sens inverse"))
            append("/").append(tr("vitesse %d", sp))
            append(rangeNote)
        }
        return result(listOf(StepMachine(StepProgram.MANUAL, dur, sp, t, reverse, accessory)), label)
    }

    private fun range(s: String): ClosedFloatingPointRange<Double>? {
        val p = s.trim().replace(',', '.').split('-').map { it.trim().toDoubleOrNull() }
        if (p.isEmpty() || p.any { it == null }) return null
        return p.first()!!..p.last()!!
    }

    /** « 15-30 sec », « 1 min 30 sec », « 25-30 min », « 1 h 30 min », « 0.5 sec ». */
    fun duration(s: String): IntRange? {
        val t = s.lowercase().replace(',', '.').trim()
        val m = Regex("^(?:(\\d+(?:\\.\\d+)?)\\s*-\\s*)?(\\d+(?:\\.\\d+)?)\\s*(h|min|sec|s)\\b(?:\\s*(\\d+)\\s*(min|sec|s)\\b)?$").find(t) ?: return null
        fun unit(u: String) = when (u) { "h" -> 3600.0; "min" -> 60.0; else -> 1.0 }
        val u = unit(m.groupValues[3])
        val extra = m.groupValues[4].toIntOrNull()?.let { it * unit(m.groupValues[5]) } ?: 0.0
        val hi = m.groupValues[2].toDouble() * u + extra
        val lo = m.groupValues[1].toDoubleOrNull()?.let { it * u + extra } ?: hi
        return maxOf(1, Math.round(lo).toInt())..maxOf(1, Math.round(hi).toInt())
    }

    fun fmt(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 3600 == 0 -> "${seconds / 3600} h"
        seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60} s"
    }

    // ------------------------------------------------------------------ ingrédients et divers

    private val UNITS = listOf(
        "c. à soupe", "c. à café", "c.à.s.", "c.à.c.", "cuil. à soupe", "cuil. à café",
        "kg", "g", "ml", "cl", "dl", "l", "pincées", "pincée", "gousses", "gousse", "brins", "brin",
        "tranches", "tranche", "feuilles", "feuille", "boîtes", "boîte", "sachets", "sachet", "filet", "noix", "botte",
    )

    fun ingredient(line: String): Ingredient {
        val m = Regex("^((?:\\d+(?:[.,]\\d+)?|[½¼¾⅓⅔])(?:\\s*[½¼¾⅓⅔])?(?:\\s*-\\s*(?:\\d+(?:[.,]\\d+)?|[½¼¾]))?)\\s+(.*)$").find(line.trim())
            ?: return Ingredient(name = line.trim())
        val qty = m.groupValues[1].replace(Regex("\\s+"), " ")
        var rest = m.groupValues[2].trim()
        var unit = ""
        for (u in UNITS) {
            if (rest.startsWith("$u ") || rest == u) {
                unit = u
                rest = rest.removePrefix(u).trim()
                // « 1 c. à café rase de sel » : l'adjectif reste avec l'unité.
                val adj = Regex("^(rases?|bombées?)\\b\\s*").find(rest)
                if (adj != null) {
                    unit += " " + adj.groupValues[1]
                    rest = rest.substring(adj.range.last + 1).trim()
                }
                break
            }
        }
        if (unit.isNotEmpty()) {
            rest = when {
                rest.startsWith("de ") -> rest.removePrefix("de ")
                rest.startsWith("d'") || rest.startsWith("d’") -> rest.substring(2)
                else -> rest
            }
        }
        return Ingredient(qty, unit, rest.trim())
    }

    fun servings(yield: String?): Int {
        val m = Regex("^(\\d+)\\s*(portions?|parts?|personnes?|pièces?)").find(yield.orEmpty().trim()) ?: return 0
        return m.groupValues[1].toIntOrNull() ?: 0
    }

    /** Durée ISO 8601 (« PT1H20M ») en minutes. */
    fun minutes(iso: String?): Int {
        val m = Regex("^P(?:\\d+D)?T?(?:(\\d+)H)?(?:(\\d+)M)?").find(iso.orEmpty().trim()) ?: return 0
        return (m.groupValues[1].toIntOrNull() ?: 0) * 60 + (m.groupValues[2].toIntOrNull() ?: 0)
    }

    private val ENTITIES = mapOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'", "&apos;" to "'",
        "&frac12;" to "½", "&frac14;" to "¼", "&frac34;" to "¾", "&deg;" to "°", "&eacute;" to "é", "&egrave;" to "è",
        "&agrave;" to "à", "&ccedil;" to "ç", "&oelig;" to "œ", "&hellip;" to "…", "&rsquo;" to "’", "&laquo;" to "«", "&raquo;" to "»",
    )

    /** Texte sans balises ni entités HTML, espaces simplifiés. */
    fun clean(html: String): String {
        var s = html.replace(Regex("<[^>]+>"), "")
        for ((k, v) in ENTITIES) s = s.replace(k, v)
        s = Regex("&#(\\d+);").replace(s) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
        // « 1 ½ » sans espace insécable bizarre, et ½ collé : « 1 ½ c. à café ».
        return s.replace(' ', ' ').replace(Regex("\\s+"), " ").trim()
    }
}
