package nz.t1d.camapsdisplay

import android.app.Notification
import android.content.Intent
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification


class CamAPSNotificationScanner : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val fromPackage: String = sbn.packageName
        val notif: Notification = sbn.notification
        val ongoing: Boolean = sbn.isOngoing

        println("Notification ${fromPackage} .. ${notif} .. ${ongoing}")

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        //
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}