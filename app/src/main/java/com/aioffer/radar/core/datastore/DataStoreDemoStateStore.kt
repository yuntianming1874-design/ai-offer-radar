package com.aioffer.radar.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aioffer.radar.core.network.DemoScenario
import com.aioffer.radar.core.network.DemoState
import com.aioffer.radar.core.network.DemoStateStore
import kotlinx.coroutines.flow.first

private const val DEMO_STATE_PREFERENCES_NAME = "demo_state"

private val Context.demoStateDataStore by preferencesDataStore(name = DEMO_STATE_PREFERENCES_NAME)

fun createDemoStateStore(context: Context): DemoStateStore =
    DataStoreDemoStateStore(context.applicationContext.demoStateDataStore)

/**
 * Persists the demo scenario and the demo prices in the app's private DataStore.
 *
 * This file is only used when the demo data source is active. Real sources never read or
 * write it, so switching sources cannot relabel demo history as official history.
 */
class DataStoreDemoStateStore(
    private val dataStore: DataStore<Preferences>
) : DemoStateStore {
    override suspend fun load(): DemoState? {
        val preferences = dataStore.data.first()
        val scenarioName = preferences[KEY_SCENARIO] ?: return null
        val scenario = runCatching { DemoScenario.valueOf(scenarioName) }.getOrNull() ?: return null

        return DemoState(
            scenario = scenario,
            kimiActualAmountMinor = preferences[KEY_KIMI_ACTUAL_AMOUNT] ?: 10_000L,
            kimiOriginalAmountMinor = preferences[KEY_KIMI_ORIGINAL_AMOUNT] ?: 12_000L,
            newOfferAdded = preferences[KEY_NEW_OFFER_ADDED] ?: false
        )
    }

    override suspend fun save(state: DemoState) {
        dataStore.edit { preferences ->
            preferences[KEY_SCENARIO] = state.scenario.name
            preferences[KEY_KIMI_ACTUAL_AMOUNT] = state.kimiActualAmountMinor
            preferences[KEY_KIMI_ORIGINAL_AMOUNT] = state.kimiOriginalAmountMinor
            preferences[KEY_NEW_OFFER_ADDED] = state.newOfferAdded
        }
    }

    private companion object {
        val KEY_SCENARIO = stringPreferencesKey("scenario")
        val KEY_KIMI_ACTUAL_AMOUNT = longPreferencesKey("kimi_actual_amount_minor")
        val KEY_KIMI_ORIGINAL_AMOUNT = longPreferencesKey("kimi_original_amount_minor")
        val KEY_NEW_OFFER_ADDED = booleanPreferencesKey("new_offer_added")
    }
}
