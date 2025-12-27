package com.linerecorder.app.util

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.linerecorder.app.model.RecordingState
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 音訊錄製器輔助類
 * 支援 MediaRecorder 和 AudioRecord 兩種錄製方式
 */
class AudioRecorderHelper(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioRecorderHelper"
        private const val BUFFER_SIZE_FACTOR = 2
    }
    
    private val preferenceManager = PreferenceManager(context)
    private val recordingManager = RecordingManager(context)
    
    private var mediaRecorder: MediaRecorder? = null
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    
    private var currentFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    
    var state: RecordingState = RecordingState.IDLE
        private set
    
    var onStateChanged: ((RecordingState) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onAmplitudeChanged: ((Int) -> Unit)? = null
    
    /**
     * 開始錄音
     */
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "Already recording")
            return false
        }
        
        try {
            currentFile = recordingManager.getNewRecordingFile()
            
            val format = preferenceManager.audioFormat
            val success = when (format) {
                "wav" -> startAudioRecordRecording()
                else -> startMediaRecorderRecording()
            }
            
            if (success) {
                isRecording = true
                isPaused = false
                startTime = System.currentTimeMillis()
                pausedDuration = 0
                updateState(RecordingState.RECORDING)
                Log.i(TAG, "Recording started: ${currentFile?.absolutePath}")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            onError?.invoke("錄音啟動失敗: ${e.message}")
            updateState(RecordingState.ERROR)
            return false
        }
    }
    
    /**
     * 使用 MediaRecorder 錄音
     */
    private fun startMediaRecorderRecording(): Boolean {
        return try {
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            
            mediaRecorder?.apply {
                // 使用 VOICE_COMMUNICATION 支援耳機錄音
                setAudioSource(preferenceManager.audioSource)
                
                val format = preferenceManager.audioFormat
                when (format) {
                    "m4a" -> {
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                    "3gp" -> {
                        setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    }
                    else -> {
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    }
                }
                
                setAudioSamplingRate(preferenceManager.sampleRate)
                setAudioEncodingBitRate(preferenceManager.bitRate)
                setOutputFile(currentFile?.absolutePath)
                
                // Android 11+ 設定非隱私敏感，允許與其他應用共享音訊輸入
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setPrivacySensitive(false)
                }
                
                prepare()
                start()
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaRecorder failed", e)
            mediaRecorder?.release()
            mediaRecorder = null
            false
        }
    }
    
    /**
     * 使用 AudioRecord 錄音 (WAV 格式)
     */
    private fun startAudioRecordRecording(): Boolean {
        return try {
            val sampleRate = preferenceManager.sampleRate
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR
            
            audioRecord = AudioRecord(
                preferenceManager.audioSource,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                audioRecord?.release()
                audioRecord = null
                return false
            }
            
            audioRecord?.startRecording()
            
            // 啟動錄音執行緒
            recordingThread = Thread {
                writeWavFile(bufferSize, sampleRate)
            }
            recordingThread?.start()
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord failed", e)
            audioRecord?.release()
            audioRecord = null
            false
        }
    }
    
    /**
     * 寫入 WAV 檔案
     */
    private fun writeWavFile(bufferSize: Int, sampleRate: Int) {
        val file = currentFile ?: return
        val buffer = ByteArray(bufferSize)
        
        try {
            FileOutputStream(file).use { fos ->
                // 寫入 WAV 檔頭 (暫時填充，稍後更新)
                writeWavHeader(fos, 0, sampleRate)
                
                var totalBytesWritten = 0L
                
                while (isRecording && !Thread.currentThread().isInterrupted) {
                    if (!isPaused) {
                        val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: -1
                        if (bytesRead > 0) {
                            fos.write(buffer, 0, bytesRead)
                            totalBytesWritten += bytesRead
                            
                            // 計算振幅
                            val amplitude = calculateAmplitude(buffer, bytesRead)
                            onAmplitudeChanged?.invoke(amplitude)
                        }
                    } else {
                        Thread.sleep(100)
                    }
                }
                
                // 更新 WAV 檔頭中的大小資訊
                updateWavHeader(file, totalBytesWritten, sampleRate)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing WAV file", e)
        }
    }
    
    /**
     * 寫入 WAV 檔頭
     */
    private fun writeWavHeader(fos: FileOutputStream, dataSize: Long, sampleRate: Int) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        
        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt((36 + dataSize).toInt())
        header.put("WAVE".toByteArray())
        
        // fmt subchunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size
        header.putShort(1) // AudioFormat (PCM)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        
        // data subchunk
        header.put("data".toByteArray())
        header.putInt(dataSize.toInt())
        
        fos.write(header.array())
    }
    
    /**
     * 更新 WAV 檔頭
     */
    private fun updateWavHeader(file: File, dataSize: Long, sampleRate: Int) {
        try {
            RandomAccessFile(file, "rw").use { raf ->
                // 更新檔案大小
                raf.seek(4)
                raf.write(intToByteArray((36 + dataSize).toInt()))
                
                // 更新資料大小
                raf.seek(40)
                raf.write(intToByteArray(dataSize.toInt()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header", e)
        }
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }
    
    /**
     * 計算振幅
     */
    private fun calculateAmplitude(buffer: ByteArray, bytesRead: Int): Int {
        var sum = 0.0
        for (i in 0 until bytesRead step 2) {
            if (i + 1 < bytesRead) {
                val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
                sum += sample * sample
            }
        }
        val rms = kotlin.math.sqrt(sum / (bytesRead / 2))
        return (rms / 32768.0 * 100).toInt().coerceIn(0, 100)
    }
    
    /**
     * 停止錄音
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            return null
        }
        
        updateState(RecordingState.STOPPING)
        isRecording = false
        
        try {
            // 停止 MediaRecorder
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            // 停止 AudioRecord
            audioRecord?.apply {
                stop()
                release()
            }
            audioRecord = null
            
            // 等待錄音執行緒結束
            recordingThread?.join(1000)
            recordingThread = null
            
            updateState(RecordingState.IDLE)
            Log.i(TAG, "Recording stopped: ${currentFile?.absolutePath}")
            
            return currentFile
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recording", e)
            onError?.invoke("停止錄音失敗: ${e.message}")
            updateState(RecordingState.ERROR)
            return null
        }
    }
    
    /**
     * 暫停錄音
     */
    fun pauseRecording() {
        if (!isRecording || isPaused) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.pause()
            }
            isPaused = true
            updateState(RecordingState.PAUSED)
            Log.i(TAG, "Recording paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing recording", e)
        }
    }
    
    /**
     * 恢復錄音
     */
    fun resumeRecording() {
        if (!isRecording || !isPaused) return
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mediaRecorder?.resume()
            }
            isPaused = false
            updateState(RecordingState.RECORDING)
            Log.i(TAG, "Recording resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Error resuming recording", e)
        }
    }
    
    /**
     * 獲取當前錄音時長（毫秒）
     */
    fun getRecordingDuration(): Long {
        if (!isRecording) return 0
        return System.currentTimeMillis() - startTime - pausedDuration
    }
    
    /**
     * 獲取當前振幅
     */
    fun getAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * 是否正在錄音
     */
    fun isRecording(): Boolean = isRecording
    
    /**
     * 是否暫停
     */
    fun isPaused(): Boolean = isPaused
    
    /**
     * 更新狀態
     */
    private fun updateState(newState: RecordingState) {
        state = newState
        onStateChanged?.invoke(newState)
    }
    
    /**
     * 釋放資源
     */
    fun release() {
        stopRecording()
        mediaRecorder?.release()
        mediaRecorder = null
        audioRecord?.release()
        audioRecord = null
    }
}
