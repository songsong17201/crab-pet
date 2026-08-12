package com.ever.crabpet.behavior

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

/**
 * 电量监控：
 * - <25% 提醒充电
 * - <20% 50%概率生气或委屈
 * - 充电中 → 开心反应
 * - 充满 → 表扬
 */
class BatteryMonitor(private val context: Context) {

    var onBatteryEvent: ((String, Int) -> Unit)? = null
    private var lastWarningLevel = 100
    private var wasCharging = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val percent = (level * 100) / scale
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // 充电状态变化
            if (isCharging && !wasCharging) {
                onBatteryEvent?.invoke("charging", percent)
                wasCharging = true
                lastWarningLevel = 100
                return
            }
            if (!isCharging && wasCharging) {
                wasCharging = false
            }
            if (isCharging) return

            // 未充电时的电量检测
            when {
                percent <= 15 && lastWarningLevel > 15 -> {
                    lastWarningLevel = 15
                    if (Math.random() < 0.5) {
                        onBatteryEvent?.invoke("critical_angry", percent)
                    } else {
                        onBatteryEvent?.invoke("critical_sad", percent)
                    }
                }
                percent <= 20 && lastWarningLevel > 20 -> {
                    lastWarningLevel = 20
                    if (Math.random() < 0.5) {
                        onBatteryEvent?.invoke("critical_angry", percent)
                    } else {
                        onBatteryEvent?.invoke("critical_sad", percent)
                    }
                }
                percent <= 25 && lastWarningLevel > 25 -> {
                    lastWarningLevel = 25
                    onBatteryEvent?.invoke("low", percent)
                }
            }
        }
    }

    fun startMonitoring() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun stopMonitoring() {
        try {
            context.unregisterReceiver(receiver)
        } catch (_: Exception) {}
    }
}