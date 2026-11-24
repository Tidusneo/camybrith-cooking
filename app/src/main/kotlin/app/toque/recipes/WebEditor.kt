package app.toque.recipes

import app.toque.ToqueApp
import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.Recipe
import app.toque.editeur.EditorHost
import app.toque.editeur.RecipeEditorServer
import app.toque.ui.Ui
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.Executors

/**
 * Éditeur de recettes web (module :editeur) : démarré dans le processus de l'interface quand il
 * est activé dans les réglages. Les recettes enregistrées dans la page passent par l'importeur
 * (comme un data.json déposé dans le dossier d'import) puis arrivent dans le catalogue.
 */
object WebEditor {
    private var server: RecipeEditorServer? = null
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "toque-editeur").apply { isDaemon = true } }

    @Volatile var error: String = ""
        private set

    val running: Boolean get() = server?.running == true
    val port: Int get() = RecipeEditorServer.DEFAULT_PORT

    /** Démarre ou arrête le serveur selon le réglage ; [done] est appelé (hors du fil principal) après. */
    fun apply(app: ToqueApp, done: (() -> Unit)? = null) {
        exec.execute {
            try {
                val s = server ?: RecipeEditorServer(Host(app)).also { server = it }
                if (app.prefs.editorEnabled) s.start() else s.stop()
                error = ""
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
                app.log.write("editeur", "démarrage impossible : $error")
                try { server?.stop() } catch (_: Throwable) {}
                server = null
            }
            done?.invoke()
        }
    }

    /** Adresses du robot sur le réseau local (Wi-Fi, Ethernet), pour les afficher dans les réglages. */
    fun addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { !it.isLoopbackAddress }
            .sortedByDescending { it.isSiteLocalAddress }
            .map { "http://" + it.hostAddress + ":" + port }
    } catch (_: Exception) {
        emptyList()
    }

    private class Host(private val app: ToqueApp) : EditorHost {
        override val dataDir: File get() = File(app.filesDir, "editeur")
        override val importDir: File? get() = try { app.getExternalFilesDir("import") } catch (_: Exception) { null }
        override val accessCode: String get() = app.prefs.editorCode
        override val dark: Boolean get() = Ui.resolve(Ui.Theme.of(app.prefs.theme)) == Ui.Theme.DARK
        override val version: String
            get() = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }

        override fun publish(recipe: Recipe, image: File?) {
            val dest = File(app.recipes.imagesDir, Cookidoo.imageName(recipe.id))
            if (image != null && image.isFile) image.copyTo(dest, overwrite = true)
            app.recipes.upsertAll(listOf(recipe))
        }

        override fun unpublish(recipeId: String) {
            app.recipes.remove(listOf(recipeId))
            File(app.recipes.imagesDir, Cookidoo.imageName(recipeId)).delete()
        }

        override fun font(name: String): ByteArray? = try {
            app.assets.open("fonts/$name").use { it.readBytes() }
        } catch (_: Exception) {
            null
        }

        override fun log(message: String) = app.log.write("editeur", message)
    }
}
