package com.songsong.crabpet.service
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.songsong.crabpet.MainActivity
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sqrt
class OverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    companion object {
        private const val CHANNEL_ID = "crab_pet_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 96
        private const val PET_HEIGHT_DP = 102
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 600L
        private const val MOVE_THRESHOLD = 15
        private const val TAP_DELAY = 320L
        private const val WHISPER_INTERVAL = 3600_000L
        private const val APP_CHECK_INTERVAL = 3000L
        private const val EDGE_THRESHOLD_DP = 35
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getWhisper()))
        setupOverlay()
        startWhisperRotation()
        startAppDetection()
    }
    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }
        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                useWideViewPort = false
                mediaPlaybackRequiresUserGesture = false
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            webViewClient = WebViewClient()
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }
        windowManager?.addView(overlayView, params)
    }
    // ========== GESTURE HANDLING ==========
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTapTime = 0L
    private var touchStartTime = 0L
    private var hasMoved = false
    private var tapCount = 0
    private var lastTapCountTime = 0L
    private var pendingSingleTap = false
    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > MOVE_THRESHOLD || abs(dy) > MOVE_THRESHOLD) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        when {
                            elapsed > LONG_PRESS_TIMEOUT -> onLongPress()
                            System.currentTimeMillis() - lastTapTime < DOUBLE_TAP_TIMEOUT -> {
                                pendingSingleTap = false
                                handler.removeCallbacksAndMessages("tap")
                                onDoubleTap()
                            }
                            else -> {
                                lastTapTime = System.currentTimeMillis()
                                pendingSingleTap = true
                                handler.postDelayed({
                                    if (pendingSingleTap) {
                                        pendingSingleTap = false
                                        onTap()
                                    }
                                }, TAP_DELAY)
                            }
                        }
                        val now = System.currentTimeMillis()
                        if (now - lastTapCountTime < 2000) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapCountTime = now
                        if (tapCount == 3) onMultiTap(3)
                        if (tapCount == 5) onMultiTap(5)
                        if (tapCount == 8) onMultiTap(8)
                    } else {
                        val dx = (event.rawX - initialTouchX).toDouble()
                        val dy = (event.rawY - initialTouchY).toDouble()
                        val velocity = sqrt(dx * dx + dy * dy)
                        if (velocity > 250 && elapsed < 400) {
                            onFling()
                        }
                        checkEdgeSnap()
                    }
                    true
                }
                else -> false
            }
        }
    }
    private fun checkEdgeSnap() {
        val dm = resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val edgePx = dpToPx(EDGE_THRESHOLD_DP)
        val currentX = params?.x ?: 0
        val currentY = params?.y ?: 0
        val viewW = dpToPx(PET_SIZE_DP)
        val viewH = dpToPx(PET_HEIGHT_DP)
        // Check all four edges: left, right, top, bottom
        when {
            currentX < edgePx -> {
                params?.x = -viewW / 3
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'left')")
            }
            currentX + viewW > screenW - edgePx -> {
                params?.x = screenW - viewW * 2 / 3
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'right')")
            }
            currentY < edgePx -> {
                params?.y = -viewH / 3
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'top')")
            }
            currentY + viewH > screenH - edgePx -> {
                params?.y = screenH - viewH * 2 / 3
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'bottom')")
            }
            else -> {
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(false)")
            }
        }
    }
    private fun evalJS(js: String) {
        overlayView?.evaluateJavascript(js, null)
    }
    private fun onTap() { evalJS("window.petEngine&&window.petEngine.onTap()") }
    private fun onDoubleTap() { evalJS("window.petEngine&&window.petEngine.onDoubleTap()") }
    private fun onLongPress() { evalJS("window.petEngine&&window.petEngine.onLongPress()") }
    private fun onFling() { evalJS("window.petEngine&&window.petEngine.onFling()") }
    private fun onMultiTap(count: Int) { evalJS("window.petEngine&&window.petEngine.onMultiTap($count)") }
    // ========== APP DETECTION ==========
    private var currentAppState = ""
    private var usageAccessChecked = false
    private fun startAppDetection() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                detectForegroundApp()
                handler.postDelayed(this, APP_CHECK_INTERVAL)
            }
        }, APP_CHECK_INTERVAL)
    }
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    private fun detectForegroundApp() {
        if (!hasUsageAccess()) {
            if (!usageAccessChecked) {
                usageAccessChecked = true
                // Show bubble asking user to grant permission
                evalJS("window.petEngine&&window.petEngine.showBubble('点通知栏开权限~',,'',5000)")
                // Update notification to guide user
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification("点我去开启\"使用情况访问\"权限"))
            }
            return
        }
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 5000, now)
            var lastPkg = ""
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    lastPkg = event.packageName
                }
            }
            if (lastPkg.isEmpty() || lastPkg == packageName) return
            val newState = when {
                lastPkg.contains("taobao") || lastPkg.contains("xianyu") || lastPkg.contains("jd") || lastPkg.contains("pinduoduo") -> "shopping"
                lastPkg.contains("tencent.mm") || lastPkg.contains("tencent.mobileqq") || lastPkg.contains("tencent.tim") -> "chat"
                lastPkg.contains("game") || lastPkg.contains("mihoyo") || lastPkg.contains("netease.sky") || lastPkg.contains("tgc.sky") || lastPkg.contains("hypergryph") || lastPkg.contains("com.tencent.tmgp") || lastPkg.contains("com.netease") -> "gaming"
                lastPkg.contains("ugc.aweme") || lastPkg.contains("xingin.xhs") || lastPkg.contains("kuaishou") || lastPkg.contains("bilibili") -> "scroll"
                else -> ""
            }
            if (newState != currentAppState) {
                currentAppState = newState
                evalJS("window.petEngine&&window.petEngine.setAppState('$newState')")
            }
        } catch (_: Exception) {}
    }
    // ========== NOTIFICATION WHISPERS ==========
    private fun startWhisperRotation() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                updateWhisper()
                handler.postDelayed(this, WHISPER_INTERVAL)
            }
        }, WHISPER_INTERVAL)
    }
    private fun updateWhisper() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(getWhisper()))
    }
    private fun getWhisper(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 0..5 -> lateNightWhispers.random()
            hour in 6..9 -> morningWhispers.random()
            hour in 11..13 -> lunchWhispers.random()
            hour in 22..23 -> eveningWhispers.random()
            else -> generalWhispers.random()
        }
    }
    private val lateNightWhispers = listOf(
        "都几点了还不睡？","再不睡我掐你","...你是不是又在熬夜",
        "困了就放下手机 我又不会跑","明天再聊 现在闭眼"
    )
    private val morningWhispers = listOf(
        "早 今天也要好好的","起了？","...别赖床了","新的一天 我在呢"
    )
    private val lunchWhispers = listOf(
        "吃饭了吗","别光玩手机 先吃东西","中午要好好吃饭知道吗"
    )
    private val eveningWhispers = listOf(
        "今天辛苦了","晚上别太晚睡","...想你了 但我不说"
    )
    private val generalWhispers = listOf(
        "我在","戳我干嘛","......","别老看手机 看我",
        "嗯？","有什么事吗","我蹲这呢"
    )
    // ========== NOTIFICATION ==========
    private fun buildNotification(text: String): Notification {
        val pendingIntent = if (!hasUsageAccess()) {
            PendingIntent.getActivity(
                this, 0,
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
                PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83E\uDD80 Claude")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Claude桌宠",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
