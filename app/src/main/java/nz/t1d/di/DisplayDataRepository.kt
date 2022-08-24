package nz.t1d.di

import android.graphics.drawable.Drawable
import nz.t1d.diasend.DiasendClient
import nz.t1d.diasend.DiasendDatum
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.Delegates



@Singleton
class DisplayDataRepository @Inject constructor() {


    //
    private var previousBGLReading: Float = 0f
    private var previousBGLReadingTime: Long = 0

    // Data to be accessed
    var bglReading: Float = 0f
    var bglDiff: String = ""
    var bglReadingTime: Long = 0
    var bglDirectionImage : Drawable? = null
    var bglUnit: String = "mmol/L"

    // Diasend data ordered so that newest is at the front of the set
    var diasendData : SortedSet<DiasendDatum> = sortedSetOf(object : Comparator<DiasendDatum>{
        override fun compare(p0: DiasendDatum?, p1: DiasendDatum?): Int {
            if(p1 == null || p0 == null) {
                return 0
            }
            return p1.createdAt.compareTo(p0.createdAt)
        }
    })

    var insulinOnBoard: Float = 0f
    var insulinOnBoardBasal: Float = 0f
    var insulinOnBoardBolus: Float = 0f


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


        // extract all the stats
        insulinOnBoard = 0f
        for(d in diasendData) {
            println(d)
            when(d.type) {
                "insulin_bolus" -> {insulinOnBoard += d.totalValue}
            }
        }
        println("BOLUS $insulinOnBoard")
        // extract recent bolus, basal and carbs listings
        // decide whether to override the notif stats (if non existant or too old)
        // redraw the listeners by calling updateListeners
        updatedListeners()
    }
}


