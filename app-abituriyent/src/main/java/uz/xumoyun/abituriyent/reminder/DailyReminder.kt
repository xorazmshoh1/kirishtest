package uz.xumoyun.abituriyent.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import uz.xumoyun.abituriyent.MainActivity
import uz.xumoyun.abituriyent.R
import uz.xumoyun.abituriyent.data.AbiRepository
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun repository(): AbiRepository
}

/** Har kuni belgilangan vaqtda: kunlik test hali yechilmagan bo'lsa, eslatma chiqaradi. */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java).repository()
        if (!repo.dailyDoneToday.first()) DailyReminder.notify(applicationContext, repo.streak.first())
        return Result.success()
    }
}

object DailyReminder {
    private const val WORK = "daily_reminder"
    private const val CHANNEL = "daily_test"

    /** Keyingi eslatmagacha kechikish (daqiqa, 00:00 dan hisoblangan vaqt bo'yicha). */
    fun initialDelay(now: LocalDateTime, minutesOfDay: Int): Duration {
        val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next)
    }

    fun schedule(context: Context, minutesOfDay: Int) {
        val wm = WorkManager.getInstance(context)
        if (minutesOfDay < 0) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val delay = initialDelay(LocalDateTime.now(), minutesOfDay)
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun notify(context: Context, streak: Int) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.channel_daily), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (streak > 0) context.getString(R.string.reminder_text_streak, streak) else context.getString(R.string.reminder_text)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_school)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(1, n)
    }
}
