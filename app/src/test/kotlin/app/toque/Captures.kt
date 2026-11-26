package app.toque

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import app.toque.core.machine.LinkState
import app.toque.core.machine.Snapshot
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.Step
import app.toque.core.recipes.StepMachine
import app.toque.core.recipes.Weigh
import app.toque.ui.MainActivity
import app.toque.ui.Screen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Captures d'écran de l'interface, à l'échelle de l'écran du Monsieur Cuisine Smart (1280 × 800),
 * sans robot ni émulateur. Les images vont dans app/build/captures.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28], qualifiers = "w1280dp-h800dp-land-mdpi")
abstract class Captures {
    protected val dir = File(System.getProperty("captures.dir") ?: "build/captures").apply { mkdirs() }

    protected fun activity(setup: (Prefs) -> Unit = {}): MainActivity {
        val application = androidx_free_app()
        // Pas de service machine : l'interface reçoit ses états par MachineClient.preview().
        val sink = android.os.Messenger(android.os.Handler(android.os.Looper.getMainLooper()) { true })
        org.robolectric.Shadows.shadowOf(application).setComponentNameAndServiceForBindService(
            android.content.ComponentName(app.toque.machine.Ipc.PILOT_PACKAGE, app.toque.machine.Ipc.PILOT_SERVICE), sink.binder)
        setup(application.prefs)
        app.toque.core.machine.TemperatureTable.current = application.prefs.activeRobot.temperatures
        val a = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        // Laisser passer l'animation d'ouverture.
        for (i in 0 until 40) idle(100)
        return a
    }

    private fun androidx_free_app(): ToqueApp =
        org.robolectric.RuntimeEnvironment.getApplication() as ToqueApp

    protected fun idle(ms: Long = 50) {
        ShadowLooper.idleMainLooper(ms, TimeUnit.MILLISECONDS)
        Thread.sleep(30)
        ShadowLooper.idleMainLooper()
    }

    protected fun shot(a: MainActivity, name: String, frames: Int = 40) {
        idle(400)
        val root: View = a.window.decorView.rootView
        val w = 1280
        val h = 800
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, w, h)
        idle(16)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        // Quelques images de suite : les animations (jauge) avancent comme sur le robot.
        for (i in 0 until frames) {
            root.draw(Canvas(bmp))
            ShadowLooper.idleMainLooper(16, TimeUnit.MILLISECONDS)
        }
        root.draw(Canvas(bmp))
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    protected fun ready(weight: Int = 0, temp: Int = 0) = Snapshot(
        link = LinkState.READY, transportName = "Démo", transportUp = true, hasSample = true,
        weightG = weight, scaleStable = true, actualTempC = temp, bowlDetected = true, firmware = 3090,
    )

    protected fun samples(a: MainActivity) {
        val repo = a.app.recipes
        repo.replaceAll(SAMPLES)
        // Attendre l'écriture et la reconstruction de l'index (fil des recettes).
        for (i in 0 until 100) {
            idle(20)
            if (repo.loaded && repo.count == SAMPLES.size) break
            Thread.sleep(20)
        }
        repo.setFavorite("s4", true)
        repo.setFavorite("s2", true)
        repo.setFavorite("s5", true)
        repo.setFavorite("s3", true)
        idle(200)
        Thread.sleep(200)
        idle(200)
    }

    protected fun push(a: MainActivity, s: Screen) {
        a.push(s)
        idle()
    }

    companion object {
        val RISOTTO = Recipe(
            id = "s1", title = "Risotto aux champignons", category = "Plat", servings = 4, totalMinutes = 35, difficulty = 2,
            ingredients = listOf(IngredientGroup(items = listOf(
                Ingredient("1", "", "oignon"), Ingredient("30", "g", "huile d'olive"), Ingredient("300", "g", "riz arborio"),
                Ingredient("10", "cl", "vin blanc"), Ingredient("80", "cl", "bouillon de légumes"), Ingredient("250", "g", "champignons de Paris"),
                Ingredient("50", "g", "parmesan"),
            ))),
            steps = listOf(
                Step("Mettez l'oignon coupé en deux dans le bol et hachez.", StepMachine(seconds = 5, speed = 5)),
                Step("Raclez les parois, ajoutez 30 g d'huile d'olive et faites revenir.", StepMachine(seconds = 180, speed = 1, tempC = 100)),
                Step("Pesez le riz arborio.", weigh = Weigh("riz arborio", 300)),
                Step("Ajoutez le riz et faites le nacrer en sens inverse, sans le gobelet, jusqu'à ce que les grains deviennent translucides.",
                    StepMachine(seconds = 180, speed = 1, tempC = 100, reverse = true)),
                Step("Versez 10 cl de vin blanc et 80 cl de bouillon de légumes.", null),
                Step("Faites cuire.", StepMachine(seconds = 1080, speed = 1, tempC = 100, reverse = true)),
                Step("Ajoutez le parmesan, mélangez et servez."),
            ),
        )
        val SAMPLES = listOf(
            RISOTTO,
            Recipe("s2", "Velouté de potimarron", "Soupe", totalMinutes = 40),
            Recipe("s3", "Pain de mie maison", "Boulangerie", totalMinutes = 130),
            Recipe("s4", "Poulet au curry et lait de coco", "Plat", totalMinutes = 45, rating = 4.6, ratingCount = 212),
            Recipe("s5", "Gâteau fondant au chocolat", "Dessert", totalMinutes = 50, rating = 4.8, ratingCount = 431),
            Recipe("s6", "Pâte à pizza", "Boulangerie", totalMinutes = 80),
            Recipe("s7", "Soupe à l'oignon gratinée", "Soupe", totalMinutes = 55),
            Recipe("s8", "Blanquette de veau", "Plat", totalMinutes = 105),
            Recipe("s9", "Houmous maison", "Apéritif", totalMinutes = 10),
            Recipe("s10", "Crème pâtissière", "Dessert", totalMinutes = 15),
            Recipe("s11", "Dahl de lentilles corail", "Plat", totalMinutes = 30),
            Recipe("s12", "Brioche tressée", "Boulangerie", totalMinutes = 180),
        )
    }
}

class CapturesInterface : Captures() {
    private fun tour(theme: String) {
        val a = activity { p ->
            p.theme = theme
            p.language = "fr"
            app.toque.core.i18n.I18n.select("fr")
            p.robot = "smart"
            p.guidedRecipe = "s1"
            p.guidedStep = 3
        }
        samples(a)
        val b = a
        b.app.machine.preview(ready())
        shot(b, "$theme-1-accueil")

        b.open(app.toque.ui.Section.RECIPES)
        shot(b, "$theme-2-recettes")
        push(b, app.toque.ui.screens.RecipeScreen(b, RISOTTO))
        shot(b, "$theme-3-fiche")

        b.open(app.toque.ui.Section.MANUAL)
        shot(b, "$theme-4-manuel")
        b.app.machine.preview(ready(weight = 842, temp = 70).copy(
            jobActive = true, jobKind = "MANUAL", jobLabel = "Cuisson manuelle", jobState = "RUNNING",
            remainingMs = 192_000, totalMs = 300_000, elapsedMs = 108_000, jobSpeed = 2, jobTempLevel = 11, speedLevel = 2,
        ))
        shot(b, "$theme-5-cuisson")
        b.app.machine.preview(ready())
        idle()

        // Pas à pas : la pesée du riz (étape 3), en train de couler
        b.open(app.toque.ui.Section.RECIPES)
        val cook = app.toque.ui.screens.CookScreen(b, RISOTTO, 2)
        push(b, cook)
        for (g in intArrayOf(40, 90, 140, 180, 212)) {
            b.app.machine.preview(ready(weight = g))
            idle(200)
        }
        shot(b, "$theme-6-pesee", frames = 8)
        for (g in intArrayOf(260, 300)) {
            b.app.machine.preview(ready(weight = g))
            idle(200)
        }
        shot(b, "$theme-7-pesee-ok", frames = 150)
        b.app.machine.preview(ready(weight = 372))
        shot(b, "$theme-8-pesee-trop", frames = 150)

        // Étape avec quantités dans le texte, puis la fenêtre de pesée
        b.app.machine.preview(ready(weight = 372))
        val go = cook.javaClass.getDeclaredMethod("go", Int::class.javaPrimitiveType).apply { isAccessible = true }
        go.invoke(cook, 4)
        idle()
        shot(b, "$theme-9-etape-quantites")
        val found = app.toque.core.recipes.Quantities.find(RISOTTO.steps[4].text, RISOTTO.allIngredients)
        val open = cook.javaClass.getDeclaredMethod("openWeigh", List::class.java, Int::class.javaPrimitiveType).apply { isAccessible = true }
        open.invoke(cook, found, 0)
        idle()
        for (g in intArrayOf(400, 430, 450)) {
            b.app.machine.preview(ready(weight = g))
            idle(200)
        }
        shot(b, "$theme-10-fenetre-pesee", frames = 10)
        b.closeOverlay()
        go.invoke(cook, 3)
        idle()
        shot(b, "$theme-11-etape-machine")

        b.open(app.toque.ui.Section.PROGRAMS)
        b.app.machine.preview(ready(weight = 320))
        shot(b, "$theme-12-programmes")
        push(b, app.toque.ui.screens.SmartProgramsScreen(b))
        shot(b, "$theme-12b-autres-programmes")
        b.open(app.toque.ui.Section.SCALE)
        b.app.machine.preview(ready(weight = 180))
        idle(1500)
        shot(b, "$theme-13-balance")
        b.open(app.toque.ui.Section.SETTINGS)
        shot(b, "$theme-14-reglages")
        b.confirm("Piloter la vraie machine", "Camybrith Cooking va prendre le contrôle du moteur et de la chauffe à la place de l'appli d'origine.", "Continuer", danger = true) {}
        shot(b, "$theme-15-dialogue")
        b.closeOverlay()
        b.toast("Ajoutée aux favoris", app.toque.ui.Ui.OK)
        shot(b, "$theme-16-message")
    }

    @Test
    fun clair() = tour("light")

    @Test
    fun sombre() = tour("dark")
}
