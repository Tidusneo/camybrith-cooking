package app.toque

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import app.toque.core.i18n.tr
import app.toque.core.json.Json
import app.toque.core.recipes.Ingredient
import app.toque.core.recipes.IngredientGroup
import app.toque.core.recipes.Quantities
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.SearchIndex
import app.toque.core.recipes.Step
import app.toque.core.recipes.TextNorm

/**
 * Sentinelle du compilateur. L'interface n'est plus interprétée (pas de vmSafeMode) : sur le
 * Smart, le compilateur d'Android 8.1 a déjà recompilé de travers des fonctions très appelées
 * (tr() renvoyait null). Toutes les secondes, on refait des calculs dont on connaît le résultat,
 * calculé au démarrage avant toute compilation, et assez souvent pour que le compilateur s'en
 * empare. Au premier écart : journal, alerte, et l'appli conseille la version prudente.
 * Le moteur n'est pas concerné : son pilote est une autre appli, restée interprétée.
 */
class CompilerWatch(private val log: Logbook, private val onProblem: (String) -> Unit) {
    // Fil à part, de faible priorité : le contrôle ne ralentit pas l'interface. Le code compilé
    // est le même pour tous les fils, l'écart se verrait donc ici aussi.
    private val thread = HandlerThread("toque-sentinelle", android.os.Process.THREAD_PRIORITY_BACKGROUND).apply { start() }
    private val main = Handler(thread.looper)
    private var expected: List<String> = emptyList()
    /** Langue dans laquelle [expected] a été calculé : changer de langue n'est pas une anomalie. */
    private var expectedLanguage = ""
    private var rounds = 0L
    @Volatile var failed = false
        private set

    private val index by lazy {
        SearchIndex(listOf(
            Recipe(id = "a", title = "Soupe de potiron", category = "Soupes",
                ingredients = listOf(IngredientGroup("", listOf(Ingredient("500", "g", "potiron")))), steps = listOf(Step("Mixer le potiron"))),
            Recipe(id = "b", title = "Pâte à crêpes", category = "Desserts",
                ingredients = listOf(IngredientGroup("", listOf(Ingredient("250", "g", "farine"))))),
        ))
    }

    /** Calculs témoins : textes traduits, normalisation, recherche, JSON, quantités. */
    private fun compute(): List<String> = listOf(
        tr("Recettes"), tr("Lancer"), tr("Réglages"), tr("Couvercle ouvert"),
        tr("%d recettes importées", 12),
        TextNorm.normalize("Crème brûlée à l'œuf"),
        TextNorm.tokens("Les pommes de terre nouvelles").joinToString(","),
        index.search("crepes").joinToString(",") { it.entry.id },
        index.search("potiron").joinToString(",") { it.entry.id + ":" + it.score },
        Json.write(Json.parse("""{"a":[1,2.5,"é"],"b":true}""")),
        Quantities.find("Ajouter 120 g de farine et 20 cl de lait").joinToString(",") { "${it.label}=${it.grams}" },
    )

    fun start() {
        main.post {
            expected = compute()
            expectedLanguage = app.toque.core.i18n.I18n.code
            main.postDelayed(tick, 1_000)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (failed) return
            // Nouvelle langue choisie dans les réglages : les textes témoins changent, on les recalcule.
            if (app.toque.core.i18n.I18n.code != expectedLanguage) {
                expected = compute()
                expectedLanguage = app.toque.core.i18n.I18n.code
            }
            val t0 = SystemClock.uptimeMillis()
            try {
                // Cent passages : le compilateur traite ces fonctions comme très appelées.
                repeat(100) {
                    val got = compute()
                    if (got != expected) {
                        // Langue changée pendant ce contrôle : pas une anomalie, on recalcule au prochain.
                        if (app.toque.core.i18n.I18n.code != expectedLanguage) {
                            main.postDelayed(this, 1_000)
                            return
                        }
                        val i = got.indices.firstOrNull { got[it] != expected.getOrNull(it) } ?: -1
                        fail("calcul témoin $i : « ${expected.getOrNull(i)} » devenu « ${got.getOrNull(i)} »")
                        return
                    }
                }
            } catch (t: Throwable) {
                fail("calcul témoin en erreur : $t")
                return
            }
            rounds++
            if (rounds == 30L) log.write("compilateur", "sentinelle : 30 contrôles sans écart (${SystemClock.uptimeMillis() - t0} ms le dernier)")
            // Toutes les secondes la première minute, puis toutes les 10 s.
            main.postDelayed(this, if (rounds < 60) 1_000 else 10_000)
        }
    }

    private fun fail(what: String) {
        failed = true
        log.write("compilateur", "ANOMALIE : $what")
        onProblem(what)
    }
}
