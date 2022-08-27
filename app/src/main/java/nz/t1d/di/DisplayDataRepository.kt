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
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.truncate


interface BaseDataClass {
    val time: LocalDateTime
    var value: Float

    fun minsAgo(): Long {
        return Duration.between(time, LocalDateTime.now()).toMinutes()
    }

    fun minsAgoString(): String {
        val mins = minsAgo()
        val hours = truncate(mins / 60.0f).toLong()

        if (hours != 0L) {
            val hourMins = mins - (60 * hours)
            return "${hours}h ${hourMins}m ago"
        }
        return "${mins}m ago"
    }
}

data class BasalInsulinChange(
    override var value: Float,
    override var time: LocalDateTime,
) : BaseDataClass

data class BolusInsulin(
    override var value: Float,
    override var time: LocalDateTime,
    var onset: Float,
    var peak: Float,
    var dia: Float,
) : BaseDataClass {
    private val TAG = "BolusInsulin"
    fun valueAfterDecay(): Float {
        val mins = minsAgo()
        // TODO starting with linear decay but clearly this is incorect
        if (mins > dia) {
            return 0f
        }
        val decayRate = (dia - mins) / dia
        val ret = decayRate * value
        return ret
    }

}

data class CarbIntake(
    override var value: Float,
    override var time: LocalDateTime,
    var decay: Float,
) : BaseDataClass {
    private val TAG = "CarbIntake"
    fun valueAfterDecay(): Float {
        val mins = minsAgo()
        // TODO starting with linear decay but clearly this is incorect
        if (mins > decay) {
            return 0f
        }
        val decayRate = (decay - mins) / decay
        val ret = decayRate * value
        return ret
    }
}

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

    var insulinDuration = prefs.getString("insulin_duration", "0.0")!!.toFloat()
    var insulinOnset = prefs.getString("insulin_onset", "0.0")!!.toFloat()
    var insulinPeak = prefs.getString("insulin_peak", "0.0")!!.toFloat()

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


    // insulin
    var insulinOnBoard: Float = 0f
    var insulinOnBoardBasal: Float = 0f
    var insulinOnBoardBolus: Float = 0f
    var insulingBasalTotal: Float = 0f
    var insulinBolusTotal: Float = 0f
    var insulinCurrentBasal: Float = 0f
    var insulinBoluses: SortedSet<BolusInsulin> = sortedSetOf(dataComparitor)
    var insulinBasalChanges: SortedSet<BasalInsulinChange> = sortedSetOf(dataComparitor)
    var insulinBasalBoluses: SortedSet<BolusInsulin> = sortedSetOf(dataComparitor)

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

        // Calculate a bunch of usaeful stuff here
        insulinDuration = prefs.getString("insulin_duration", "0.0")!!.toFloat()
        insulinOnset = prefs.getString("insulin_onset", "0.0")!!.toFloat()
        insulinPeak = prefs.getString("insulin_peak", "0.0")!!.toFloat()
        val carbDuration = 120f

        // Some useful dates
        val now = LocalDateTime.now()
        val midnight = LocalDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT)
        val insulinDurationTime = now.minusMinutes((insulinDuration * 1.5).toLong())
        val midnightMinus = midnight.minusMinutes((insulinDuration * 1.5).toLong())

        // extract all the types
        for (d in patientData) {
            var ld = d.createdAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            when (d.type) {
                "insulin_bolus" -> {
                    insulinBoluses.add(BolusInsulin(d.totalValue, ld, insulinOnset, insulinPeak, insulinDuration))
                }
                "carb" -> {
                    carbs.add(CarbIntake(d.value, ld, carbDuration))
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

        // Since this program can run for days to make sure we are discarding any really old data we remove it from the sets here
        insulinBoluses = insulinBoluses.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)
        carbs = carbs.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)
        insulinBasalChanges = insulinBasalChanges.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)
        bglReadings = bglReadings.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)

        // Take the basal changes and calculate the equivilant boluses
        processBasalChanges(midnightMinus)


        // end of compression

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


        // BOLUSES
        for (d in insulinBoluses) {
            if (d.time > midnight) {
                insulinBolusTotal += d.value
            }

            val bolusesRemaingInsulin = d.valueAfterDecay()
            insulinOnBoardBolus += bolusesRemaingInsulin
            insulinOnBoard += bolusesRemaingInsulin

            if (d.time > insulinDurationTime) {
                recentEvents.add(d)
            }
        }


        // Basal insulin is much harder because it is changes in a curve where the integral is the total.

        for (d in insulinBasalBoluses) {
            if (d.time > midnight) {
                insulingBasalTotal += d.value
            }

            val bolusesRemaingInsulin = d.valueAfterDecay()
            insulinOnBoardBasal += bolusesRemaingInsulin
            insulinOnBoard += bolusesRemaingInsulin
        }


        // CARBS
        for (d in carbs) {
            if (d.time > insulinDurationTime) {
                recentEvents.add(d)
            }
        }

        var todayTotalReadings = 0f
        var todayTotalInrangeReadings = 0f
        var sumTotalReadings = 0f
        for (d in bglReadings) {
            if (d.time > midnight) {
                todayTotalReadings += 1
                sumTotalReadings += d.value
                if (d.value >= 3.9 && d.value <= 10) {
                    todayTotalInrangeReadings += 1
                }
            }
        }

        timeInRange = todayTotalInrangeReadings / todayTotalReadings
        meanBGL = sumTotalReadings / todayTotalReadings
        var tmpSTD: Double = 0.0;
        for (d in bglReadings) {
            if (d.time > midnight) {
                tmpSTD += Math.pow((d.value - meanBGL).toDouble(), 2.0)
            }
        }
        stdBGL = Math.sqrt((tmpSTD / todayTotalReadings).toDouble()).toFloat()

        // OVERRIDES OF THE NOTIF DATA
        if (bglReadings.size > 0 && bglReading == 0f) {
            val first = bglReadings.first()
            bglReading = first.value
            bglReadingTime = first.time.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000
        }

        // update
        updatedListeners()
    }


    private fun processBasalChanges(midnightMinus: LocalDateTime) {
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

        // This may be a stupid way to do this
        // We want to calculate IOB and total basal but for that we need to turn the basalratechanges into "how much insulin was delivered?"
        // For that what we do is calculate

        // Delete All previous items (this may be overkill but I think
        insulinBasalBoluses.clear()

        // Get now every 4 minutes calculate the basal rate, add that value as a bolusChange to insulinBasalBoluses
        var basalTime = LocalDateTime.now()
        var insulinBasalChangesList = ArrayDeque(insulinBasalChanges.toMutableList())
        var currentBasal = insulinBasalChangesList.first()
        var isEmpty = insulinBasalChangesList.isEmpty()
        while (basalTime > midnightMinus) {
            basalTime = basalTime.minusMinutes(4)
            while (basalTime < currentBasal.time) {
                if (insulinBasalChangesList.isEmpty()) {
                    isEmpty = true
                    break
                } else {
                    currentBasal = insulinBasalChangesList.pop()
                }
            }
            if (isEmpty) {
                Log.d(TAG, "processBasalChanges no finished at time $basalTime")
                break
            }
            // divide by 15 to make the rate per 4 mins from per hour
            insulinBasalBoluses.add(BolusInsulin(currentBasal.value / 15.0f, basalTime, insulinOnset, insulinPeak, insulinDuration))
        }
    }
//    private fun calculateBasalTotal(midnight : LocalDateTime): Float {
//        for (d in insulinBasalChanges) {
//            if (d.time > midnight) {
//                insulingBasalTotal += d.value
//            }
//        }
//        return 0.0f
//    }
//    private fun calculateIOBBasal(midnight : LocalDateTime): Float  {
//        // Not sure if this is a good idea, but the idea is to treat every minute as a bolus at 1/60
//
//        for (d in insulinBasalChanges) {
//            if (d.time > midnight) {
//                insulingBasalTotal += d.value
//            }
//            if (d.time > d.dia) {
//                val basalsRemaingInsulin = d.value // TODO make relateive to time
//                insulinOnBoardBasal += basalsRemaingInsulin
//                insulinOnBoard += basalsRemaingInsulin
//            }
//        }
//    }

}


