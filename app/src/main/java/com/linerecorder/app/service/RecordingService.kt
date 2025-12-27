package com.linerecorder.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.linerecorder.app.R
import com.linerecorder.app.model.RecordingState
import com.linerecorder.app.ui.MainActivity
import com.linerecorder.app.util.AudioRecorderHelper
import com.linerecorder.app.util.PreferenceManager
import java.io.File

/**
 * 錄音前台服務
 * 負責在背景執行錄音任務
 */
class RecordingService : Service() {
    
    companion object {
        private const val TAG = "RecordingService"
        
        const val ACTION_START_SERVICE = "com.linerecorder.app.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.linerecorder.app.STOP_SERVICE"
        const val ACTION_START_RECORDING = "com.linerecorder.app.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.linerecorder.app.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.linerecorder.app.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.linerecorder.app.RESUME_RECORDING"
        
        private const val NOTIFICATION_CHANNEL_ID = "line_recorder_channel"
        private const val NOTIFICATION_ID = 1001
        private const val RECORDING_NOTIFICATION_ID = 1002
        
        // 服務實例
        var instance: RecordingService? = null
            private set
    }
    
    private val binder = LocalBinder()
    
    private lateinit var audioRecorderHelper: AudioRecorderHelper
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var audioManager: AudioManager
    private lateinit var notificationManager: NotificationManager
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    
    // 錄音狀態監聽器
    var onRecordingStateChanged: ((RecordingState) -> Unit)? = null
    var onRecordingCompleted: ((File?) -> Unit)? = null
    
    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service created")
        
        instance = this
        
        audioRecorderHelper = AudioRecorderHelper(this)
        preferenceManager = PreferenceManager(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 設定錄音狀態回調
        audioRecorderHelper.onStateChanged = { state ->
            Log.d(TAG, "Recording state changed: $state")
            onRecordingStateChanged?.invoke(state)
            updateNotification(state)
        }
        
        audioRecorderHelper.onError = { error ->
            Log.e(TAG, "Recording error: $error")
        }
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundService()
            }
            ACTION_STOP_SERVICE -> {
                stopForegroundService()
            }
            ACTION_START_RECORDING -> {
                startRecording()
            }
            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
            ACTION_PAUSE_RECORDING -> {
                pauseRecording()
            }
            ACTION_RESUME_RECORDING -> {
                resumeRecording()
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    /**
     * 建立通知頻道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 啟動前台服務
     */
    private fun startForegroundService() {
        if (isServiceRunning) return
        
        Log.i(TAG, "Starting foreground service")
        
        val notification = createServiceNotification()
        startForeground(NOTIFICATION_ID, notification)
        
        acquireWakeLock()
        isServiceRunning = true
    }
    
    /**
     * 停止前台服務
     */
    private fun stopForegroundService() {
        Log.i(TAG, "Stopping foreground service")
        
        stopRecording()
        releaseWakeLock()
        
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        isServiceRunning = false
    }
    
    /**
     * 建立服務通知
     */
    private fun createServiceNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_service_title))
            .setContentText(getString(R.string.notification_service_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * 建立錄音中通知
     */
    private fun createRecordingNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // 停止錄音按鈕
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, RecordingService::class.java).apply {
                action = ACTION_STOP_RECORDING
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_recording_title))
            .setContentText(getString(R.string.notification_recording_text))
            .setSmallIcon(R.drawable.ic_recording)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(getColor(R.color.status_recording))
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopIntent)
            .build()
    }
    
    /**
     * 更新通知
     */
    private fun updateNotification(state: RecordingState) {
        val notification = when (state) {
            RecordingState.RECORDING -> createRecordingNotification()
            else -> createServiceNotification()
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * 開始錄音
     */
    fun startRecording() {
        if (audioRecorderHelper.isRecording()) {
            Log.w(TAG, "Already recording")
            return
        }
        
        Log.i(TAG, "Starting recording")
        
        // 配置音訊設定以支援耳機
        configureAudioForRecording()
        
        val success = audioRecorderHelper.startRecording()
        if (success) {
            updateNotification(RecordingState.RECORDING)
        }
    }
    
    /**
     * 停止錄音
     */
    fun stopRecording() {
        if (!audioRecorderHelper.isRecording()) {
            return
        }
        
        Log.i(TAG, "Stopping recording")
        
        val file = audioRecorderHelper.stopRecording()
        onRecordingCompleted?.invoke(file)
        
        // 恢復音訊設定
        restoreAudioSettings()
        
        updateNotification(RecordingState.IDLE)
    }
    
    /**
     * 暫停錄音
     */
    fun pauseRecording() {
        audioRecorderHelper.pauseRecording()
    }
    
    /**
     * 恢復錄音
     */
    fun resumeRecording() {
        audioRecorderHelper.resumeRecording()
    }
    
    /**
     * 配置音訊以支援錄音（包括耳機）
     */
    private fun configureAudioForRecording() {
        try {
            // 檢查是否連接耳機
            val hasHeadset = hasConnectedHeadset()
            Log.d(TAG, "Headset connected: $hasHeadset")
            
            // 設定音訊模式為通訊模式，這樣可以在耳機模式下錄音
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            
            // 如果有耳機，確保使用耳機麥克風
            if (hasHeadset) {
                // 啟用藍牙 SCO（如果是藍牙耳機）
                if (hasBluetoothHeadset()) {
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring audio", e)
        }
    }
    
    /**
     * 恢復音訊設定
     */
    private fun restoreAudioSettings() {
        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            
            if (audioManager.isBluetoothScoOn) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring audio settings", e)
        }
    }
    
    /**
     * 檢查是否連接耳機
     */
    private fun hasConnectedHeadset(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }
    
    /**
     * 檢查是否連接藍牙耳機
     */
    private fun hasBluetoothHeadset(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        return devices.any { device ->
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
    }
    
    /**
     * 獲取 WakeLock 保持 CPU 運行
     */
    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LineRecorder::RecordingWakeLock"
            )
        }
        wakeLock?.acquire(10 * 60 * 1000L) // 最多 10 分鐘
    }
    
    /**
     * 釋放 WakeLock
     */
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    /**
     * 是否正在錄音
     */
    fun isRecording(): Boolean = audioRecorderHelper.isRecording()
    
    /**
     * 獲取錄音時長
     */
    fun getRecordingDuration(): Long = audioRecorderHelper.getRecordingDuration()
    
    /**
     * 獲取錄音狀態
     */
    fun getRecordingState(): RecordingState = audioRecorderHelper.state
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service destroyed")
        
        audioRecorderHelper.release()
        releaseWakeLock()
        instance = null
    }
}
