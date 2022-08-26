package nz.t1d.di

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import nz.t1d.diasend.DiasendDatum
import java.text.DecimalFormat
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.HashSet


interface BaseDataClass {
    val time: LocalDateTime
    var value: Float

    fun minsAgo(): Long {
        return Duration.between(time,LocalDateTime.now()).toMinutes()
    }
}

data class BasalInsulinChange(
    override var value: Float,
    override var time: LocalDateTime,
) : BaseDataClass

data class BolusInsulin(
    override var value: Float,
    override var time: LocalDateTime,
) : BaseDataClass

data class CarbIntake(
    override var value: Float,
    override var time: LocalDateTime,
) : BaseDataClass

data class BGLReading(
    override var value: Float,
    override var time: LocalDateTime,
) : BaseDataClass


object dataComparitor : Comparator<BaseDataClass> {
    override fun compare(p0: BaseDataClass?, p1: BaseDataClass?): Int {
        if (p1 == null || p0 == null) {
            return 0
        }
        return p1.time.compareTo(p0.time)
    }
}

@Singleton
class DisplayDataRepository @Inject constructor(@ApplicationContext context: Context) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val TAG = "DisplayDataRepository"

    //
    private var previousBGLReading: Float = 0f
    private var previousBGLReadingTime: Long = 0

    // Data to be accessed
    var bglReading: Float = 0f
    var bglDiff: String = ""
    var bglReadingTime: Long = 0
    var bglDirectionImage: Drawable? = null
    var bglUnit: String = "mmol/L"

    // Diasend data ordered so that newest is at the front of the set
    var diasendData: SortedSet<DiasendDatum> = sortedSetOf(object : Comparator<DiasendDatum> {
        override fun compare(p0: DiasendDatum?, p1: DiasendDatum?): Int {
            if (p1 == null || p0 == null) {
                return 0
            }
            return p1.createdAt.compareTo(p0.createdAt)
        }
    })

    // insulin
    var insulinOnBoard: Float = 0f
    var insulinOnBoardBasal: Float = 0f
    var insulinOnBoardBolus: Float = 0f
    var insulingBasalTotal: Float = 0f
    var insulinBolusTotal: Float = 0f
    var insulinCurrentBasal: Float = 0f
    var insulinBoluses: SortedSet<BolusInsulin> = sortedSetOf(dataComparitor)
    var insulinBasalChanges: SortedSet<BasalInsulinChange> = sortedSetOf(dataComparitor)

    // carbs
    var carbsOnBoard: Float = 0f
    var carbsTotal: Float = 0f
    var carbs: SortedSet<CarbIntake> = sortedSetOf(dataComparitor)

    // bgl
    var timeInRange: Float = 0f
    var meanBGL: Float = 0f
    var stdBGL: Float = 0f
    var bglReadings: SortedSet<BGLReading> = sortedSetOf(dataComparitor)

    var recentEvents: SortedSet<BaseDataClass> = sortedSetOf(dataComparitor)

    // store of listeners for changes in the display data
    val listeners = mutableListOf<() -> Unit>()

    fun newNotificationAvailable(nd: NotificationData) {
        // store previoud data
        previousBGLReading = bglReading
        previousBGLReadingTime = bglReadingTime

        // update current readings
        bglReading = nd.reading
        bglReadingTime = nd.time
        bglUnit = nd.unit
        bglDirectionImage = nd.image_drawable
        bglDiff = calculateDiff()

        // Update listeners something has changes
        updatedListeners()
        // queue up a fetch from diasend
    }

    fun updatedListeners() {
        listeners.forEach { it() }
    }

    fun calculateDiff(): String {
        val dec = DecimalFormat("+#,##0.0;-#")
        val readingDiff = bglReading - previousBGLReading
        val fiveMinutes = (bglReadingTime - previousBGLReadingTime) / (60000.0 * 5.0)
        val diff = readingDiff / fiveMinutes
        return "${dec.format(diff)} $bglUnit"
    }


    fun addPatientData(patientData: List<DiasendDatum>?) {
        if (patientData == null) {
            return
        }
        // add all to the set which will dedup and order them
        diasendData.addAll(patientData)

        // extract all the types
        for (d in patientData) {
            var ld = d.createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            when (d.type) {
                "insulin_bolus" -> {
                    insulinBoluses.add(BolusInsulin(d.totalValue, ld))
                }
                "carb" -> {
                    carbs.add(CarbIntake(d.value, ld))
                }
                "insulin_basal" -> {
                    insulinBasalChanges.add(BasalInsulinChange(d.value, ld))
                }
                "glucose" -> {
                    bglReadings.add(BGLReading(d.value, ld))
                }
                else -> {
                    Log.d(TAG, "UNKNOWN DIASEND TYPE ${d.type}")
                }
            }
        }

        // Compress Basal Changes (there are a ton of useless ones)
        var previousMinsAgo: Long = -1
        var previousValue: Float = -1f
        val removeElemets = mutableListOf<BasalInsulinChange>()
        for (re in insulinBasalChanges) {
            if (re.minsAgo() == previousMinsAgo || re.value == previousValue) {
                removeElemets.add(re)
                continue
            }
            previousMinsAgo = re.minsAgo()
            previousValue = re.value

        }
        for (re in removeElemets) {
            insulinBasalChanges.remove(re)
        }


        // end of compression
        println(insulinBoluses)
        println(insulinBasalChanges)
        println(carbs)
        println(bglReadings)

        insulinCurrentBasal = if (insulinBasalChanges.size > 0) insulinBasalChanges.first().value else 0.0f

        // calculate all the stats
        insulinOnBoard = 0f
        insulinOnBoardBasal = 0f
        insulinOnBoardBolus = 0f
        insulingBasalTotal = 0f
        insulinBolusTotal = 0f
        carbsOnBoard = 0f
        carbsTotal = 0f
        timeInRange = 0f
        meanBGL = 0f
        stdBGL = 0f

        recentEvents.clear()

        val insulinDuration = prefs.getString("insulin_duration", "0.0")!!.toFloat()
        val insulinOnset = prefs.getString("insulin_onset", "0.0")!!.toFloat()
        val insulinPeak = prefs.getString("insulin_peak", "0.0")!!.toFloat()

        val now = LocalDateTime.now()
        val midnight = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)
        val insulinDurationTime = now.minusMinutes((insulinDuration * 1.5).toLong())
        val glucoseRecentTime = now.minusMinutes(30)



        // Calculate
        for (d in insulinBoluses) {
            if (d.time > midnight) {
                insulinBolusTotal += d.value
            }
            if (d.time > insulinDurationTime) {
                val bolusesRemaingInsulin = calculateInsulinDecay(d.value, d.minsAgo(), insulinDuration)
                insulinOnBoardBolus += bolusesRemaingInsulin
                insulinOnBoard += bolusesRemaingInsulin
                recentEvents.add(d)
            }
        }

        // Basal insulin is much harder because it is changes in a curve where the integral is the total.
        for (d in insulinBasalChanges) {
            if (d.time > midnight) {
                insulingBasalTotal += d.value
            }
            if (d.time > insulinDurationTime) {
                val basalsRemaingInsulin = d.value // TODO make relateive to time
                insulinOnBoardBasal += basalsRemaingInsulin
                insulinOnBoard += basalsRemaingInsulin
                recentEvents.add(d)
            }
        }

        for (d in carbs) {

            if (d.time > midnight) {
                carbsTotal += d.value
            }
            if (d.time > insulinDurationTime) {
                val carbsWithDecay = calculateCarbDecay(d.value, d.minsAgo(), 120f) // TODO guess a decay rate for carbs and add to the total
                carbsOnBoard += carbsWithDecay
                recentEvents.add(d)
            }
        }

        var todayTotalReadings = 0f
        var todayTotalInrangeReadings = 0f

        for (d in bglReadings) {
            if (d.time > midnight) {
                todayTotalReadings += 1
                if (d.value >= 3.9 && d.value <= 10) {
                    todayTotalInrangeReadings += 1
                }
            }
            // TODO check if this reading is significatnly in front of the notification reader and write it out
        }
        timeInRange = todayTotalInrangeReadings/todayTotalReadings

        // OVERRIDES OF THE NOTIF DATA
        if (bglReadings.size > 0 &&  bglReading == 0f) {
            val first = bglReadings.first()
            bglReading = first.value
        }

        // update
        updatedListeners()
    }

    private fun calculateInsulinDecay(value: Float, mins: Long, dia: Float) : Float {
        // TODO starting with linear decay but clearly this is incorect
        if (mins > dia) {
            return 0f
        }
        val decay = (dia-mins)/dia
        val ret = decay * value
        Log.d(TAG, "Insulin Decay Calculation v $value, m $mins, d: $dia = $ret")
        return ret
    }

    private fun calculateCarbDecay(value: Float, mins: Long, decayTime : Float) : Float {
        // TODO starting with linear decay but clearly this is incorect
        if (mins > decayTime) {
            return 0f
        }
        val decay = (decayTime-mins)/decayTime
        val ret = decay * value
        Log.d(TAG, "Carb Decay Calculation v $value, m $mins, d: $decay = $ret")
        return ret
    }

}


