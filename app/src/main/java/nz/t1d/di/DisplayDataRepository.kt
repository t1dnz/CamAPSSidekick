package nz.t1d.di

import android.content.Context
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import nz.t1d.datamodels.*
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayDataRepository @Inject constructor(@ApplicationContext context: Context) {

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val TAG = "DisplayDataRepository"

    // store of listeners for changes in the display data
    val listeners = mutableListOf<() -> Unit>()

    private val t1d = T1DModel(iobModel = BiLinearIOB())

    val bglReadings: SortedSet<BGLReading>
      get() = t1d.bglReadings

    val recentEvents: SortedSet<BaseDataClass>
      get() = t1d.recentEvents

    val insulinOnBoardBolus: Float
      get() = t1d.estimateBolusInsulinOnboard(t1d)

    val insulinOnBoardBasal: Float
      get() = t1d.estimateBasalInsulinOnBoard(t1d)

    val timeInRange: Float
      get() = t1d.timeInRange

    val insulinCurrentBasal: Float
      get() = t1d.insulinCurrentBasal

    val meanBGL: Float
      get() = t1d.meanBGL

    val stdBGL: Float
      get() = t1d.stdBGL

    // TODO Update these (and other) preferences when they change
    init {
        t1d.insulinDuration = prefs.getString("insulin_duration", "0.0")!!.toFloat()
        t1d.insulinOnset =  prefs.getString("insulin_onset", "0.0")!!.toFloat()
        t1d.insulinPeak =   prefs.getString("insulin_peak", "0.0")!!.toFloat()
    }

    // Update the body BGL via notification from CamAPS notif
    fun newNotificationAvailable(nd: NotificationData) {
        t1d.addBGlReading(nd.reading, nd.time, nd.unit, DATA_SOURCE.CAMAPS_NOTIF)
        // Update listeners something has changes
        updatedListeners()
    }

    fun updatedListeners() {
        listeners.forEach { it() }
    }

    fun addPatientData(patientData: PatientData) {
        t1d.addPatientData(patientData)
        t1d.removeOldData()
        t1d.processData()
        updatedListeners()
    }


}


