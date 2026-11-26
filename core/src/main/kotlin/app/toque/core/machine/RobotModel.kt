package app.toque.core.machine

import app.toque.core.i18n.tr
import app.toque.core.i18n.trKey
import app.toque.core.protocol.Mcs
import app.toque.core.recipes.McOnline
import app.toque.core.smart.SmartController

/**
 * Les deux robots pris en charge. Tout ce qui les distingue côté pilotage est ici : port série,
 * vitesse, appli d'origine et son service moteur, période d'émission, cœur de pilotage.
 */
enum class RobotModel(
    val key: String,
    private val fr: String,
    /** Appli d'origine du robot. */
    val vendorPackage: String,
    val serialDevice: String,
    val baud: Int,
    /** Fin du nom du service de l'appli d'origine qui ouvre le port série (désactivé par Toque). */
    val hardwareServiceSuffix: String,
    /** Période d'émission des commandes. */
    val periodMs: Long,
) {
    CONNECT("connect", trKey("Monsieur Cuisine Connect"), "de.silpion.mc2", "/dev/ttyMT0", 4800, "HardwareMachineService", 100),

    /** SKMS 1200 A1 (MediaTek) : appli com.tecpal.device.mc30, qui ouvre le port dans SerialPortMC30Service.onBind. */
    SMART("smart", trKey("Monsieur Cuisine Smart"), "com.tecpal.device.mc30", Mcs.DEVICE_PATH, Mcs.BAUD_RATE, "SerialPortMC30Service", 200),

    /** SKMS 1200 C1 (Allwinner R818) : port /dev/ttyS2. */
    SMART_R818("smart_r818", trKey("Monsieur Cuisine Smart (R818)"), "com.tecpal.device.mc30", Mcs.DEVICE_PATH_ALLWINNER, Mcs.BAUD_RATE, "SerialPortMC30Service", 200);

    val label: String get() = tr(fr)

    val isSmart: Boolean get() = this == SMART || this == SMART_R818

    /** Type d'appareil des recettes sur monsieur-cuisine.com. */
    val recipeDevice: String get() = when (this) {
        CONNECT -> McOnline.DEVICE
        SMART, SMART_R818 -> McOnline.DEVICE_SMART
    }

    val temperatures: TemperatureTable get() = when (this) {
        CONNECT -> TemperatureTable.CONNECT
        SMART, SMART_R818 -> TemperatureTable.SMART
    }

    fun controller(clock: Clock, config: ControllerConfig): MachineCore = when (this) {
        CONNECT -> MachineController(clock, config)
        SMART -> SmartController(clock)
        SMART_R818 -> SmartController(clock, app.toque.core.smart.SmartConfig(staleIdleMs = 4_000L, sendConstantTemperature = true))
    }

    companion object {
        const val AUTO = "auto"

        fun of(key: String?): RobotModel? = values().firstOrNull { it.key == key }

        /**
         * Modèle déduit de la carte ou de l'appli d'origine installée. Le Connect reste le choix par défaut
         * (téléphone de démonstration, appli d'origine désinstallée).
         */
        fun detect(installed: (String) -> Boolean): RobotModel = detect(installed) { false }

        fun detect(installed: (String) -> Boolean, isR818Board: () -> Boolean): RobotModel = when {
            isR818Board() -> SMART_R818
            installed(SMART.vendorPackage) && !installed(CONNECT.vendorPackage) -> SMART
            else -> CONNECT
        }

        /** Vrai si [serviceName] est le service moteur de l'appli d'origine de l'un des robots. */
        fun isHardwareService(serviceName: String): Boolean = values().any { serviceName.endsWith(it.hardwareServiceSuffix) }
    }
}
