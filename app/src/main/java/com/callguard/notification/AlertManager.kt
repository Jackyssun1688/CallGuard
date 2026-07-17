package com.callguard.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.callguard.MainActivity
import com.callguard.R

/**
 * 通知+短信管理 — 分析完成后通知用户 + 可选SMS备份。
 */
class AlertManager(private val context: Context) {

    companion object {
        private const val TAG = "CallGuard.Alert"
        private const val CHANNEL_SAFE = "callguard_safe"
        private const val CHANNEL_SPAM = "callguard_spam"
        private const val CHANNEL_TRANSCRIPT = "callguard_transcript"

        // SMS 发送的配置 key
        const val PREF_SEND_SMS = "pref_send_sms"
        const val PREF_PHONE_NUMBER = "pref_phone_number"
    }

    init {
        createNotificationChannels()
    }

    // ========== 通知通道 ==========

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_SAFE, "安全通话",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "主动提醒您接听安全来电"
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_SPAM, "骚扰拦截",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "已拦截的骚扰电话通知"
                    setShowBadge(false)
                },
                NotificationChannel(
                    CHANNEL_TRANSCRIPT, "通话记录",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "通话内容文字稿"
                }
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            channels.forEach { manager.createNotificationChannel(it) }
        }
    }

    // ========== 主动提醒：安全来电，请用户接听 ==========

    /**
     * 安全通话提醒：通知用户接听（或回拨）。
     */
    fun notifySafeCall(phoneNumber: String, transcript: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val transcriptPreview = if (transcript.length > 120) {
            transcript.take(120) + "…"
        } else {
            transcript
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_SAFE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📞 安全来电 — 请接听")
            .setContentText("来自 $phoneNumber")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("检测为安全通话\n来电号码: $phoneNumber\n识别内容: $transcriptPreview"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)

        Log.d(TAG, "Safe call notification sent for $phoneNumber")
    }

    // ========== 骚扰拦截通知 ==========

    /**
     * 骚扰拦截通知。
     */
    fun notifySpamBlocked(phoneNumber: String, category: String, transcript: String) {
        val categoryLabel = labelForCategory(category)
        val notification = NotificationCompat.Builder(context, CHANNEL_SPAM)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("🚫 已拦截 $categoryLabel")
            .setContentText("来电: $phoneNumber")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("已经自动挂断 $categoryLabel\n号码: $phoneNumber\n识别内容: $transcript"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ========== 短信发送 ==========

    /**
     * 发送通话摘要短信到本机。
     * 需要 SEND_SMS 权限。
     */
    fun sendTranscriptSms(phoneNumber: String, transcript: String, isSpam: Boolean, category: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "SMS permission not granted, skipping SMS")
            return
        }

        val categoryLabel = if (isSpam) "【${labelForCategory(category)}】" else "【安全通话✅】"
        val message = buildString {
            appendLine("📞 来电卫士 · 未能接听的通话记录")
            appendLine("━━━━━━━━━━━━━━━━")
            appendLine("号码: $phoneNumber")
            appendLine("时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date())}")
            appendLine("判断: $categoryLabel")
            appendLine("━━━━━━━━━━━━━━━━")
            appendLine("对方讲话内容:")
            appendLine(transcript.ifEmpty { "（无录音内容）"))
        }

        try {
            val smsManager = context.getSystemService(Context.SMS_SERVICE) as SmsManager
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.d(TAG, "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS", e)
        }
    }

    // ========== 通话记录通知 ==========

    /**
     * 通话结束后发送文字稿通知。
     */
    fun notifyTranscript(phoneNumber: String, transcript: String, isSpam: Boolean, category: String) {
        val title = if (isSpam) {
            "🚫 已拦截 ${labelForCategory(category)}"
        } else {
            "📞 安全通话 — 文字稿"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TRANSCRIPT)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText("来自 $phoneNumber")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("号码: $phoneNumber\n\n转文字:\n$transcript"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    // ========== 工具 ==========

    private fun labelForCategory(category: String): String = when (category) {
        "loan" -> "贷款推销"
        "scam" -> "诈骗电话 ⚠️"
        "promo" -> "广告推销"
        "auto_voice" -> "自动语音"
        "other" -> "骚扰电话"
        else -> "骚扰电话"
    }
}
