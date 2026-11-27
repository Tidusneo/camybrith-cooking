package app.toque.core.backup

/**
 * Rescue mode (spec 007, data-model.md › État de secours › Règle de décision): at least 2 crashes
 * of the `app.toque` interface at startup, up to 120 s after `sys.boot_completed`, trigger the
 * return to the Lidl app. Never while an operation journal is being processed.
 *
 * Why 2 and not 3: on Android 8, a home app that crashes twice in a row within a minute is no
 * longer restarted (the system asks which home app to use instead), so a third crash never comes
 * (emulator scenario B6). A false alarm only costs a switch to the Lidl app, undone by the bubble.
 *
 * The `logcat -b crash` buffer is cleared at every restart: crashes during the boot itself (before
 * `sys.boot_completed`, e.g. a home screen crashing in a loop) count too.
 */
object RescueRule {
    const val THRESHOLD = 2
    const val WINDOW_MS = 120_000L

    /** Times (ms) of the interface crashes in `logcat -b crash -d -v epoch`. */
    fun crashes(logcat: String, pkg: String = RobotId.APP): List<Long> {
        val res = ArrayList<Long>()
        val java = "Process: $pkg, PID:"
        val native = ">>> $pkg <<<"
        for (line in logcat.lines()) {
            if (!line.contains(java) && !line.contains(native)) continue
            val t = line.trimStart().substringBefore(' ').toDoubleOrNull() ?: continue
            res += (t * 1000).toLong()
        }
        return res
    }

    /** True if the return to the Lidl app must start. [bootTime]: time of `sys.boot_completed` (ms). */
    fun shouldTrigger(crashes: List<Long>, bootTime: Long, now: Long, journalInProgress: Boolean): Boolean {
        if (journalInProgress) return false
        val end = minOf(now, bootTime + WINDOW_MS)
        return crashes.count { it <= end } >= THRESHOLD
    }
}
