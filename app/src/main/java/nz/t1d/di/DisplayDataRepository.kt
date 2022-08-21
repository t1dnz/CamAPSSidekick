package nz.t1d.di

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayDataRepository @Inject constructor() {
    fun latestBGL() {
        // TODO fetch bgl from camAPS app and diasend pick the best one
    }
}
