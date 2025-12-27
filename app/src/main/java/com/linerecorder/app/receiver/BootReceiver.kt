package com.linerecorder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.linerecorder.app.service.RecordingService
import com.linerecorder.app.util.PreferenceManager

/**
 * 開機接收器
 * 在設備開機後自動啟動錄音服務
 */
class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed received")
            
            val preferenceManager = PreferenceManager(context)
            
            // 檢查是否啟用開機自動啟動
            if (preferenceManager.autoStart) {
                Log.i(TAG, "Auto start enabled, starting recording service")
                
                val serviceIntent = Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_START_SERVICE
                }
                
                context.startForegroundService(serviceIntent)
            } else {
                Log.i(TAG, "Auto start disabled")
            }
        }
    }
}
