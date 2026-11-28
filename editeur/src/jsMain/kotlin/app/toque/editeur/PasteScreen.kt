package app.toque.editeur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextArea

/**
 * Coller une recette trouvée ailleurs : le robot la découpe (titre, portions, ingrédients,
 * étapes), repère les réglages Thermomix et les convertit pour le Monsieur Cuisine Smart. Le
 * résultat s'ouvre dans l'éditeur, à relire avant d'enregistrer.
 */
@Composable
fun PasteScreen(app: AppState) {
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf("") }

    Div({ classes(S.header) }) {
        H1({ classes(S.title) }) { Text(tr("Coller une recette")) }
        // Sur téléphone, la barre de gauche est masquée : retour à la liste ici.
        PillButton(tr("Mes recettes"), Icon.BACK, "ghost") { app.go("#/") }
    }
    // Par lien : le robot lit la recette publiée dans la page (données schema.org).
    Div({ classes(S.card) }) {
        H2 { Text(tr("Importer depuis un lien")) }
        P({ classes(S.muted) }) {
            Text(tr("Collez l'adresse d'une page de recette (blog, site de cuisine) : le robot récupère le titre, les ingrédients et les étapes, et convertit les réglages Thermomix."))
        }
        Input(InputType.Url) {
            classes(S.input)
            attr("placeholder", "https://…")
            value(url)
            onInput { url = it.value; linkError = "" }
        }
        if (linkError.isNotEmpty()) P({ classes(S.error) }) { Text(linkError) }
        Div({ style { property("margin-top", "12px") } }) {
            PillButton(if (busy) tr("Lecture de la page…") else tr("Importer"), Icon.CHECK, "primary", enabled = url.isNotBlank() && !busy) {
                busy = true
                app.scope.launch {
                    try {
                        app.draft = Api.link(url)
                        app.go("#/new")
                    } catch (e: Throwable) {
                        linkError = e.message ?: ""
                    }
                    busy = false
                }
            }
        }
    }
    Div({ classes(S.card) }) {
        H2 { Text(tr("Ou coller le texte")) }
        P({ classes(S.muted) }) {
            Text(tr("Collez une recette entière : titre, ingrédients et étapes. Les réglages écrits à la manière Thermomix (par exemple « 20 min/100°C/vit. 1 ») sont convertis pour le Monsieur Cuisine Smart. Vous relirez le résultat avant de l'enregistrer."))
        }
        TextArea(text) {
            classes(S.input, S.textarea)
            attr("rows", "14")
            attr("placeholder", tr("Collez le texte de la recette ici"))
            onInput { text = it.value; error = "" }
        }
        P({ classes(S.hint) }) { Text(tr("Collez uniquement des recettes que vous avez le droit d'utiliser.")) }
        if (error.isNotEmpty()) P({ classes(S.error) }) { Text(error) }
        Div({ style { property("margin-top", "12px") } }) {
            PillButton(tr("Convertir"), Icon.CHECK, "primary", enabled = text.isNotBlank() && !busy) {
                busy = true
                app.scope.launch {
                    try {
                        app.draft = Api.paste(text)
                        app.go("#/new")
                    } catch (e: Throwable) {
                        error = e.message ?: ""
                    }
                    busy = false
                }
            }
        }
    }
}
