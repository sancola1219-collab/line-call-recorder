package com.linerecorder.app.util

import android.content.Context
import android.media.MediaMetadataRetriever
import com.linerecorder.app.model.Recording
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 錄音檔案管理器
 */
class RecordingManager(private val context: Context) {
    
    private val preferenceManager = PreferenceManager(context)
    
    /**
     * 獲取錄音儲存目錄
     */
    fun getRecordingsDirectory(): File {
        val customPath = preferenceManager.storagePath
        val dir = if (customPath != null) {
            File(customPath)
        } else {
            File(context.getExternalFilesDir(null), "Recordings")
        }
        
        if (!dir.exists()) {
            dir.mkdirs()
        }
        
        return dir
    }
    
    /**
     * 生成新的錄音檔案名稱
     */
    fun generateFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        val format = preferenceManager.audioFormat
        return "LINE_${timestamp}.$format"
    }
    
    /**
     * 獲取新的錄音檔案路徑
     */
    fun getNewRecordingFile(): File {
        val dir = getRecordingsDirectory()
        val fileName = generateFileName()
        return File(dir, fileName)
    }
    
    /**
     * 獲取所有錄音檔案
     */
    fun getAllRecordings(): List<Recording> {
        val dir = getRecordingsDirectory()
        if (!dir.exists()) return emptyList()
        
        val supportedExtensions = listOf("m4a", "3gp", "wav", "mp3", "aac", "ogg")
        
        return dir.listFiles()
            ?.filter { file ->
                file.isFile && supportedExtensions.any { 
                    file.name.lowercase().endsWith(".$it") 
                }
            }
            ?.mapNotNull { file -> createRecordingFromFile(file) }
            ?.sortedByDescending { it.createdAt }
            ?: emptyList()
    }
    
    /**
     * 從檔案建立 Recording 物件
     */
    private fun createRecordingFromFile(file: File): Recording? {
        if (!file.exists()) return null
        
        val duration = getAudioDuration(file)
        
        return Recording(
            id = file.lastModified(),
            filePath = file.absolutePath,
            fileName = file.name,
            duration = duration,
            fileSize = file.length(),
            createdAt = file.lastModified()
        )
    }
    
    /**
     * 獲取音訊檔案時長
     */
    private fun getAudioDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
    
    /**
     * 刪除錄音檔案
     */
    fun deleteRecording(recording: Recording): Boolean {
        return try {
            recording.file.delete()
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 刪除多個錄音檔案
     */
    fun deleteRecordings(recordings: List<Recording>): Int {
        var deletedCount = 0
        recordings.forEach { recording ->
            if (deleteRecording(recording)) {
                deletedCount++
            }
        }
        return deletedCount
    }
    
    /**
     * 獲取錄音總數
     */
    fun getRecordingsCount(): Int {
        return getAllRecordings().size
    }
    
    /**
     * 獲取錄音總大小
     */
    fun getTotalRecordingsSize(): Long {
        return getAllRecordings().sumOf { it.fileSize }
    }
    
    /**
     * 獲取格式化的總大小
     */
    fun getFormattedTotalSize(): String {
        val totalSize = getTotalRecordingsSize()
        return when {
            totalSize < 1024 -> "$totalSize B"
            totalSize < 1024 * 1024 -> String.format("%.1f KB", totalSize / 1024.0)
            totalSize < 1024 * 1024 * 1024 -> String.format("%.1f MB", totalSize / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", totalSize / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * 清理舊的錄音檔案（保留最近 N 個）
     */
    fun cleanupOldRecordings(keepCount: Int): Int {
        val recordings = getAllRecordings()
        if (recordings.size <= keepCount) return 0
        
        val toDelete = recordings.drop(keepCount)
        return deleteRecordings(toDelete)
    }
    
    /**
     * 清理超過指定天數的錄音
     */
    fun cleanupRecordingsOlderThan(days: Int): Int {
        val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
        val toDelete = getAllRecordings().filter { it.createdAt < cutoffTime }
        return deleteRecordings(toDelete)
    }
}
