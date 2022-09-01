package nz.t1d.camapsdisplay

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import nz.t1d.camapsdisplay.databinding.ActivityMainBinding
import nz.t1d.di.CamAPSNotificationReceiver
import nz.t1d.di.DiasendPoller
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var camAPSNotificationReceiver: CamAPSNotificationReceiver

    @Inject
    lateinit var diasendPoller: DiasendPoller

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.content_fragment)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)


        // Make sure listenting to notifications has been enabled to listen to CamAPS notifs
        val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
        if (!isNotificationServiceEnabled()) {
            startActivity(intent)
        }


        // Setup Preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        setupDisplaySleep(prefs.getBoolean("display_sleep", false))
        setupDisplayDark(prefs.getBoolean("display_dark", false))
        prefs.registerOnSharedPreferenceChangeListener(this)

        // initialize the camAPS notification receiver to start listening
        camAPSNotificationReceiver.listen()
        diasendPoller.start_diasend_poller()
    }



    override fun onDestroy() {
        super.onDestroy()
        camAPSNotificationReceiver.stopListening()
        diasendPoller.stop_diasend_poller()
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

    override fun onSharedPreferenceChanged(sp: SharedPreferences, key: String) {
        when (key) {
            "display_sleep" -> setupDisplaySleep(sp.getBoolean(key, false))
            "display_dark" -> setupDisplayDark(sp.getBoolean(key, false))
        }
    }

    private fun setupDisplaySleep(displaySleep: Boolean) {
        if (displaySleep) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun setupDisplayDark(displayDark: Boolean) {
        // from https://stackoverflow.com/questions/62577645/android-view-view-systemuivisibility-deprecated-what-is-the-replacement
        if (displayDark) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, binding.mainActivity).let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            binding.actionbar.background = ResourcesCompat.getDrawable(baseContext.resources, R.drawable.border, null)
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            WindowInsetsControllerCompat(window, binding.mainActivity).show(WindowInsetsCompat.Type.systemBars())
            binding.actionbar.background = null
            binding.actionbar.setBackgroundColor(ResourcesCompat.getColor(baseContext.resources,  R.color.purple_200, null))
        }
    }

}