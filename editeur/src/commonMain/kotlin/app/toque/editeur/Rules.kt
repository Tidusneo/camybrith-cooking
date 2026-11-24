package app.toque.editeur

/**
 * Ce que le Monsieur Cuisine Smart accepte, programme par programme. Mêmes valeurs que l'appli
 * (Limits, Programs et SmartProgram dans core ; RulesTest vérifie qu'elles concordent) : le
 * formulaire ne propose que des réglages que l'appli pourra lancer.
 */
object Rules {
    const val MAX_SPEED = 10
    const val MAX_SPEED_HEATED = 3
    const val MAX_SPEED_REVERSE = 3
    const val MAX_SPEED_WHISK = 4
    /** Au-delà de cette vitesse, [MAX_SECONDS_HIGH_SPEED] au plus. */
    const val HIGH_SPEED = 6
    const val MAX_SECONDS_HIGH_SPEED = 15 * 60
    const val MAX_PULSES = 20
    const val MAX_GRAMS = 5000

    /** Températures de la cuisson manuelle : 40 à 130 °C de 5 en 5. */
    val TEMPERATURES = (40..130 step 5).toList()

    /** Limites d'un programme : durée en secondes, température en °C (0 : sans réglage de température). */
    class Spec(
        val minS: Int, val maxS: Int, val stepS: Int, val defaultS: Int,
        val minC: Int = 0, val maxC: Int = 0, val stepC: Int = 0, val defaultC: Int = 0,
    ) {
        val temperatures: List<Int> get() = if (stepC == 0) emptyList() else (minC..maxC step stepC).toList()
    }

    fun spec(p: Program): Spec = when (p) {
        Program.MANUAL -> Spec(1, 99 * 60, 1, 60, 40, 130, 5, 0)
        Program.KNEAD -> Spec(1, 120, 1, 45)
        Program.STEAM -> Spec(1, 99 * 60, 1, 20 * 60)
        Program.TOAST -> Spec(1, 14 * 60, 1, 7 * 60, 40, 130, 5, 130)
        Program.TURBO -> Spec(0, 0, 0, 0)
        Program.PUREE -> Spec(5, 180, 5, 30)
        Program.SMOOTHIE -> Spec(10, 180, 5, 60)
        Program.SOUS_VIDE -> Spec(60, 12 * 3600, 60, 3600, 40, 85, 1, 60)
        Program.SLOW_COOK -> Spec(60, 8 * 3600, 60, 2 * 3600, 60, 100, 5, 90)
        Program.FERMENT -> Spec(60, 12 * 3600, 60, 8 * 3600, 30, 50, 1, 40)
    }

    /** Température fixée par le programme (vapeur), 0 sinon. */
    fun fixedTemperature(p: Program): Int = if (p == Program.STEAM) 120 else 0

    /** Vitesse la plus haute permise en cuisson manuelle avec ces réglages. */
    fun maxSpeed(m: Machine): Int {
        var max = MAX_SPEED
        if (m.tempC > 0) max = minOf(max, MAX_SPEED_HEATED)
        if (m.reverse) max = minOf(max, MAX_SPEED_REVERSE)
        if (m.whisk) max = minOf(max, MAX_SPEED_WHISK)
        return max
    }

    fun maxSeconds(m: Machine): Int {
        val max = spec(m.program).maxS
        return if (m.program == Program.MANUAL && m.speed > HIGH_SPEED) minOf(max, MAX_SECONDS_HIGH_SPEED) else max
    }

    fun minSeconds(m: Machine): Int = spec(m.program).minS

    /** Durée hors des limites du programme (à corriger avant d'enregistrer). */
    fun durationOk(m: Machine): Boolean {
        if (m.program == Program.TURBO) return true
        val s = spec(m.program)
        return m.seconds in s.minS..maxSeconds(m) && (s.stepS <= 1 || m.seconds % s.stepS == 0)
    }

    /** Réglage par défaut d'un programme. */
    fun defaults(p: Program): Machine {
        val s = spec(p)
        return when (p) {
            Program.MANUAL -> Machine(p, s.defaultS, speed = 1)
            Program.TURBO -> Machine(p, 0, speed = 0, pulses = 3)
            Program.TOAST -> Machine(p, s.defaultS, speed = 0, tempC = s.defaultC, reverse = true)
            else -> Machine(p, s.defaultS, speed = 0, tempC = fixedTemperature(p).takeIf { it > 0 } ?: s.defaultC)
        }
    }

    /**
     * Réglage ramené à ce que permet son programme : vitesse plafonnée (chauffe, sens inverse,
     * fouet), température de la liste, champs sans objet remis à zéro. La durée n'est pas
     * touchée (elle se tape) : voir [durationOk].
     */
    fun normalize(m: Machine): Machine {
        val s = spec(m.program)
        return when (m.program) {
            Program.MANUAL -> {
                val t = if (m.tempC <= 0) 0 else nearest(m.tempC, TEMPERATURES)
                val x = m.copy(tempC = t, pulses = 0)
                x.copy(speed = x.speed.coerceIn(1, maxSpeed(x)))
            }
            Program.TURBO -> Machine(Program.TURBO, 0, 0, 0, pulses = m.pulses.coerceIn(1, MAX_PULSES))
            Program.TOAST -> Machine(m.program, m.seconds, 0, nearest(m.tempC.takeIf { it > 0 } ?: s.defaultC, s.temperatures), reverse = true)
            else -> {
                val fixed = fixedTemperature(m.program)
                val t = when {
                    fixed > 0 -> fixed
                    s.stepC == 0 -> 0
                    else -> nearest(m.tempC.takeIf { it > 0 } ?: s.defaultC, s.temperatures)
                }
                Machine(m.program, m.seconds, 0, t)
            }
        }
    }

    private fun nearest(v: Int, list: List<Int>): Int = list.minByOrNull { kotlin.math.abs(it - v) } ?: v

    /** « 45 s », « 2 min », « 1 min 30 s », « 1 h 30 min ». */
    fun duration(seconds: Int): String = when {
        seconds < 60 -> "$seconds s"
        seconds % 3600 == 0 -> "${seconds / 3600} h"
        seconds >= 3600 -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
        seconds % 60 == 0 -> "${seconds / 60} min"
        else -> "${seconds / 60} min ${seconds % 60} s"
    }
}
