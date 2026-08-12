package com.ever.crabpet.behavior

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*

class AppBehaviorDetector(private val context: Context) {

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private var detectionJob: Job? = null
    private var onAppDetected: ((String) -> Unit)? = null
    
    // App分类映射
    private val musicApps = setOf(
        "com.netease.cloudmusic",
        "com.tencent.qqmusic",
        "fm.xiami.main",
        "com.kugou.android",
        "com.spotify.music"
    )
    
    private val shoppingApps = setOf(
        "com.taobao.taobao",
        "com.xianyu.app",
        "com.jd.lib.android",
        "com.tmall.wireless"
    )
    
    private val cameraApps = setOf(
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.GoogleCamera",
        "com.android.gallery3d"
    )
    
    private val gameApps = setOf(
        "com.tencent.tmgp.sgame", // 王者荣耀
        "com.tencent.tmgp.pubgmhd", // 和平精英
        "com.miHoYo.Yuanshen" // 原神
    )
    
    private var lastAppPackage: String? = null
    private var appSwitchHistory = mutableListOf<Pair<Long, String>>()

    fun startDetection(callback: (String) -> Unit) {
        onAppDetected = callback
        detectionJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                detectForegroundApp()
                delay(3000) // 每3秒检测一次
            }
        }
    }

    fun stopDetection() {
        detectionJob?.cancel()
    }

    private fun detectForegroundApp() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 5000 // 最近5秒
        
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )
        
        val sortedStats = usageStatsList.sortedByDescending { it.lastTimeUsed }
        val currentApp = sortedStats.firstOrNull()?.packageName ?: return
        
        // 检测App切换
        if (currentApp != lastAppPackage) {
            lastAppPackage = currentApp
            handleAppSwitch(currentApp)
            
            // 记录切换历史
            appSwitchHistory.add(Pair(System.currentTimeMillis(), currentApp))
            if (appSwitchHistory.size > 10) {
                appSwitchHistory.removeAt(0)
            }
            
            // 检测杂耍模式（60秒内切换3个App）
            checkJugglingMode()
        }
    }

    private fun handleAppSwitch(packageName: String) {
        val appType = when (packageName) {
            in musicApps -> "music"
            in shoppingApps -> "shopping"
            in cameraApps -> "camera"
            in gameApps -> "game"
            else -> null
        }
        
        appType?.let {
            Log.d("CrabPet", "App detected: $packageName -> $it")
            onAppDetected?.invoke(it)
        }
    }

    private fun checkJugglingMode() {
        val now = System.currentTimeMillis()
        val recentSwitches = appSwitchHistory.filter { (time, _) ->
            now - time < 60000 // 60秒内
        }
        
        val uniqueApps = recentSwitches.map { it.second }.toSet()
        if (uniqueApps.size >= 3) {
            Log.d("CrabPet", "Juggling mode detected!")
            onAppDetected?.invoke("juggling")
        }
    }
}