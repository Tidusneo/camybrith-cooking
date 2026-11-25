package app.toque.core

import app.toque.core.update.Release
import app.toque.core.update.Releases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test fun compareVersions() {
        assertTrue(Releases.isNewer("v0.2.0", "0.1.0"))
        assertTrue(Releases.isNewer("0.10.0", "0.9.9"))
        assertTrue(Releases.isNewer("1.0", "0.99.99"))
        assertFalse(Releases.isNewer("v0.2.0", "0.2.0"))
        assertFalse(Releases.isNewer("0.2", "0.2.0"))
        assertFalse(Releases.isNewer("0.1.9", "0.2.0"))
        assertEquals(0, Releases.compare("V1.2.3-beta", "1.2.3"))
    }

    @Test fun parseLatestRelease() {
        val json = """
            {"tag_name":"v0.2.0","name":"Toque 0.2.0","body":"- Mises à jour depuis GitHub\r\n",
             "draft":false,"prerelease":false,
             "assets":[
               {"name":"notes.txt","size":12,"browser_download_url":"https://example.invalid/notes.txt"},
               {"name":"Toque-0.2.0.apk","size":2345678,
                "browser_download_url":"https://github.com/Tidusneo/camybrith-cooking/releases/download/v0.2.0/Toque-0.2.0.apk"}
             ]}
        """.trimIndent()
        val r = Releases.parse(json)
        assertEquals("v0.2.0", r.tag)
        assertEquals("0.2.0", r.version)
        assertEquals("Toque 0.2.0", r.title)
        assertEquals("- Mises à jour depuis GitHub", r.notes)
        assertEquals("Toque-0.2.0.apk", r.apkName)
        assertEquals(2345678L, r.apkSize)
        assertTrue(r.apkUrl!!.endsWith("/v0.2.0/Toque-0.2.0.apk"))
    }

    @Test fun linksOutsideThisRepositoryAreIgnored() {
        val json = """{"tag_name":"v9.0.0","assets":[
            {"name":"Camybrith-9.0.0-rapide.apk","size":10,"browser_download_url":"https://github.com/Tidusneo24150/camybrith-cooking/releases/download/v9.0.0/r.apk"},
            {"name":"Camybrith-pilote-9.apk","size":5,"browser_download_url":"https://evil.example/pilote.apk"}]}"""
        val r = Releases.parse(json, "rapide")
        assertNull(r.apkUrl)
        assertNull(r.pilotUrl)
    }

    @Test fun releaseWithoutApk() {
        val r = Releases.parse("""{"tag_name":"v0.3.0","name":"","body":null,"assets":[]}""")
        assertEquals("v0.3.0", r.title)
        assertNull(r.apkUrl)
        assertEquals("", r.notes)
    }

    @Test fun camybrithReleasePicksVariantAndPilot() {
        val json = """
            {"tag_name":"v1.3.0","name":"Camybrith Cooking 1.3.0","body":"",
             "assets":[
               {"name":"Camybrith-1.3.0-prudente.apk","size":20,"browser_download_url":"https://github.com/Tidusneo/camybrith-cooking/releases/download/v1.3.0/p.apk"},
               {"name":"Camybrith-1.3.0-rapide.apk","size":10,"browser_download_url":"https://github.com/Tidusneo/camybrith-cooking/releases/download/v1.3.0/r.apk"},
               {"name":"Camybrith-pilote-1.2.apk","size":5,"browser_download_url":"https://github.com/Tidusneo/camybrith-cooking/releases/download/v1.3.0/pilote.apk"},
               {"name":"camybrith_animation.zip","size":3,"browser_download_url":"https://x.invalid/a.zip"}
             ]}
        """.trimIndent()
        val fast = Releases.parse(json, Releases.variantOf("1.2.0-rapide"))
        assertEquals("Camybrith-1.3.0-rapide.apk", fast.apkName)
        assertEquals(10L, fast.apkSize)
        assertEquals("Camybrith-pilote-1.2.apk", fast.pilotName)
        val safe = Releases.parse(json, Releases.variantOf("1.2.0-prudente"))
        assertEquals("Camybrith-1.3.0-prudente.apk", safe.apkName)
        assertEquals("https://github.com/Tidusneo/camybrith-cooking/releases/download/v1.3.0/pilote.apk", safe.pilotUrl)
        assertEquals("prudente", Releases.variantOf("0.8.3-prudent"))
        assertTrue(Releases.isNewer("v1.3.0", "1.2.0-rapide"))
        assertFalse(Releases.isNewer("v1.2.0", "1.2.0-prudente"))
    }

    private fun release(version: String, commit: String = "", beta: Boolean = false) = Release(
        tag = if (beta) Releases.BETA_TAG else "v$version", version = version, title = "", notes = "",
        apkName = "a.apk", apkUrl = "${Releases.DOWNLOAD_PREFIX}x/a.apk", apkSize = 1, commit = commit, beta = beta,
    )

    @Test fun betaReleaseIsRecognised() {
        val json = """{"tag_name":"latest-beta","name":"Camybrith Cooking 1.4.2 bêta (6eb3b1d)","prerelease":true,
            "target_commitish":"6eb3b1d1c0ffee0000000000000000000000abcd","assets":[
            {"name":"Camybrith-1.4.2-beta-prudente.apk","size":20,"browser_download_url":"https://github.com/Tidusneo/camybrith-cooking/releases/download/latest-beta/p.apk"},
            {"name":"Camybrith-1.4.2-beta-rapide.apk","size":10,"browser_download_url":"https://github.com/Tidusneo/camybrith-cooking/releases/download/latest-beta/r.apk"},
            {"name":"Camybrith-pilote-1.1-beta.apk","size":5,"browser_download_url":"https://github.com/Tidusneo/camybrith-cooking/releases/download/latest-beta/pilote.apk"}]}"""
        val r = Releases.parse(json, Releases.variantOf("1.4.2-rapide-beta-6eb3b1d"))
        assertTrue(r.beta)
        assertEquals("6eb3b1d1c0ffee0000000000000000000000abcd", r.commit)
        assertEquals("Camybrith-1.4.2-beta-rapide.apk", r.apkName)
        assertEquals("Camybrith-pilote-1.1-beta.apk", r.pilotName)
        assertEquals("1.4.2", r.version)
        assertFalse(Releases.parse("""{"tag_name":"v1.4.2","assets":[]}""").beta)
        assertEquals("prudente", Releases.variantOf("1.4.2-prudente-beta-6eb3b1d"))
        assertEquals("6eb3b1d", Releases.betaCommitOf("1.4.2-rapide-beta-6eb3b1d"))
        assertNull(Releases.betaCommitOf("1.4.2-rapide"))
        assertEquals(0, Releases.compare("1.4.2-rapide-beta-6eb3b1d", "1.4.2"))
    }

    @Test fun betaChannelPicksTheRightBuild() {
        val public = release("1.4.2")
        val beta = release("1.4.2", commit = "abc1234ffff", beta = true)
        // Canal public : la bêta n'est jamais proposée.
        assertNull(Releases.pick(public, beta, "1.4.2-rapide", betaChannel = false))
        // Canal bêta depuis une version publique de même numéro : on passe à la bêta.
        assertEquals(beta, Releases.pick(public, beta, "1.4.2-rapide", betaChannel = true))
        // Déjà sur cette bêta : rien à faire.
        assertNull(Releases.pick(public, beta, "1.4.2-rapide-beta-abc1234", betaChannel = true))
        // Une autre construction de la bêta : proposée, même numéro.
        assertEquals(beta, Releases.pick(public, beta, "1.4.2-prudente-beta-0000aaa", betaChannel = true))
        // La publique est passée devant la bêta : c'est elle qui est proposée.
        val newer = release("1.4.3")
        assertEquals(newer, Releases.pick(newer, beta, "1.4.2-rapide-beta-abc1234", betaChannel = true))
        // Bêta en retard sur la version installée : jamais proposée.
        assertNull(Releases.pick(newer, beta, "1.4.3-rapide", betaChannel = true))
        // Bêta en avance sur la publique : proposée.
        val ahead = release("1.5.0", commit = "def5678", beta = true)
        assertEquals(ahead, Releases.pick(newer, ahead, "1.4.3-rapide", betaChannel = true))
        // Pas de bêta publiée, ou bêta sans fichier : on retombe sur la publique.
        assertEquals(newer, Releases.pick(newer, null, "1.4.2-rapide", betaChannel = true))
        assertEquals(newer, Releases.pick(newer, beta.copy(apkUrl = null), "1.4.2-rapide", betaChannel = true))
    }
}
