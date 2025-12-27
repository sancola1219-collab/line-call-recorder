# LINE 通話錄音 App 研究筆記

## AudioPlaybackCapture API (Android 10+)

### 關鍵要點
1. **API 用途**: 允許應用程式複製其他應用程式正在播放的音訊
2. **主要使用場景**: 串流應用程式捕獲遊戲音訊

### 實作要求
要捕獲音訊，應用程式必須滿足以下要求：
1. 必須擁有 `RECORD_AUDIO` 權限
2. 必須調用 `MediaProjectionManager.createScreenCaptureIntent()` 並獲得用戶批准
3. 捕獲和播放的應用程式必須在同一用戶配置文件中

### 實作步驟
1. 調用 `AudioPlaybackCaptureConfiguration.Builder.build()` 建立配置
2. 通過 `setAudioPlaybackCaptureConfig` 將配置傳遞給 `AudioRecord`

### 限制條件
被捕獲的應用程式必須：
1. 設置 usage 為 `USAGE_MEDIA`、`USAGE_GAME` 或 `USAGE_UNKNOWN`
2. 設置 capture policy 為 `AudioAttributes.ALLOW_CAPTURE_BY_ALL`

### LINE 的問題
- LINE 可能設置了 `allowAudioPlaybackCapture="false"` 或使用限制性的 capture policy
- 這意味著 AudioPlaybackCapture API 可能無法直接捕獲 LINE 音訊

## 替代方案

### 1. Accessibility Service 方案
- Android 10+ 背景錄音的唯一解決方案
- 可以在其他應用使用麥克風時同時錄音
- 需要用戶在設定中啟用無障礙服務

### 2. 麥克風錄音方案
- 使用 MediaRecorder 或 AudioRecord 錄製麥克風音訊
- 可以錄製揚聲器播放的聲音（但品質較差）
- 使用耳機時可能需要特殊處理

### 3. 現有解決方案參考
- **Cube ACR**: 支援 LINE、WhatsApp 等 VoIP 通話錄音
- **BoldBeast**: 支援多種 VoIP 應用錄音
- 這些應用使用 Accessibility Service 來實現功能

## 技術方案選擇

### 推薦方案：Accessibility Service + 麥克風錄音
1. 使用 Accessibility Service 偵測 LINE 通話狀態
2. 使用 AudioRecord 錄製麥克風音訊（可錄製自己的聲音）
3. 使用 AudioPlaybackCapture 嘗試錄製對方聲音（如果 LINE 允許）
4. 如果無法捕獲對方聲音，則依賴揚聲器外放或耳機麥克風

### 耳機錄音支援
- 使用 `VOICE_COMMUNICATION` 音源可以在使用耳機時錄音
- 需要處理音訊路由變化

## Android 音訊輸入共享規則 (關鍵發現)

### Voice Call + 普通 App 場景
根據官方文檔，當語音通話進行時：
1. **通話永遠接收音訊**
2. **Accessibility Service 可以捕獲音訊** ← 這是關鍵！
3. 擁有 `CAPTURE_AUDIO_OUTPUT` 權限的預裝應用可以捕獲通話

### Accessibility Service 的特權
- AccessibilityService 是特權應用
- 當 Service 的 UI 在最上層時，Service 和其他 App 都能接收音訊
- 這提供了用語音命令控制語音通話或視頻捕獲的功能

### 最終技術方案

**使用 Accessibility Service 實現 LINE 通話錄音：**

1. **偵測 LINE 通話**：監聽 LINE 應用的通知或視窗事件
2. **自動開始錄音**：當偵測到 LINE 通話時自動啟動錄音
3. **使用 MediaRecorder/AudioRecord**：錄製麥克風音訊
4. **耳機支援**：使用 `VOICE_COMMUNICATION` 音源，可在耳機模式下錄音
5. **Foreground Service**：確保背景錄音不被系統終止
