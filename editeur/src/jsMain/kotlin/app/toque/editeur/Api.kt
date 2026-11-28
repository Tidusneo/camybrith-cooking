package app.toque.editeur

import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response
import org.w3c.files.Blob
import kotlin.js.json

class ApiError(val status: Int, message: String) : Exception(message)

/** Appels au serveur de l'appli (même origine). Le code d'accès voyage dans un cookie. */
object Api {
    private const val COOKIE = "editeur"
    private val format = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Appelé quand le serveur refuse le code (page de connexion). */
    var onUnauthorized: () -> Unit = {}

    private suspend fun send(method: String, path: String, body: dynamic = null, type: String? = null): Response {
        val headers = json()
        if (type != null) headers["Content-Type"] = type
        val r = window.fetch(path, RequestInit(method = method, headers = headers, body = body, credentials = "same-origin".asDynamic())).await()
        if (r.status.toInt() == 401) {
            onUnauthorized()
            throw ApiError(401, tr("Code d'accès refusé"))
        }
        if (!r.ok) {
            val text = try { r.text().await() } catch (_: Throwable) { "" }
            throw ApiError(r.status.toInt(), text.ifBlank { tr("Erreur du serveur (%d)", r.status.toInt()) })
        }
        return r
    }

    private suspend fun <T> read(r: Response, s: KSerializer<T>): T = format.decodeFromString(s, r.text().await())

    private suspend fun <T, R> sendJson(method: String, path: String, value: T, s: KSerializer<T>, out: KSerializer<R>): R =
        read(send(method, path, format.encodeToString(s, value), "application/json"), out)

    suspend fun info(): Info = read(send("GET", "/api/info"), Info.serializer())
    suspend fun strings(): Strings = read(send("GET", "/api/strings"), Strings.serializer())

    suspend fun login(code: String): Boolean {
        val r = window.fetch("/api/login", RequestInit(method = "POST", headers = json("Content-Type" to "application/json"),
            body = format.encodeToString(Login.serializer(), Login(code)))).await()
        when (r.status.toInt()) {
            204 -> {
                // 30 jours ; une nouvelle clé sur le robot déconnecte tout de suite.
                document.cookie = "$COOKIE=${encodeURIComponent(code)}; path=/; max-age=2592000; SameSite=Strict"
                return true
            }
            429 -> throw ApiError(429, tr("Trop d'essais : patientez une minute"))
            else -> return false
        }
    }

    fun logout() {
        document.cookie = "$COOKIE=; path=/; max-age=0"
    }

    val hasCode: Boolean get() = document.cookie.split(";").any { it.trim().startsWith("$COOKIE=") }

    suspend fun recipes(): List<RecipeSummary> = read(send("GET", "/api/recipes"), ListSerializer(RecipeSummary.serializer()))
    suspend fun recipe(id: String): EditorRecipe = read(send("GET", "/api/recipes/" + encodeURIComponent(id)), EditorRecipe.serializer())

    suspend fun save(r: EditorRecipe): SaveResult =
        if (r.id.isEmpty()) sendJson("POST", "/api/recipes", r, EditorRecipe.serializer(), SaveResult.serializer())
        else sendJson("PUT", "/api/recipes/" + encodeURIComponent(r.id), r, EditorRecipe.serializer(), SaveResult.serializer())

    suspend fun delete(id: String) { send("DELETE", "/api/recipes/" + encodeURIComponent(id)) }

    suspend fun paste(text: String): EditorRecipe = sendJson("POST", "/api/paste", Paste(text), Paste.serializer(), EditorRecipe.serializer())

    suspend fun link(url: String): EditorRecipe = sendJson("POST", "/api/link", Link(url), Link.serializer(), EditorRecipe.serializer())

    suspend fun preview(r: EditorRecipe): Preview = sendJson("POST", "/api/preview", r, EditorRecipe.serializer(), Preview.serializer())

    suspend fun uploadImage(id: String, jpeg: Blob) { send("PUT", "/api/recipes/" + encodeURIComponent(id) + "/image", jpeg, "image/jpeg") }

    fun imageUrl(id: String, version: Long) = "/api/recipes/" + encodeURIComponent(id) + "/image?v=$version"
}

external fun encodeURIComponent(s: String): String
