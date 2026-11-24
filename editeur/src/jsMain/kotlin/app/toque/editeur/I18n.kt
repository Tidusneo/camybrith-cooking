package app.toque.editeur

/**
 * Textes de la page : écrits en français dans le code, traduits avec la langue de l'appli
 * (fichiers lang/<code>.json de l'appli, envoyés par /api/strings).
 */
object WebI18n {
    var texts: Map<String, String> = emptyMap()
}

private val PLACEHOLDER = Regex("%[sd]")

fun tr(fr: String, vararg args: Any?): String {
    val t = WebI18n.texts[fr] ?: fr
    if (args.isEmpty()) return t
    var i = 0
    return PLACEHOLDER.replace(t) { args.getOrNull(i++)?.toString() ?: "" }
}
