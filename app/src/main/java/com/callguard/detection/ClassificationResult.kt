package com.callguard.detection

/**
 * 骚扰分类检测结果。
 */
data class ClassificationResult(
    /** 是否被判定为骚扰 */
    val isSpam: Boolean,
    /** 分类标签: "loan" | "scam" | "promo" | "auto_voice" | "other" | "" */
    val category: String,
    /** 置信度 0.0 ~ 1.0 */
    val confidence: Float,
    /** 触发匹配的关键词 */
    val matchedKeywords: List<String> = emptyList()
)
