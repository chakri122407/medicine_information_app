package com.example.medicine_informationapp.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.medicine_informationapp.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val activeReminders = db.reminderDao().getActiveReminders()
                    activeReminders.forEach { reminder ->
                        AlarmScheduler.scheduleAlarm(context, reminder)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
