package com.auraline.core.domain

/** Salida de audio activa en el dispositivo. */
enum class AudioRoute(val label: String) {
    SPEAKER("Altavoz"),
    WIRED_HEADSET("Auriculares con cable"),
    BLUETOOTH("Bluetooth"),
    USB("USB"),
    UNKNOWN("Salida desconocida")
}

/** Una etapa del procesamiento, con su estado real (no simulado). */
data class EffectStage(
    val name: String,
    val active: Boolean,
    val detail: String = ""
)

/** Sesión de audio a la que el motor está enganchado. */
data class AudioSession(
    val id: Int,
    val owner: String,
    val global: Boolean
)

/**
 * Estado real del motor tal y como lo reporta el sistema.
 * La UI lo muestra para que el usuario pueda comprobar que el procesado ocurre.
 */
data class EngineStatus(
    val running: Boolean = false,
    val globalScope: Boolean = false,
    val route: AudioRoute = AudioRoute.UNKNOWN,
    val sampleRateHz: Int = 0,
    val stages: List<EffectStage> = emptyList(),
    val sessions: List<AudioSession> = emptyList(),
    val lastError: String? = null
) {
    val activeStages: Int get() = stages.count { it.active }
}

/** Resultado de la comprobación acústica con el micrófono. */
data class AudioCheckResult(
    val referenceDb: Float = 0f,
    val processedDb: Float = 0f,
    val deltaDb: Float = 0f,
    val passed: Boolean = false,
    val message: String = ""
)
