package com.callguard.call

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.preference.PreferenceManager
import com.callguard.App
import com.callguard.audio.CallAudioForegroundService
import com.callguard.audio.CallAudioRecorder
import com.callguard.audio.SpeechToTextEngine
import com.callguard.detection.SpamClassifier
import com.callguard.notification.AlertManager
import com.callguard.storage.CallLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
/**
 * 无障碍服务 — 自动接听 / 自动挂断。
 *
 * 检测到来电界面后：
 * 1. 自动点击"接听"按钮
 * 2. 打开扬声器
 * 3. 启动录音 + 语音识别（20秒）
 * 4. 分类检测
 * 5. 骚扰 → 自动挂断 | 非骚扰 → 通知用户
 */
class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallGuard.Accessibility"
        private const val MAX_LISTEN_SECONDS = 20

        /** 全局是否正在处理一个来电 */
        @Volatile
        var isProcessingCall = false

        /** 当前正在处理的号码 */
        @Volatile
        var currentPhoneNumber: String = ""

        /** 标记是否应该自动接听 */
        @Volatile
        var shouldAutoAnswer = false

        /** 标记是否应该自动挂断 */
        @Volatile
        var shouldAutoHangup = false

        /** 标记是否已接听 */
        @Volatile
        var hasAnswered = false
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var spamClassifier: SpamClassifier
    private lateinit var alertManager: AlertManager
    private lateinit var audioRecorder: CallAudioRecorder
    private lateinit var sttEngine: SpeechToTextEngine

    override fun onCreate() {
        super.onCreate()
        spamClassifier = SpamClassifier()
        alertManager = AlertManager(this)
        audioRecorder = CallAudioRecorder(this)
        sttEngine = SpeechToTextEngine(this)
        Log.d(TAG, "AccessibilityService created")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!CallScreeningService.isEnabled) return
        if (isProcessingCall) return

        // 检测来电界面（窗口变化 + 来电包名）
        val packageName = event.packageName?.toString() ?: return

        // Android 系统电话应用的包名
        val phonePackages = listOf(
            "com.android.dialer",           // Google Phone / AOSP
            "com.android.incallui",         // AOSP InCallUI
            "com.google.android.dialer",    // Google Dialer
            "com.android.phone",            // Android Phone
            "com.oneplus.dialer",           // OnePlus
            "com.xiaomi.incall",            // Xiaomi
            "com.android.server.telecom",   // Telecom
        )

        if (packageName !in phonePackages) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {

                // 查找接听按钮
                val rootNode = rootInActiveWindow ?: return
                try {
                    // 搜索"接听"按钮的常见描述
                    val answerButton = findAnswerButton(rootNode)
                    if (answerButton != null && shouldAutoAnswer && !hasAnswered) {
                        hasAnswered = true
                        Log.d(TAG, "Found answer button, auto-answering...")
                        answerButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        // 异步等待 + 处理音频
                        scope.launch {
                            delay(1000) // 等待接听完成
                            // 打开扬声器
                            enableSpeakerphone(rootNode)
                            // 开始音频处理
                            processAudio()
                        }
                    }
                } finally {
                    rootNode.recycle()
                }
            }
        }
    }

    /**
     * 在界面树中查找"接听"按钮。
     */
    private fun findAnswerButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 常见接听按钮的描述文本和 content-desc
        val answerTexts = listOf(
            "接听", "Answer", "接听电话", "应答",
            "下滑接听", "上滑接听"
        )
        val answerIds = listOf(
            "com.android.dialer:id/answer_button",
            "com.android.dialer:id/answer_and_cancel_button",
            "com.android.incallui:id/answerButton",
            "com.android.incallui:id/answerAndHoldButton",
            "com.google.android.dialer:id/answer_button",
            "com.android.dialer:id/answerAndHoldButton"
        )

        // 检查当前节点
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""

        if (contentDesc.lowercase() in answerTexts.map { it.lowercase() } ||
            text in answerTexts ||
            viewId in answerIds
        ) {
            if (node.isClickable) return node
        }

        // 递归查找子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findAnswerButton(child)
            if (result != null) return result
            child.recycle()
        }

        return null
    }

    /**
     * 打开扬声器模式（尝试点击"扬声器"按钮）。
     */
    private fun enableSpeakerphone(node: AccessibilityNodeInfo) {
        val speakerTexts = listOf("扬声器", "Speaker", "免提")
        val speakerIds = listOf(
            "com.android.dialer:id/speaker_button",
            "com.android.incallui:id/speakerButton",
            "com.google.android.dialer:id/speaker_button"
        )

        fun findSpeaker(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val desc = n.contentDescription?.toString() ?: ""
            val id = n.viewIdResourceName ?: ""
            if (desc.lowercase() in speakerTexts.map { it.lowercase() } || id in speakerIds) {
                if (n.isClickable) return n
            }
            for (i in 0 until n.childCount) {
                val child = n.getChild(i) ?: continue
                val result = findSpeaker(child)
                if (result != null) return result
                child.recycle()
            }
            return null
        }

        val speaker = findSpeaker(node)
        speaker?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            it.recycle()
            Log.d(TAG, "Speakerphone enabled")
        }
    }

    /**
     * 核心流程：录音 → 语音识别 → 分类 → 决策
     */
    private suspend fun processAudio() {
        Log.d(TAG, "Starting audio processing for $currentPhoneNumber")

        // 启动前台服务确保不被杀死
        CallAudioForegroundService.start(this)

        // 1. 录音（最多 20 秒）
        val audioFile = audioRecorder.captureFirst20Seconds()
        if (audioFile == null) {
            Log.e(TAG, "Audio capture failed")
            CallAudioForegroundService.stop(this)
            isProcessingCall = false
            // 即使没收到音频，也通知用户有来电
            alertManager.notifySafeCall(currentPhoneNumber, "（无法获取录音）")
            return
        }

        // 2. 语音转文字
        Log.d(TAG, "Transcribing audio...")
        val transcript = sttEngine.transcribeFile(audioFile)
        Log.d(TAG, "Transcript: '$transcript'")

        // 3. 删除临时音频文件
        audioFile.delete()

        // 4. 分类检测
        val result = spamClassifier.classify(transcript)
        Log.d(TAG, "Classification: spam=${result.isSpam}, category=${result.category}, confidence=${result.confidence}")

        // 5. 保存到数据库
        saveToDatabase(
            phoneNumber = currentPhoneNumber,
            transcript = transcript,
            isSpam = result.isSpam,
            category = result.category,
            confidence = result.confidence
        )

        // 6. 决策
        if (result.isSpam && result.confidence >= 0.5f) {
            // 骚扰 → 自动挂断
            Log.d(TAG, "Spam detected, hanging up...")
            alertManager.notifySpamBlocked(currentPhoneNumber, result.category, transcript)
            autoHangup()
        } else {
            // 安全通话 → 通知用户（也可能已经挂断了，但至少通知）
            Log.d(TAG, "Safe call detected, notifying user...")
            alertManager.notifySafeCall(currentPhoneNumber, transcript)
            // 振动提醒用户接听（如果通话还在进行中）
            vibratePhone()
        }

        // 发送文字稿通知
        alertManager.notifyTranscript(currentPhoneNumber, transcript, result.isSpam, result.category)

        // 可选：发送短信
        if (shouldSendSms()) {
            alertManager.sendTranscriptSms(currentPhoneNumber, transcript, result.isSpam, result.category)
        }

        // 清理
        CallAudioForegroundService.stop(this)
        isProcessingCall = false
        currentPhoneNumber = ""
        shouldAutoAnswer = false
        hasAnswered = false
    }

    /**
     * 自动挂断 — 在通话界面点击"挂断"按钮。
     */
    private suspend fun autoHangup() {
        delay(500) // 等界面稳定
        val rootNode = rootInActiveWindow ?: return

        try {
            val hangupTexts = listOf("挂断", "End", "结束通话", "结束", "Hang up", "End call")
            val hangupIds = listOf(
                "com.android.dialer:id/end_button",
                "com.android.dialer:id/endButton",
                "com.android.incallui:id/endCallButton",
                "com.android.incallui:id/endButton",
                "com.google.android.dialer:id/end_button",
                "com.android.dialer:id/floating_end_call"
            )

            fun findHangup(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                val desc = n.contentDescription?.toString() ?: ""
                val id = n.viewIdResourceName ?: ""
                if (desc in hangupTexts || id in hangupIds) {
                    if (n.isClickable) return n
                }
                for (i in 0 until n.childCount) {
                    val child = n.getChild(i) ?: continue
                    val result = findHangup(child)
                    if (result != null) return result
                    child.recycle()
                }
                return null
            }

            val hangup = findHangup(rootNode)
            if (hangup != null) {
                hangup.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                hangup.recycle()
                Log.d(TAG, "Call ended (spam)")
            } else {
                Log.w(TAG, "Hangup button not found")
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 振动提醒用户。
     */
    private fun vibratePhone() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 400, 200, 400, 200, 800),
                        -1
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 400, 200, 400, 200, 800), -1)
            }
        }
    }

    private suspend fun saveToDatabase(
        phoneNumber: String,
        transcript: String,
        isSpam: Boolean,
        category: String,
        confidence: Float
    ) {
        try {
            val entity = CallLogEntity(
                phoneNumber = phoneNumber,
                transcript = transcript,
                isSpam = isSpam,
                spamCategory = category,
                confidence = confidence,
                durationSeconds = MAX_LISTEN_SECONDS,
                status = if (isSpam) "completed" else "missed"
            )
            App.get().repository.insert(entity)
            Log.d(TAG, "Call log saved to database")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save call log", e)
        }
    }

    private fun shouldSendSms(): Boolean {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
        return prefs.getBoolean(AlertManager.PREF_SEND_SMS, false)
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrupted")
        isProcessingCall = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isProcessingCall = false
        hasAnswered = false
        Log.d(TAG, "AccessibilityService destroyed")
    }
}
