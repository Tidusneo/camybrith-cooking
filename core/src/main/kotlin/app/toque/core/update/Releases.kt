package app.toque.core.update

import app.toque.core.json.Json

/** Une version publiée sur GitHub (page « Releases » du dépôt). */
data class Release(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    /** Interface (app.toque), dans la variante demandée (rapide ou prudente). */
    val apkName: String?,
    val apkUrl: String?,
    val apkSize: Long,
    /** Pilote moteur (app.toque.pilote), publié avec chaque version. */
    val pilotName: String? = null,
    val pilotUrl: String? = null,
    val pilotSize: Long = 0L,
)

/** Lecture de l'API GitHub « dernière version » et comparaison de numéros de version. */
object Releases {
    const val REPO = "Tidusneo/camybrith-cooking"
    const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    const val PAGE_URL = "https://github.com/$REPO/releases"

    /** « v0.2.0 » → « 0.2.0 ». */
    fun versionOf(tag: String): String = tag.trim().removePrefix("v").removePrefix("V").trim()

    /** Compare deux versions « 1.2.3 » chiffre par chiffre (« 0.2 » = « 0.2.0 »). */
    fun compare(a: String, b: String): Int {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(candidate: String, installed: String): Boolean = compare(candidate, installed) > 0

    private fun parts(v: String): List<Int> =
        versionOf(v).split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    /** « 1.2.0-rapide » → « rapide », « 1.2.0-prudente » (ou « -prudent ») → « prudente ». */
    fun variantOf(versionName: String): String =
        if (versionName.contains("prudent", ignoreCase = true)) "prudente" else "rapide"

    /**
     * Analyse la réponse de `GET /repos/{dépôt}/releases/latest`. [variant] choisit l'interface
     * (« Camybrith-1.2.0-rapide.apk » ou « …-prudente.apk ») ; le pilote est « Camybrith-pilote-*.apk ».
     */
    /** Préfixe des fichiers publiés par ce dépôt : un lien ailleurs n'est jamais téléchargé. */
    const val DOWNLOAD_PREFIX = "https://github.com/$REPO/releases/download/"

    private fun ours(url: String?): String? = url?.takeIf { it.startsWith(DOWNLOAD_PREFIX) }

    fun parse(json: String, variant: String = "rapide"): Release {
        @Suppress("UNCHECKED_CAST")
        val o = Json.parse(json) as? Map<String, Any?> ?: throw IllegalArgumentException("réponse GitHub illisible")
        val tag = o["tag_name"] as? String ?: throw IllegalArgumentException("version sans numéro")
        @Suppress("UNCHECKED_CAST")
        val assets = (o["assets"] as? List<Any?>).orEmpty().mapNotNull { it as? Map<String, Any?> }
        fun name(a: Map<String, Any?>) = (a["name"] as? String).orEmpty()
        val apks = assets.filter { name(it).endsWith(".apk", ignoreCase = true) }
        val pilot = apks.firstOrNull { name(it).contains("pilote", ignoreCase = true) }
        val uis = apks.filter { it !== pilot }
        val apk = uis.firstOrNull { name(it).contains("-$variant", ignoreCase = true) }
            ?: uis.firstOrNull { !name(it).contains("prudent", ignoreCase = true) && variant == "rapide" }
            ?: uis.singleOrNull()
        return Release(
            tag = tag,
            version = versionOf(tag),
            title = (o["name"] as? String)?.takeIf { it.isNotBlank() } ?: tag,
            notes = (o["body"] as? String).orEmpty().trim(),
            apkName = apk?.get("name") as? String,
            apkUrl = ours(apk?.get("browser_download_url") as? String),
            apkSize = (apk?.get("size") as? Number)?.toLong() ?: 0L,
            pilotName = pilot?.get("name") as? String,
            pilotUrl = ours(pilot?.get("browser_download_url") as? String),
            pilotSize = (pilot?.get("size") as? Number)?.toLong() ?: 0L,
        )
    }
}
