package com.callguard.call

import android.content.SharedPreferences
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.preference.PreferenceManager
import com.callguard.App
import com.callguard.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Call Screening Service — Android 10+ 原生来电筛选服务。
 *
 * 工作流程：
 * 1. 系统收到陌生来电时，先调用本服务的 onScreenCall()
 * 2. 判断是否联系人 → 是则放行
 * 3. 非联系人 → 静音，标记为待处理，触发 CallHandler 自动接听
 * 4. 20秒音频分析完成后，根据分类结果决定挂断或通知用户
 */
class CallScreeningService : CallScreeningService() {

    companion object {
        private const val TAG = "CallGuard.Screening"
        private const val PREF_ENABLED = "screening_enabled"

        /** App 启用状态 */
        var isEnabled: Boolean = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        isEnabled = prefs.getBoolean(PREF_ENABLED, true)
        Log.d(TAG, "CallScreeningService created, enabled=$isEnabled")
    }

    override fun onScreenCall(callDetails: Call.Details) {
        if (!isEnabled) {
            Log.d(TAG, "Screening disabled, allowing call through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "未知号码"
        Log.d(TAG, "Incoming call from: $phoneNumber")

        // 检查是否已知联系人 — 联系人直接放行
        if (isContact(phoneNumber)) {
            Log.d(TAG, "$phoneNumber is a contact, allowing through")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // 检查是否为已知骚扰号码（之前标记过的）
        if (isKnownSpam(phoneNumber)) {
            Log.d(TAG, "$phoneNumber is known spam, rejecting")
            respondToCall(callDetails, CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .build())
            return
        }

        // 未知号码 — 静音并交给 CallHandler 处理后续逻辑
        Log.d(TAG, "$phoneNumber is unknown, silencing and handing off to CallHandler")

        // 告诉系统：不让响铃，不拒接（我们会自己处理）
        respondToCall(callDetails, CallResponse.Builder()
            .setDisallowCall(true)      // 不让系统响铃
            .setSilenceCall(true)       // 静音
            .setRejectCall(false)       // 不拒接 — 我们后续会自动接听
            .setSkipCallLog(false)      // 保留通话记录
            .setSkipNotification(false) // 保留通知（我们会覆盖）
            .build())

        // 启动 CallHandler 处理自动接听+分析流程
        scope.launch {
            CallHandler(this@CallScreeningService)
                .processCall(phoneNumber, callDetails)
        }
    }

    /**
     * 检查号码是否在通讯录中。
     */
    private fun isContact(phoneNumber: String): Boolean {
        // 使用 ContactsContract 查询（简化处理）
        try {
            val uri = android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI
                .buildUpon()
                .appendPath(phoneNumber)
                .build()
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                return it.count > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check contacts", e)
        }
        return false
    }

    /**
     * 检查是否为已知骚扰号码（从数据库查询）。
     */
    private fun isKnownSpam(phoneNumber: String): Boolean {
        // 简单实现：之前标记为 spam 的号码
        // 完整实现在 CallHandler 中
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "CallScreeningService destroyed")
    }
}
