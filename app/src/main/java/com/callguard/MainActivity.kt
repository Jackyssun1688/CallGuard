package com.callguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.callguard.call.CallAccessibilityService
import com.callguard.call.CallScreeningService
import com.callguard.databinding.ActivityMainBinding
import com.callguard.notification.AlertManager
import com.callguard.storage.CallLogEntity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var callLogAdapter: CallLogAdapter
    private var isEnabled: Boolean = false

    // 权限请求
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show()
            checkPermissions()
        } else {
            Toast.makeText(this, "部分权限被拒绝，功能可能受限", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        checkPermissions()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "来电卫士 CallGuard"

        // 通话记录列表
        callLogAdapter = CallLogAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = callLogAdapter

        // 开关按钮
        binding.btnToggle.setOnClickListener {
            if (isEnabled) {
                disableScreening()
            } else {
                enableScreening()
            }
        }

        // 权限按钮
        binding.btnPermissions.setOnClickListener {
            requestPermissions()
        }

        // 无障碍设置
        binding.btnAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        // 清空记录
        binding.btnClearLogs.setOnClickListener {
            lifecycleScope.launch {
                App.get().repository.deleteAll()
                Toast.makeText(this@MainActivity, "已清空通话记录", Toast.LENGTH_SHORT).show()
            }
        }

        // 短信开关
        binding.switchSms.setOnCheckedChangeListener { _, isChecked ->
            PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(AlertManager.PREF_SEND_SMS, isChecked)
                .apply()
        }

        // 帮助
        binding.btnHelp.setOnClickListener {
            showHelpDialog()
        }
    }

    private fun observeData() {
        // 监听数据库变化
        App.get().repository.allLogs.observe(this) { logs ->
            callLogAdapter.submitList(logs)
            binding.textEmpty.visibility = if (logs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private fun updateUI() {
        isEnabled = CallScreeningService.isEnabled
        binding.btnToggle.text = if (isEnabled) "关闭防护" else "开启防护"
        binding.btnToggle.setBackgroundColor(
            ContextCompat.getColor(this,
                if (isEnabled) android.R.color.holo_red_light
                else android.R.color.holo_green_light
            )
        )
        binding.textStatus.text = if (isEnabled) "🛡️ 防护已开启" else "⚠️ 防护已关闭"
    }

    private fun enableScreening() {
        if (!checkPermissions()) return

        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("screening_enabled", true)
            .apply()
        CallScreeningService.isEnabled = true
        updateUI()
        Toast.makeText(this, "来电卫士已启动", Toast.LENGTH_SHORT).show()
    }

    private fun disableScreening() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean("screening_enabled", false)
            .apply()
        CallScreeningService.isEnabled = false
        updateUI()
        Toast.makeText(this, "来电卫士已关闭", Toast.LENGTH_SHORT).show()
    }

    /**
     * 检查并请求所有必要权限。
     */
    private fun checkPermissions(): Boolean {
        val needed = mutableListOf<String>()

        // 电话权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ANSWER_PHONE_CALLS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        // 录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }

        // 通讯录
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_CONTACTS)
        }

        // 通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 短信权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.SEND_SMS)
        }

        // 更新权限状态显示
        updatePermissionStatus(needed.isEmpty())

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
            return false
        }

        return true
    }

    private fun requestPermissions() {
        checkPermissions()
        if (!isAccessibilityServiceEnabled()) {
            openAccessibilitySettings()
        }
    }

    private fun updatePermissionStatus(allGranted: Boolean) {
        binding.textPermissionStatus.text = if (allGranted) {
            "✅ 所有权限已授予"
        } else {
            "⚠️ 部分权限缺失"
        }
        binding.btnPermissions.text = if (allGranted) "✅ 权限" else "⚠️ 权限"
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/.call.CallAccessibilityService"
        try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.split(':').any { it.equals(service, ignoreCase = true) }
        } catch (e: Exception) {
            return false
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Toast.makeText(
            this,
            "请手动开启「来电卫士」的无障碍服务权限",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showHelpDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("如何使用来电卫士")
            .setMessage(
                """
                1. 授予所有权限（点击"权限"按钮）
                2. 在系统设置中开启「来电卫士」的无障碍服务
                   （已自动跳转，找到"来电卫士"并开启即可）
                3. 点击"开启防护"

                工作原理：
                • 陌生来电 → 自动静音
                • 通讯录来电 → 正常响铃
                • 自动接听 → 分析20秒
                • 贷款/推销/诈骗/自动语音 → 自动挂断
                • 安全通话 → 通知您接听
                • 通话文字稿会保存并通知您

                可选：
                • 开启短信转发：文字稿自动发短信到本机
                
                提示：
                • 需要 Android 10+
                • 录音仅在通话期间进行，保护隐私
                """.trimIndent()
            )
            .setPositiveButton("知道了", null)
            .show()
    }
}
