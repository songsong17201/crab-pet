package com.ever.crabpet

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btn_start)?.setOnClickListener {
            if (checkOverlayPermission()) {
                startOverlayService()
            }
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val hasPermission = Settings.canDrawOverlays(this)
        findViewById<TextView>(R.id.tv_status)?.text = if (hasPermission) {
            "悬浮窗权限：已授予 ✓\n点击下方按钮启动桌宠"
        } else {
            "悬浮窗权限：未授予\n点击下方按钮会跳转设置页面"
        }
    }

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
            return false
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY) {
            // 延迟500ms检查，华为系统需要时间生效
            Handler(Looper.getMainLooper()).postDelayed({
                if (Settings.canDrawOverlays(this)) {
                    startOverlayService()
                } else {
                    Toast.makeText(this, "需要悬浮窗权限才能显示桌宠", Toast.LENGTH_SHORT).show()
                }
                updateStatus()
            }, 500)
        }
    }

    private fun startOverlayService() {
        try {
            val intent = Intent(this, com.ever.crabpet.overlay.OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "小螃蟹已启动！", Toast.LENGTH_SHORT).show()
            // 延迟关闭，确保service起来了
            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1000)
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val REQUEST_OVERLAY = 1001
    }
}