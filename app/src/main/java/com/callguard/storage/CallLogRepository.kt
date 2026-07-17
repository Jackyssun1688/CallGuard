package com.callguard.storage

import android.content.Context

/**
 * 通话记录仓库 — 封装 Room 数据库操作，供其他模块调用。
 */
class CallLogRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).callLogDao()

    /** 插入一条新记录，返回 id */
    suspend fun insert(log: CallLogEntity): Long = dao.insert(log)

    /** 更新记录 */
    suspend fun update(log: CallLogEntity) = dao.update(log)

    /** 获取所有记录 (Flow，可观察变化) */
    val allLogs = dao.getAll()

    /** 安全通话记录 */
    val safeCalls = dao.getSafeCalls()

    /** 骚扰记录 */
    val spamCalls = dao.getSpamCalls()

    /** 按号码查询最近一条 */
    suspend fun getLatestByNumber(phone: String): CallLogEntity? = dao.getLatestByNumber(phone)

    /** 按 id 查询 */
    suspend fun getById(id: Long): CallLogEntity? = dao.getById(id)

    /** 删除所有 */
    suspend fun deleteAll() = dao.deleteAll()

    /** 骚扰总数 (Flow) */
    val spamCount = dao.spamCount()

    /** 安全总数 (Flow) */
    val safeCount = dao.safeCount()
}
