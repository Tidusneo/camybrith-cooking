package app.toque.core.i18n

import app.toque.core.json.Json
import java.util.Locale

/**
 * Une langue de l'interface. Le français est la langue source (textes écrits dans le code) ;
 * les autres viennent chacune d'un fichier `lang/<code>.json` : { "_name": "English", "texte français": "traduction", … }.
 */
class Language(val code: String, val name: String, val texts: Map<String, String>) {
    val locale: Locale = Locale.forLanguageTag(code)
}

object I18n {
    /** Valeur du réglage : langue du système. */
    const val AUTO = "auto"

    val FRENCH = Language("fr", "Français", emptyMap())

    /** Langues disponibles : le français, puis celles des fichiers, par nom. */
    @Volatile var languages: List<Language> = listOf(FRENCH)
        private set

    @Volatile var current: Language = FRENCH

    val code: String get() = current.code
    val locale: Locale get() = current.locale

    /** Lit un fichier de langue. Les clés commençant par « _ » sont des informations (nom…). */
    fun parse(code: String, json: String): Language {
        val o = Json.parse(json) as? Map<*, *> ?: throw IllegalArgumentException("$code : objet JSON attendu")
        val texts = HashMap<String, String>()
        for ((k, v) in o) {
            if (k !is String || k.startsWith("_")) continue
            if (v is String && v.isNotEmpty()) texts[k] = v
        }
        val name = (o["_name"] as? String)?.takeIf { it.isNotBlank() } ?: code
        return Language(code, name, texts)
    }

    /** Remplace les langues disponibles (une langue de même code plus loin dans la liste l'emporte). */
    fun install(files: Collection<Language>) {
        val byCode = LinkedHashMap<String, Language>()
        for (l in files) if (l.code != FRENCH.code) byCode[l.code] = l
        languages = listOf(FRENCH) + byCode.values.sortedBy { it.name.lowercase(Locale.ROOT) }
        current = find(current.code) ?: FRENCH
    }

    fun find(code: String?): Language? {
        if (code.isNullOrBlank()) return null
        val c = code.lowercase(Locale.ROOT)
        return languages.firstOrNull { it.code.lowercase(Locale.ROOT) == c }
            ?: languages.firstOrNull { it.code.lowercase(Locale.ROOT).substringBefore('-') == c.substringBefore('-') }
    }

    /** Langue effective d'après le réglage (« auto » ou un code) et la langue du système. */
    fun resolve(setting: String?, systemLanguage: String): Language =
        if (setting.isNullOrBlank() || setting == AUTO) find(systemLanguage) ?: find("en") ?: FRENCH
        else find(setting) ?: FRENCH

    fun select(code: String?) {
        current = find(code) ?: FRENCH
    }
}

/**
 * Traduit un texte écrit en français dans la langue de l'interface (le français s'il manque).
 *
 * Jamais `null` : sur le Smart, le compilateur d'Android recompile parfois de travers cette
 * fonction très appelée et lui fait renvoyer `null` (interface qui plante en boucle, 28/11/2026).
 * On garde donc une variable locale et on retombe sur le texte français au moindre doute ;
 * [CompileGuard] recompile l'appli en arrière-plan pour corriger la cause.
 */
fun tr(fr: String): String {
    val t = I18n.current.texts[fr]
    @Suppress("SENSELESS_COMPARISON")
    return if (t != null) t else fr
}

/** Comme [tr], avec des paramètres au format `String.format` (%s, %d…). Ne plante jamais. */
fun tr(fr: String, vararg args: Any?): String {
    @Suppress("SENSELESS_COMPARISON")
    val template = tr(fr).let { if (it != null) it else fr }
    return try {
        String.format(I18n.locale, template, *args)
    } catch (_: Throwable) {
        template
    }
}

/** Marque un texte à traduire plus tard avec [tr] (sans le traduire tout de suite). */
fun trKey(fr: String): String = fr
