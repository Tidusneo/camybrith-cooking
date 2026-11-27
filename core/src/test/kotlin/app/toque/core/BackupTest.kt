package app.toque.core

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
}
