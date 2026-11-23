package uz.ekranqoriqchisi

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Telefon yoqilganda eslatma chiqaradi. Android 14+ kamerali xizmatni
 * avtomatik ishga tushirishga ruxsat bermaydi, shuning uchun ota-ona bir
 * bosish bilan himoyani yoqadi.
 */
class YoqilishQabulchisi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("eslatma", "Eslatmalar", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val ochish = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.AVTO_YOQISH, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            2,
            NotificationCompat.Builder(context, "eslatma")
                .setSmallIcon(R.drawable.ic_bildirishnoma)
                .setContentTitle("Ekran qo'riqchisi")
                .setContentText("Himoyani yoqish uchun bosing 👁")
                .setContentIntent(ochish)
                .setAutoCancel(true)
                .build()
        )
    }
}
