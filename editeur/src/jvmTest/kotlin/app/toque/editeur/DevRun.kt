package app.toque.editeur

import app.toque.core.i18n.I18n
import app.toque.core.recipes.Recipe
import org.junit.Assume
import org.junit.Test
import java.io.File

class DevRun {
    @Test fun run() {
        val dir = System.getenv("EDITEUR_DEV") ?: return Assume.assumeTrue(false)
        // EDITEUR_LANG=es (ou en…) : langue de l'éditeur, fichiers de app/src/main/assets/lang/.
        System.getenv("EDITEUR_LANG")?.let { code ->
            val files = File("../app/src/main/assets/lang").listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            I18n.install(files.map { I18n.parse(it.name.removeSuffix(".json"), it.readText()) })
            I18n.select(code)
        }
        app.toque.core.machine.TemperatureTable.current = app.toque.core.machine.TemperatureTable.SMART
        val host = object : EditorHost {
            override val dataDir = File(dir)
            override val importDir: File? = null
            override val accessCode = "123456"
            override val dark = System.getenv("EDITEUR_DARK") != null
            override val version = "dev"
            override fun publish(recipe: Recipe, image: File?) { println("publish ${recipe.id}") }
            override fun unpublish(recipeId: String) { println("unpublish $recipeId") }
            override fun font(name: String): ByteArray? = File("../app/src/main/assets/fonts/$name").takeIf { it.isFile }?.readBytes()
            override fun log(message: String) { println(message) }
        }
        RecipeEditorServer(host, 8099).start()
        Thread.sleep(30 * 60_000L)
    }
}
