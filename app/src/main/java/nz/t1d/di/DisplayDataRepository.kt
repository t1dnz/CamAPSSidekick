package nz.t1d.di

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import nz.t1d.camapsdisplay.R
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

    fun secondsAgo(): Long {
        return Duration.between(time, LocalDateTime.now()).toMillis() / 1000L // toSeconds not supported yet
    }


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
        // This is taken from the Bilinear algorithm here https://openaps.readthedocs.io/en/latest/docs/While%20You%20Wait%20For%20Gear/understanding-insulin-on-board-calculations.html
        // actual code here https://github.com/openaps/oref0/blob/88cf032aa74ff25f69464a7d9cd601ee3940c0b3/lib/iob/calculate.js#L36
        // basically insulin rate increases linearly from start to peak, then decreases linearly from peak to dia
        // this makes a triangle which we can calculate the area left which will be the total remaining insulin
        // TODO: maybe use the exponential insulin curves also described on the page above
        // TODO: take onset into consideration by squashing triangle a bit

        val minsAgo = minsAgo().toFloat()
        if (minsAgo >= dia) {
            return 0f
        }

        // percent of insulin used is 100 so we make a triangle whose area is 100
        // 1/2 w * h  = 100, w = dia, solve for h. so h = (100*2)/dia
        val height = 200f / dia // for 180 it is about 1.11

        // Now we can divide that into two right angle triangels, first the one going to peak the second coming down
        // Because it is linear up and linear down all we need to know is the interior angles of the two triangles to find the area
        val areaOfFirstTrialngle = (peak * height) / 2 // 1/2 * w * h
        val areaOfSecondTrialngle = ((dia - peak) * height) / 2 // 1/2 * w * h

        val slopeFirstTriangle = height / peak
        val slopeSecondTriangle = height / (dia - peak)


        var percentOfInsulinUsed = 0f
        var pastPeak = false
        var triangleArea = 0f
        if (minsAgo < peak) {
            pastPeak = false
            triangleArea = areaOfTriangleFromSlope(slopeFirstTriangle, minsAgo)
            percentOfInsulinUsed = triangleArea

        } else {
            pastPeak = true

            // The second triangle is at the back, so need to minus it from the total to find actual area
            triangleArea = areaOfTriangleFromSlope(slopeSecondTriangle, dia - minsAgo)

            percentOfInsulinUsed = areaOfFirstTrialngle + (areaOfSecondTrialngle - triangleArea)
        }

        val ret = (1 - (percentOfInsulinUsed / 100)) * value
        return ret
    }

    private fun areaOfTriangleFromSlope(slope: Float, width: Float): Float {
        val height = slope * width
        return (height * width) / 2f
    }
}

data class CarbIntake(
    override var value: Float,
    override var time: LocalDateTime,
    var decay: Float,
) : BaseDataClass


enum class DATA_SOURCE{CAMAPS_NOTIF, DIASEND}

data class BGLReading(
    override var value: Float,
    override var time: LocalDateTime,
    var bglUnit: String = "mmol/L",
) : BaseDataClass {
    private val TAG = "BGLReading"
    var source: DATA_SOURCE = DATA_SOURCE.DIASEND

    // reference to previous reading making this kind of a
    var previousReading: BGLReading? = null

    fun calculateDiff(): Float {
        previousReading?.let { pr ->
            val duration = Duration.between(pr.time, time).toMillis() / 1000
            // Calculate rate per min then multiply by 5
            val rate = (value - pr.value) / duration
            return rate * 300
        }
        return 0f
    }


    // Return the BGL reading we trust more to be correct
    fun lessTruthy(that: BGLReading) : BGLReading {
        if (source == DATA_SOURCE.DIASEND) {
            return that
        }
        if (that.source == DATA_SOURCE.DIASEND) {
            return this
        }

        // At this point they must both be notifs so we just return the older one
        if (time > that.time) {
            return this
        }
        return that
    }

    fun diffString(unit: Boolean = true): String {
        var  diff = calculateDiff()
        val dec = DecimalFormat("+#,##0.0;-#")
        if (unit) {
            return "${dec.format(diff)} $bglUnit"
        }
        return dec.format(diff)
    }

    fun directionImageId(): Int? {
        var  diff = calculateDiff()
        if (diff == null) {
            return null
        }
        when {
            diff < -1 -> {
                return R.drawable.ic_down_arrow
            }
            diff < -0.2 -> {
                return R.drawable.ic_downish_arrow
            }
            diff > 1 -> {
                return R.drawable.ic_up_arrow
            }
            diff > 0.2 -> {
                return R.drawable.ic_upish_arrow
            }
        }
        return R.drawable.ic_side_arrow
    }
}


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
        val reading = BGLReading(nd.reading, nd.time, nd.unit)
        reading.source = DATA_SOURCE.CAMAPS_NOTIF
        bglReadings.add(reading)
        processBGLReadings()

        // Update listeners something has changes
        updatedListeners()
    }

    fun updatedListeners() {
        listeners.forEach { it() }
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
        val insulinDurationTime = now.minusMinutes((insulinDuration * 1.2).toLong())
        val carbDurationTime = now.minusMinutes((120).toLong())
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
        processBGLReadings()

        // TODO process and join bolus and carbs into a single item

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
        if (!insulinBoluses.isEmpty()) recentEvents.add(insulinBoluses.first()) // always add last bolus
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
        if(!carbs.isEmpty()) recentEvents.add(carbs.first()) // always add last Carb
        for (d in carbs) {
            if (d.time > carbDurationTime) {
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

        // update
        updatedListeners()
    }


    private fun processBGLReadings() {
        // Because we can get notifications from multiple locations we can have ver similar duplicates
        // we remove all readings if they are within 1 minute of each other
        // keeping the diasend reading as priority because it is more truthy

        val bglList = bglReadings.toList()
        for (i in bglList.indices) {
            val d1 = bglList[i]

            // already been removed, skip
            if (!bglReadings.contains(d1)) {
                continue
            }

            // Find next value that will return a diff
            for (j in (i + 1)..(bglList.size - 1)) {
                val d2 = bglList[j]

                val secondsDuration = Duration.between(d2.time, d1.time).toMillis() / 1000
                // if the difference in time between readings is less than 60 seconds
                if (secondsDuration < 60) {
                    var notTruthy = d1.lessTruthy(d2)
                    bglReadings.remove(notTruthy)
                    continue
                }
            }
        }

        // assign the previous reading to each of the bglreadings so they can self calculate diff and such
        var futureReading: BGLReading? = null
        for (d in bglReadings) {
            if (futureReading != null) {
                futureReading.previousReading = d
            }
            futureReading = d
        }
    }

    private fun processBasalChanges(midnightMinus: LocalDateTime) {
        if (insulinBasalChanges.isEmpty()) {
            return
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

}


