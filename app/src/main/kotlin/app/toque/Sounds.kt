package app.toque

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.SystemClock
import android.provider.Settings
import java.io.File
import java.util.zip.ZipFile

/**
 * Sons Camybrith Cooking. Ceux de l'appli sont dans assets/sons (synthétisés par tools/smart/sons.py).
 * Chacun peut être remplacé par un fichier à soi, même nom, en .wav, .ogg ou .mp3, déposé dans
 * Android/data/app.toque/files/sons/ ; « Recharger les sons » dans les réglages les prend en compte.
 * Le volume est celui des médias du robot (AudioManager.STREAM_MUSIC).
 * Les sons de l'appli, courts, passent par SoundPool. Les sons personnels (sauf l'appui) passent par
 * MediaPlayer : SoundPool coupe sur Android 8 tout son qui dépasse 1 Mo une fois décodé (environ 6 s
 * en stéréo 44,1 kHz), et un son de démarrage de console dure bien plus.
 * Le son « compte » n'existe que s'il est fourni : il part quand il reste sa durée dans un programme,
 * pour finir pile avec lui (voir EndCue).
 */
class Sounds(private val c: Context, private val log: Logbook? = null) {
    /** [builtIn] : fourni avec l'appli (sinon joué seulement si on dépose le sien). */
    enum class Kind(val file: String, val builtIn: Boolean = true) {
        STARTUP("demarrage"), START("debut"), END("fin"), RECIPE("recette"), ALERT("alerte"), PAUSE("pause"), TAP("appui"),
        /** Pesée : cible atteinte, cible dépassée. */
        WEIGH_OK("pesee"), WEIGH_OVER("trop"),
        /** Compte à rebours de fin de programme, calé pour finir avec lui. */
        COUNTDOWN("compte", builtIn = false),
    }

    private var pool: SoundPool? = null
    private val ids = HashMap<Kind, Int>()
    private val ready = HashSet<Int>()
    private val waiting = HashMap<Int, (() -> Unit)?>()
    private val files = HashMap<Kind, File>()
    private val players = ArrayList<MediaPlayer>()
    /** Sons longs en cours qu'on peut couper (compte à rebours). */
    private val held = HashMap<Kind, MediaPlayer>()
    private val lengths = HashMap<Kind, Long>()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    private val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** Dossier des sons personnels (créé au premier chargement). */
    val customDir: File? get() = c.getExternalFilesDir("sons")

    /** Sons remplacés par un fichier personnel au dernier chargement. */
    var custom: List<Kind> = emptyList()
        private set

    private fun own(k: Kind): File? {
        val dir = customDir ?: return null
        return listOf("wav", "ogg", "mp3").map { File(dir, "${k.file}.$it") }.firstOrNull { it.isFile && it.length() > 0 }
    }

    @Synchronized
    fun load() {
        release()
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attributes)
            .build()
        p.setOnLoadCompleteListener { sp, id, status ->
            synchronized(this) {
                if (status != 0) return@setOnLoadCompleteListener
                ready += id
                if (waiting.containsKey(id)) {
                    val started = waiting.remove(id)
                    sp.play(id, 1f, 1f, 1, 0, 1f)
                    started?.invoke()
                }
            }
        }
        val mine = ArrayList<Kind>()
        for (k in Kind.values()) {
            try {
                val file = own(k)
                if (file == null && !k.builtIn) continue
                if (file != null && k != Kind.TAP) {
                    mine += k
                    files[k] = file
                    if (k == Kind.COUNTDOWN) {
                        val ms = durationOf(file)
                        if (ms != null) lengths[k] = ms
                        log?.write("sons", "${file.name} : " + (ms?.let { "$it ms, part à $it ms de la fin" } ?: "durée illisible, son ignoré"))
                    }
                    continue
                }
                val id = if (file != null) {
                    mine += k
                    p.load(file.path, 1)
                } else {
                    c.assets.openFd("sons/${k.file}.wav").use { p.load(it, 1) }
                }
                if (id != 0) ids[k] = id
            } catch (e: Exception) {
                log?.write("sons", "${k.file} : ${e.message}")
            }
        }
        custom = mine
        pool = p
    }

    /**
     * Joue un son ; s'il n'est pas encore chargé (démarrage), il part dès qu'il l'est.
     * [started] est appelé quand le son démarre vraiment (ou tout de suite s'il n'y a rien à jouer).
     */
    @Synchronized
    fun play(k: Kind, started: (() -> Unit)? = null) {
        files[k]?.let { playFile(it, started); return }
        val p = pool
        val id = ids[k]
        if (p == null || id == null) {
            started?.invoke()
            return
        }
        if (id in ready) {
            p.play(id, 1f, 1f, 1, 0, 1f)
            started?.invoke()
        } else waiting[id] = started
    }

    private fun playFile(file: File, started: (() -> Unit)? = null) {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(file.path)
            mp.setOnPreparedListener {
                it.start()
                started?.invoke()
            }
            mp.setOnCompletionListener { done(it) }
            mp.setOnErrorListener { player, what, _ ->
                log?.write("sons", "${file.name} : erreur $what")
                done(player)
                started?.invoke()
                true
            }
            players += mp
            mp.prepareAsync()
        } catch (e: Exception) {
            log?.write("sons", "${file.name} : ${e.message}")
            done(mp)
            started?.invoke()
        }
    }

    /** Durée d'un son personnel, en millisecondes (null s'il n'y en a pas). */
    @Synchronized
    fun lengthMs(k: Kind): Long? = lengths[k]

    /**
     * Joue un son personnel à partir de [offsetMs], en le gardant sous la main pour [stop].
     * Le temps de préparation est rattrapé : le son finit là où on l'attend.
     */
    @Synchronized
    fun playFrom(k: Kind, offsetMs: Long) {
        stop(k)
        val file = files[k] ?: return
        val asked = SystemClock.elapsedRealtime()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(attributes)
            mp.setDataSource(file.path)
            mp.setOnPreparedListener { p ->
                // Pas d'attente de la fin du positionnement : certains Android 8 ne la signalent
                // jamais avant le premier start(), et le son restait muet.
                val at = offsetMs + (SystemClock.elapsedRealtime() - asked)
                if (at >= MIN_SEEK_MS) p.seekTo(at.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                p.start()
                log?.write("sons", "${file.name} : lecture à partir de $at ms")
            }
            mp.setOnCompletionListener { finish(k, it) }
            mp.setOnErrorListener { player, what, _ ->
                log?.write("sons", "${file.name} : erreur $what")
                finish(k, player)
                true
            }
            held[k] = mp
            players += mp
            mp.prepareAsync()
        } catch (e: Exception) {
            log?.write("sons", "${file.name} : ${e.message}")
            finish(k, mp)
        }
    }

    /** Coupe un son lancé par [playFrom]. */
    @Synchronized
    fun stop(k: Kind) {
        held.remove(k)?.let { done(it) }
    }

    @Synchronized
    private fun finish(k: Kind, mp: MediaPlayer) {
        if (held[k] === mp) held.remove(k)
        done(mp)
    }

    /** Durée lue dans les métadonnées, sinon en préparant le fichier. */
    private fun durationOf(f: File): Long? {
        val r = MediaMetadataRetriever()
        val meta = try {
            r.setDataSource(f.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
        } catch (e: Exception) {
            log?.write("sons", "${f.name} : métadonnées illisibles (${e.message})")
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
        if (meta != null) return meta
        val mp = MediaPlayer()
        return try {
            mp.setDataSource(f.path)
            mp.prepare()
            mp.duration.toLong().takeIf { it > 0 }
        } catch (e: Exception) {
            log?.write("sons", "${f.name} : illisible (${e.message})")
            null
        } finally {
            mp.release()
        }
    }

    @Synchronized
    private fun done(mp: MediaPlayer) {
        players.remove(mp)
        mp.release()
    }

    /**
     * Le son d'ouverture n'est joué qu'une fois par démarrage du robot, et pas du tout si l'animation
     * de démarrage du robot contient déjà un son (module Camybrith installé avec le son).
     */
    fun startupDue(prefs: Prefs): Boolean {
        val boot = bootId() ?: return true
        if (prefs.startupSoundBoot == boot) return false
        prefs.startupSoundBoot = boot
        return !bootAnimationHasSound()
    }

    private fun bootId(): String? {
        val count = try {
            Settings.Global.getInt(c.contentResolver, Settings.Global.BOOT_COUNT, -1)
        } catch (_: Exception) {
            -1
        }
        if (count >= 0) return "n$count"
        return try {
            "id" + File("/proc/sys/kernel/random/boot_id").readText().trim()
        } catch (_: Exception) {
            null
        }
    }

    private fun bootAnimationHasSound(): Boolean =
        try {
            ZipFile("/system/media/bootanimation.zip").use { z -> z.entries().asSequence().any { it.name.endsWith("audio.wav") } }
        } catch (_: Exception) {
            false
        }

    val maxVolume: Int get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    var volume: Int
        get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        set(v) = audio.setStreamVolume(AudioManager.STREAM_MUSIC, v.coerceIn(0, maxVolume), 0)

    private companion object {
        /** En dessous, on ne cherche pas dans le fichier : on part du début. */
        const val MIN_SEEK_MS = 60L
    }

    @Synchronized
    fun release() {
        pool?.release()
        pool = null
        players.forEach { it.release() }
        players.clear()
        held.clear()
        lengths.clear()
        files.clear()
        ids.clear()
        ready.clear()
        waiting.clear()
    }
}
