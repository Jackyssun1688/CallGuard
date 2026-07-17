package com.callguard

import android.app.Application
import android.util.Log
import com.callguard.storage.AppDatabase
import com.callguard.storage.CallLogRepository

/**
 * Application 类 — 全局初始化。
 */
class App : Application() {

    lateinit var repository: CallLogRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        repository = CallLogRepository(this)
        Log.d(TAG, "CallGuard initialized")
    }

    companion object {
        private const val TAG = "CallGuard.App"

        @Volatile
        private var instance: App? = null

        fun get(): App = instance ?: throw IllegalStateException("App not initialized")
    }
}
