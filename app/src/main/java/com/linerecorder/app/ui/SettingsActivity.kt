package com.linerecorder.app.ui

import android.media.MediaRecorder
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.linerecorder.app.databinding.ActivitySettingsBinding
import com.linerecorder.app.util.PreferenceManager
import com.linerecorder.app.util.RecordingManager

/**
 * 設定頁面 Activity
 */
class SettingsActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var recordingManager: RecordingManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        preferenceManager = PreferenceManager(this)
        recordingManager = RecordingManager(this)
        
        setupToolbar()
        setupSettings()
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        updateStorageInfo()
    }
    
    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }
    
    private fun setupSettings() {
        // 音訊來源設定
        binding.audioSourceSetting.setOnClickListener {
            showAudioSourceDialog()
        }
        
        // 音訊格式設定
        binding.audioFormatSetting.setOnClickListener {
            showAudioFormatDialog()
        }
        
        // 取樣率設定
        binding.sampleRateSetting.setOnClickListener {
            showSampleRateDialog()
        }
        
        // 開機自動啟動
        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.autoStart = isChecked
        }
        
        // 顯示錄音通知
        binding.switchShowNotification.setOnCheckedChangeListener { _, isChecked ->
            preferenceManager.showNotification = isChecked
        }
    }
    
    private fun updateUI() {
        // 音訊來源
        binding.audioSourceValue.text = getAudioSourceName(preferenceManager.audioSource)
        
        // 音訊格式
        binding.audioFormatValue.text = getAudioFormatName(preferenceManager.audioFormat)
        
        // 取樣率
        binding.sampleRateValue.text = getSampleRateName(preferenceManager.sampleRate)
        
        // 開關狀態
        binding.switchAutoStart.isChecked = preferenceManager.autoStart
        binding.switchShowNotification.isChecked = preferenceManager.showNotification
        
        // 儲存資訊
        updateStorageInfo()
    }
    
    private fun updateStorageInfo() {
        binding.recordingsCount.text = recordingManager.getRecordingsCount().toString()
        binding.totalSize.text = recordingManager.getFormattedTotalSize()
        binding.storagePath.text = "內部儲存/Recordings"
    }
    
    private fun showAudioSourceDialog() {
        val sources = preferenceManager.getAvailableAudioSources()
        val currentIndex = sources.indexOfFirst { it.first == preferenceManager.audioSource }
        
        AlertDialog.Builder(this)
            .setTitle("選擇音訊來源")
            .setSingleChoiceItems(
                sources.map { it.second }.toTypedArray(),
                currentIndex.coerceAtLeast(0)
            ) { dialog, which ->
                preferenceManager.audioSource = sources[which].first
                binding.audioSourceValue.text = sources[which].second
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showAudioFormatDialog() {
        val formats = preferenceManager.getAvailableAudioFormats()
        val currentIndex = formats.indexOfFirst { it.first == preferenceManager.audioFormat }
        
        AlertDialog.Builder(this)
            .setTitle("選擇音訊格式")
            .setSingleChoiceItems(
                formats.map { it.second }.toTypedArray(),
                currentIndex.coerceAtLeast(0)
            ) { dialog, which ->
                preferenceManager.audioFormat = formats[which].first
                binding.audioFormatValue.text = formats[which].second
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showSampleRateDialog() {
        val rates = preferenceManager.getAvailableSampleRates()
        val currentIndex = rates.indexOfFirst { it.first == preferenceManager.sampleRate }
        
        AlertDialog.Builder(this)
            .setTitle("選擇取樣率")
            .setSingleChoiceItems(
                rates.map { it.second }.toTypedArray(),
                currentIndex.coerceAtLeast(0)
            ) { dialog, which ->
                preferenceManager.sampleRate = rates[which].first
                binding.sampleRateValue.text = rates[which].second
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun getAudioSourceName(source: Int): String {
        return when (source) {
            MediaRecorder.AudioSource.MIC -> "麥克風"
            MediaRecorder.AudioSource.VOICE_COMMUNICATION -> "語音通訊 (推薦)"
            MediaRecorder.AudioSource.VOICE_RECOGNITION -> "語音識別"
            MediaRecorder.AudioSource.CAMCORDER -> "攝影機"
            else -> "預設"
        }
    }
    
    private fun getAudioFormatName(format: String): String {
        return when (format) {
            "m4a" -> "M4A (AAC)"
            "3gp" -> "3GP (AMR)"
            "wav" -> "WAV (PCM)"
            else -> format.uppercase()
        }
    }
    
    private fun getSampleRateName(rate: Int): String {
        return when (rate) {
            8000 -> "8 kHz (低品質)"
            16000 -> "16 kHz"
            22050 -> "22.05 kHz"
            44100 -> "44.1 kHz (CD品質)"
            48000 -> "48 kHz (高品質)"
            else -> "$rate Hz"
        }
    }
}
