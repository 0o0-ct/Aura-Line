package com.auraline.app.audio.privilege

import android.content.Context
import org.json.JSONObject

object SystemAudioDiagnostics {

    fun generateJsonDiagnostic(context: Context): String {
        val report = AudioCapabilityDetector.detect(context)
        val caps = report.capabilities

        val json = JSONObject()
        json.put("engineVersion", "Rust 1.0.0 (auraline-core)")
        json.put("androidApi", caps.androidApi)
        json.put("manufacturer", caps.manufacturer)
        json.put("model", caps.model)
        json.put("accessLevel", caps.accessLevel.name)
        json.put("effectiveUid", caps.effectiveUid)
        json.put("shizukuAvailable", caps.shizukuAvailable)
        json.put("shizukuAuthorized", caps.shizukuAuthorized)
        json.put("isShellUid", caps.isShellUid)
        json.put("isRootUid", caps.isRootUid)
        json.put("rootAvailable", caps.rootAvailable)
        json.put("rootAuthorized", caps.rootAuthorized)
        json.put("selinuxEnforcing", caps.selinuxEnforcing)
        json.put("audioHalType", caps.audioHalType)
        json.put("effectsHalSupported", caps.effectsHalSupported)
        json.put("nativeSampleRateHz", report.nativeSampleRateHz)
        json.put("dynamicsProcessingSupported", report.dynamicsProcessingSupported)
        json.put("captureSupported", report.captureSupported)
        json.put("selectedBackend", report.selectedBackend.name)
        json.put("activeOutputDevice", report.activeOutputDeviceName)

        return json.toString(2)
    }
}
