package com.callguard.call

import android.content.Context
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

/**
 * CallHandler — 来电处理的核心逻辑。
 *
 * 在 CallScreeningService 拦截到陌生来电后，
 * 由它编排整个流程：触发无障碍服务接听、等待分析完成、
 * 最终挂断或通知用户。
 */
class CallHandler(private val context: Context) {

    companion object {
        private const val TAG = "CallGuard.Handler"
    }

    /**
     * 处理一个陌生来电的完整流程。
     */
    suspend fun processCall(phoneNumber: String, callDetails: Call.Details) {
        Log.d(TAG, "Processing call from $phoneNumber")

        // 1. 记录当前处理的号码
        CallAccessibilityService.currentPhoneNumber = phoneNumber

        // 2. 标记无障碍服务应当自动接听
        CallAccessibilityService.shouldAutoAnswer = true
        CallAccessibilityService.isProcessingCall = true
        CallAccessibilityService.hasAnswered = false

        // 3. 等待无障碍服务自动接听并完成分析
        // （由 CallAccessibilityService 完成录音 → 识别 → 分类 → 决策）
        // 不需要在这里等待，CallAccessibilityService 会独立完成整个过程

        Log.d(TAG, "CallHandler handed off to AccessibilityService")
    }
}
