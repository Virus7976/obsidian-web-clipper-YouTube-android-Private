package app.marginalia

import app.obsi.core.*
import app.obsi.core.data.*
import app.obsi.core.obsidian.*
import app.obsi.core.ui.*
import app.obsi.core.update.*
import app.obsi.core.link.*
import app.obsi.core.share.*

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Auto-save found a new note with fields still to fill. With the app in the background, this
 * notification asks for them. Tapping it opens the save sheet for that video.
 */
object SaveAsk {
    private const val CHANNEL = "save-ask"
    private const val NOTIFICATION = 7310
    const val EXTRA_SAVE = "app.marginalia.SAVE_VIDEO"

    fun post(app: MarginaliaApp, id: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val video = app.library.get(id) ?: return
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Notes to finish", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            app, NOTIFICATION, Intent(app, MainActivity::class.java).putExtra(EXTRA_SAVE, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_highlight)
            .setContentTitle("Finish your note")
            .setContentText("Add your details to save ${video.meta.title}.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION, n)
    }

    fun clear(app: MarginaliaApp) {
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION)
    }
}
