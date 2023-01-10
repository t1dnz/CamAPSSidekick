package nz.t1d.di

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import dagger.hilt.android.qualifiers.ApplicationContext
import nz.t1d.datamodels.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

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

    var insulinBoluses: SortedSet<BolusInsulin> = sortedSetOf(dataComparitor)
    var insulinBasalChanges: SortedSet<BasalInsulinChange> = sortedSetOf(dataComparitor)
    var bglReadings: SortedSet<BGLReading> = sortedSetOf(dataComparitor)
    var carbs: SortedSet<CarbIntake> = sortedSetOf(dataComparitor)

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
    var insulinBasalBoluses: SortedSet<BolusInsulin> = sortedSetOf(dataComparitor)

    // carbs
    var carbsOnBoard: Float = 0f
    var carbsTotal: Float = 0f

    // bgl
    var timeInRange: Float = 0f
    var meanBGL: Float = 0f
    var stdBGL: Float = 0f


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


    fun addPatientData(patientData: DataCollection) {
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
        val carbDurationTime = now.minusMinutes((carbDuration).toLong())
        val midnightMinus = midnight.minusMinutes((insulinDuration * 1.5).toLong())

        // Join all the data together
        insulinBoluses.addAll(patientData.insulinBoluses)
        carbs.addAll(patientData.carbs)
        insulinBasalChanges.addAll(patientData.insulinBasalChanges)
        bglReadings.addAll(patientData.bglReadings)


        // Since this program can run for days to make sure we are discarding any really old data we remove it from the sets here
        insulinBoluses = insulinBoluses.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)
        carbs = carbs.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)
        insulinBasalChanges = insulinBasalChanges.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)
        bglReadings = bglReadings.filter { d -> d.time > midnightMinus }.toSortedSet(dataComparitor)

        // Take the basal changes and calculate the equivilant boluses
        processBasalChanges(midnightMinus)
        processBGLReadings()
        joinTogetherBolusInfo()
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

            val bolusesRemaingInsulin = d.valueAfterDecay(insulinOnset, insulinPeak, insulinDuration)
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

            val bolusesRemaingInsulin = d.valueAfterDecay(insulinOnset, insulinPeak, insulinDuration)
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
            insulinBasalBoluses.add(BolusInsulin(currentBasal.value / 15.0f, basalTime))
        }
    }

    private fun joinTogetherBolusInfo() {
        // We want to find events in a time range
        // We want to join the boluses with carbs
        for (bolus in insulinBoluses) {
            val closeCarbs = findEvents(bolus.time, bolus.time.plusMinutes(2), carbs)
            if (!closeCarbs.isEmpty()) {
                bolus.carbIntake = closeCarbs.first()
                carbs.remove(bolus.carbIntake)
            }
        }
    }

    private fun <T: BaseDataClass> findEvents(from: LocalDateTime, to: LocalDateTime, setOf : SortedSet<T>) : List<T>{
        val list = mutableListOf<T>()
        for (d in setOf) {
            if (d.time < from) {
                return list
            }
            if (d.time > to) {
                continue
            }
            list.add(d)
        }
        return list
    }
}


