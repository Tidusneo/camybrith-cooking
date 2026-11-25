package app.toque.core.machine

/**
 * Son « compte à rebours » de fin de programme : il démarre quand il reste exactement sa durée,
 * pour se terminer pile avec le programme (un son de 9 s part quand il reste 9 s).
 *
 * Une pause ou un arrêt le coupe ; à la reprise, il repart à l'endroit qui le fait encore finir
 * avec le programme. Du temps ajouté en cours de route le coupe aussi, il repart au bon moment.
 * Rien pour le turbo ni pour un programme plus court que le son.
 */
class EndCue {
    sealed class Action {
        object None : Action()
        object Stop : Action()
        /** Jouer le son à partir de [offsetMs] (0 : depuis le début). */
        data class Start(val offsetMs: Long) : Action()
    }

    /** Le son est censé jouer en ce moment. */
    var playing = false
        private set

    /** [soundMs] : durée du son, null s'il n'y en a pas (ou si les sons sont coupés). */
    fun update(s: Snapshot, soundMs: Long?): Action {
        val lead = soundMs?.takeIf { it > 0 }
        val st = s.state
        if (playing) {
            return when {
                lead == null -> stop()
                // Fin normale : on laisse le son finir tout seul.
                st == JobState.STOPPING || st == JobState.FINISHED -> { playing = false; Action.None }
                st == null || st == JobState.PAUSED || st == JobState.ABORTED -> stop()
                s.remainingMs > lead + SLACK_MS -> stop()
                else -> Action.None
            }
        }
        if (lead == null || st != JobState.RUNNING || s.turboActive || s.kind == JobKind.TURBO) return Action.None
        if (s.totalMs < lead || s.remainingMs > lead || s.remainingMs < MIN_REMAINING_MS) return Action.None
        playing = true
        return Action.Start(lead - s.remainingMs)
    }

    private fun stop(): Action {
        playing = false
        return Action.Stop
    }

    companion object {
        /** Marge avant de considérer que du temps a été ajouté (les instantanés arrivent par à-coups). */
        const val SLACK_MS = 1_500L
        /** En dessous, inutile de lancer un bout de son. */
        const val MIN_REMAINING_MS = 700L
    }
}
