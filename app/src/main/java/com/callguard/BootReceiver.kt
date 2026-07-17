package com.callguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机广播接收器 — 系统重启后重新启用服务。
 * 不需要额外操作，系统会重新绑定 CallScreeningService。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CallGuard.Boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Device rebooted, services will be re-bound by system")
        }
    }
}
