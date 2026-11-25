package app.toque.editeur

import app.toque.core.recipes.Recipe
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ServerTest {
    @get:Rule val tmp = TemporaryFolder()

    private inner class FakeHost : EditorHost {
        override val dataDir: File get() = File(tmp.root, "ed")
        override val importDir: File? = null
        override val accessCode = "123456"
        override val dark = false
        override val version = "test"
        val published = ArrayList<Recipe>()
        val removed = ArrayList<String>()
        override fun publish(recipe: Recipe, image: File?) { published += recipe }
        override fun unpublish(recipeId: String) { removed += recipeId }
        override fun font(name: String): ByteArray? = null
        override fun log(message: String) {}
    }

    private fun json(r: EditorRecipe) = EditorJson.encodeToString(EditorRecipe.serializer(), r)

    @Test fun editsRecipes() {
        val host = FakeHost()
        val store = RecipeStore(host.dataDir).apply { load() }
        testApplication {
            application { editorModule(store, host) }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/api/recipes").status)
            assertEquals(HttpStatusCode.Unauthorized, client.post("/api/login") { contentType(ContentType.Application.Json); setBody("""{"code":"0"}""") }.status)
            assertEquals(HttpStatusCode.NoContent, client.post("/api/login") { contentType(ContentType.Application.Json); setBody("""{"code":"123456"}""") }.status)

            val recipe = EditorRecipe(
                title = "Soupe", ingredients = listOf("300 g carottes"),
                sections = listOf(EditorSection(steps = listOf(EditorStep("Mixer", machine = Machine(Program.MANUAL, 1200, 8))))),
            )
            val preview = client.post("/api/preview") { header("X-Code", "123456"); contentType(ContentType.Application.Json); setBody(json(recipe)) }.bodyAsText()
            // 20 min à vitesse 8 : plus de 15 min au-delà de la vitesse 6.
            assertTrue(preview, preview.contains("\"review\":true") && preview.contains("Durée maximale"))

            val saved = client.post("/api/recipes") { header("X-Code", "123456"); contentType(ContentType.Application.Json); setBody(json(recipe)) }
            assertEquals(HttpStatusCode.OK, saved.status)
            assertEquals("tm-soupe", host.published.single().id)

            val list = client.get("/api/recipes") { header("Cookie", "editeur=123456") }.bodyAsText()
            assertTrue(list, list.contains("\"id\":\"soupe\"") && list.contains("\"review\":1"))

            val bad = client.put("/api/recipes/soupe/image") { header("X-Code", "123456"); setBody(byteArrayOf(1, 2, 3)) }
            assertEquals(HttpStatusCode.BadRequest, bad.status)
            val img = client.put("/api/recipes/soupe/image") { header("X-Code", "123456"); setBody(byteArrayOf(-1, -40, -1, -32, 0)) }
            assertEquals(HttpStatusCode.NoContent, img.status)
            assertEquals(HttpStatusCode.OK, client.get("/api/recipes/soupe/image") { header("X-Code", "123456") }.status)

            assertEquals(HttpStatusCode.NoContent, client.delete("/api/recipes/soupe") { header("X-Code", "123456") }.status)
            assertEquals(listOf("tm-soupe"), host.removed)
            assertEquals(HttpStatusCode.NotFound, client.get("/api/recipes/soupe") { header("X-Code", "123456") }.status)
        }
    }

    @Test fun servesTheWebPage() {
        val host = FakeHost()
        testApplication {
            application { editorModule(RecipeStore(host.dataDir).apply { load() }, host) }
            val page = client.get("/")
            assertEquals(HttpStatusCode.OK, page.status)
            assertTrue(page.bodyAsText().contains("editeur.js"))
            assertEquals(HttpStatusCode.OK, client.get("/editeur.js").status)
            assertEquals(HttpStatusCode.NotFound, client.get("/../data.json").status)
        }
    }

    @Test fun readsCookie() {
        assertEquals("123456", cookie("a=1; toque_code=123456; b=2", "toque_code"))
        assertEquals("x", cookie("toque_code=\"x\"", "toque_code"))
        assertEquals(null, cookie("toque_codex=1; junk", "toque_code"))
        assertEquals(null, cookie(null, "toque_code"))
    }
}
