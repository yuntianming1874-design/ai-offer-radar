package com.aioffer.radar.core.push

data class PushNotificationMessage(
    val title: String? = null,
    val body: String? = null,
    val productId: String? = null,
    val data: Map<String, String> = emptyMap()
)

interface PushNotificationService {
    suspend fun onNewToken(token: String)
    suspend fun onMessage(message: PushNotificationMessage)
}

class NoOpPushNotificationService : PushNotificationService {
    override suspend fun onNewToken(token: String) = Unit
    override suspend fun onMessage(message: PushNotificationMessage) = Unit
}
