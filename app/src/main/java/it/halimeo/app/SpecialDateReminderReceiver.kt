package it.halimeo.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val CHANNEL_ID = "halimeo_special_dates"
private const val ACTION_REMINDER = "it.halimeo.app.SPECIAL_DATE_REMINDER"

fun scheduleSpecialDateReminder(context: Context, label: String, date: LocalDate) {
    val today = LocalDate.now()
    val nextDate = if (date.isBefore(today) || (date == today && LocalTime.now().hour >= 9)) {
        runCatching { date.withYear(today.year + 1) }.getOrElse { date.plusYears(1) }
    } else date.withYear(today.year)
    val trigger = LocalDateTime.of(nextDate, LocalTime.of(9, 0))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(context, SpecialDateReminderReceiver::class.java).apply {
        action = ACTION_REMINDER
        putExtra("label", label)
        putExtra("date", date.toString())
    }
    val pending = PendingIntent.getBroadcast(context, 47021, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
}

class SpecialDateReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Date speciali", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Promemoria per compleanni e date importanti salvate in HaliMeo"
            })
        }
        val label = intent.getStringExtra("label") ?: "Data speciale"
        val date = intent.getStringExtra("date").orEmpty()
        val detail = if (date.isNotBlank()) "Una data importante da ricordare · $date" else "Una data importante da ricordare"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("Oggi: $label")
            .setContentText(detail)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            manager.notify(47021, notification)
        }
        // Re-arm the annual reminder after today's notification.
        runCatching { LocalDate.parse(date) }.getOrNull()?.let { scheduleSpecialDateReminder(context, label, it) }
    }
}
