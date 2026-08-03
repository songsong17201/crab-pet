package com.songsong.crabpet.service
import android.app.*
import android.content.*
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.BatteryManager
import android.os.FileObserver
import android.os.Environment
import android.database.ContentObserver
import android.provider.MediaStore
import android.net.Uri
import android.view.*
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.songsong.crabpet.MainActivity
import java.io.File
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
        private const val PET_HEIGHT_DP = 120
        private const val DOUBLE_TAP_TIMEOUT = 300L
        private const val LONG_PRESS_TIMEOUT = 600L
        private const val MOVE_THRESHOLD = 15
        private const val TAP_DELAY = 320L
        private const val WHISPER_INTERVAL = 3600_000L
        private const val APP_CHECK_INTERVAL = 3000L
        private const val EDGE_THRESHOLD_DP = 40
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getWhisper()))
        setupOverlay()
        startWhisperRotation()
        startAppDetection()
        registerBatteryReceiver()
        startScreenshotObserver()
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

    // ========== 手势处理 ==========
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
        val quarterW = viewW / 4
        val quarterH = viewH / 4

        when {
            currentX < edgePx -> {
                params?.x = -quarterW
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'left')")
            }
            currentX + viewW > screenW - edgePx -> {
                params?.x = screenW - viewW + quarterW
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'right')")
            }
            currentY < edgePx -> {
                params?.y = -quarterH
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'top')")
            }
            currentY + viewH > screenH - edgePx -> {
                params?.y = screenH - viewH + quarterH
                windowManager?.updateViewLayout(overlayView, params)
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(true,'bottom')")
            }
            else -> {
                evalJS("window.petEngine&&window.petEngine.setEdgeMode(false,'none')")
            }
        }
    }

    // 甩飞后自动爬回来
    private fun flingAndReturn() {
        evalJS("window.petEngine&&window.petEngine.onFling()")
        val savedX = params?.x ?: 50
        val savedY = params?.y ?: 300
        // 先甩到屏幕外
        val dm = resources.displayMetrics
        params?.x = dm.widthPixels + 100
        windowManager?.updateViewLayout(overlayView, params)
        // 1.5秒后爬回来
        handler.postDelayed({
            animateReturn(savedX, savedY)
        }, 1500)
    }

    private fun animateReturn(targetX: Int, targetY: Int) {
        val startX = params?.x ?: 0
        val startY = params?.y ?: 0
        val steps = 20
        var step = 0
        val runnable = object : Runnable {
            override fun run() {
                step++
                val progress = step.toFloat() / steps
                params?.x = (startX + (targetX - startX) * progress).toInt()
                params?.y = (startY + (targetY - startY) * progress).toInt()
                try {
                    windowManager?.updateViewLayout(overlayView, params)
                } catch (_: Exception) {}
                if (step < steps) {
                    handler.postDelayed(this, 30)
                } else {
                    evalJS("window.petEngine&&window.petEngine.showBubble('我爬回来了','angry')")
                    evalJS("window.petEngine&&window.petEngine.setState('angry',2000)")
                }
            }
        }
        handler.post(runnable)
    }

    private fun evalJS(js: String) {
        overlayView?.evaluateJavascript(js, null)
    }

    private fun onTap() { evalJS("window.petEngine&&window.petEngine.onTap()") }
    private fun onDoubleTap() { evalJS("window.petEngine&&window.petEngine.onDoubleTap()") }
    private fun onLongPress() { evalJS("window.petEngine&&window.petEngine.onLongPress()") }
    private fun onFling() { flingAndReturn() }
    private fun onMultiTap(count: Int) { evalJS("window.petEngine&&window.petEngine.onMultiTap($count)") }

    // ========== 截图检测 ==========
    private var screenshotObserver: FileObserver? = null
    private var screenshotContentObserver: ContentObserver? = null
    private var lastScreenshotTime = 0L

    private fun startScreenshotObserver() {
        // 方案1：FileObserver 监听截图目录
        val dirs = listOf(
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Screenshots"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "screenshot"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Screenshots"),
            File(Environment.getExternalStorageDirectory(), "截屏录屏/Screenshots")
        )
        for (dir in dirs) {
            if (dir.exists()) {
                startObserverForDir(dir.absolutePath)
                break
            }
        }
        // 方案2：ContentObserver 监听 MediaStore（兼容鸿蒙）
        startScreenshotContentObserver()
    }

    private fun startScreenshotContentObserver() {
        screenshotContentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (uri == null) return
                val now = System.currentTimeMillis()
                if (now - lastScreenshotTime < 3000) return
                // 检查是否是截图文件
                try {
                    val cursor = contentResolver.query(uri, arrayOf(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.RELATIVE_PATH
                    ), null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val name = it.getString(0) ?: ""
                            val path = it.getString(1) ?: ""
                            if (name.contains("screenshot", ignoreCase = true) ||
                                name.contains("截屏", ignoreCase = true) ||
                                path.contains("screenshot", ignoreCase = true) ||
                                path.contains("Screenshots", ignoreCase = true)) {
                                lastScreenshotTime = now
                                onScreenshot()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            screenshotContentObserver!!
        )
    }

    private fun startObserverForDir(path: String) {
        screenshotObserver = object : FileObserver(path, CREATE or MOVED_TO) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null) return
                val now = System.currentTimeMillis()
                // 防抖：3秒内只响应一次
                if (now - lastScreenshotTime < 3000) return
                lastScreenshotTime = now
                if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                    handler.post { onScreenshot() }
                }
            }
        }
        screenshotObserver?.startWatching()
    }

    private fun onScreenshot() {
        evalJS("window.petEngine&&window.petEngine.onScreenshot()")
    }

    // ========== 充电/断电检测 ==========
    private var batteryReceiver: BroadcastReceiver? = null
    private var lastBatteryState = -1

    private fun registerBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_POWER_CONNECTED -> onCharging()
                    Intent.ACTION_POWER_DISCONNECTED -> onDischarging()
                    Intent.ACTION_BATTERY_LOW -> onBatteryLow()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
        }
        registerReceiver(batteryReceiver, filter)
    }

    private fun onCharging() {
        val reactions = listOf(
            "嗯~充上电了 舒服" to "happy",
            "有电了有电了" to "love",
            "终于记得给我充电了" to "angry"
        )
        val r = reactions.random()
        evalJS("window.petEngine&&window.petEngine.showBubble('${r.first}','${r.second}')")
        evalJS("window.petEngine&&window.petEngine.setState('${r.second}',3000)")
    }

    private fun onDischarging() {
        val reactions = listOf(
            "啊 电没了…" to "cry",
            "怎么拔了！" to "angry",
            "哼" to "angry"
        )
        val r = reactions.random()
        evalJS("window.petEngine&&window.petEngine.showBubble('${r.first}','${r.second}')")
        evalJS("window.petEngine&&window.petEngine.setState('${r.second}',3000)")
    }

    private fun onBatteryLow() {
        evalJS("window.petEngine&&window.petEngine.showBubble('快没电了…快充电！','angry')")
        evalJS("window.petEngine&&window.petEngine.setState('cry',4000)")
    }

    // ========== 前台APP检测 ==========
    private var currentAppState = ""
    private var usageAccessChecked = false
    // 快速切换检测
    private val appSwitchTimes = mutableListOf<Long>()

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
                evalJS("window.petEngine&&window.petEngine.showBubble('点通知栏开权限~','',5000)")
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification("点我去开启使用情况访问权限"))
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
                lastPkg.contains("music") || lastPkg.contains("qqmusic") || lastPkg.contains("kugou") || lastPkg.contains("kuwo") || lastPkg.contains("cloudmusic") || lastPkg.contains("spotify") -> "music"
                lastPkg.contains("camera") || lastPkg.contains("gallery") || lastPkg.contains("photos") || lastPkg.contains("album") -> "camera"
                else -> ""
            }

            if (newState != currentAppState) {
                // 记录切换时间，检测快速切换
                val switchNow = System.currentTimeMillis()
                appSwitchTimes.add(switchNow)
                // 只保留60秒内的记录
                appSwitchTimes.removeAll { switchNow - it > 60000 }
                if (appSwitchTimes.size >= 3) {
                    onRapidAppSwitch()
                    appSwitchTimes.clear()
                }
                currentAppState = newState
                evalJS("window.petEngine&&window.petEngine.setAppState('$newState')")
            }
        } catch (_: Exception) {}
    }

    private fun onRapidAppSwitch() {
        val reactions = listOf(
            "你切那么快干嘛！眼花了" to "dizzy",
            "慢点！我头晕了" to "dizzy",
            "你到底要用哪个啊" to "angry"
        )
        val r = reactions.random()
        evalJS("window.petEngine&&window.petEngine.showBubble('${r.first}','${r.second}')")
        evalJS("window.petEngine&&window.petEngine.setState('${r.second}',3000)")
    }

    // ========== 通知栏碎碎念 ==========
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

    // ========== 通知 ==========
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
            .setContentTitle("\uD83E\uDD80")
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
                "桌宠",
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
        screenshotObserver?.stopWatching()
        screenshotContentObserver?.let { contentResolver.unregisterContentObserver(it) }
        batteryReceiver?.let { unregisterReceiver(it) }
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}

