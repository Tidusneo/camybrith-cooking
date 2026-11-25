package app.toque.editeur

import kotlinx.serialization.Serializable

/**
 * Recette telle que l'éditeur la manipule. Enregistrée dans le format du fichier data.json
 * (docs/RECIPES.en.md), avec les réglages du Monsieur Cuisine dans les étapes, puis lue par
 * l'importeur de l'appli (Cookidoo).
 */
@Serializable
data class EditorRecipe(
    /** Identifiant du fichier data.json (sans le préfixe « tm- » de l'appli) ; vide = nouvelle. */
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val servings: Int = 0,
    val prepMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val category: String = "",
    val tags: List<String> = emptyList(),
    /** Une ligne par ingrédient : « 300 g carottes ». */
    val ingredients: List<String> = emptyList(),
    /** Au moins une section ; une section sans nom = étapes sans titre. */
    val sections: List<EditorSection> = listOf(EditorSection()),
    /** Rempli par le serveur. */
    val hasImage: Boolean = false,
    val updated: Long = 0,
)

@Serializable
data class EditorSection(val name: String = "", val steps: List<EditorStep> = emptyList())

/**
 * Une étape : un texte et, au plus, un réglage du Monsieur Cuisine ([machine]) ou une pesée
 * ([weigh]). Une étape lue d'un ancien fichier avec des réglages Thermomix (`<nobr>…</nobr>`)
 * reste en texte libre ([raw]) : l'importeur la convertit comme avant.
 */
@Serializable
data class EditorStep(
    val text: String = "",
    val machine: Machine? = null,
    val weigh: Weighing? = null,
    val raw: Boolean = false,
) {
    val kind: StepKind
        get() = when {
            raw -> StepKind.RAW
            machine != null -> StepKind.MACHINE
            weigh != null -> StepKind.WEIGH
            else -> StepKind.TEXT
        }

    companion object {
        val TAG = Regex("<nobr>([\\s\\S]*?)</nobr>")

        /** Étape lue d'un texte data.json sans champ « machine » ni « weigh ». */
        fun fromText(text: String): EditorStep {
            val t = text.trim()
            return if (TAG.containsMatchIn(t)) EditorStep(t, raw = true) else EditorStep(t)
        }
    }
}

@Serializable
enum class StepKind { TEXT, MACHINE, WEIGH, RAW }

/** Programmes d'une étape, clés du format de l'appli (StepProgram dans core). */
@Serializable
enum class Program(val key: String) {
    MANUAL("manuel"), KNEAD("petrir"), STEAM("vapeur"), TOAST("rissoler"), TURBO("turbo"),
    PUREE("mixer"), SMOOTHIE("smoothie"), SOUS_VIDE("sous-vide"), SLOW_COOK("mijoter"), FERMENT("fermenter");

    companion object {
        fun of(key: String?): Program? = values().firstOrNull { it.key == key?.trim()?.lowercase() }
    }
}

/** Réglage du Monsieur Cuisine, comme l'appli l'exécute (StepMachine dans core). */
@Serializable
data class Machine(
    val program: Program = Program.MANUAL,
    /** Durée en secondes (une valeur, pas de fourchette). */
    val seconds: Int = 60,
    /** 1 à 10 (cuisson manuelle). */
    val speed: Int = 1,
    /** °C, 0 = sans chauffe. */
    val tempC: Int = 0,
    val reverse: Boolean = false,
    /** Fouet monté sur la lame (cuisson manuelle). */
    val whisk: Boolean = false,
    /** Turbo : nombre d'impulsions. */
    val pulses: Int = 0,
)

/** Pesée sur la balance du robot. */
@Serializable
data class Weighing(val label: String = "", val grams: Int = 0)

@Serializable
data class RecipeSummary(
    val id: String,
    val title: String,
    val author: String = "",
    val category: String = "",
    val steps: Int = 0,
    /** Étapes dont le réglage a été adapté ou est à faire à la main. */
    val review: Int = 0,
    val hasImage: Boolean = false,
    val updated: Long = 0,
)

/** Ce que le robot fera de la recette (conversion de l'importeur). */
@Serializable
data class Preview(val steps: List<PreviewStep> = emptyList(), val notes: String = "", val error: String = "")

@Serializable
data class PreviewStep(
    val text: String,
    val machine: String = "",
    val review: Boolean = false,
    val manual: Boolean = false,
    /** « 300 g · riz » pour une pesée. */
    val weigh: String = "",
    /** Règles de sécurité enfreintes, traduites. */
    val problems: List<String> = emptyList(),
)

@Serializable
data class Info(val dark: Boolean = false, val language: String = "fr", val version: String = "")

@Serializable
data class Strings(val texts: Map<String, String> = emptyMap())

@Serializable
data class Login(val code: String)

@Serializable
data class SaveResult(val recipe: EditorRecipe, val preview: Preview)
