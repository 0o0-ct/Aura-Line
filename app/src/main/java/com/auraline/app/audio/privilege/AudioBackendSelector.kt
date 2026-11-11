package com.auraline.app.audio.privilege

object AudioBackendSelector {

    fun selectBackend(
        caps: DeviceCapabilities,
        dynamicsProcessingSupported: Boolean,
        captureSupported: Boolean
    ): AudioIntegrationMode {
        return when {
            // Level 3: Root HAL Module
            caps.accessLevel == AccessLevel.ROOT_SYSTEM && caps.effectsHalSupported -> {
                AudioIntegrationMode.SystemEffectsHal
            }
            // Level 2 / Level 1: Rust Owned PCM Stream
            caps.shizukuAuthorized && captureSupported -> {
                AudioIntegrationMode.RustCapturePlayback
            }
            // Level 1: Standard Framework Sessions
            dynamicsProcessingSupported -> {
                AudioIntegrationMode.FrameworkSession
            }
            // Fallback: Pure Rust PCM Engine
            else -> {
                AudioIntegrationMode.RustOwnedPcm
            }
        }
    }
}
