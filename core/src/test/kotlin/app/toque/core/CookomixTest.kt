package app.toque.core

import app.toque.core.machine.TemperatureTable
import app.toque.core.recipes.Cookomix
import app.toque.core.recipes.RecipeSource
import app.toque.core.recipes.StepProgram
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Page écrite pour le test, sur le modèle des pages cookomix.com (pas de vraie recette). */
class CookomixTest {
    private var before = TemperatureTable.current

    @Before fun smart() { before = TemperatureTable.current; TemperatureTable.current = TemperatureTable.SMART }
    @After fun restore() { TemperatureTable.current = before }

    private val page = """
        <html><head><script type="application/ld+json">{"@context":"https://schema.org","@graph":[
          {"@type":"WebPage","name":"x"},
          {"@type":"Recipe","name":"Soupe d'essai au thermomix","recipeYield":"4","prepTime":"PT10M","totalTime":"PT40M",
           "image":["https://www.cookomix.com/a-300x200.jpg","https://www.cookomix.com/a-800x600.jpg"],
           "recipeIngredient":["Carottes - 500 grammes","Eau - 800 grammes","Sel - 1 pincée","Huile d'olive - 1 cuillère à soupe"],
           "recipeInstructions":[
             {"@type":"HowToStep","text":"Mettre les carottes dans le bol du Thermomix."},
             {"@type":"HowToStep","text":"Mixer 10 sec/vitesse 5."},
             {"@type":"HowToStep","text":"Cuire 3 min/90°C/vitesse 3."},
             {"@type":"HowToStep","text":"Cuire 12 min/Varoma/vitesse 1."},
             {"@type":"HowToStep","text":"Cuire 8 min/100°C/Vitesse Cuillère."},
             {"@type":"HowToStep","text":"Mettre dans le four pendant 25 min à 200°C."}
           ]}]}</script></head><body></body></html>
    """.trimIndent()

    @Test fun adresse() {
        assertTrue(Cookomix.isCookomixUrl("cookomix.com/recettes/soupe-thermomix/"))
        assertTrue(Cookomix.isCookomixUrl("https://www.cookomix.com/recettes/soupe-thermomix/"))
        assertEquals("ck-soupe-carottes", Cookomix.idFor("https://www.cookomix.com/recettes/soupe-carottes-thermomix/"))
    }

    @Test fun recette() {
        val r = Cookomix.parse(page, "https://www.cookomix.com/recettes/soupe-essai-thermomix/")
        assertNotNull(r); r!!
        assertEquals("Soupe d'essai", r.title)
        assertEquals("ck-soupe-essai", r.id)
        assertEquals(RecipeSource.USER, r.source)
        assertTrue("Cookomix" in r.tags)
        assertEquals(4, r.servings)
        assertEquals(40, r.totalMinutes)
        val ing = r.allIngredients
        assertEquals("500", ing[0].qty); assertEquals("g", ing[0].unit); assertEquals("Carottes", ing[0].name)
        assertEquals("c. à soupe", ing[3].unit)
        assertEquals("https://www.cookomix.com/a-800x600.jpg", Cookomix.imageUrl(Cookomix.findRecipe(page)!!))

        assertTrue(r.steps[0].text, r.steps[0].text.contains("dans le bol") && !r.steps[0].text.contains("Thermomix", true))
        val m = r.steps.mapNotNull { it.machine }
        assertEquals(4, m.size)
        // 10 sec / vitesse 5
        assertEquals(StepProgram.MANUAL, m[0].program); assertEquals(10, m[0].seconds); assertEquals(5, m[0].speed)
        // 3 min / 90 °C / vitesse 3
        assertEquals(180, m[1].seconds); assertEquals(90, m[1].tempC); assertEquals(3, m[1].speed)
        // Varoma → programme vapeur
        assertEquals(StepProgram.STEAM, m[2].program); assertEquals(720, m[2].seconds)
        // Vitesse cuillère → vitesse 1 en sens inverse
        assertEquals(480, m[3].seconds); assertEquals(1, m[3].speed); assertTrue(m[3].reverse)
        // Le four n'est pas un réglage du robot
        assertNull(r.steps.last().machine)
    }

    @Test fun pageSansRecette() {
        assertNull(Cookomix.parse("<html><body>Recette Premium</body></html>", "https://www.cookomix.com/recettes/x/"))
    }
}
