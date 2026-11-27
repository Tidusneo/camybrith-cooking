package app.toque.core

import app.toque.core.backup.Space
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val mo = 1024L * 1024

    @Test fun alerteSousDixPourcent() {
        assertTrue(Space.alerte(99 * mo, 1000 * mo))
        assertFalse(Space.alerte(100 * mo, 1000 * mo))
        assertFalse(Space.alerte(0, 0))
        assertEquals(15, Space.pourcentLibre(150, 1000))
    }

    @Test fun seuilDeSauvegarde() {
        assertTrue(Space.peutSauvegarder(53 * mo, 3 * mo))
        assertFalse(Space.peutSauvegarder(52 * mo, 3 * mo))
        assertFalse(Space.peutRecreerReserve(10 * mo))
        assertTrue(Space.peutRecreerReserve(30 * mo))
    }

    @Test fun taillesLisibles() {
        assertEquals("350 Mo", Space.lisible(350 * mo))
        assertEquals("1,2 Go", Space.lisible(1229 * mo))
        assertEquals("12 Ko", Space.lisible(12 * 1024))
    }
}
