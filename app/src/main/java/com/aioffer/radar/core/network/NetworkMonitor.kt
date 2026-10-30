package com.aioffer.radar.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}

class AndroidNetworkMonitor(context: Context) : NetworkMonitor {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(hasOnlineNetwork())
            }

            override fun onLost(network: Network) {
                trySend(hasOnlineNetwork())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(hasOnlineNetwork())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }
        connectivityManager.registerDefaultNetworkCallback(callback)
        trySend(hasOnlineNetwork())
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun hasOnlineNetwork(): Boolean {
        val capabilities = connectivityManager.activeNetwork?.let(connectivityManager::getNetworkCapabilities)
            ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
