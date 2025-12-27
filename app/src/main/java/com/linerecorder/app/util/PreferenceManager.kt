package com.linerecorder.app.util

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaRecorder

/**
 * 偏好設定管理器
 */
class PreferenceManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "line_recorder_prefs"
        
        // 音訊設定
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_AUDIO_FORMAT = "audio_format"
        private const val KEY_SAMPLE_RATE = "sample_rate"
        private const val KEY_BIT_RATE = "bit_rate"
        
        // 一般設定
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_SHOW_NOTIFICATION = "show_notification"
        private const val KEY_VIBRATE_ON_START = "vibrate_on_start"
        
        // 儲存設定
        private const val KEY_STORAGE_PATH = "storage_path"
        private const val KEY_MAX_RECORDING_DURATION = "max_recording_duration"
        
        // 預設值
        const val DEFAULT_AUDIO_SOURCE = MediaRecorder.AudioSource.VOICE_COMMUNICATION
        const val DEFAULT_AUDIO_FORMAT = "m4a"
        const val DEFAULT_SAMPLE_RATE = 44100
        const val DEFAULT_BIT_RATE = 128000
        const val DEFAULT_MAX_DURATION = 3600000L // 1小時
    }
    
    // 音訊來源
    var audioSource: Int
        get() = prefs.getInt(KEY_AUDIO_SOURCE, DEFAULT_AUDIO_SOURCE)
        set(value) = prefs.edit().putInt(KEY_AUDIO_SOURCE, value).apply()
    
    // 音訊格式
    var audioFormat: String
        get() = prefs.getString(KEY_AUDIO_FORMAT, DEFAULT_AUDIO_FORMAT) ?: DEFAULT_AUDIO_FORMAT
        set(value) = prefs.edit().putString(KEY_AUDIO_FORMAT, value).apply()
    
    // 取樣率
    var sampleRate: Int
        get() = prefs.getInt(KEY_SAMPLE_RATE, DEFAULT_SAMPLE_RATE)
        set(value) = prefs.edit().putInt(KEY_SAMPLE_RATE, value).apply()
    
    // 位元率
    var bitRate: Int
        get() = prefs.getInt(KEY_BIT_RATE, DEFAULT_BIT_RATE)
        set(value) = prefs.edit().putInt(KEY_BIT_RATE, value).apply()
    
    // 開機自動啟動
    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()
    
    // 顯示通知
    var showNotification: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NOTIFICATION, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_NOTIFICATION, value).apply()
    
    // 開始錄音時震動
    var vibrateOnStart: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE_ON_START, false)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE_ON_START, value).apply()
    
    // 儲存路徑
    var storagePath: String?
        get() = prefs.getString(KEY_STORAGE_PATH, null)
        set(value) = prefs.edit().putString(KEY_STORAGE_PATH, value).apply()
    
    // 最大錄音時長
    var maxRecordingDuration: Long
        get() = prefs.getLong(KEY_MAX_RECORDING_DURATION, DEFAULT_MAX_DURATION)
        set(value) = prefs.edit().putLong(KEY_MAX_RECORDING_DURATION, value).apply()
    
    /**
     * 獲取音訊來源名稱
     */
    fun getAudioSourceName(): String {
        return when (audioSource) {
            MediaRecorder.AudioSource.MIC -> "麥克風"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "語音通訊"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "語音識別"
            MediaRecorder.AudioSource.CAMCORDER -> "攝影機"
            else -> "預設"
        }
    }
    
    /**
     * 獲取可用的音訊來源列表
     */
    fun getAvailableAudioSources(): List<Pair<Int, String>> {
        return listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "語音通訊 (推薦)",
            MediaRecorder.AudioSource.MIC to "麥克風",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "語音識別"
        )
    }
    
    /**
     * 獲取可用的音訊格式列表
     */
    fun getAvailableAudioFormats(): List<Pair<String, String>> {
        return listOf(
            "m4a" to "M4A (AAC)",
            "3gp" to "3GP (AMR)",
            "wav" to "WAV (PCM)"
        )
    }
    
    /**
     * 獲取可用的取樣率列表
     */
    fun getAvailableSampleRates(): List<Pair<Int, String>> {
        return listOf(
            8000 to "8 kHz (低品質)",
            16000 to "16 kHz",
            22050 to "22.05 kHz",
            44100 to "44.1 kHz (CD品質)",
            48000 to "48 kHz (高品質)"
        )
    }
}
