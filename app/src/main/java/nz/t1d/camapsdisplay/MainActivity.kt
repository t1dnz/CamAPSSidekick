package nz.t1d.camapsdisplay

import android.content.*
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.ui.AppBarConfiguration
import nz.t1d.camapsdisplay.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var displayLocked: Boolean = true
    private lateinit var notificationReciever: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        // Button to stop the display from sleeping
        binding.displayLock.setOnClickListener { _ ->
            displayLocked = !displayLocked
            if (displayLocked) {
                println("Locked")
                binding.displayLock.text = getString(R.string.locked_display)
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                println("UnLocked")
                binding.displayLock.text = getString(R.string.unlocked_display)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // check if notifications have been enabled
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        if (!isNotificationServiceEnabled()) {
            startActivity(intent)
        }

        // Listen to notifications
        println("registering listener")
        notificationReciever = object : BroadcastReceiver() {
            override fun onReceive(contxt: Context?, intent: Intent?) {
                println("HAHAHAHAH")
                val parent = binding.frame
                val rv = intent?.extras?.get("view") as RemoteViews
                println("HAHAHAHAH ${rv}")
                val v = rv.apply(applicationContext,  parent)
                parent.addView(v)

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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
            notificationReciever
        );
    }
}