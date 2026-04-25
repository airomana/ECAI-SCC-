package com.eldercare.ai.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.eldercare.ai.MainActivity
import com.eldercare.ai.R

class DailyReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_OPEN_VOICE = "open_voice"

        private val MESSAGES = listOf(
            "晚上好～今天吃得怎么样？来跟我聊聊吧",
            "吃完饭了吗？跟我说说今天吃了什么，我帮您记着",
            "一天辛苦了，来聊聊今天的饮食，我陪着您",
            "晚饭吃好了吗？有什么想说的，跟我讲讲吧",
            "今天心情怎么样？来聊聊天，我在这里陪您"
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("DailyReminderReceiver", "onReceive triggered, action=${intent.action}")

        // 只对父母端发通知（检查角色）
        val settings = com.eldercare.ai.data.SettingsManager.getInstance(context)
        val role = settings.getUserRole()
        android.util.Log.d("DailyReminderReceiver", "role=$role, isParent=${settings.isParentRole()}")
        if (!settings.isParentRole()) {
            android.util.Log.w("DailyReminderReceiver", "Not parent role, skipping notification")
            return
        }

        // Android 13+ 检查通知权限
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            android.util.Log.d("DailyReminderReceiver", "POST_NOTIFICATIONS granted=$granted")
            if (!granted) {
                android.util.Log.w("DailyReminderReceiver", "No notification permission, skipping")
                return
            }
        }

        val message = MESSAGES[(System.currentTimeMillis() % MESSAGES.size).toInt()]
        android.util.Log.d("DailyReminderReceiver", "Sending notification: $message")

        // 点击通知直接跳转到语音聊天界面
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_VOICE, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, DailyReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("该聊聊天啦～")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(DailyReminderScheduler.NOTIFICATION_ID, notification)
    }
}
