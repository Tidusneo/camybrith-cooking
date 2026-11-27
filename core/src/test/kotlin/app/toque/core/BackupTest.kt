package app.toque.core

import app.toque.core.backup.BackupContent
import app.toque.core.backup.BackupData
import app.toque.core.backup.Migrations
import app.toque.core.backup.Validation
import app.toque.core.backup.BackupFile
import app.toque.core.backup.Manifest
import app.toque.core.backup.Secrets
import app.toque.core.backup.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val mb = 1024L * 1024

    @Test fun alertBelowTenPercent() {
        assertTrue(Space.isLow(99 * mb, 1000 * mb))
        assertFalse(Space.isLow(100 * mb, 1000 * mb))
        assertFalse(Space.isLow(0, 0))
        assertEquals(15, Space.percentFree(150, 1000))
    }

    @Test fun backupThreshold() {
        assertTrue(Space.canBackup(53 * mb, 3 * mb))
        assertFalse(Space.canBackup(52 * mb, 3 * mb))
        assertFalse(Space.canRecreateReserve(10 * mb))
        assertTrue(Space.canRecreateReserve(30 * mb))
    }

    @Test fun readableSizes() {
        assertEquals("350 Mo", Space.readable(350 * mb))
        assertEquals("1,2 Go", Space.readable(1229 * mb))
        assertEquals("12 Ko", Space.readable(12 * 1024))
    }

    @Test fun noSecretInSettings() {
        val prefs = mapOf("mqttPassword" to "s3cret!", "mqttHost" to "192.168.1.2", "theme" to "sombre",
            "brandNewKey" to "x", "mode" to "hardware", "lastCrashMs" to 12L)
        val kept = Secrets.filter(prefs)
        assertEquals(setOf("mqttHost", "theme"), kept.keys)
        assertFalse(kept.values.contains("s3cret!"))
        assertEquals(listOf("mqttPassword"), Secrets.toReenter(prefs))
        assertEquals(emptyList<String>(), Secrets.toReenter(mapOf("mqttPassword" to "")))
    }

    @Test fun lidlRecipesAreLeftOut() {
        assertTrue(BackupContent.isLidlRecipe("mc-123"))
        assertTrue(BackupContent.isLidlRecipe("web-abc"))
        assertFalse(BackupContent.isLidlRecipe("ck-soupe"))
        assertFalse(BackupContent.isLidlRecipe("tm-r1"))
    }

    @Test fun manifestRoundTrip() {
        val m = Manifest("2026-11-27T10:00:00+01:00", "1.4.3-rapide", 51, 2, BackupContent.robotFingerprint("SN123"),
            listOf("favorites", "recipes", "settings"), mapOf("favorites" to 4, "recipes" to 2),
            listOf(BackupFile("favorites.json", 10, BackupContent.sha256("x".toByteArray()))))
        assertEquals(m, Manifest.parse(m.toJson()))
        assertTrue(m.toJson().contains("\"secretsIncluded\": false"))
        assertFalse(m.toJson().contains("SN123"))
        assertEquals("camybrith-20261127-100000.camybrith", BackupContent.fileName("2026-11-27T10:00:00+01:00"))
        val names = (1..7).map { "camybrith-202611%02d-100000.camybrith".format(it) }
        assertEquals(names.take(2), BackupContent.toDelete(names.shuffled()))
    }

    private fun backup(entries: Map<String, ByteArray>, versionCode: Int = 51, robot: String = "r1", format: Int = 1) = Manifest(
        "2026-11-27T10:00:00+01:00", "1.4.3-rapide", versionCode, 2, robot, listOf("favorites", "recipes", "settings"),
        emptyMap(), entries.map { (p, b) -> BackupFile(p, b.size.toLong(), BackupContent.sha256(b)) }, format)

    private val entries = mapOf(
        "favorites.json" to "[\"ck-a\",\"mc-1\"]".toByteArray(),
        "recipes.json" to "[{\"id\":\"ck-a\",\"title\":\"Soupe\",\"json\":\"{}\",\"updated\":1},{\"id\":\"mc-9\",\"title\":\"Lidl\",\"json\":\"{}\",\"updated\":1}]".toByteArray(),
        "settings.json" to "{\"theme\":\"sombre\",\"mqttPassword\":\"x\"}".toByteArray(),
    )

    @Test fun validBackup() {
        val r = Validation.check(backup(entries), entries + (Manifest.NAME to ByteArray(1)), 51, "r1")
        assertTrue(r.errors.toString(), r.ok)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun oneChangedByteIsRefused() {
        val damaged = entries.toMutableMap()
        damaged["settings.json"] = "{\"theme\":\"sombrE\",\"mqttPassword\":\"x\"}".toByteArray()
        val r = Validation.check(backup(entries), damaged, 51, "r1")
        assertFalse(r.ok)
        assertTrue(r.errors.single().contains("abîmé"))
    }

    @Test fun missingOrExtraFileIsRefused() {
        assertFalse(Validation.check(backup(entries), entries - "recipes.json", 51, "r1").ok)
        assertFalse(Validation.check(backup(entries), entries + ("autre.sh" to ByteArray(3)), 51, "r1").ok)
    }

    @Test fun newerVersionIsRefused() {
        assertFalse(Validation.check(backup(entries, versionCode = 60), entries, 51, "r1").ok)
    }

    @Test fun otherRobotIsWarned() {
        val r = Validation.check(backup(entries, robot = "r2"), entries, 51, "r1")
        assertTrue(r.ok)
        assertEquals(1, r.warnings.size)
    }

    @Test fun unknownFormatIsRefused() {
        assertFalse(Validation.check(backup(entries, format = 7), entries, 51, "r1").ok)
    }

    @Test fun oldFormatIsConvertedInMemory() {
        val old = mapOf("preferences.json" to "{\"theme\":\"clair\"}".toByteArray())
        val data = BackupData(Migrations.toCurrent(0, old))
        assertEquals("clair", data.settings["theme"])
    }

    @Test fun readingFiltersLidlRecipesAndSecrets() {
        val data = BackupData(entries)
        assertEquals(listOf("ck-a"), data.recipes.map { it["id"] })
        assertEquals(setOf("theme"), data.settings.keys)
        assertEquals(listOf("ck-a", "mc-1"), data.favorites)
    }
}
