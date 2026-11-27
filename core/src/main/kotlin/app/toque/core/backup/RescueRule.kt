package app.toque.core.backup

/**
 * Rescue mode (spec 007, data-model.md › État de secours › Règle de décision): at least 3 crashes
 * of the `app.toque` interface at startup, up to 120 s after `sys.boot_completed`, trigger the
 * return to the Lidl app. Never while an operation journal is being processed.
 *
 * The `logcat -b crash` buffer is cleared at every restart: crashes during the boot itself (before
 * `sys.boot_completed`, e.g. a home screen crashing in a loop) count too.
 */
object RescueRule {
    const val THRESHOLD = 3
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
