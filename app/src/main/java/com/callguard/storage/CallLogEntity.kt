package com.callguard.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 通话记录实体，每条记录存储一个被筛查的来电信息。
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 来电号码 */
    val phoneNumber: String,

    /** 呼叫时间戳 (毫秒) */
    val timestamp: Long = System.currentTimeMillis(),

    /** 通话时长 (秒) */
    val durationSeconds: Int = 0,

    /** 转文字稿 — 识别到的对方讲话内容 */
    val transcript: String = "",

    /** 是否被标记为骚扰/垃圾 */
    val isSpam: Boolean = false,

    /** 骚扰类别: "loan" | "scam" | "promo" | "auto_voice" | "other" | "" */
    val spamCategory: String = "",

    /** 置信度 0.0 ~ 1.0 */
    val confidence: Float = 0f,

    /** 处理状态: "screening" | "completed" | "missed" | "error" */
    val status: String = "completed"
)
