package com.aioffer.radar.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.aioffer.radar.core.network.DemoScenario
import com.aioffer.radar.core.network.DemoState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreDemoStateStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun savedState_isReloadedByANewStoreInstance() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val file = File(temporaryFolder.root, "demo_state.preferences_pb")
            val dataStore = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
            val state = DemoState(
                scenario = DemoScenario.PRICE_DECREASE,
                kimiActualAmountMinor = 8_000,
                kimiOriginalAmountMinor = 12_000,
                newOfferAdded = true
            )

            DataStoreDemoStateStore(dataStore).save(state)

            assertEquals(state, DataStoreDemoStateStore(dataStore).load())
        } finally {
            scope.cancel()
        }
    }
}
