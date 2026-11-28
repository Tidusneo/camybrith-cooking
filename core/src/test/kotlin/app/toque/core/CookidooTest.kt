package app.toque.core

import app.toque.core.json.Json
import app.toque.core.machine.Accessory
import app.toque.core.machine.TemperatureTable
import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.StepProgram
import app.toque.core.recipes.Weigh
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Exemples écrits pour le test, au format décrit dans docs/RECETTES.md (pas de vraies recettes). */
class CookidooTest {
    private var before = TemperatureTable.current

    @Before
    fun smart() {
        before = TemperatureTable.current
        TemperatureTable.current = TemperatureTable.SMART
    }

    @After
    fun restore() {
        TemperatureTable.current = before
    }

    private fun one(raw: String) = Cookidoo.convert(raw).machines.single()

    @Test
    fun hachageSimple() {
        val c = Cookidoo.convert("15-30 sec/vitesse 7")
        val m = c.machines.single()
        assertEquals(StepProgram.MANUAL, m.program)
        assertEquals(15, m.seconds)
        assertEquals(7, m.speed)
        assertEquals(0, m.tempC)
        assertTrue(c.review)
        assertEquals("15 s/vitesse 7 (jusqu'à 30 s)", c.label)
    }

    @Test
    fun traductionExacteSansVerification() {
        val c = Cookidoo.convert("20 min/100°C//vitesse 1")
        val m = c.machines.single()
        assertEquals(1200, m.seconds)
        assertEquals(100, m.tempC)
        assertTrue(m.reverse)
        assertEquals(1, m.speed)
        assertFalse(c.review)
        assertEquals("", c.note)
        assertTrue(m.valid)
    }

    @Test
    fun vitesseMijotageEtDemiVitesse() {
        val spoon = one("12 min/100°C//vitesse ")
        assertEquals(1, spoon.speed)
        assertTrue(spoon.reverse)
        assertEquals(4, one("30 sec/vitesse 3.5").speed)
        assertEquals(2, one("20 min/100°C/vitesse 2.5").speed)
    }

    @Test
    fun chauffeLimiteeAVitesse3EtTemperatureGraduee() {
        val c = Cookidoo.convert("7 min/98°C/vitesse 4")
        val m = c.machines.single()
        assertEquals(3, m.speed)
        assertEquals(95, m.tempC)
        assertTrue(c.review)
        assertTrue(c.note.contains("vitesse 3 au plus en chauffe"))
        assertTrue(m.valid)
    }

    @Test
    fun sensInverseLimiteAVitesse3() {
        assertEquals(3, one("40 sec//vitesse 4").speed)
    }

    @Test
    fun varomaDevientVapeur() {
        val m = one("25 min/Varoma//vitesse 1")
        assertEquals(StepProgram.STEAM, m.program)
        assertEquals(1500, m.seconds)
    }

    @Test
    fun petrinEnDeuxFoisAuDelaDe2Min() {
        val c = Cookidoo.convert("Pétrin /2 min 30 sec")
        assertEquals(listOf(120, 30), c.machines.map { it.seconds })
        assertTrue(c.machines.all { it.program == StepProgram.KNEAD })
        assertEquals(StepProgram.KNEAD, one("Pétrin /90 sec").program)
    }

    @Test
    fun turboEtMixage() {
        val t = one("Turbo/0.5 sec/3-5 fois")
        assertEquals(StepProgram.TURBO, t.program)
        assertEquals(3, t.pulses)
        val b = one("mixage /1 min")
        assertEquals(StepProgram.PUREE, b.program)
        assertEquals(60, b.seconds)
    }

    @Test
    fun grandeVitesseLimiteeA10Min() {
        assertEquals(600, one("12 min/vitesse 8").seconds)
    }

    @Test
    fun fouetLimiteAVitesse4() {
        val m = Cookidoo.convert("3 min/vitesse 5", whisk = true).machines.single()
        assertEquals(Accessory.WHISK, m.accessory)
        assertEquals(4, m.speed)
    }

    @Test
    fun sansDureeARéglerALaMain() {
        val c = Cookidoo.convert("100°C//vitesse 1")
        assertTrue(c.machines.isEmpty())
        assertTrue(c.note.startsWith(Cookidoo.MANUAL_MARK))
    }

    @Test
    fun durees() {
        assertEquals(90..90, Cookidoo.duration("1 min 30 sec"))
        assertEquals(1500..1800, Cookidoo.duration("25-30 min"))
        assertEquals(1..1, Cookidoo.duration("0.5 sec"))
        assertNull(Cookidoo.duration("vitesse 3"))
    }

    @Test
    fun ingredients() {
        val a = Cookidoo.ingredient("100 g de pain rassis")
        assertEquals(listOf("100", "g", "pain rassis"), listOf(a.qty, a.unit, a.name))
        val b = Cookidoo.ingredient(Cookidoo.clean("1 &frac12; c. à café rase de sel"))
        assertEquals(listOf("1 ½", "c. à café rase", "sel"), listOf(b.qty, b.unit, b.name))
        val c = Cookidoo.ingredient("1 gousse d'ail")
        assertEquals(listOf("1", "gousse", "ail"), listOf(c.qty, c.unit, c.name))
        val d = Cookidoo.ingredient("2 œufs")
        assertEquals(listOf("2", "", "œufs"), listOf(d.qty, d.unit, d.name))
        assertEquals("beurre", Cookidoo.ingredient(" beurre").name)
    }

    @Test
    fun recetteComplete() {
        val json = """
        [{"id":"r1","title":"Soupe d'essai","rating_count":"12","rating_score":"4.5",
          "ingredients":["300 g de carottes","500 g d'eau"],"tags":["végétarien"],
          "schema":{"name":"Soupe d'essai","recipeYield":"4 portions","prepTime":"PT10M","totalTime":"PT1H5M","recipeCategory":["Soupes"],
            "recipeInstructions":[
              {"@type":"HowToStep","text":"Mettre les carottes dans le bol et hacher <nobr>5 sec/vitesse 5</nobr>. Racler les parois."},
              {"@type":"HowToStep","text":"Ajouter l'eau et cuire <nobr>20 min/100°C/vitesse 1</nobr>. Mixer <nobr>1 min/vitesse 5-10</nobr>. Servir."}]}}]
        """
        val root = Json.parse(json)
        assertTrue(Cookidoo.isExport(root))
        val (list, bad) = Cookidoo.parseAll(root)
        assertEquals(0, bad)
        val r = list.single()
        assertEquals("tm-r1", r.id)
        assertEquals(4, r.servings)
        assertEquals(65, r.totalMinutes)
        assertEquals("Soupes", r.category)
        assertEquals(2, r.allIngredients.size)
        assertEquals("eau", r.allIngredients[1].name)
        assertEquals(3, r.steps.size)
        assertEquals("Mettre les carottes dans le bol et hacher 5 s/vitesse 5. Racler les parois.", r.steps[0].text)
        assertEquals(1200, r.steps[1].machine!!.seconds)
        assertTrue(r.steps[2].text.startsWith("Mixer 1 min/vitesse"))
        assertTrue(r.steps.all { it.machine!!.valid })
    }

    @Test
    fun deuxReglagesDansUnePhraseEtSections() {
        val steps = Cookidoo.steps("Ajouter la crème, puis chauffer <nobr>3 min/80°C/vitesse 2</nobr> et mixer <nobr>5 sec/vitesse 5</nobr>. Servir.")
        assertEquals(2, steps.size)
        assertEquals("Ajouter la crème, puis chauffer 3 min/80 °C/vitesse 2.", steps[0].text)
        assertEquals("Mixer 5 s/vitesse 5. Servir.", steps[1].text)
        assertEquals(180, steps[0].machine!!.seconds)
        assertEquals(5, steps[1].machine!!.seconds)

        val json = """[{"id":"r2","title":"Avec sections","ingredients":[],
          "schema":{"recipeInstructions":[{"@type":"HowToSection","name":"La sauce","itemListElement":[
            {"@type":"HowToStep","text":"Hacher <nobr>2 sec/vitesse 8</nobr>."}]}]}}]"""
        val r = Cookidoo.parseAll(Json.parse(json)).first.single()
        assertEquals(listOf("La sauce :", "Hacher 2 s/vitesse 8."), r.steps.map { it.text })
    }

    @Test
    fun etapesAuFormatDuMonsieurCuisine() {
        val json = """[{"id":"r3","title":"Natif","ingredients":["300 g de riz"],
          "schema":{"recipeInstructions":[
            {"@type":"HowToStep","text":"","weigh":{"label":"riz","grams":300}},
            {"@type":"HowToStep","text":"Cuire.","machine":{"program":"manuel","seconds":600,"speed":1,"tempC":100,"reverse":true,"accessory":"blade","pulses":0}},
            {"@type":"HowToStep","text":"Trop vite.","machine":{"program":"manuel","seconds":60,"speed":8,"tempC":100}},
            {"@type":"HowToStep","text":"Mixer.","machine":{"program":"mixer","seconds":30}}]}}]"""
        val saved = TemperatureTable.current
        TemperatureTable.current = TemperatureTable.SMART
        try {
            val r = Cookidoo.parseAll(Json.parse(json)).first.single()
            assertEquals(4, r.steps.size)
            assertEquals(Weigh("riz", 300), r.steps[0].weigh)
            assertEquals("Peser 300 g de riz", r.steps[0].text)
            val cook = r.steps[1].machine!!
            assertEquals(StepMachine(StepProgram.MANUAL, 600, 1, 100, true), cook)
            assertEquals("Cuire.", r.steps[1].text)
            assertFalse(r.steps[1].needsReview)
            assertTrue(r.steps[2].needsReview)
            assertEquals(StepProgram.PUREE, r.steps[3].machine!!.program)
            assertFalse(r.steps[3].needsReview)
            assertFalse("Thermomix" in r.tags)
            assertEquals("1 réglage est à vérifier.", r.notes)
        } finally {
            TemperatureTable.current = saved
        }
    }
}
