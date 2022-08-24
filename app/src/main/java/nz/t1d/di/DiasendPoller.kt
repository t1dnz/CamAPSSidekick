package nz.t1d.di

import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

import nz.t1d.diasend.DiasendClient
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiasendPoller @Inject constructor(
    val diasendClient: DiasendClient,
    val ddr: DisplayDataRepository
) {
    private var TAG = "DiasendPoller"
    private var poller : Job? = null
    private var from: LocalDateTime? = null
    /*
        Rules for fetching Diasend Data
        1. Initially we fetch all of todays data (if any data with DIA time (about 10 hours) is in previous dat get that)
        3. fetch more recent data every 5 minutes
     */

    fun  start_diasend_poller(scope: LifecycleCoroutineScope)  {
        // poll diasend 5 mins
        Log.d(TAG, "Poller starting")

        if (from == null) {
            from = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)
            // Either use midnight or 10 hours ago which ever is older
            var fromMinusDIA = LocalDateTime.now().minusHours(10)
            if (from!! > fromMinusDIA) {
                from = fromMinusDIA
            }
        }

        if(poller == null) {
            poller = scope.launch {
                while(isActive) {
                    try {
                        Log.d(TAG, "Fetch data from diasend now=${LocalDateTime.now()} from=$from")
                        var patientData = diasendClient.getPatientData(
                            from!!,
                            LocalDateTime.now()
                        )
                        Log.d(TAG, "Returning list of data ${patientData?.size}")
                        ddr.addPatientData(patientData)
                        // change from to be to 30 mins ago
                        from = LocalDateTime.now().minusMinutes(30)
                    } catch (e: Throwable) {
                        println(e)
                    }

                    val t5mins: Long = 5 * 60 * 1000 // 5 mins
                    delay(t5mins)
                }
            }
        }
    }

    fun stop_diasend_poller() {
        poller?.cancel()
        poller = null
        Log.d(TAG, "Poller stopping")
    }

}