package app.toque.editeur

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.css.Style
import org.jetbrains.compose.web.dom.A
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.Form
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.Input
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable

/** État commun de la page : thème, écran (adresse après « # »), connexion, messages. */
class AppState {
    val scope = MainScope()
    var dark by mutableStateOf(false)
    var version by mutableStateOf("")
    var route by mutableStateOf(window.location.hash)
    var needLogin by mutableStateOf(false)
    var ready by mutableStateOf(false)
    var toast by mutableStateOf<Pair<String, Boolean>?>(null)

    /** Modifications non enregistrées : on demande avant de quitter l'écran. */
    var dirty = false

    /** Recette collée et découpée, que l'écran « Nouvelle » ouvre à la place d'une recette vide. */
    var draft: EditorRecipe? = null

    fun go(hash: String) {
        if (window.location.hash == hash) route = hash else window.location.hash = hash
    }

    fun message(text: String, error: Boolean = false) {
        toast = text to error
        scope.launch {
            delay(if (error) 5000 else 2500)
            if (toast?.first == text) toast = null
        }
    }

    fun applyTheme() {
        document.body?.classList?.toggle("dark", dark)
    }
}

fun main() {
    val app = AppState()
    Api.onUnauthorized = { app.needLogin = true }

    var lastHash = window.location.hash
    window.addEventListener("hashchange", {
        val h = window.location.hash
        if (h == lastHash) return@addEventListener
        if (app.dirty && !window.confirm(tr("Quitter sans enregistrer les modifications ?"))) {
            window.history.replaceState(null, "", lastHash.ifEmpty { "#/" })
            return@addEventListener
        }
        app.dirty = false
        lastHash = h
        app.route = h
    })
    window.addEventListener("beforeunload", { e ->
        if (app.dirty) {
            e.preventDefault()
            e.asDynamic().returnValue = ""
        }
    })

    app.scope.launch {
        try {
            val info = Api.info()
            app.dark = info.dark
            app.version = info.version
            document.documentElement?.setAttribute("lang", info.language)
            app.applyTheme()
            WebI18n.texts = Api.strings().texts
            document.title = tr("Éditeur de recettes") + " · Camybrith Cooking"
        } catch (_: Throwable) {
        }
        // QR code du robot : la clé arrive après « #k= » (jamais envoyée au serveur dans l'adresse).
        val fromQr = window.location.hash.takeIf { it.startsWith("#k=") }?.removePrefix("#k=")
        if (fromQr != null) {
            window.history.replaceState(null, "", "#/")
            app.route = "#/"
            try { Api.login(decodeURIComponent(fromQr)) } catch (_: Throwable) {}
        }
        app.needLogin = !Api.hasCode
        app.ready = true
    }

    renderComposable(rootElementId = "root") {
        Style(S)
        if (!app.ready) return@renderComposable
        if (app.needLogin) {
            LoginScreen(app)
        } else {
            Shell(app)
        }
        app.toast?.let { (text, error) ->
            Div({ classes(S.toast); if (error) classes(S.toastError) }) { Text(text) }
        }
    }
}

@Composable
private fun Shell(app: AppState) {
    val route = app.route.removePrefix("#")
    Div({ classes(S.shell) }) {
        Div({ classes(S.rail) }) {
            A(href = "#/", { classes(S.logo) }) {
                Icon.Show(Icon.POT, 40)
                Span({ classes(S.logoName) }) { Text("Camybrith") }
                Span({ classes(S.logoSub) }) { Text("COOKING") }
            }
            A(href = "#/", {
                classes(S.navItem)
                if (!route.startsWith("/new") && !route.startsWith("/r/") && !route.startsWith("/paste")) classes(S.navActive)
            }) {
                Icon.Show(Icon.BOOK, 26)
                Text(tr("Recettes"))
            }
            A(href = "#/new", { classes(S.navItem); if (route.startsWith("/new")) classes(S.navActive) }) {
                Icon.Show(Icon.PLUS, 26)
                Text(tr("Nouvelle"))
            }
            A(href = "#/paste", { classes(S.navItem); if (route.startsWith("/paste")) classes(S.navActive) }) {
                Icon.Show(Icon.COPY, 24)
                Text(tr("Coller"))
            }
            Div({ style { property("flex", "1") } })
            Div({
                classes(S.navItem)
                onClick {
                    Api.logout()
                    app.dirty = false
                    app.needLogin = true
                }
            }) {
                Icon.Show(Icon.LOGOUT, 22)
                Text(tr("Déconnexion"))
            }
            if (app.version.isNotEmpty()) Span({ classes(S.hint) }) { Text(app.version) }
        }
        Div({ classes(S.main) }) {
            when {
                route.startsWith("/new") -> EditorScreen(app, null)
                route.startsWith("/paste") -> PasteScreen(app)
                route.startsWith("/r/") -> EditorScreen(app, decodeURIComponent(route.removePrefix("/r/")))
                else -> ListScreen(app)
            }
        }
    }
}

@Composable
private fun LoginScreen(app: AppState) {
    var code by androidx.compose.runtime.remember { mutableStateOf("") }
    var error by androidx.compose.runtime.remember { mutableStateOf("") }
    var busy by androidx.compose.runtime.remember { mutableStateOf(false) }
    Div({ classes(S.login) }) {
        Div({ style { property("color", "var(--accent)") } }) { Icon.Show(Icon.POT, 64) }
        H1({ classes(S.title) }) { Text(tr("Éditeur de recettes")) }
        P({ classes(S.muted) }) {
            Text(tr("Scannez le QR code affiché sur le robot, ou saisissez la clé de Réglages › Éditeur de recettes."))
        }
        Form(attrs = {
            classes(S.card)
            addEventListener("submit") { e ->
                e.preventDefault()
                if (busy) return@addEventListener
                busy = true
                app.scope.launch {
                    try {
                        if (Api.login(code.trim())) {
                            error = ""
                            app.needLogin = false
                            app.route = window.location.hash
                        } else {
                            error = tr("Clé incorrecte")
                        }
                    } catch (e: Throwable) {
                        error = e.message ?: ""
                    }
                    busy = false
                }
            }
        }) {
            Input(InputType.Text) {
                classes(S.input, S.codeInput)
                attr("autocapitalize", "characters")
                attr("autocomplete", "off")
                attr("spellcheck", "false")
                attr("placeholder", "ABCD-EFGH")
                attr("maxlength", "12")
                attr("autofocus", "")
                value(code)
                onInput { code = it.value.uppercase().filter { c -> c.isLetterOrDigit() || c == '-' } }
            }
            if (error.isNotEmpty()) P({ classes(S.error) }) { Text(error) }
            Div({ style { property("margin-top", "16px") } }) {
                PillButton(tr("Se connecter"), Icon.CHECK, "primary", enabled = code.isNotBlank() && !busy) {}
            }
        }
    }
}

external fun decodeURIComponent(s: String): String
