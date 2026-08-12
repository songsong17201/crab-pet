package com.ever.crabpet.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.app.NotificationCompat
import com.ever.crabpet.R
import com.ever.crabpet.behavior.AppBehaviorDetector
import com.ever.crabpet.behavior.BatteryMonitor
import com.ever.crabpet.behavior.DialogueManager
import com.ever.crabpet.behavior.IdleBehaviorManager
import kotlinx.coroutines.*

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: WebView
    private lateinit var appDetector: AppBehaviorDetector
    private lateinit var idleManager: IdleBehaviorManager
    private lateinit var dialogueManager: DialogueManager
    private lateinit var batteryMonitor: BatteryMonitor
    private val mainHandler = Handler(Looper.getMainLooper())

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isDragging = false
    private var clickCount = 0
    private var lastClickTime = 0L
    private var touchDownTime = 0L
    private var longPressJob: Job? = null

    // 快速拖拽检测
    private var lastDragX = 0f
    private var lastDragY = 0f
    private var lastDragTime = 0L
    private var dragSpeed = 0f

    // 随机行走
    private var roamJob: Job? = null
    private var isRoaming = false

    // 关键词广播接收
    private val keywordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.getStringExtra("action") ?: return
            val value = intent.getStringExtra("value") ?: return
            mainHandler.post {
                notifyWebView(action, value)
                idleManager.resetIdle()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dialogueManager = DialogueManager()

        setupOverlayView()
        setupBehaviorSystems()
        setupBatteryMonitor()
        startRoaming()

        // 注册关键词广播
        val filter = IntentFilter("com.ever.crabpet.KEYWORD_DETECTED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(keywordReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(keywordReceiver, filter)
        }
    }

    private fun setupOverlayView() {
        overlayView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                // 华为WebView需要关闭硬件加速才能渲染透明背景
                mediaPlaybackRequiresUserGesture = false
            }
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // 华为/鸿蒙系统确保透明
            setLayerType(android.view.View.LAYER_TYPE_NONE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            visibility = android.view.View.VISIBLE
            // WebView加载完成后强制刷新一次确保渲染
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.visibility = android.view.View.VISIBLE
                    view?.requestLayout()
                    view?.invalidate()
                }
            }
            loadUrl("file:///android_asset/web/crab.html")
        }

        val params = WindowManager.LayoutParams(
            300,
            300,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 400
        }

        overlayView.setOnTouchListener { _, event ->
            handleTouch(event, params)
            true
        }

        windowManager.addView(overlayView, params)
    }

    private fun setupBehaviorSystems() {
        // App行为检测
        appDetector = AppBehaviorDetector(this)
        appDetector.startDetection { appType ->
            mainHandler.post {
                notifyWebView("app", appType)
                idleManager.resetIdle()
            }
        }

        // 待机行为管理
        idleManager = IdleBehaviorManager(this)
        idleManager.start(
            stageCallback = { stage ->
                mainHandler.post {
                    notifyWebView("idle", stage.jsAction)
                }
            },
            batteryCallback = { state ->
                mainHandler.post {
                    val stateStr = when (state) {
                        IdleBehaviorManager.BatteryState.LOW_25 -> "low"
                        IdleBehaviorManager.BatteryState.CHARGING_HAPPY -> "charging"
                        IdleBehaviorManager.BatteryState.CRITICAL_ANGRY -> "critical_angry"
                        IdleBehaviorManager.BatteryState.CRITICAL_SAD -> "critical_sad"
                    }
                    notifyWebView("battery", stateStr)
                }
            }
        )
    }

    private fun startRoaming() {
        roamJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                // 每30-90秒随机走一次
                delay((30000L + (Math.random() * 60000).toLong()))

                if (!isDragging && !isRoaming) {
                    performRoam()
                }
            }
        }
    }

    private suspend fun performRoam() {
        isRoaming = true
        notifyWebView("action", "walk")

        val params = overlayView.layoutParams as WindowManager.LayoutParams
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // 随机目标位置
        val targetX = (Math.random() * (screenWidth - 200)).toInt()
        val targetY = (Math.random() * (screenHeight - 400)).toInt() + 100

        val startX = params.x
        val startY = params.y
        val steps = 60 // 约1秒动画

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            // 缓动
            val ease = progress * (2 - progress)
            params.x = (startX + (targetX - startX) * ease).toInt()
            params.y = (startY + (targetY - startY) * ease).toInt()

            withContext(Dispatchers.Main) {
                windowManager.updateViewLayout(overlayView, params)
            }
            delay(16) // ~60fps
        }

        notifyWebView("action", "idle")
        isRoaming = false
    }

    private fun handleTouch(event: MotionEvent, params: WindowManager.LayoutParams): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                touchDownTime = System.currentTimeMillis()
                lastDragX = event.rawX
                lastDragY = event.rawY
                lastDragTime = System.currentTimeMillis()
                dragSpeed = 0f

                // 长按检测（800ms）
                longPressJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(800)
                    if (!isDragging) {
                        notifyWebView("click", "longpress")
                        idleManager.resetIdle()
                    }
                }

                // 点击计数
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                    clickCount++
                } else {
                    clickCount = 1
                }
                lastClickTime = currentTime

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                    isDragging = true
                    longPressJob?.cancel()
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(overlayView, params)

                    // 计算拖拽速度
                    val now = System.currentTimeMillis()
                    val dt = (now - lastDragTime).coerceAtLeast(1)
                    val ddx = event.rawX - lastDragX
                    val ddy = event.rawY - lastDragY
                    dragSpeed = Math.sqrt((ddx * ddx + ddy * ddy).toDouble()).toFloat() / dt
                    lastDragX = event.rawX
                    lastDragY = event.rawY
                    lastDragTime = now
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                longPressJob?.cancel()
                val touchDuration = System.currentTimeMillis() - touchDownTime

                if (!isDragging) {
                    if (touchDuration < 300) {
                        // 短按点击
                        when {
                            clickCount >= 5 -> {
                                notifyWebView("click", "multiple")
                                clickCount = 0
                            }
                            clickCount == 2 -> notifyWebView("click", "double")
                            else -> {
                                // 稍微延迟以区分单双击
                                mainHandler.postDelayed({
                                    if (clickCount == 1) {
                                        notifyWebView("click", "single")
                                    }
                                }, 320)
                            }
                        }
                    }
                } else {
                    // 快速拖拽检测 → 晕旋转
                    if (dragSpeed > 3f) {
                        notifyWebView("click", "fastdrag")
                    }
                }

                // 重置待机
                idleManager.resetIdle()

                // 边缘吸附检测
                checkEdgeSnap(params)
                return true
            }
        }
        return false
    }

    private fun checkEdgeSnap(params: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val viewSize = 200
        val snapThreshold = 50

        var snapped = false

        // 左边缘
        if (params.x < snapThreshold - viewSize / 2) {
            params.x = -viewSize / 3
            notifyWebView("edge", "left")
            snapped = true
        }
        // 右边缘
        else if (params.x > screenWidth - viewSize / 2 - snapThreshold) {
            params.x = screenWidth - viewSize * 2 / 3
            notifyWebView("edge", "right")
            snapped = true
        }
        // 上边缘
        if (params.y < snapThreshold - viewSize / 2) {
            params.y = -viewSize / 3
            notifyWebView("edge", "top")
            snapped = true
        }
        // 下边缘
        else if (params.y > screenHeight - viewSize / 2 - snapThreshold) {
            params.y = screenHeight - viewSize * 2 / 3
            notifyWebView("edge", "bottom")
            snapped = true
        }

        if (snapped) {
            windowManager.updateViewLayout(overlayView, params)
        }
    }

    private fun notifyWebView(action: String, value: String) {
        overlayView.evaluateJavascript("window.handleNativeEvent('$action', '$value')", null)
    }

    private fun setupBatteryMonitor() {
        batteryMonitor = BatteryMonitor(this)
        batteryMonitor.onBatteryEvent = { event, _ ->
            mainHandler.post {
                notifyWebView("battery", event)
                if (event != "charging") {
                    val dialogue = dialogueManager.getChargingDialogue(event)
                    notifyWebView("dialogue", dialogue)
                }
            }
        }
        batteryMonitor.startMonitoring()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "桌宠服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持桌宠运行"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("蟹蟹桌宠")
            .setContentText("小螃蟹正在陪伴你~")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        roamJob?.cancel()
        appDetector.stopDetection()
        idleManager.stop()
        batteryMonitor.stopMonitoring()
        unregisterReceiver(keywordReceiver)
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "crab_pet_service"
        private const val NOTIFICATION_ID = 1001
    }
}