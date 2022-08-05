package nz.t1d.camapsdisplay

import android.content.*
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.*
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.ui.AppBarConfiguration
import nz.t1d.camapsdisplay.databinding.ActivityMainBinding
import java.time.Instant


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var displayLocked: Boolean = true
    private lateinit var notificationReciever: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Button to stop the display from sleeping
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.displayLock.setOnClickListener { _ ->
            displayLocked = !displayLocked
            if (displayLocked) {
                binding.displayLock.text = getString(R.string.locked_display)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                binding.displayLock.text = getString(R.string.unlocked_display)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // check if notifications have been enabled
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        if (!isNotificationServiceEnabled()) {
            startActivity(intent)
        }

        binding.bglTime.format = ""
        binding.bglTime.start()
        binding.bglTime.setOnChronometerTickListener { chrono ->
            chrono.text = DateUtils.getRelativeTimeSpanString(chrono.base)
        }

        // Listen to notifications
        notificationReciever = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context?, intent: Intent?) {
                val rv = intent?.extras?.get("view") as RemoteViews
                val v = rv.apply(applicationContext,  null)

                val nd = processView(v)

                binding.bglImage.setImageDrawable(nd.image_drawable)
                binding.bglReading.text = nd.reading
                binding.bglUnits.text = nd.unit
                binding.bglTime.base = nd.time

                // make visible
                binding.progressBar.visibility = View.INVISIBLE
                binding.bgllayout.visibility = View.VISIBLE

            }
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            notificationReciever,  IntentFilter("CamAPSFXNotification")
        );
    }

    /**
     * Is Notification Service Enabled.
     * Verifies if the notification listener service is enabled.
     * Got it from: https://github.com/kpbird/NotificationListenerService-Example/blob/master/NLSExample/src/main/java/com/kpbird/nlsexample/NLService.java
     * @return True if enabled, false otherwise.
     */
    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        if (!TextUtils.isEmpty(flat)) {
            val names = flat.split(":").toTypedArray()
            for (i in names.indices) {
                val cn = ComponentName.unflattenFromString(names[i])
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.packageName)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.bglTime.stop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            notificationReciever
        );
    }

    data class NotificationData(var reading: String, var unit: String, var time: Long, var image_drawable: Drawable?)

    // modified from xdrip
    private fun processView(view: View): NotificationData{
        // recursivly loop through all children looking for info
        val nd = NotificationData("0.0", "mmol/L", Instant.now().toEpochMilli(),null)
        getTextViews(nd,  view.rootView as ViewGroup)
        return nd
    }


    private fun getTextViews(output: NotificationData, parent: ViewGroup) {
        val children = parent.childCount
        for (i in 0 until children) {
            val view = parent.getChildAt(i)
            if (view.visibility === View.VISIBLE) {
                if (view is TextView) {
                    val text = (view as TextView).text.toString()
                    if (text.matches("[0-9]+[.,][0-9]+".toRegex())) {
                        output.reading = text
                    }
                }
                else if (view is ImageView) {
                    val iv = (view as ImageView)
                    output.image_drawable = iv.drawable
                } else if (view is ViewGroup) {
                    getTextViews(output, view as ViewGroup)
                }
            }
        }
    }
}