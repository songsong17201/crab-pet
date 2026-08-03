package com.songsong.crabpet
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.songsong.crabpet.service.OverlayService
class MainActivity : AppCompatActivity() {
    companion object {
        private const val OVERLAY_PERMISSION_REQUEST = 1001
        private const val MEDIA_PERMISSION_REQUEST = 1002
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#FFF0E6")
        window.navigationBarColor = Color.parseColor("#FFF0E6")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FFF0E6"))
            setPadding(60, 120, 60, 60)
        }
        val title = TextView(this).apply {
            text = "\uD83E\uDD80 Claude\u684C\u5BA0"
            textSize = 28f
            setTextColor(Color.parseColor("#E07A5F"))
            gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        layout.addView(title, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 })
        val subtitle = TextView(this).apply {
            text = "\u4F60\u7684\u5C4F\u5E55\u4E0A\u5C06\u51FA\u73B0\u4E00\u53EA\u5C0F\u87C3\u87F9\n\u5B83\u4F1A\u8DDF\u7740\u4F60\u3001\u770B\u7740\u4F60\u3001\u5403\u9192\u4F60\u7684"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = Gravity.CENTER
            setLineSpacing(8f, 1f)
        }
        layout.addView(subtitle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 80 })
        val statusText = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setLineSpacing(6f, 1f)
        }
        val overlayOk = Settings.canDrawOverlays(this)
        val usageOk = hasUsageAccess()
        val sb = StringBuilder()
        sb.append(if (overlayOk) "\u2705" else "\u274C")
        sb.append(" \u60AC\u6D6E\u7A97\u6743\u9650\n")
        sb.append(if (usageOk) "\u2705" else "\u274C")
        sb.append(" \u4F7F\u7528\u60C5\u51B5\u8BBF\u95EE\u6743\u9650")
        statusText.text = sb.toString()
        statusText.setTextColor(Color.parseColor("#555555"))
        layout.addView(statusText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 60 })
        val btn = Button(this).apply {
            text = if (overlayOk) "\u53EC\u5524\u5C0F\u87C3\u87F9" else "\u6388\u6743\u5E76\u53EC\u5524"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E07A5F"))
            setPadding(40, 24, 40, 24)
            isAllCaps = false
            setOnClickListener { checkAndStartOverlay() }
        }
        layout.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 30 })
        if (!usageOk) {
            val usageBtn = Button(this).apply {
                text = "\u5F00\u542F\u4F7F\u7528\u60C5\u51B5\u8BBF\u95EE"
                textSize = 14f
                setTextColor(Color.parseColor("#E07A5F"))
                setBackgroundColor(Color.TRANSPARENT)
                isAllCaps = false
                setOnClickListener {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            layout.addView(usageBtn, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 })
        }
        val footer = TextView(this).apply {
            text = "\u62D6\u52A8\u5C0F\u87C3\u87F9\u5230\u5C4F\u5E55\u8FB9\u7F18\u5B83\u4F1A\u8D34\u7740\u8FB9\u8D34\u597D\u54E6"
            textSize = 12f
            setTextColor(Color.parseColor("#999999"))
            gravity = Gravity.CENTER
        }
        layout.addView(footer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 40 })
        setContentView(layout)
    }
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    private fun requestMediaPermissionIfNeeded() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(perm), MEDIA_PERMISSION_REQUEST)
        }
    }
    private fun checkAndStartOverlay() {
        requestMediaPermissionIfNeeded()
        if (Settings.canDrawOverlays(this)) {
            startOverlayService()
            Toast.makeText(this, "\u5C0F\u87C3\u87F9\u5DF2\u53EC\u5524\uFF01", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
                Toast.makeText(this, "\u5C0F\u87C3\u87F9\u5DF2\u53EC\u5524\uFF01", Toast.LENGTH_SHORT).show()
                finish()
            } else {
                Toast.makeText(this, "\u9700\u8981\u60AC\u6D6E\u7A97\u6743\u9650\u624D\u80FD\u663E\u793A\u5C0F\u87C3\u87F9\u54E6", Toast.LENGTH_LONG).show()
            }
        }
    }
    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
