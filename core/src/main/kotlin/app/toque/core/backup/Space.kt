package app.toque.core.backup

/** Storage rules (data-model.md › Espace de stockage, research.md D15). */
object Space {
    const val RESERVE_BYTES = 2L * 1024 * 1024
    const val BACKUP_MARGIN = 50L * 1024 * 1024
    /** To recreate the reserve, at least this much must be free on top of it. */
    const val MIN_FOR_RESERVE = 20L * 1024 * 1024

    /** Alert when less than 10 % of `/data` is free. */
    fun isLow(free: Long, total: Long): Boolean = total > 0 && free * 10 < total

    fun percentFree(free: Long, total: Long): Int = if (total <= 0) 0 else ((free * 100) / total).toInt()

    /** A backup may start if free space covers its estimated size + 50 MB. */
    fun canBackup(free: Long, estimatedSize: Long): Boolean = free >= estimatedSize + BACKUP_MARGIN

    fun canRecreateReserve(free: Long): Boolean = free >= RESERVE_BYTES + MIN_FOR_RESERVE

    /** Size shown to the user, in French: « 1,2 Go », « 350 Mo », « 12 Ko ». */
    fun readable(bytes: Long): String {
        val kb = 1024.0
        return when {
            bytes >= kb * kb * kb -> String.format(java.util.Locale.FRANCE, "%.1f Go", bytes / (kb * kb * kb))
            bytes >= kb * kb -> "${(bytes / (kb * kb)).toLong()} Mo"
            else -> "${(bytes / kb).toLong()} Ko"
        }
    }
}
