package nz.t1d.di

import nz.t1d.diasend.DiasendClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayDataRepository @Inject constructor(
    private val diasendClient: DiasendClient
) {
    fun latestBGL() {
        // TODO fetch bgl from camAPS app and diasend pick the best one
    }
}
