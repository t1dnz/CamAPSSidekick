package nz.t1d.di

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationData(
    var reading: Float,
    var unit: String,
    var time: Long,
    var image_drawable: Drawable?
)

@Singleton
class CamAPSNotificationReceiver @Inject constructor(
    @ApplicationContext context: Context,
    val ddr: DisplayDataRepository
) {
    private var context: Context = context

    private lateinit var notificationReciever: BroadcastReceiver

    fun listen() {
        // Listen to notifications update the fragment
        notificationReciever = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context?, intent: Intent?) {
                // Get the View of the notifications
                val rv = intent?.extras?.get("view") as RemoteViews
                // Expand it to be an object
                val v = rv.apply(context, null)

                // Extract the information from the view
                val nd = processView(v)
                ddr.newNotificationAvailable(nd)
            }
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(
            notificationReciever, IntentFilter("CamAPSFXNotification")
        )
    }

    fun stopListening() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(
            notificationReciever
        )
    }

    // modified from xdrip
    private fun processView(view: View?): NotificationData {
        // recursivly loop through all children looking for info
        val nd = NotificationData(0.0f, "mmol/L", Instant.now().toEpochMilli(), null)
        if (view != null) {
            getTextViews(nd, view.rootView as ViewGroup)
        }
        return nd
    }

    private fun getTextViews(output: NotificationData, parent: ViewGroup) {
        val children = parent.childCount
        for (i in 0 until children) {
            val view = parent.getChildAt(i)
            if (view.visibility === View.VISIBLE) {
                if (view is TextView) {
                    val text = view.text.toString()
                    if (text.matches("[0-9]+[.,][0-9]+".toRegex())) {
                        output.reading = text.toFloat()
                    }
                } else if (view is ImageView) {
                    val iv = view
                    output.image_drawable = iv.drawable
                } else if (view is ViewGroup) {
                    getTextViews(output, view)
                }
            }
        }
    }
}