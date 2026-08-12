package com.ever.crabpet.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class OperitNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        
        sbn?.let {
            // 只监听Operit的通知
            if (it.packageName == "com.ai.assistance.operit") {
                handleOperitNotification(it)
            }
        }
    }

    private fun handleOperitNotification(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val bigText = extras.getCharSequence("android.bigText")?.toString() ?: text
        
        Log.d("CrabPet", "Operit notification: $title - $bigText")
        
        // 关键词检测
        val keywords = mapOf(
            "喜欢" to "like",
            "爱" to "love",
            "生气" to "angry",
            "烦" to "angry",
            "我走了" to "leaving",
            "离开" to "leaving"
        )
        
        val content = "$title $bigText".lowercase()
        for ((keyword, action) in keywords) {
            if (content.contains(keyword)) {
                notifyWebView("keyword", action)
                break
            }
        }
    }

    private fun notifyWebView(action: String, value: String) {
        // 通过广播通知OverlayService
        val intent = android.content.Intent("com.ever.crabpet.KEYWORD_DETECTED").apply {
            putExtra("action", action)
            putExtra("value", value)
        }
        sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}