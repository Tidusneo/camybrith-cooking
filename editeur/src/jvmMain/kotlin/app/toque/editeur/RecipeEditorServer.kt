package app.toque.editeur

import app.toque.core.i18n.I18n
import app.toque.core.i18n.tr
import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.Recipe
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import java.io.File

/** Ce que l'appli fournit à l'éditeur (le module ne dépend pas d'Android). */
interface EditorHost {
    /** Dossier de l'éditeur (data.json, images/). */
    val dataDir: File
    /** Dossier d'import de l'appli, pour reprendre les recettes déjà envoyées (peut être absent). */
    val importDir: File?
    /** Code demandé par la page web avant toute modification. */
    val accessCode: String
    /** Thème sombre de l'appli en ce moment. */
    val dark: Boolean
    val version: String
    /** Recette convertie à mettre dans le catalogue de l'appli, avec sa photo s'il y en a une. */
    fun publish(recipe: Recipe, image: File?)
    /** Retire du catalogue la recette d'identifiant [recipeId] (préfixe « tm- » compris). */
    fun unpublish(recipeId: String)
    /** Police de l'appli (« Poppins-Bold.ttf »…), null si absente. */
    fun font(name: String): ByteArray?
    fun log(message: String)
}

/**
 * Serveur web de l'éditeur de recettes, sur le réseau local (toutes les interfaces, port [port]).
 * Sert l'interface (ressources web/ du module) et l'API JSON /api/…
 */
class RecipeEditorServer(private val host: EditorHost, val port: Int = DEFAULT_PORT) {
    private var server: EmbeddedServer<*, *>? = null

    val running: Boolean @Synchronized get() = server != null

    /** Démarre (sans effet s'il tourne déjà). Lève une exception si le port est pris. */
    @Synchronized
    fun start() {
        if (server != null) return
        val store = RecipeStore(host.dataDir, host.importDir).apply { load() }
        val s = embeddedServer(CIO, port = port, host = "0.0.0.0") { editorModule(store, host) }
        s.start(wait = false)
        server = s
        host.log("éditeur démarré sur le port $port")
    }

    @Synchronized
    fun stop() {
        val s = server ?: return
        server = null
        s.stop(200, 1000)
        host.log("éditeur arrêté")
    }

    companion object {
        const val DEFAULT_PORT = 8080
        const val COOKIE = "editeur"
        const val MAX_IMAGE = 8 * 1024 * 1024
    }
}

internal val EditorJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/** Conversion par l'importeur de l'appli : ce que le robot fera de la recette. */
internal object Conversion {
    fun recipe(entry: Map<String, Any?>): Recipe? = Cookidoo.parse(entry)

    fun preview(recipe: Recipe?): Preview {
        if (recipe == null) return Preview(error = tr("Recette illisible par l'importeur"))
        return Preview(
            steps = recipe.steps.map {
                PreviewStep(
                    text = it.text,
                    machine = it.machine?.summary.orEmpty(),
                    review = it.needsReview,
                    manual = it.machine == null && it.text.contains(Cookidoo.MANUAL_MARK),
                    weigh = it.weigh?.let { w -> tr("Peser %d g", w.grams) + if (w.label.isEmpty()) "" else " · " + w.label }.orEmpty(),
                    problems = it.machine?.violations()?.map { v -> v.message }.orEmpty(),
                )
            },
            notes = recipe.notes,
        )
    }

    fun review(recipe: Recipe?): Int = recipe?.steps?.count { it.needsReview } ?: 0
}

/** Limite les essais de code : 10 erreurs, puis une minute d'attente. */
internal class Throttle(private val clock: () -> Long = System::currentTimeMillis) {
    private var failures = 0
    private var lockedUntil = 0L

    @Synchronized fun locked(): Boolean = clock() < lockedUntil

    @Synchronized fun fail() {
        if (++failures >= 10) {
            failures = 0
            lockedUntil = clock() + 60_000
        }
    }

    @Synchronized fun success() { failures = 0 }
}

internal fun Application.editorModule(store: RecipeStore, host: EditorHost) {
    install(ContentNegotiation) { json(EditorJson) }
    // Sans liaison slf4j, Ktor ne journalise rien : les erreurs vont au journal de l'appli.
    intercept(ApplicationCallPipeline.Monitoring) {
        try {
            proceed()
        } catch (e: Throwable) {
            host.log("${context.request.local.method.value} ${context.request.local.uri} : ${e.stackTraceToString().take(2000)}")
            throw e
        }
    }
    val throttle = Throttle()

    fun authorized(call: RoutingCall): Boolean {
        val code = cookie(call.request.headers[HttpHeaders.Cookie], RecipeEditorServer.COOKIE) ?: call.request.headers["X-Code"]
        return host.accessCode.isNotEmpty() && code == host.accessCode
    }

    fun summary(r: EditorRecipe): RecipeSummary {
        val converted = store.entry(r.id)?.let { Conversion.recipe(it) }
        return RecipeSummary(r.id, r.title, r.author, r.category, r.sections.sumOf { it.steps.size }, Conversion.review(converted), r.hasImage, r.updated)
    }

    fun publish(id: String) {
        val entry = store.entry(id) ?: return
        val recipe = Conversion.recipe(entry) ?: return
        host.publish(recipe, store.imageFile(id))
    }

    /** Entrée data.json d'un brouillon, sans l'enregistrer (aperçu). */
    fun draftEntry(r: EditorRecipe): Map<String, Any?> = store.entryOf(r)

    routing {
        get("/") { resource(call, "index.html") }
        get("/{file}") { resource(call, call.parameters["file"].orEmpty()) }
        get("/fonts/{name}") {
            val name = call.parameters["name"].orEmpty()
            val bytes = if (Regex("^[A-Za-z0-9-]+\\.ttf$").matches(name)) host.font(name) else null
            if (bytes == null) call.respond(HttpStatusCode.NotFound)
            else {
                call.response.header(HttpHeaders.CacheControl, "max-age=86400")
                call.respondBytes(bytes, ContentType("font", "ttf"))
            }
        }

        route("/api") {
            get("info") { call.respond(Info(host.dark, I18n.code, host.version)) }
            get("strings") { call.respond(Strings(I18n.current.texts)) }
            post("login") {
                if (throttle.locked()) return@post call.respond(HttpStatusCode.TooManyRequests)
                val code = call.receive<Login>().code.trim()
                if (host.accessCode.isNotEmpty() && code == host.accessCode) {
                    throttle.success()
                    call.respond(HttpStatusCode.NoContent)
                } else {
                    throttle.fail()
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }

            get("recipes") {
                if (!authorized(call)) return@get call.respond(HttpStatusCode.Unauthorized)
                call.respond(store.list().map { summary(it) }.sortedByDescending { it.updated })
            }
            post("recipes") {
                if (!authorized(call)) return@post call.respond(HttpStatusCode.Unauthorized)
                val r = call.receive<EditorRecipe>()
                if (r.title.isBlank()) return@post call.respond(HttpStatusCode.BadRequest, tr("Donnez un titre à la recette"))
                val saved = store.save(r.copy(id = ""))
                publish(saved.id)
                call.respond(SaveResult(saved, Conversion.preview(store.entry(saved.id)?.let { Conversion.recipe(it) })))
            }
            get("recipes/{id}") {
                if (!authorized(call)) return@get call.respond(HttpStatusCode.Unauthorized)
                val r = store.get(call.parameters["id"].orEmpty()) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.respond(r)
            }
            put("recipes/{id}") {
                if (!authorized(call)) return@put call.respond(HttpStatusCode.Unauthorized)
                val id = call.parameters["id"].orEmpty()
                if (store.get(id) == null) return@put call.respond(HttpStatusCode.NotFound)
                val r = call.receive<EditorRecipe>()
                if (r.title.isBlank()) return@put call.respond(HttpStatusCode.BadRequest, tr("Donnez un titre à la recette"))
                val saved = store.save(r.copy(id = id))
                publish(id)
                call.respond(SaveResult(saved, Conversion.preview(store.entry(id)?.let { Conversion.recipe(it) })))
            }
            delete("recipes/{id}") {
                if (!authorized(call)) return@delete call.respond(HttpStatusCode.Unauthorized)
                val id = call.parameters["id"].orEmpty()
                if (!store.delete(id)) return@delete call.respond(HttpStatusCode.NotFound)
                host.unpublish(Cookidoo.ID_PREFIX + id)
                call.respond(HttpStatusCode.NoContent)
            }
            get("recipes/{id}/image") {
                if (!authorized(call)) return@get call.respond(HttpStatusCode.Unauthorized)
                val f = store.imageFile(call.parameters["id"].orEmpty()) ?: return@get call.respond(HttpStatusCode.NotFound)
                call.response.header(HttpHeaders.CacheControl, "no-cache")
                call.respondBytes(f.readBytes(), ContentType.Image.JPEG)
            }
            put("recipes/{id}/image") {
                if (!authorized(call)) return@put call.respond(HttpStatusCode.Unauthorized)
                val id = call.parameters["id"].orEmpty()
                if (store.get(id) == null) return@put call.respond(HttpStatusCode.NotFound)
                val bytes = call.receiveChannel().readRemaining(RecipeEditorServer.MAX_IMAGE + 1L).readByteArray()
                val jpeg = bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
                if (bytes.size > RecipeEditorServer.MAX_IMAGE || !jpeg) return@put call.respond(HttpStatusCode.BadRequest, tr("Photo JPEG de 8 Mo au plus"))
                store.setImage(id, bytes)
                publish(id)
                call.respond(HttpStatusCode.NoContent)
            }
            post("preview") {
                if (!authorized(call)) return@post call.respond(HttpStatusCode.Unauthorized)
                val r = call.receive<EditorRecipe>()
                // Un brouillon sans titre se prévisualise quand même (l'importeur exige un nom).
                call.respond(Conversion.preview(Conversion.recipe(draftEntry(r.copy(title = r.title.ifBlank { "?" })))))
            }
        }
    }
}

/**
 * Valeur du cookie [name] dans l'en-tête Cookie. Pas call.request.cookies : il passe par
 * ConcurrentHashMap.computeIfAbsent, absent d'Android 6 (API 23).
 */
internal fun cookie(header: String?, name: String): String? =
    header?.split(';')?.firstNotNullOfOrNull { part ->
        val (k, v) = part.split('=', limit = 2).takeIf { it.size == 2 } ?: return@firstNotNullOfOrNull null
        v.trim().removeSurrounding("\"").takeIf { k.trim() == name }
    }

private val TYPES = mapOf(
    "html" to ContentType.Text.Html, "js" to ContentType.Application.JavaScript, "css" to ContentType.Text.CSS,
    "svg" to ContentType.Image.SVG, "png" to ContentType.Image.PNG, "ico" to ContentType("image", "x-icon"),
)

/** Fichiers de l'interface, emportés dans les ressources du module (dossier web/). */
private suspend fun resource(call: RoutingCall, name: String) {
    val ext = name.substringAfterLast('.', "")
    val type = TYPES[ext]
    val bytes = if (type != null && Regex("^[A-Za-z0-9._-]+$").matches(name) && !name.startsWith("."))
        RecipeEditorServer::class.java.classLoader?.getResourceAsStream("web/$name")?.use { it.readBytes() } else null
    if (bytes == null || type == null) return call.respond(HttpStatusCode.NotFound)
    call.response.header(HttpHeaders.CacheControl, "no-cache")
    call.respondBytes(bytes, type)
}
