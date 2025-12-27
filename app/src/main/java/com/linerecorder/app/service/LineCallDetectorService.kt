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
            "Video",
            "VoiceCall",
            "VideoCall",
            "CallActivity",
            "VoipActivity"
        )
        
        // LINE 通話相關的 UI 元素關鍵字（中文、英文、日文）
        private val CALL_UI_KEYWORDS = listOf(
            // 中文
            "通話中",
            "撥號中",
            "來電",
            "響鈴",
            "掛斷",
            "接聽",
            "拒絕",
            "靜音",
            "擴音",
            "開始視訊",
            "關閉麥克風",
            "音效設定",
            // 英文
            "calling",
            "ringing",
            "in call",
            "incoming",
            "outgoing",
            "hang up",
            "answer",
            "decline",
            "mute",
            "speaker",
            // 日文
            "通話",
            "発信",
            "着信"
        )
        
        // 通話時間格式的正則表達式 (例如: 00:27, 1:23:45)
        private val CALL_TIMER_PATTERN = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")
        
        // 服務實例（用於外部檢查服務狀態）
        var instance: LineCallDetectorService? = null
            private set
        
        // 當前通話狀態
        var currentCallState: CallState = CallState.IDLE
            private set
        
        // 狀態變化監聯器
        var onCallStateChanged: ((CallState) -> Unit)? = null
    }
    
    private var lastDetectedState: CallState = CallState.IDLE
    private var callStartTime: Long = 0
    private var isInLineApp = false
    private var lastWindowClassName: String? = null
    private var callCheckHandler: android.os.Handler? = null
    private var lastCallUIDetectedTime: Long = 0
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility Service connected")
        
        instance = this
        callCheckHandler = android.os.Handler(mainLooper)
        
        // 配置服務 - 監聽所有應用以便偵測 LINE
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
            // 不限制 packageNames，監聽所有應用
            packageNames = null
        }
        
        // 啟動錄音服務
        startRecordingService()
        
        Log.i(TAG, "Service configured and ready")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val packageName = event.packageName?.toString() ?: return
        
        // 記錄所有 LINE 相關事件
        if (packageName == LINE_PACKAGE) {
            Log.d(TAG, "LINE Event: type=${event.eventType}, class=${event.className}, text=${event.text}")
        }
        
        // 處理 LINE 應用的事件
        if (packageName == LINE_PACKAGE) {
            isInLineApp = true
            handleLineEvent(event)
        } else {
            if (isInLineApp) {
                isInLineApp = false
                // 離開 LINE 應用，延遲檢查是否需要結束通話
                scheduleCallEndCheck()
            }
        }
    }
    
    /**
     * 處理 LINE 應用事件
     */
    private fun handleLineEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowStateChanged(event)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleWindowContentChanged(event)
            }
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotificationChanged(event)
            }
        }
        
        // 定期掃描當前視窗
        scanCurrentWindow()
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
            updateCallState(CallState.ACTIVE)
            return
        }
        
        // 檢查事件文字
        val eventText = event.text?.joinToString(" ") ?: ""
        if (containsCallKeywords(eventText)) {
            Log.i(TAG, "Detected call keywords in event: $eventText")
            updateCallState(CallState.ACTIVE)
        }
    }
    
    /**
     * 處理視窗內容變化
     */
    private fun handleWindowContentChanged(event: AccessibilityEvent) {
        val eventText = event.text?.joinToString(" ") ?: ""
        
        // 檢查是否有通話計時器格式的文字
        if (CALL_TIMER_PATTERN.containsMatchIn(eventText)) {
            Log.i(TAG, "Detected call timer: $eventText")
            lastCallUIDetectedTime = System.currentTimeMillis()
            updateCallState(CallState.ACTIVE)
            return
        }
        
        // 檢查是否包含通話關鍵字
        if (containsCallKeywords(eventText)) {
            Log.i(TAG, "Detected call keywords: $eventText")
            lastCallUIDetectedTime = System.currentTimeMillis()
            updateCallState(CallState.ACTIVE)
        }
    }
    
    /**
     * 處理通知變化
     */
    private fun handleNotificationChanged(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: return
        
        Log.d(TAG, "Notification: $text")
        
        if (containsCallKeywords(text)) {
            Log.i(TAG, "Detected LINE call notification: $text")
            
            when {
                text.contains("來電") || text.contains("incoming") || text.contains("着信") -> {
                    updateCallState(CallState.RINGING)
                }
                text.contains("通話中") || text.contains("in call") || text.contains("通話") -> {
                    updateCallState(CallState.ACTIVE)
                }
            }
        }
    }
    
    /**
     * 掃描當前視窗尋找通話 UI
     */
    private fun scanCurrentWindow() {
        try {
            val rootNode = rootInActiveWindow ?: return
            
            val hasCallUI = searchForCallUI(rootNode, 0)
            
            if (hasCallUI) {
                lastCallUIDetectedTime = System.currentTimeMillis()
                if (currentCallState == CallState.IDLE) {
                    Log.i(TAG, "Detected call UI through window scan")
                    updateCallState(CallState.ACTIVE)
                }
            }
            
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning window", e)
        }
    }
    
    /**
     * 遞迴搜尋通話 UI 元素
     */
    private fun searchForCallUI(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > 15) return false // 限制搜尋深度
        
        try {
            // 檢查節點文字
            val nodeText = node.text?.toString() ?: ""
            val nodeDesc = node.contentDescription?.toString() ?: ""
            val nodeId = node.viewIdResourceName ?: ""
            
            // 檢查是否有通話計時器
            if (CALL_TIMER_PATTERN.matches(nodeText)) {
                Log.d(TAG, "Found call timer in node: $nodeText")
                return true
            }
            
            // 檢查是否包含通話關鍵字
            val combinedText = "$nodeText $nodeDesc"
            if (containsCallKeywords(combinedText)) {
                Log.d(TAG, "Found call UI element: $combinedText")
                return true
            }
            
            // 檢查 resource ID 是否包含通話相關
            if (nodeId.contains("call", ignoreCase = true) || 
                nodeId.contains("voip", ignoreCase = true) ||
                nodeId.contains("hangup", ignoreCase = true) ||
                nodeId.contains("mute", ignoreCase = true)) {
                Log.d(TAG, "Found call-related resource ID: $nodeId")
                return true
            }
            
            // 遞迴檢查子節點
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = searchForCallUI(child, depth + 1)
                child.recycle()
                if (found) return true
            }
        } catch (e: Exception) {
            // 忽略節點存取錯誤
        }
        
        return false
    }
    
    /**
     * 檢查文字是否包含通話關鍵字
     */
    private fun containsCallKeywords(text: String): Boolean {
        if (text.isBlank()) return false
        return CALL_UI_KEYWORDS.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
    }
    
    /**
     * 排程通話結束檢查
     */
    private fun scheduleCallEndCheck() {
        callCheckHandler?.removeCallbacksAndMessages(null)
        callCheckHandler?.postDelayed({
            checkCallEnded()
        }, 2000)
    }
    
    /**
     * 檢查通話是否結束
     */
    private fun checkCallEnded() {
        if (currentCallState == CallState.ACTIVE || currentCallState == CallState.RINGING) {
            val timeSinceLastUI = System.currentTimeMillis() - lastCallUIDetectedTime
            
            // 如果超過 3 秒沒有偵測到通話 UI，認為通話結束
            if (timeSinceLastUI > 3000 && !isInLineApp) {
                Log.i(TAG, "Call appears to have ended (no UI detected for ${timeSinceLastUI}ms)")
                updateCallState(CallState.ENDED)
            }
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
                Log.i(TAG, "LINE call ringing")
            }
            CallState.ACTIVE -> {
                callStartTime = System.currentTimeMillis()
                Log.i(TAG, "LINE call started - triggering recording")
                notifyRecordingService(true)
            }
            CallState.ENDED -> {
                val duration = System.currentTimeMillis() - callStartTime
                Log.i(TAG, "LINE call ended, duration: ${duration}ms")
                notifyRecordingService(false)
                
                // 重置狀態
                android.os.Handler(mainLooper).postDelayed({
                    if (currentCallState == CallState.ENDED) {
                        currentCallState = CallState.IDLE
                        onCallStateChanged?.invoke(CallState.IDLE)
                    }
                }, 1000)
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
        try {
            val intent = Intent(this, RecordingService::class.java).apply {
                action = if (startRecording) {
                    RecordingService.ACTION_START_RECORDING
                } else {
                    RecordingService.ACTION_STOP_RECORDING
                }
            }
            startService(intent)
            Log.i(TAG, "Sent ${if (startRecording) "START" else "STOP"} recording intent")
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying recording service", e)
        }
    }
    
    /**
     * 啟動錄音服務
     */
    private fun startRecordingService() {
        try {
            val intent = Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_START_SERVICE
            }
            startForegroundService(intent)
            Log.i(TAG, "Started recording service")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording service", e)
        }
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility Service destroyed")
        callCheckHandler?.removeCallbacksAndMessages(null)
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
