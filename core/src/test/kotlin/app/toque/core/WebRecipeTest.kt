package app.toque.core

import app.toque.core.recipes.WebRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebRecipeTest {
    private val page = """
        <html><head>
        <script type="application/ld+json">{"@context":"https://schema.org","@graph":[
          {"@type":"WebPage","name":"Blog"},
          {"@type":"Recipe","name":"Tarte aux pommes &amp; cannelle","recipeYield":["6","6 parts"],
           "author":{"@type":"Person","name":"Julie"},"recipeCategory":["Desserts"],
           "prepTime":"PT20M","totalTime":"PT1H5M",
           "aggregateRating":{"@type":"AggregateRating","ratingValue":"4.8","ratingCount":"123"},
           "image":["https://exemple.fr/tarte.jpg"],
           "recipeIngredient":["250 g de farine","125 g de beurre","3 pommes"],
           "recipeInstructions":[
             {"@type":"HowToSection","name":"La pâte","itemListElement":[
               {"@type":"HowToStep","text":"Mettre la farine et le beurre dans le bol, mixer 20 sec/vit. 5 (TM31 : 15 sec/vit. 6)."}]},
             {"@type":"HowToStep","text":"Étaler la pâte. Cuire au four 35 min à 180°C."}]}
        ]}</script></head><body></body></html>
    """.trimIndent()

    @Test
    fun readsTheRecipeFromStructuredData() {
        val r = WebRecipe.parse(page)!!
        assertEquals("Tarte aux pommes & cannelle", r.title)
        assertEquals(6, r.servings)
        assertEquals(listOf("250 g de farine", "125 g de beurre", "3 pommes"), r.ingredients)
        assertEquals("Desserts", r.category)
        assertEquals(20, r.prepMinutes)
        assertEquals(65, r.totalMinutes)
        assertEquals("Julie", r.author)
        assertEquals(4.8, r.rating, 0.001)
        assertEquals(123, r.ratingCount)
        assertEquals("https://exemple.fr/tarte.jpg", r.imageUrl)
        assertTrue(r.steps.any { it.startsWith("La pâte") })
        // Le réglage du bol est repéré, la variante TM31 retirée.
        assertTrue(r.steps.toString(), r.steps.any { it.contains("<nobr>") && it.contains("20 sec") })
        assertTrue(r.steps.none { it.contains("TM31") })
    }

    @Test
    fun pageWithoutRecipeIsRefused() {
        assertNull(WebRecipe.parse("<html><script type=\"application/ld+json\">{\"@type\":\"WebPage\"}</script></html>"))
        assertNull(WebRecipe.parse("<html><body>Pas de données</body></html>"))
    }
}
