package com.masarif.app.sms

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
import com.masarif.app.R
import com.masarif.app.ui.MainActivity

/** إشعار صامت واحد فقط بعدد التجار المنتظرين. لا رنين، لا اهتزاز، يُحدَّث في مكانه. */
class PendingNotifier(private val context: Context) {

    private fun ensureChannel() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(CHANNEL_ID, "قائمة الانتظار", NotificationManager.IMPORTANCE_LOW).apply {
                description = "تجار بانتظار التصنيف"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun update(pendingMerchants: Int) {
        val nm = NotificationManagerCompat.from(context)
        if (pendingMerchants <= 0) {
            nm.cancel(NOTIFICATION_ID)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!nm.areNotificationsEnabled()) return

        ensureChannel()
        val open = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_QUEUE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val text = if (pendingMerchants == 1) "تاجر واحد بانتظار التصنيف" else "$pendingMerchants تجار بانتظار التصنيف"
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_masarif)
            .setContentTitle("مصاريف")
            .setContentText(text)
            .setContentIntent(pi)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, n)
        } catch (e: SecurityException) {
            // الصلاحية مرفوضة: نكتفي بالشارة داخل التطبيق.
        }
    }

    companion object {
        const val CHANNEL_ID = "pending_merchants"
        const val NOTIFICATION_ID = 1001
    }
}
