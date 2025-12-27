package com.linerecorder.app.model

import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 錄音檔案資料模型
 */
data class Recording(
    val id: Long,
    val filePath: String,
    val fileName: String,
    val duration: Long,  // 毫秒
    val fileSize: Long,  // 位元組
    val createdAt: Long, // 時間戳
    val callerInfo: String? = null
) {
    val file: File
        get() = File(filePath)
    
    val exists: Boolean
        get() = file.exists()
    
    val formattedDuration: String
        get() {
            val seconds = (duration / 1000) % 60
            val minutes = (duration / (1000 * 60)) % 60
            val hours = duration / (1000 * 60 * 60)
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    
    val formattedFileSize: String
        get() {
            return when {
                fileSize < 1024 -> "$fileSize B"
                fileSize < 1024 * 1024 -> String.format("%.1f KB", fileSize / 1024.0)
                else -> String.format("%.1f MB", fileSize / (1024.0 * 1024.0))
            }
        }
    
    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            return sdf.format(Date(createdAt))
        }
    
    companion object {
        fun fromFile(file: File): Recording? {
            if (!file.exists()) return null
            
            return Recording(
                id = file.lastModified(),
                filePath = file.absolutePath,
                fileName = file.name,
                duration = 0, // 需要透過 MediaMetadataRetriever 獲取
                fileSize = file.length(),
                createdAt = file.lastModified()
            )
        }
    }
}

/**
 * 錄音狀態
 */
enum class RecordingState {
    IDLE,       // 待機
    PREPARING,  // 準備中
    RECORDING,  // 錄音中
    PAUSED,     // 暫停
    STOPPING,   // 停止中
    ERROR       // 錯誤
}

/**
 * 通話狀態
 */
enum class CallState {
    IDLE,       // 無通話
    RINGING,    // 響鈴中
    ACTIVE,     // 通話中
    ENDED       // 通話結束
}
