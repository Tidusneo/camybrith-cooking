package app.toque.core

import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.PastedText
import app.toque.core.recipes.StepProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PastedTextTest {
    private val risotto = """
        Risotto aux champignons, pour 2 personnes

        Ingrédients :
        1 échalote
        30 g de beurre
        160 g de riz arborio
        450 g de bouillon de légumes

        Préparation :
        1. Mettre l'échalote dans le bol et hacher 5 sec/vit. 5. Racler les parois.
        2. Ajouter le beurre et rissoler 3 min/120°C/vit. 1.
        3. Ajouter le riz et nacrer 2 min/98°C/sens inverse/vit. 1.
        4. Ajouter le bouillon, puis cuire 15-17 min/100°C/sens inverse/vit. mijotage.
    """.trimIndent()

    @Test fun splitsTitleServingsIngredientsAndSteps() {
        val r = PastedText.parse(risotto)
        assertEquals("Risotto aux champignons", r.title)
        assertEquals(2, r.servings)
        assertEquals(listOf("1 échalote", "30 g de beurre", "160 g de riz arborio", "450 g de bouillon de légumes"), r.ingredients)
        assertEquals(4, r.settings)
        assertTrue(r.steps[0], r.steps[0].contains("<nobr>5 sec/vitesse 5</nobr>"))
        assertTrue(r.steps.joinToString(), r.steps.any { it.contains("<nobr>15-17 min/100°C/sens inverse/vitesse mijotage</nobr>") })
        assertTrue(r.steps.any { it.contains("Racler les parois") })
    }

    @Test fun settingsReachTheConverter() {
        val steps = PastedText.parse(risotto).steps.flatMap { Cookidoo.steps(it) }
        val machines = steps.mapNotNull { it.machine }
        assertEquals(4, machines.size)
        assertEquals(5, machines[0].speed)
        // 98 °C ramené à la graduation du Smart, sens inverse gardé.
        assertEquals(95, machines[2].tempC)
        assertTrue(machines[2].reverse)
        // Mijotage : vitesse 1 en sens inverse, durée la plus courte.
        assertEquals(1, machines[3].speed)
        assertTrue(machines[3].reverse)
        assertEquals(15 * 60, machines[3].seconds)
        assertTrue(steps.any { it.needsReview })
    }

    @Test fun freeWordingAndSpecialPrograms() {
        val r = PastedText.parse("""
            Pâte à pizza
            Ingrédients : 250 g de farine, 150 g d'eau, 10 g de levure
            Chauffer l'eau 2 minutes à 37 degrés vitesse 2. Ajouter la farine et pétrir Pétrin/3 min. Turbo/0.5 sec/3-5 fois.
            Cuire à la vapeur 25 min/Varoma/vit. 1.
        """.trimIndent())
        assertEquals("Pâte à pizza", r.title)
        assertEquals(3, r.ingredients.size)
        val machines = r.steps.flatMap { Cookidoo.steps(it) }.mapNotNull { it.machine }
        assertEquals(37, machines[0].tempC)
        assertEquals(2, machines[0].speed)
        assertEquals(StepProgram.KNEAD, machines[1].program)
        assertEquals(StepProgram.KNEAD, machines[2].program)
        assertEquals(StepProgram.TURBO, machines[3].program)
        assertEquals(3, machines[3].pulses)
        assertEquals(StepProgram.STEAM, machines.last().program)
    }

    @Test fun recipeWithoutHeadingsStillSplits() {
        val r = PastedText.parse("""
            Velouté de courgettes
            Pour 4 personnes
            3 courgettes
            1 oignon
            Mixer 10 secondes vitesse 6, puis cuire 20 minutes à 100 degrés vitesse 1.
        """.trimIndent())
        assertEquals("Velouté de courgettes", r.title)
        assertEquals(4, r.servings)
        assertEquals(listOf("3 courgettes", "1 oignon"), r.ingredients)
        assertEquals(2, r.settings)
    }

    @Test fun plainTextHasNoSetting() {
        val r = PastedText.parse("Salade\n2 tomates\nCouper les tomates. Assaisonner.")
        assertEquals(0, r.settings)
        assertEquals(listOf("Couper les tomates. Assaisonner."), r.steps)
    }
}
