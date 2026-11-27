package app.toque.core.backup

/** Règles d'espace de stockage (data-model.md › Espace de stockage, research.md D15). */
object Space {
    const val RESERVE_OCTETS = 2L * 1024 * 1024
    const val MARGE_SAUVEGARDE = 50L * 1024 * 1024
    /** Pour recréer la réserve, il faut au moins ceci de libre en plus d'elle. */
    const val MINIMUM_POUR_RESERVE = 20L * 1024 * 1024

    /** Alerte quand il reste moins de 10 % de `/data`. */
    fun alerte(libre: Long, total: Long): Boolean = total > 0 && libre * 10 < total

    fun pourcentLibre(libre: Long, total: Long): Int = if (total <= 0) 0 else ((libre * 100) / total).toInt()

    /** Une sauvegarde peut commencer si l'espace libre couvre sa taille estimée + 50 Mo. */
    fun peutSauvegarder(libre: Long, tailleEstimee: Long): Boolean = libre >= tailleEstimee + MARGE_SAUVEGARDE

    fun peutRecreerReserve(libre: Long): Boolean = libre >= RESERVE_OCTETS + MINIMUM_POUR_RESERVE

    /** Taille lisible en français : « 1,2 Go », « 350 Mo », « 12 Ko ». */
    fun lisible(octets: Long): String {
        val ko = 1024.0
        return when {
            octets >= ko * ko * ko -> String.format(java.util.Locale.FRANCE, "%.1f Go", octets / (ko * ko * ko))
            octets >= ko * ko -> "${(octets / (ko * ko)).toLong()} Mo"
            else -> "${(octets / ko).toLong()} Ko"
        }
    }
}
