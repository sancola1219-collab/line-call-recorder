package com.linerecorder.app.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.linerecorder.app.R
import com.linerecorder.app.databinding.ActivityMainBinding
import com.linerecorder.app.model.CallState
import com.linerecorder.app.model.RecordingState
import com.linerecorder.app.service.LineCallDetectorService
import com.linerecorder.app.service.RecordingService

/**
 * 主頁面 Activity
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var audioManager: AudioManager
    
    private var recordingService: RecordingService? = null
    private var isServiceBound = false
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateUI()
            handler.postDelayed(this, 1000)
        }
    }
    
    // 權限請求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "權限已授予", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "需要權限才能錄音", Toast.LENGTH_LONG).show()
        }
    }
    
    // 服務連接
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as RecordingService.LocalBinder
            recordingService = binder.getService()
            isServiceBound = true
            
            recordingService?.onRecordingStateChanged = { state ->
                runOnUiThread { updateRecordingStatus(state) }
            }
            
            updateUI()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            recordingService = null
            isServiceBound = false
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        setupUI()
        checkPermissions()
    }
    
    override fun onResume() {
        super.onResume()
        bindRecordingService()
        handler.post(updateRunnable)
        
        // 設定 LINE 通話狀態監聽
        LineCallDetectorService.onCallStateChanged = { state ->
            runOnUiThread { updateCallState(state) }
        }
    }
    
    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
    }
    
    private fun setupUI() {
        // 啟用服務按鈕
        binding.btnEnableService.setOnClickListener {
            openAccessibilitySettings()
        }
        
        // 查看錄音按鈕
        binding.btnViewRecordings.setOnClickListener {
            startActivity(Intent(this, RecordingsActivity::class.java))
        }
        
        // 設定按鈕
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
    
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        
        // 錄音權限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // 通知權限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
    
    private fun bindRecordingService() {
        val intent = Intent(this, RecordingService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun updateUI() {
        updateServiceStatus()
        updateRecordingStatus(recordingService?.getRecordingState() ?: RecordingState.IDLE)
        updateHeadsetStatus()
    }
    
    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        
        val indicator = binding.serviceStatusIndicator.background as? GradientDrawable
        if (isEnabled) {
            indicator?.setColor(ContextCompat.getColor(this, R.color.status_enabled))
            binding.serviceStatusText.text = getString(R.string.service_enabled)
            binding.btnEnableService.text = "已啟用"
            binding.btnEnableService.isEnabled = false
        } else {
            indicator?.setColor(ContextCompat.getColor(this, R.color.status_disabled))
            binding.serviceStatusText.text = getString(R.string.service_disabled)
            binding.btnEnableService.text = getString(R.string.enable_service)
            binding.btnEnableService.isEnabled = true
        }
    }
    
    private fun updateRecordingStatus(state: RecordingState) {
        val indicator = binding.recordingStatusIndicator.background as? GradientDrawable
        
        when (state) {
            RecordingState.RECORDING -> {
                indicator?.setColor(ContextCompat.getColor(this, R.color.status_recording))
                binding.recordingStatusText.text = getString(R.string.recording_active)
                binding.recordingDuration.visibility = android.view.View.VISIBLE
                binding.waveformContainer.visibility = android.view.View.VISIBLE
                
                // 更新錄音時長
                val duration = recordingService?.getRecordingDuration() ?: 0
                binding.recordingDuration.text = formatDuration(duration)
            }
            RecordingState.PAUSED -> {
                indicator?.setColor(ContextCompat.getColor(this, R.color.accent))
                binding.recordingStatusText.text = "已暫停"
                binding.recordingDuration.visibility = android.view.View.VISIBLE
                binding.waveformContainer.visibility = android.view.View.GONE
            }
            else -> {
                indicator?.setColor(ContextCompat.getColor(this, R.color.status_idle))
                binding.recordingStatusText.text = getString(R.string.recording_idle)
                binding.recordingDuration.visibility = android.view.View.GONE
                binding.waveformContainer.visibility = android.view.View.GONE
            }
        }
    }
    
    private fun updateCallState(state: CallState) {
        when (state) {
            CallState.RINGING -> {
                binding.recordingStatusText.text = "LINE 來電中..."
            }
            CallState.ACTIVE -> {
                binding.recordingStatusText.text = "LINE 通話中"
            }
            CallState.ENDED -> {
                binding.recordingStatusText.text = "通話結束"
            }
            CallState.IDLE -> {
                // 由 updateRecordingStatus 處理
            }
        }
    }
    
    private fun updateHeadsetStatus() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val hasWiredHeadset = devices.any { 
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
        val hasBluetoothHeadset = devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
        }
        
        val (text, iconTint) = when {
            hasBluetoothHeadset -> "藍牙耳機已連接" to R.color.primary
            hasWiredHeadset -> "有線耳機已連接" to R.color.primary
            else -> "未連接耳機" to R.color.text_secondary
        }
        
        binding.headsetStatusText.text = text
        binding.headsetIcon.setColorFilter(ContextCompat.getColor(this, iconTint))
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        
        val serviceName = ComponentName(this, LineCallDetectorService::class.java)
        return enabledServices.any { service ->
            service.resolveInfo.serviceInfo.let {
                ComponentName(it.packageName, it.name) == serviceName
            }
        }
    }
    
    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "請找到「LINE 通話錄音」並啟用", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "無法開啟設定", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
