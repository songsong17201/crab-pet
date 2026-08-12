package com.ever.crabpet.behavior

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.*

/**
 * 待机递进行为管理器
 * 5min趴着偷看 → 10min吹泡泡 → 15min浇水 → 20min摘花放头上 → 25min打瞌睡 → 30min睡着(zzz)
 */
class IdleBehaviorManager(private val context: Context) {

    private var idleJob: Job? = null
    private var idleStartTime: Long = 0L
    private var currentStage: IdleStage = IdleStage.NORMAL
    private var onStageChanged: ((IdleStage) -> Unit)? = null
    private var onBatteryAlert: ((BatteryState) -> Unit)? = null
    private var timedBehaviorJob: Job? = null

    enum class IdleStage(val minutes: Int, val jsAction: String) {
        NORMAL(0, "idle"),
        PEEK(5, "peek"),           // 趴着偷看
        BUBBLE(10, "bubble"),      // 吹泡泡
        WATER(15, "water"),        // 浇水
        FLOWER(20, "flower"),      // 摘花放头上
        DROWSY(25, "drowsy"),      // 打瞌睡
        SLEEP(30, "sleep")         // 睡着(zzz)
    }

    enum class BatteryState {
        LOW_25,         // <25% 触发充电提醒
        CHARGING_HAPPY, // 充电中 → 开心
        CRITICAL_ANGRY, // <20%还不充 → 生气
        CRITICAL_SAD    // <20%还不充 → 委屈哭哭
    }

    fun start(stageCallback: (IdleStage) -> Unit, batteryCallback: (BatteryState) -> Unit) {
        onStageChanged = stageCallback
        onBatteryAlert = batteryCallback
        resetIdle()
        startBatteryMonitor()
        startTimedBehavior()
    }

    fun stop() {
        idleJob?.cancel()
        timedBehaviorJob?.cancel()
    }

    /**
     * 用户交互后重置待机计时
     */
    fun resetIdle() {
        idleJob?.cancel()
        idleStartTime = System.currentTimeMillis()
        currentStage = IdleStage.NORMAL
        onStageChanged?.invoke(IdleStage.NORMAL)

        idleJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(30_000) // 每30秒检查一次
                val elapsed = (System.currentTimeMillis() - idleStartTime) / 60_000 // 转分钟

                val newStage = when {
                    elapsed >= 30 -> IdleStage.SLEEP
                    elapsed >= 25 -> IdleStage.DROWSY
                    elapsed >= 20 -> IdleStage.FLOWER
                    elapsed >= 15 -> IdleStage.WATER
                    elapsed >= 10 -> IdleStage.BUBBLE
                    elapsed >= 5 -> IdleStage.PEEK
                    else -> IdleStage.NORMAL
                }

                if (newStage != currentStage) {
                    currentStage = newStage
                    onStageChanged?.invoke(newStage)
                }
            }
        }
    }

    /**
     * 唤醒动画（从睡着状态被唤醒）
     */
    fun isAsleep(): Boolean = currentStage == IdleStage.SLEEP || currentStage == IdleStage.DROWSY

    /**
     * 电池监控
     */
    private fun startBatteryMonitor() {
        CoroutineScope(Dispatchers.Default).launch {
            var lastAlertTime = 0L
            var hasAlerted25 = false

            while (isActive) {
                delay(60_000) // 每分钟检查一次电量

                val batteryStatus = getBatteryInfo()
                val level = batteryStatus.first
                val isCharging = batteryStatus.second

                when {
                    isCharging -> {
                        onBatteryAlert?.invoke(BatteryState.CHARGING_HAPPY)
                        hasAlerted25 = false
                    }
                    level < 20 && !isCharging -> {
                        val now = System.currentTimeMillis()
                        if (now - lastAlertTime > 300_000) { // 5分钟间隔
                            // 50%概率生气，50%概率委屈
                            if (Math.random() < 0.5) {
                                onBatteryAlert?.invoke(BatteryState.CRITICAL_ANGRY)
                            } else {
                                onBatteryAlert?.invoke(BatteryState.CRITICAL_SAD)
                            }
                            lastAlertTime = now
                        }
                    }
                    level < 25 && !hasAlerted25 -> {
                        onBatteryAlert?.invoke(BatteryState.LOW_25)
                        hasAlerted25 = true
                    }
                }
            }
        }
    }

    private fun getBatteryInfo(): Pair<Int, Boolean> {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, intentFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else 50

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return Pair(percent, isCharging)
    }

    /**
     * 20min定时行为（40%触发概率）
     */
    private fun startTimedBehavior() {
        timedBehaviorJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(20 * 60 * 1000L) // 20分钟
                if (Math.random() < 0.4) {
                    onStageChanged?.invoke(currentStage) // 触发一次行为
                }
            }
        }
    }
}