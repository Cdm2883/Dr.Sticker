package vip.cdms.drsticker.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import vip.cdms.drsticker.MainActivity
import vip.cdms.drsticker.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StickerNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun create(): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "TODO: NotificationChannel.name",
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopService = PendingIntent.getService(
            context,
            1,
            Intent(context, StickerService::class.java).setAction(StickerService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_drsticker)
            .setContentTitle("TODO: Notification.title")
            .setContentText("TODO: Notification.text")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Stop", stopService)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 2317
        private const val CHANNEL_ID = "sticker_runtime"
    }
}
