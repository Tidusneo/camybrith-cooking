package app.toque.editeur

import app.toque.core.json.Json
import app.toque.core.json.list
import app.toque.core.json.obj
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RecipeStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sample = """
        [{"id": "soup1", "title": "Carrot soup", "author": "Grandma", "rating_score": "4.5",
          "ingredients": ["300 g carrots", "500 g water"], "tags": ["vegetarian"],
          "schema": {"name": "Carrot soup", "recipeYield": "4 servings", "prepTime": "PT10M", "totalTime": "PT35M",
            "recipeCategory": ["Soups"], "keywords": "x",
            "recipeInstructions": [
              {"@type": "HowToStep", "text": "Chop <nobr>5 sec/vitesse 5</nobr>."},
              {"@type": "HowToSection", "name": "Sauce", "itemListElement": [{"@type": "HowToStep", "text": "Cook <nobr>20 min/100°C/vitesse 1</nobr>."}]}
            ]}}]
    """.trimIndent()

    private fun importDir(): File {
        val dir = tmp.newFolder("import")
        File(dir, "data.json.importe").writeText(sample)
        File(dir, "images").mkdirs()
        File(dir, "images/soup1.jpg").writeBytes(byteArrayOf(-1, -40, -1, 0))
        return dir
    }

    @Test fun seedsFromImportedFilesOnce() {
        val imp = importDir()
        val store = RecipeStore(tmp.newFolder("ed"), imp).apply { load() }
        val r = store.get("soup1")!!
        assertEquals("Carrot soup", r.title)
        assertEquals("Grandma", r.author)
        assertEquals(4, r.servings)
        assertEquals(10, r.prepMinutes)
        assertEquals(35, r.totalMinutes)
        assertEquals("Soups", r.category)
        assertEquals(2, r.sections.size)
        assertEquals("Sauce", r.sections[1].name)
        // Ancien réglage Thermomix : gardé en texte libre, l'importeur le convertit.
        assertEquals(StepKind.RAW, r.sections[0].steps[0].kind)
        assertTrue(r.hasImage)

        // Modifié dans l'éditeur, puis rechargé : le fichier d'import déjà lu ne l'écrase pas.
        store.save(r.copy(title = "Soupe"))
        val again = RecipeStore(File(tmp.root, "ed"), imp).apply { load() }
        assertEquals("Soupe", again.get("soup1")!!.title)
    }

    @Test fun keepsUnknownFieldsAndWritesTheDocumentedFormat() {
        val dir = tmp.newFolder("ed")
        val store = RecipeStore(dir, importDir()).apply { load() }
        store.save(store.get("soup1")!!.copy(servings = 6))
        val e = Json.parse(File(dir, "data.json").readText()).list().single().obj()
        assertEquals("4.5", e["rating_score"])
        val schema = e["schema"].obj()
        assertEquals("x", schema["keywords"])
        assertEquals("6 portions", schema["recipeYield"])
        val steps = schema["recipeInstructions"].list()
        assertEquals("Chop <nobr>5 sec/vitesse 5</nobr>.", steps[0].obj()["text"])
        assertEquals("HowToSection", steps[1].obj()["@type"])
    }

    @Test fun createsAndDeletes() {
        val store = RecipeStore(tmp.newFolder("ed")).apply { load() }
        val a = store.save(EditorRecipe(title = "Crème brûlée"))
        val b = store.save(EditorRecipe(title = "Crème brûlée"))
        assertEquals("creme-brulee", a.id)
        assertEquals("creme-brulee-2", b.id)
        assertNotNull(store.entry(a.id))
        assertTrue(store.delete(a.id))
        assertFalse(store.delete(a.id))
        assertEquals(listOf("creme-brulee-2"), store.list().map { it.id })
    }

    @Test fun writesMonsieurCuisineSettingsAndWeighing() {
        val dir = tmp.newFolder("ed")
        val store = RecipeStore(dir).apply { load() }
        val steps = listOf(
            EditorStep("", weigh = Weighing("riz", 300)),
            // Vitesse trop haute en chauffe : ramenée à 3 à l'enregistrement.
            EditorStep("Cuire.", machine = Machine(Program.MANUAL, 600, 7, 100, reverse = true)),
            EditorStep("Fouetter.", machine = Machine(Program.MANUAL, 120, 6, whisk = true)),
            EditorStep("Vapeur.", machine = Machine(Program.STEAM, 1200, 5, 60)),
            EditorStep("Mixer.", machine = Machine(Program.PUREE, 30)),
        )
        val saved = store.save(EditorRecipe(title = "Riz", sections = listOf(EditorSection(steps = steps))))
        val e = Json.parse(File(dir, "data.json").readText()).list().single().obj()
        val written = e["schema"].obj()["recipeInstructions"].list().map { it.obj() }
        assertEquals(mapOf("label" to "riz", "grams" to 300L), written[0]["weigh"])
        val cook = written[1]["machine"].obj()
        assertEquals(listOf("manuel", 600L, 3L, 100L, true, "blade"), listOf(cook["program"], cook["seconds"], cook["speed"], cook["tempC"], cook["reverse"], cook["accessory"]))
        assertEquals(4L, written[2]["machine"].obj()["speed"])
        assertEquals("whisk", written[2]["machine"].obj()["accessory"])
        assertEquals(listOf(120L, 0L), written[3]["machine"].obj().let { listOf(it["tempC"], it["speed"]) })

        val back = store.get(saved.id)!!.sections.single().steps
        assertEquals(Weighing("riz", 300), back[0].weigh)
        assertEquals(Machine(Program.MANUAL, 600, 3, 100, reverse = true), back[1].machine)
        assertEquals(true, back[2].machine!!.whisk)
        assertEquals(Program.PUREE, back[4].machine!!.program)
    }
}
