package nz.t1d.di

import android.graphics.drawable.Drawable
import nz.t1d.diasend.DiasendClient
import java.text.DecimalFormat
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.properties.Delegates


@Singleton
class DisplayDataRepository @Inject constructor(
    val diasendClient: DiasendClient
) {

    val listeners = mutableListOf<() -> Unit>()

    var recentNotificationData: NotificationData by Delegates.observable(
        NotificationData(
            0f,
            "mmoll",
            0,
            null
        )
    ) { _, _, _ -> update() }
    var previousNotificationData: NotificationData = NotificationData(0f, "mmoll", 0, null)

    fun update() {
        listeners.forEach { it() }
    }

    fun getUnit(): String {
        return recentNotificationData.unit
    }

    fun getBGLReading(): Float {
        return recentNotificationData.reading
    }

    fun getLastBGLReadingTime(): Long {
        return recentNotificationData.time
    }

    fun getBGLDiff(): String {
        val nd = recentNotificationData
        val previousND = previousNotificationData

        val dec = DecimalFormat("+#,##0.0;-#")
        val readingDiff = nd.reading - previousND.reading
        val fiveMinutes = (nd.time - previousND.time) / (60000.0 * 5.0)
        val diff = readingDiff / fiveMinutes
        return "${dec.format(diff)} ${nd.unit}"
    }

    fun getImageToDraw(): Drawable? {
        return recentNotificationData.image_drawable
    }
}


