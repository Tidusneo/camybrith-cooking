package app.toque.recipes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Handler
import android.os.Looper
import app.toque.core.json.Json
import app.toque.core.json.double
import app.toque.core.json.int
import app.toque.core.json.list
import app.toque.core.json.obj
import app.toque.core.json.str
import app.toque.core.recipes.Cookidoo
import app.toque.core.recipes.Recipe
import app.toque.core.recipes.RecipeCodec
import app.toque.core.recipes.RecipeSource
import app.toque.core.recipes.SearchIndex
import java.io.File
import java.util.concurrent.Executors

/**
 * Recettes Lidl copiées localement (SQLite) + index de recherche en mémoire.
 * L'appli fonctionne entièrement hors ligne ; seule la synchro passe par le réseau.
 */
class RecipeRepo(private val context: Context) {
    private val helper = object : SQLiteOpenHelper(context, "recettes.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE recipes (id TEXT PRIMARY KEY, title TEXT NOT NULL, json TEXT NOT NULL, updated INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE favorites (id TEXT PRIMARY KEY)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "toque-recettes").apply { priority = Thread.NORM_PRIORITY - 1 } }
    private val main = Handler(Looper.getMainLooper())
    private val listeners = ArrayList<() -> Unit>()

    @Volatile var index: SearchIndex = SearchIndex(emptyList())
        private set
    @Volatile var loaded = false
        private set
    private val favorites = HashSet<String>()

    val imagesDir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    fun onChange(l: () -> Unit): () -> Unit {
        listeners += l
        return { listeners -= l }
    }

    private fun notifyChanged() = main.post { for (l in ArrayList(listeners)) l() }

    /**
     * Au démarrage : d'abord le résumé du catalogue gardé en cache (titres, catégories, photos), qui
     * s'affiche presque tout de suite ; puis le vrai index (ingrédients, étapes) lu dans la base.
     * Sur le Smart l'appli tourne sans compilation à la volée (vmSafeMode), et relire puis décoder
     * des milliers de recettes complètes prend de longues secondes.
     */
    fun load() = exec.execute {
        if (loadIndexFile()) return@execute
        quickLoad()
        rebuild()
    }

    /** Index complet enregistré au dernier calcul, avec l'état du catalogue pour lequel il vaut. */
    private val indexFile: File get() = File(context.filesDir, "index-recherche.bin")

    /** État du catalogue : change à chaque ajout, mise à jour ou suppression de recette. */
    private fun stamp(): String = helper.readableDatabase.rawQuery("SELECT COUNT(*), MAX(updated) FROM recipes", null).use { c ->
        if (c.moveToFirst()) "${c.getLong(0)}:${c.getLong(1)}" else "0:0"
    }

    private fun readFavorites(): HashSet<String> {
        val favs = HashSet<String>()
        helper.readableDatabase.rawQuery("SELECT id FROM favorites", null).use { c -> while (c.moveToNext()) favs += c.getString(0) }
        return favs
    }

    /**
     * Démarrage instantané : si le catalogue n'a pas changé depuis le dernier calcul, on relit
     * l'index tel quel (recherche par ingrédient comprise) au lieu de décoder toutes les recettes.
     */
    private fun loadIndexFile(): Boolean {
        val f = indexFile
        if (!f.isFile) return false
        return try {
            val idx = java.io.DataInputStream(java.io.BufferedInputStream(f.inputStream(), 1 shl 16)).use { input ->
                if (input.readUTF() != stamp()) return false
                SearchIndex.read(input)
            } ?: return false
            val favs = readFavorites()
            synchronized(favorites) {
                favorites.clear()
                favorites += favs
            }
            index = idx.withFavorites(favs)
            loaded = true
            notifyChanged()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun saveIndexFile(idx: SearchIndex) {
        try {
            val tmp = File(indexFile.path + ".tmp")
            java.io.DataOutputStream(java.io.BufferedOutputStream(tmp.outputStream(), 1 shl 16)).use { out ->
                out.writeUTF(stamp())
                idx.write(out)
            }
            tmp.renameTo(indexFile)
        } catch (_: Exception) {
        }
    }

    private val cacheFile: File get() = File(context.filesDir, "catalogue-resume.json")

    private fun quickLoad() {
        if (loaded) return
        val f = cacheFile
        if (!f.isFile) return
        try {
            val counts = HashMap<String, Int>()
            val list = ArrayList<Recipe>()
            val favs = HashSet<String>()
            helper.readableDatabase.rawQuery("SELECT id FROM favorites", null).use { c -> while (c.moveToNext()) favs += c.getString(0) }
            for (e in Json.parse(f.readText()).list()) {
                val m = e.obj()
                val id = m.str("id") ?: continue
                val r = Recipe(
                    id = id,
                    title = m.str("title").orEmpty(),
                    category = m.str("category").orEmpty(),
                    tags = m["tags"].list().mapNotNull { it as? String },
                    totalMinutes = m.int("totalMinutes") ?: 0,
                    source = if (m.str("source") == "user") RecipeSource.USER else RecipeSource.IMPORTED,
                    image = m.str("image").orEmpty(),
                    favorite = id in favs,
                    rating = m.double("rating") ?: 0.0,
                    ratingCount = m.int("ratingCount") ?: 0,
                )
                counts[id] = m.int("steps") ?: 0
                list += r
            }
            synchronized(favorites) {
                favorites.clear()
                favorites += favs
            }
            index = SearchIndex(list, counts)
            loaded = true
            notifyChanged()
        } catch (_: Exception) {
            // Cache illisible : on attend le vrai index.
        }
    }

    private fun saveCache(list: List<Recipe>) {
        try {
            val rows = list.map { r ->
                linkedMapOf(
                    "id" to r.id, "title" to r.title, "category" to r.category, "tags" to r.tags,
                    "totalMinutes" to r.totalMinutes, "source" to r.source.name.lowercase(), "image" to r.image,
                    "rating" to r.rating, "ratingCount" to r.ratingCount, "steps" to r.machineSteps,
                )
            }
            val tmp = File(cacheFile.path + ".tmp")
            tmp.writeText(Json.write(rows))
            tmp.renameTo(cacheFile)
        } catch (_: Exception) {
        }
    }

    private fun rebuild() {
        val db = helper.readableDatabase
        val favs = HashSet<String>()
        db.rawQuery("SELECT id FROM favorites", null).use { c -> while (c.moveToNext()) favs += c.getString(0) }
        val list = ArrayList<Recipe>()
        db.rawQuery("SELECT json FROM recipes", null).use { c ->
            while (c.moveToNext()) {
                val r = try {
                    RecipeCodec.decode(c.getString(0), RecipeSource.IMPORTED)
                } catch (_: Exception) {
                    null
                } ?: continue
                list += r.copy(favorite = r.id in favs)
            }
        }
        synchronized(favorites) {
            favorites.clear()
            favorites += favs
        }
        val idx = SearchIndex(list)
        index = idx
        loaded = true
        notifyChanged()
        saveCache(list)
        saveIndexFile(idx)
    }

    val count: Int get() = index.entries.size

    /** Lecture d'une recette (rapide : une seule ligne). */
    fun get(id: String): Recipe? {
        helper.readableDatabase.rawQuery("SELECT json FROM recipes WHERE id = ?", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            val r = try {
                RecipeCodec.decode(c.getString(0), RecipeSource.IMPORTED)
            } catch (_: Exception) {
                null
            } ?: return null
            return r.copy(favorite = isFavorite(id))
        }
    }

    fun isFavorite(id: String): Boolean = synchronized(favorites) { id in favorites }

    fun setFavorite(id: String, favorite: Boolean) {
        synchronized(favorites) { if (favorite) favorites += id else favorites -= id }
        exec.execute {
            val db = helper.writableDatabase
            if (favorite) db.execSQL("INSERT OR IGNORE INTO favorites (id) VALUES (?)", arrayOf(id))
            else db.execSQL("DELETE FROM favorites WHERE id = ?", arrayOf(id))
            // Un favori ne change pas les termes de recherche : pas besoin de tout recalculer.
            index = index.withFavorites(synchronized(favorites) { HashSet(favorites) })
            notifyChanged()
        }
    }

    /**
     * Remplace tout le catalogue par l'import du jour (miroir de l'appli d'origine).
     * Les favoris sont conservés ; ceux marqués dans l'appli d'origine sont ajoutés.
     */
    fun replaceAll(recipes: List<Recipe>) {
        exec.submit { replaceAllNow(recipes) }.get()
    }

    private fun replaceAllNow(recipes: List<Recipe>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            // Les recettes importées d'ailleurs (Thermomix…) ne font pas partie du catalogue Lidl.
            db.delete("recipes", "id NOT LIKE ?", arrayOf(Cookidoo.ID_PREFIX + "%"))
            val now = System.currentTimeMillis()
            for (r in recipes) {
                val v = ContentValues()
                v.put("id", r.id)
                v.put("title", r.title)
                v.put("json", RecipeCodec.encode(r.copy(favorite = false)))
                v.put("updated", now)
                db.insertWithOnConflict("recipes", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                if (r.favorite) db.execSQL("INSERT OR IGNORE INTO favorites (id) VALUES (?)", arrayOf(r.id))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        rebuild()
    }

    /** Ajoute ou met à jour des recettes (favoris conservés). [rebuild] : recharger l'index tout de suite. */
    fun upsertAll(recipes: List<Recipe>, rebuild: Boolean = true) {
        exec.submit {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                val now = System.currentTimeMillis()
                for (r in recipes) {
                    val v = ContentValues()
                    v.put("id", r.id)
                    v.put("title", r.title)
                    v.put("json", RecipeCodec.encode(r.copy(favorite = false)))
                    v.put("updated", now)
                    db.insertWithOnConflict("recipes", null, v, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (rebuild) rebuild()
        }.get()
    }

    /** Identifiants de toutes les recettes enregistrées. */
    fun ids(): Set<String> = exec.submit<Set<String>> {
        val out = HashSet<String>()
        helper.readableDatabase.rawQuery("SELECT id FROM recipes", null).use { c -> while (c.moveToNext()) out += c.getString(0) }
        out
    }.get()

    /**
     * Supprime les recettes de la source dont les identifiants commencent par [prefix] et sont
     * absentes de [keep] (catalogue miroir de la source). Les autres recettes ne sont pas touchées.
     */
    fun removeAllExcept(keep: Set<String>, prefix: String, rebuild: Boolean = true) {
        exec.submit {
            val db = helper.writableDatabase
            val gone = ArrayList<String>()
            db.rawQuery("SELECT id FROM recipes", null).use { c ->
                while (c.moveToNext()) c.getString(0).let { if (it.startsWith(prefix) && it !in keep) gone += it }
            }
            db.beginTransaction()
            try {
                for (id in gone) db.delete("recipes", "id = ?", arrayOf(id))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (rebuild) rebuild()
        }.get()
    }

    /** Supprime ces recettes (et leurs favoris). */
    fun remove(ids: Collection<String>, rebuild: Boolean = true) {
        exec.submit {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                for (id in ids) {
                    db.delete("recipes", "id = ?", arrayOf(id))
                    db.delete("favorites", "id = ?", arrayOf(id))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            synchronized(favorites) { favorites.removeAll(ids.toSet()) }
            if (rebuild) rebuild()
        }.get()
    }

    /** Recharge l'index de recherche (après des mises à jour sans [rebuild]). */
    fun refresh() {
        exec.submit { rebuild() }.get()
    }

    fun imageFile(name: String): File? {
        if (name.isBlank()) return null
        val clean = name.substringAfterLast('/')
        for (candidate in listOf("large-$clean", clean, "small-$clean")) {
            val f = File(imagesDir, candidate)
            if (f.exists() && f.length() > 0) return f
        }
        return null
    }

    fun runInBackground(block: () -> Unit) = exec.execute(block)
}
