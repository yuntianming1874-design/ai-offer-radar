package com.aioffer.radar.core.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfferSyncWorkerTest {
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        )
        workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(OfferSyncScheduler.UNIQUE_WORK_NAME)
    }

    @Test
    fun periodicWork_hasNetworkBackoffAndSingleRegistration() {
        val request = OfferSyncScheduler.createRequest()
        OfferSyncScheduler.schedule(InstrumentationRegistry.getInstrumentation().targetContext)
        val first = workManager.getWorkInfosForUniqueWork(OfferSyncScheduler.UNIQUE_WORK_NAME)
            .get().single()

        OfferSyncScheduler.schedule(InstrumentationRegistry.getInstrumentation().targetContext)
        val all = workManager.getWorkInfosForUniqueWork(OfferSyncScheduler.UNIQUE_WORK_NAME).get()

        assertEquals(1, all.size)
        assertEquals(first.id, all.single().id)
        assertEquals(NetworkType.CONNECTED, first.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, request.workSpec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30), request.workSpec.backoffDelayDuration)
        assertEquals(TimeUnit.MINUTES.toMillis(15), first.periodicityInfo?.repeatIntervalMillis)
    }
}
