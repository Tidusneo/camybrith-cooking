package app.toque.editeur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text

/** Initiales d'une vignette sans photo, comme dans l'appli. */
fun initials(title: String): String =
    title.split(Regex("\\s+")).filter { it.length > 2 }.take(2).joinToString("") { it.take(1).uppercase() }
        .ifEmpty { title.take(1).uppercase() }

fun placeholderStyle(seed: String, dark: Boolean): String {
    val (a, b) = Palette.placeholder(seed, dark)
    return "linear-gradient(135deg, $a, $b)"
}

private fun normalize(s: String): String =
    s.lowercase().asDynamic().normalize("NFD").unsafeCast<String>().replace(Regex("[\\u0300-\\u036f]"), "")

@Composable
fun ListScreen(app: AppState) {
    var recipes by remember { mutableStateOf<List<RecipeSummary>?>(null) }
    var error by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            recipes = Api.recipes()
        } catch (e: Throwable) {
            error = e.message ?: ""
        }
    }

    Div({ classes(S.header) }) {
        H1({ classes(S.title) }) { Text(tr("Mes recettes")) }
        PillButton(tr("Nouvelle recette"), Icon.PLUS, "primary") { app.go("#/new") }
    }
    Div({ classes(S.search) }) {
        Icon.Show(Icon.SEARCH, 24)
        Input(InputType.Search) {
            classes(S.searchInput)
            attr("placeholder", tr("Rechercher une recette"))
            value(query)
            onInput { query = it.value }
        }
    }
    if (error.isNotEmpty()) P({ classes(S.error) }) { Text(error) }
    val all = recipes ?: run {
        if (error.isEmpty()) P({ classes(S.muted) }) { Text(tr("Chargement…")) }
        return
    }
    val q = normalize(query.trim())
    val shown = all.filter { q.isEmpty() || normalize(it.title + " " + it.author + " " + it.category).contains(q) }
    if (all.isEmpty()) {
        Div({ classes(S.card, S.emptyState) }) {
            P { Text(tr("Aucune recette pour l'instant.")) }
            P({ classes(S.muted) }) {
                Text(tr("Les recettes créées ici sont envoyées dans l'appli, avec celles des autres robots."))
            }
            PillButton(tr("Créer une recette"), Icon.PLUS, "primary") { app.go("#/new") }
        }
        return
    }
    if (shown.isEmpty()) {
        P({ classes(S.muted) }) { Text(tr("Aucune recette ne correspond.")) }
        return
    }
    Div({ classes(S.tiles) }) {
        shown.forEach { r -> Tile(app, r) }
    }
}

@Composable
private fun Tile(app: AppState, r: RecipeSummary) {
    A(href = "#/r/" + encodeURIComponent(r.id), { classes(S.tile) }) {
        Div({
            classes(S.tileImage)
            style {
                if (r.hasImage) property("background-image", "url(\"${Api.imageUrl(r.id, r.updated)}\")")
                else property("background-image", placeholderStyle(r.title, app.dark))
            }
        }) { if (!r.hasImage) Text(initials(r.title)) }
        Div({ classes(S.tileBody) }) {
            Span({ classes(S.tileTitle) }) { Text(r.title.ifBlank { tr("Sans titre") }) }
            val meta = listOf(r.category, r.author).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) Span({ classes(S.muted) }) { Text(meta) }
            Div({ classes(S.row) }) {
                Chip(if (r.steps == 1) tr("1 étape") else tr("%d étapes", r.steps))
                if (r.review > 0) Chip(tr("%d à vérifier", r.review), "warn", Icon.WARN)
            }
        }
    }
}
