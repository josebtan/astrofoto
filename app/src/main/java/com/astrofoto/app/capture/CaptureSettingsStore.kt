package com.astrofoto.app.capture

import android.content.Context

/**
 * Guarda los parámetros de captura en SharedPreferences para que no se
 * pierdan al cerrar la app — quedan como estaban hasta que el usuario
 * los vuelva a modificar.
 */
class CaptureSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isoIndex: Int
        get() = prefs.getInt(KEY_ISO_INDEX, 2)
        set(value) = prefs.edit().putInt(KEY_ISO_INDEX, value).apply()

    var shutterIndex: Int
        get() = prefs.getInt(KEY_SHUTTER_INDEX, 12)
        set(value) = prefs.edit().putInt(KEY_SHUTTER_INDEX, value).apply()

    var manualFocusEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANUAL_FOCUS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MANUAL_FOCUS_ENABLED, value).apply()

    var focusDistance: Float
        get() = prefs.getFloat(KEY_FOCUS_DISTANCE, 0f)
        set(value) = prefs.edit().putFloat(KEY_FOCUS_DISTANCE, value).apply()

    var intervalEnabled: Boolean
        get() = prefs.getBoolean(KEY_INTERVAL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_INTERVAL_ENABLED, value).apply()

    var intervalSeconds: Int
        get() = prefs.getInt(KEY_INTERVAL_SECONDS, 5)
        set(value) = prefs.edit().putInt(KEY_INTERVAL_SECONDS, value).apply()

    var burstFrameCount: Int
        get() = prefs.getInt(KEY_BURST_FRAME_COUNT, 15)
        set(value) = prefs.edit().putInt(KEY_BURST_FRAME_COUNT, value).apply()

    companion object {
        private const val PREFS_NAME = "capture_settings"
        private const val KEY_ISO_INDEX = "iso_index"
        private const val KEY_SHUTTER_INDEX = "shutter_index"
        private const val KEY_MANUAL_FOCUS_ENABLED = "manual_focus_enabled"
        private const val KEY_FOCUS_DISTANCE = "focus_distance"
        private const val KEY_INTERVAL_ENABLED = "interval_enabled"
        private const val KEY_INTERVAL_SECONDS = "interval_seconds"
        private const val KEY_BURST_FRAME_COUNT = "burst_frame_count"
    }
}
