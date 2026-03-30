package com.eldercare.ai.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = com.eldercare.ai.data.SettingsManager.getInstance(context)
            if (settings.isParentRole()) {
                DailyReminderScheduler.schedule(context)
            }
        }
    }
}
