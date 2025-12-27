package com.linerecorder.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.linerecorder.app.model.CallState

/**
 * LINE 通話偵測無障礙服務
 * 監控 LINE 應用的視窗狀態變化，偵測通話開始和結束
 */
class LineCallDetectorService : AccessibilityService() {
    
    companion object {
        private const val TAG = "LineCallDetector"
        
        // LINE 應用程式包名
        const val LINE_PACKAGE = "jp.naver.line.android"
        
        // LINE 通話相關的視窗/活動名稱關鍵字
        private val CALL_ACTIVITY_KEYWORDS = listOf(
            "voip",
            "call",
            "VoIP",
            "Call",
            "InCall",
            "incall",
            "voice",
            "Voice",
            "video",
            "Video"
        )
        
        // LINE 通話相關的 UI 元素關鍵字
        private val CALL_UI_KEYWORDS = listOf(
            "通話中",
            "撥號中",
            "來電",
            "響鈴",
            "calling",
            "ringing",
            "in call",
            "incoming",
            "outgoing",
            "掛斷",
            "接聽",
            "拒絕",
            "靜音",
            "擴音"
        )
        
        // 服務實例（用於外部檢查服務狀態）
        var instance: LineCallDetectorService? = null
            private set
        
        // 當前通話狀態
        var currentCallState: CallState = CallState.IDLE
            private set
        
        // 狀態變化監聽器
        var onCallStateChanged: ((CallState) -> Unit)? = null
    }
    
    private var lastDetectedState: CallState = CallState.IDLE
    private var callStartTime: Long = 0
    private var isInLineApp = false
    private var lastWindowClassName: String? = null
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service connected")
        
        instance = this
        
        // 配置服務
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
            packageNames = arrayOf(LINE_PACKAGE)
        }
        
        // 啟動錄音服務
        startRecordingService()
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val packageName = event.packageName?.toString() ?: return
        
        // 只處理 LINE 應用的事件
        if (packageName != LINE_PACKAGE) {
            if (isInLineApp) {
                isInLineApp = false
                // 離開 LINE 應用，檢查是否需要結束通話偵測
                checkCallEnded()
            }
            return
        }
        
        isInLineApp = true
        
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleWindowContentChanged(event)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotificationChanged(event)
            }
        }
    }
    
    /**
     * 處理視窗狀態變化
     */
    private fun handleWindowStateChanged(event: AccessibilityEvent) {
        val className = event.className?.toString() ?: return
        lastWindowClassName = className
        
        Log.d(TAG, "Window state changed: $className")
        
        // 檢查是否為通話相關的活動
        val isCallActivity = CALL_ACTIVITY_KEYWORDS.any { keyword ->
            className.contains(keyword, ignoreCase = true)
        }
        
        if (isCallActivity) {
            Log.i(TAG, "Detected LINE call activity: $className")
            detectCallState(event)
        } else {
            // 檢查視窗內容是否包含通話相關元素
            checkWindowForCallUI(event)
        }
    }
    
    /**
     * 處理視窗內容變化
     */
    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        // 只在可能的通話狀態下檢查內容變化
        if (currentCallState != CallState.IDLE || isLikelyCallWindow()) {
            checkWindowForCallUI(event)
        }
    }
    
    /**
     * 處理通知變化
     */
    private fun handleNotificationChanged(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: return
        
        Log.d(TAG, "Notification: $text")
        
        // 檢查通知是否包含通話相關關鍵字
        val isCallNotification = CALL_UI_KEYWORDS.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
        
        if (isCallNotification) {
            Log.i(TAG, "Detected LINE call notification: $text")
            
            when {
                text.contains("來電") || text.contains("incoming") -> {
                    updateCallState(CallState.RINGING)
                }
                text.contains("通話中") || text.contains("in call") -> {
                    updateCallState(CallState.ACTIVE)
                }
            }
        }
    }
    
    /**
     * 檢查視窗是否包含通話 UI 元素
     */
    private fun checkWindowForCallUI(event: AccessibilityEvent) {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            // 遞迴搜尋通話相關的 UI 元素
            val hasCallUI = searchForCallUI(rootNode)
            
            if (hasCallUI && currentCallState == CallState.IDLE) {
                Log.i(TAG, "Detected call UI elements")
                updateCallState(CallState.ACTIVE)
            } else if (!hasCallUI && currentCallState == CallState.ACTIVE) {
                // 可能通話結束，但需要延遲確認
                checkCallEnded()
            }
            
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking window for call UI", e)
        }
    }
    
    /**
     * 遞迴搜尋通話 UI 元素
     */
    private fun searchForCallUI(node: AccessibilityNodeInfo): Boolean {
        // 檢查節點文字
        val nodeText = node.text?.toString() ?: ""
        val nodeDesc = node.contentDescription?.toString() ?: ""
        val nodeId = node.viewIdResourceName ?: ""
        
        val combinedText = "$nodeText $nodeDesc $nodeId"
        
        // 檢查是否包含通話關鍵字
        val hasCallKeyword = CALL_UI_KEYWORDS.any { keyword ->
            combinedText.contains(keyword, ignoreCase = true)
        }
        
        if (hasCallKeyword) {
            Log.d(TAG, "Found call UI element: $combinedText")
            return true
        }
        
        // 遞迴檢查子節點
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (searchForCallUI(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        
        return false
    }
    
    /**
     * 偵測通話狀態
     */
    private fun detectCallState(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: ""
        val className = event.className?.toString() ?: ""
        
        when {
            text.contains("來電") || text.contains("incoming") || 
            text.contains("響鈴") || text.contains("ringing") -> {
                updateCallState(CallState.RINGING)
            }
            text.contains("通話中") || text.contains("calling") ||
            className.contains("InCall", ignoreCase = true) -> {
                updateCallState(CallState.ACTIVE)
            }
            text.contains("結束") || text.contains("ended") -> {
                updateCallState(CallState.ENDED)
            }
            else -> {
                // 預設為通話中
                if (currentCallState == CallState.IDLE) {
                    updateCallState(CallState.ACTIVE)
                }
            }
        }
    }
    
    /**
     * 檢查是否可能是通話視窗
     */
    private fun isLikelyCallWindow(): Boolean {
        val className = lastWindowClassName ?: return false
        return CALL_ACTIVITY_KEYWORDS.any { keyword ->
            className.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 檢查通話是否結束
     */
    private fun checkCallEnded() {
        if (currentCallState == CallState.ACTIVE || currentCallState == CallState.RINGING) {
            // 延遲一小段時間確認通話確實結束
            android.os.Handler(mainLooper).postDelayed({
                if (!isLikelyCallWindow() && !isInLineApp) {
                    updateCallState(CallState.ENDED)
                }
            }, 1000)
        }
    }
    
    /**
     * 更新通話狀態
     */
    private fun updateCallState(newState: CallState) {
        if (currentCallState == newState) return
        
        val previousState = currentCallState
        currentCallState = newState
        
        Log.i(TAG, "Call state changed: $previousState -> $newState")
        
        when (newState) {
            CallState.RINGING -> {
                // 來電響鈴，準備錄音
                Log.i(TAG, "LINE call ringing")
            }
            CallState.ACTIVE -> {
                // 通話開始，啟動錄音
                callStartTime = System.currentTimeMillis()
                Log.i(TAG, "LINE call started")
                notifyRecordingService(true)
            }
            CallState.ENDED -> {
                // 通話結束，停止錄音
                val duration = System.currentTimeMillis() - callStartTime
                Log.i(TAG, "LINE call ended, duration: ${duration}ms")
                notifyRecordingService(false)
                
                // 重置狀態
                currentCallState = CallState.IDLE
            }
            CallState.IDLE -> {
                // 待機狀態
            }
        }
        
        onCallStateChanged?.invoke(newState)
    }
    
    /**
     * 通知錄音服務
     */
    private fun notifyRecordingService(startRecording: Boolean) {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = if (startRecording) {
                RecordingService.ACTION_START_RECORDING
            } else {
                RecordingService.ACTION_STOP_RECORDING
            }
        }
        startService(intent)
    }
    
    /**
     * 啟動錄音服務
     */
    private fun startRecordingService() {
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_SERVICE
        }
        startForegroundService(intent)
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility Service destroyed")
        instance = null
        currentCallState = CallState.IDLE
    }
    
    /**
     * 檢查服務是否已啟用
     */
    fun isServiceEnabled(): Boolean {
        return instance != null
    }
}
