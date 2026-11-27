package app.toque.recipes

import android.os.Handler
import android.os.Looper
import app.toque.core.i18n.tr
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.Recipe
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Télécharge une page cookomix.com, la convertit ([Cookomix.parse]) et, sur demande, l'ajoute aux
 * recettes perso avec sa photo. Rien n'est enregistré tant que l'utilisateur n'a pas validé.
 */
class CookomixImport(private val repo: RecipeRepo) {
    class Preview(val recipe: Recipe, val url: String, val imageUrl: String?, val image: File?)

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Lit la recette ; [done] reçoit l'aperçu ou un message d'erreur, sur le fil principal. */
    fun preview(link: String, done: (Preview?, String?) -> Unit) {
        val url = Cookomix.normalizeUrl(link)
        if (!Cookomix.isCookomixUrl(url)) {
            done(null, tr("Ce lien n'est pas une recette Cookomix (cookomix.com/recettes/…)."))
            return
        }
        exec.execute {
            val result: Pair<Preview?, String?> = try {
                val html = String(download(url, 4 * 1024 * 1024), Charsets.UTF_8)
                val r = Cookomix.parse(html, url)
                if (r == null) null to tr("Recette introuvable sur cette page (recette Premium, ou page modifiée par Cookomix).")
                else {
                    val imgUrl = Cookomix.findRecipe(html)?.let { Cookomix.imageUrl(it) }
                    // Photo dans un fichier temporaire : elle ne rejoint les images qu'à l'enregistrement.
                    val tmp = imgUrl?.let { u ->
                        try { File(repo.imagesDir, ".apercu-${r.id}.jpg").apply { writeBytes(download(u, 3 * 1024 * 1024)) } } catch (_: Exception) { null }
                    }
                    Preview(r, url, imgUrl, tmp) to null
                }
            } catch (e: Exception) {
                null to tr("Téléchargement impossible : vérifiez le Wi-Fi du robot (%s).", e.javaClass.simpleName)
            }
            main.post { done(result.first, result.second) }
        }
    }

    /** Enregistre la recette dans « Mes recettes » (remplace une version déjà importée). */
    fun save(p: Preview, done: () -> Unit) {
        exec.execute {
            p.image?.let { tmp -> if (tmp.exists()) tmp.renameTo(File(repo.imagesDir, p.recipe.image)) }
            repo.upsertAll(listOf(p.recipe))
            main.post(done)
        }
    }

    private fun download(url: String, maxBytes: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 8.1) CamybrithCooking")
        c.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9")
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { readLimited(it, maxBytes) }
        } finally {
            c.disconnect()
        }
    }

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw java.io.IOException("page trop grande")
        }
        return out.toByteArray()
    }
}
