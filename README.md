# LINE 通話錄音 App

一款專為 Android 設計的 LINE 通話自動錄音應用程式，支援有線耳機和藍牙耳機錄音。

## 功能特色

- **自動錄音**：偵測到 LINE 通話時自動開始錄音，通話結束時自動停止
- **耳機支援**：支援有線耳機、USB 耳機和藍牙耳機錄音
- **多種格式**：支援 M4A (AAC)、3GP (AMR)、WAV (PCM) 格式
- **錄音管理**：內建錄音列表，可播放、分享、刪除錄音檔案
- **開機自啟**：可設定開機後自動啟動服務
- **低功耗**：使用前台服務確保穩定運行，同時最小化電池消耗

## 系統需求

- Android 8.0 (API 26) 或更高版本
- 需要啟用無障礙服務權限
- 需要錄音權限
- 需要通知權限 (Android 13+)

## 安裝方式

### 方法一：使用 Android Studio 編譯

1. 使用 Android Studio 開啟專案資料夾
2. 等待 Gradle 同步完成
3. 點擊 Run 按鈕或使用 `./gradlew assembleDebug` 編譯
4. 將生成的 APK 安裝到手機

### 方法二：直接安裝 APK

1. 下載 `app-debug.apk` 檔案
2. 在手機上開啟檔案進行安裝
3. 如果提示「未知來源」，請在設定中允許安裝

## 使用說明

### 初次設定

1. 開啟應用程式
2. 授予錄音權限和通知權限
3. 點擊「啟用服務」按鈕
4. 在無障礙設定中找到「LINE 通話錄音」並啟用
5. 返回應用程式，確認服務狀態顯示「已啟用」

### 錄音流程

1. 當有 LINE 通話時，應用程式會自動偵測並開始錄音
2. 通話期間，通知欄會顯示錄音狀態
3. 通話結束後，錄音會自動停止並儲存
4. 可在「查看錄音」中找到所有錄音檔案

### 設定選項

- **音訊來源**：建議使用「語音通訊」以獲得最佳耳機錄音效果
- **音訊格式**：M4A 格式檔案較小，WAV 格式品質最佳
- **取樣率**：44.1 kHz 為 CD 品質，適合大多數情況
- **開機自動啟動**：啟用後開機會自動啟動服務

## 技術架構

### 核心元件

```
com.linerecorder.app/
├── service/
│   ├── LineCallDetectorService.kt  # 無障礙服務，偵測 LINE 通話
│   └── RecordingService.kt         # 前台服務，執行錄音任務
├── receiver/
│   └── BootReceiver.kt             # 開機接收器
├── ui/
│   ├── MainActivity.kt             # 主頁面
│   ├── RecordingsActivity.kt       # 錄音列表
│   └── SettingsActivity.kt         # 設定頁面
├── util/
│   ├── AudioRecorderHelper.kt      # 音訊錄製工具
│   ├── PreferenceManager.kt        # 偏好設定管理
│   └── RecordingManager.kt         # 錄音檔案管理
└── model/
    └── Recording.kt                # 資料模型
```

### 工作原理

1. **LINE 通話偵測**：使用 Android Accessibility Service 監控 LINE 應用的視窗狀態變化，偵測通話開始和結束事件。

2. **音訊錄製**：使用 `MediaRecorder` 或 `AudioRecord` API 錄製音訊。預設使用 `VOICE_COMMUNICATION` 音源，這是唯一能在使用耳機時同時錄製麥克風音訊的音源。

3. **耳機支援**：
   - 設定音訊模式為 `MODE_IN_COMMUNICATION`
   - 對於藍牙耳機，啟用 Bluetooth SCO
   - 使用 `VOICE_COMMUNICATION` 音源確保耳機麥克風可用

4. **背景運行**：使用前台服務 (Foreground Service) 確保錄音任務不會被系統終止。

## 注意事項

### 法律聲明

- 在某些地區，未經對方同意錄製通話可能違法
- 請確保您了解並遵守當地法律法規
- 本應用程式僅供合法用途使用

### 技術限制

- 由於 Android 系統限制，無法直接錄製 LINE 應用的音訊輸出（對方聲音）
- 錄音主要依賴麥克風，因此：
  - 使用揚聲器時可錄製雙方聲音
  - 使用耳機時主要錄製自己的聲音
- 部分手機廠商可能有額外的錄音限制

### 已知問題

- 某些手機可能需要額外的權限設定
- 藍牙耳機的錄音品質可能因設備而異
- LINE 應用更新後可能需要調整偵測邏輯

## 開發資訊

- **最低 SDK**：Android 8.0 (API 26)
- **目標 SDK**：Android 14 (API 34)
- **開發語言**：Kotlin
- **UI 框架**：Material Design 3

## 授權條款

本專案僅供學習和個人使用。請勿用於任何非法目的。

## 更新日誌

### v1.0.0
- 初始版本
- 支援 LINE 通話自動錄音
- 支援有線耳機和藍牙耳機
- 內建錄音管理功能
