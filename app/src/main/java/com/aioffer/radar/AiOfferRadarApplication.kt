package com.aioffer.radar

import android.app.Application

class AiOfferRadarApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // AppContainer applies the persisted auto-sync preference. Scheduling
        // here unconditionally would re-enable periodic sync after the user
        // turned it off.
        container = AppContainer(this)
    }
}
