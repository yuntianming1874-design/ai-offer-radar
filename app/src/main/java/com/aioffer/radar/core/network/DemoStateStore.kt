package com.aioffer.radar.core.network

/**
 * The mutable state of the deterministic demo feed.
 *
 * A persistent [DemoStateStore] lets a new process (for example a WorkManager run) continue
 * from the scenario that was last selected instead of silently resetting to baseline prices.
 */
data class DemoState(
    val scenario: DemoScenario = DemoScenario.INITIAL_SYNC,
    val kimiActualAmountMinor: Long = 10_000,
    val kimiOriginalAmountMinor: Long = 12_000,
    val newOfferAdded: Boolean = false
)

interface DemoStateStore {
    suspend fun load(): DemoState?

    suspend fun save(state: DemoState)
}

class InMemoryDemoStateStore(
    initialState: DemoState? = null
) : DemoStateStore {
    @Volatile
    private var state: DemoState? = initialState

    override suspend fun load(): DemoState? = state

    override suspend fun save(state: DemoState) {
        this.state = state
    }
}
