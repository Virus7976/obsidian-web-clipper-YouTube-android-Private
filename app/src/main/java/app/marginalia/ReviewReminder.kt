package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/** The daily "one old highlight" nudge (Settings > Review). */
object ReviewReminder {
    private const val CHANNEL = "review"
    private const val NOTIFICATION = 7301
    const val EXTRA_REVIEW = "review"

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 7300, Intent(context, ReviewReceiver::class.java).setAction("app.marginalia.REVIEW"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Sets the alarm to match the settings. Safe to call again and again. */
    fun schedule(context: Context, on: Boolean, hour: Int) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val p = pending(context)
        alarms.cancel(p)
        if (!on) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        // Inexact on purpose: no special permission, and a few minutes off does not matter.
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.timeInMillis, AlarmManager.INTERVAL_DAY, p)
    }

    fun post(app: MarginaliaApp) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val due = app.reviews.due(app.library.videos.value)
        val first = due.firstOrNull() ?: return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Review", NotificationManager.IMPORTANCE_DEFAULT))
        val text = first.highlight.text.ifBlank { first.highlight.note }.trim()
        val open = PendingIntent.getActivity(
            app, 7302, Intent(app, MainActivity::class.java).putExtra(EXTRA_REVIEW, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_highlight)
            .setContentTitle(if (due.size > 1) "${due.size} highlights to look at again" else "A highlight to look at again")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text).setSummaryText(first.video.meta.title))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION, n)
    }
}

class ReviewReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as MarginaliaApp
        val s = app.settings.value
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ReviewReminder.schedule(app, s.reviewReminder, s.reviewHour)
            return
        }
        if (s.reviewReminder) ReviewReminder.post(app)
    }
}
