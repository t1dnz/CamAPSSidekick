package nz.t1d.camapsdisplay

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.RemoteViews
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/*
This class captures the CamAPS FX notifications as RemoteView and then sends them to the main activity to be processed
 */
class CamAPSNotificationScanner : NotificationListenerService() {
    private val packagesToListenTo = hashSetOf("com.camdiab.fx_alert.mmoll","com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.hx.mmoll","com.camdiab.fx_alert.hx.mgdl");

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val packageName: String = sbn.packageName
        val notification: Notification = sbn.notification
        val ongoing: Boolean = sbn.isOngoing
        val contentView = notification?.contentView

        if (!packagesToListenTo.contains(packageName) || !ongoing || notification == null || contentView == null) {
            return
        }

        sendMessageToActivity(contentView)
    }

    private fun sendMessageToActivity(rv: RemoteViews) {
        val intent = Intent("CamAPSFXNotification")
        // You can also include some extra data.
        intent.putExtra("view", rv)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        //
    }

    override fun onBind(intent: Intent?): IBinder? {
        return super.onBind(intent)
    }
}