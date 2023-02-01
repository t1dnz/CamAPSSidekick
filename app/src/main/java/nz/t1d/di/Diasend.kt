package nz.t1d.diasend

import android.content.Context
import nz.t1d.clients.diasend.DiasendClient
import nz.t1d.datamodels.PatientData

import androidx.preference.PreferenceManager

import dagger.hilt.android.qualifiers.ApplicationContext


import java.time.LocalDateTime

import java.util.*

import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DiasendClient @Inject constructor(@ApplicationContext context: Context) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    private val diasendClient: DiasendClient by lazy {
        val username = prefs.getString("diasend_username", "")!!
        val password = prefs.getString("diasend_password", "")!!

        DiasendClient(diasend_username=username, diasend_password=password)
    }


    suspend fun getPatientData(date_from: LocalDateTime, date_to: LocalDateTime): PatientData {
        return diasendClient.getPatientData(date_from, date_to)
    }
}