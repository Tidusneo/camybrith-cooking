package app.toque.core

import app.toque.core.machine.EndCue
import app.toque.core.machine.JobState
import app.toque.core.machine.LinkState
import app.toque.core.machine.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndCueTest {
    private val sound = 9_100L
    private val ready = Snapshot(link = LinkState.READY, hasSample = true)

    private fun job(remaining: Long, state: JobState = JobState.RUNNING, total: Long = 60_000, kind: String = "MANUAL") =
        ready.copy(jobActive = true, jobKind = kind, jobState = state.name, totalMs = total, elapsedMs = total - remaining, remainingMs = remaining)

    @Test fun startsWhenTheSoundLengthRemains() {
        val cue = EndCue()
        assertEquals(EndCue.Action.None, cue.update(job(30_000), sound))
        assertEquals(EndCue.Action.None, cue.update(job(9_200), sound))
        assertEquals(EndCue.Action.Start(0), cue.update(job(9_100), sound))
        assertTrue(cue.playing)
        // Une seule fois.
        assertEquals(EndCue.Action.None, cue.update(job(8_000), sound))
    }

    @Test fun lateSnapshotStartsFurtherInSoItStillEndsOnTime() {
        val cue = EndCue()
        assertEquals(EndCue.Action.Start(300), cue.update(job(8_800), sound))
    }

    @Test fun naturalEndLetsTheSoundFinish() {
        val cue = EndCue()
        cue.update(job(9_000), sound)
        assertEquals(EndCue.Action.None, cue.update(job(0, JobState.STOPPING), sound))
        assertFalse(cue.playing)
        assertEquals(EndCue.Action.None, cue.update(job(0, JobState.FINISHED), sound))
    }

    @Test fun pauseCutsItAndResumePicksUpAtTheRightPlace() {
        val cue = EndCue()
        cue.update(job(9_000), sound)
        assertEquals(EndCue.Action.Stop, cue.update(job(5_000, JobState.PAUSED), sound))
        assertEquals(EndCue.Action.None, cue.update(job(5_000, JobState.PAUSED), sound))
        assertEquals(EndCue.Action.Start(4_100), cue.update(job(5_000), sound))
    }

    @Test fun stopCutsIt() {
        val cue = EndCue()
        cue.update(job(9_000), sound)
        assertEquals(EndCue.Action.Stop, cue.update(job(6_000, JobState.ABORTED), sound))
        cue.update(job(9_000), sound)
        assertEquals(EndCue.Action.Stop, cue.update(ready, sound))
    }

    @Test fun addedTimeCutsItAndItStartsAgainLater() {
        val cue = EndCue()
        cue.update(job(9_000), sound)
        assertEquals(EndCue.Action.Stop, cue.update(job(68_000, total = 120_000), sound))
        assertEquals(EndCue.Action.Start(0), cue.update(job(9_100, total = 120_000), sound))
    }

    @Test fun nothingForShortProgramsTurboOrWithoutSound() {
        assertEquals(EndCue.Action.None, EndCue().update(job(5_000, total = 5_000), sound))
        assertEquals(EndCue.Action.None, EndCue().update(job(9_000, kind = "TURBO"), sound))
        assertEquals(EndCue.Action.None, EndCue().update(job(9_000), null))
        assertEquals(EndCue.Action.None, EndCue().update(job(500), sound))
    }

    @Test fun soundsSwitchedOffWhilePlayingStopsIt() {
        val cue = EndCue()
        cue.update(job(9_000), sound)
        assertEquals(EndCue.Action.Stop, cue.update(job(8_000), null))
    }
}
