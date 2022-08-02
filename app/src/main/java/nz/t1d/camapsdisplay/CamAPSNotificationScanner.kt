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


class CamAPSNotificationScanner : NotificationListenerService() {
    val packagesToListenTo = hashSetOf("com.camdiab.fx_alert.mmoll","com.camdiab.fx_alert.mgdl","com.camdiab.fx_alert.hx.mmoll","com.camdiab.fx_alert.hx.mgdl");

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val packageName: String = sbn.packageName
        val notification: Notification = sbn.notification
        val ongoing: Boolean = sbn.isOngoing
        val contentView = notification?.contentView

        if (!packagesToListenTo.contains(packageName) || !ongoing || notification == null || contentView == null) {
            return
        }
        println("i got a notificaiton I want to show you")
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


    /// modified from xdrip
    private fun processRemote(cview: RemoteViews) {
        println("Processing the Notification View")

        val applied = cview.apply(applicationContext, null)
        // recursivly loop through all children looking for text views
        var texts: MutableList<TextView> = arrayListOf()
        getTextViews(texts,  applied.rootView as ViewGroup)

        var textStrings: List<String> = listOf()
        for (view in texts) {
            if (view.text == null) {
                continue
            }
            textStrings += view.text.toString()
        }

        for (textString in textStrings)
        {
            println( "ttt " + textString)
        }
    }
/*

    fun isValidMmol(text: String): Boolean {
        return text.matches("[0-9]+[.,][0-9]+")
    }
*/

    private fun getTextViews(output: MutableList<TextView>, parent: ViewGroup) {
        val children = parent.childCount
        for (i in 0 until children) {
            val view = parent.getChildAt(i)
            if (view.visibility === View.VISIBLE) {
                if (view is TextView) {
                    output.add(view as TextView)
                } else if (view is ViewGroup) {
                    getTextViews(output, view as ViewGroup)
                }
            }
        }
    }

}