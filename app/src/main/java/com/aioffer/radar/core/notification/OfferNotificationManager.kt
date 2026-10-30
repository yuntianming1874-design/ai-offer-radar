package com.aioffer.radar.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aioffer.radar.MainActivity
import com.aioffer.radar.R
import com.aioffer.radar.core.database.RadarDatabase
import com.aioffer.radar.core.model.NotificationEvent
import com.aioffer.radar.core.model.NotificationEventStatus
import com.aioffer.radar.core.model.NotificationEventType
import com.aioffer.radar.navigation.AppRoutes
import java.time.Clock

object OfferNotificationChannels {
    const val PRICE_CHANGES = "price_changes"

    fun create(context: Context) {
        val channel = NotificationChannel(
            PRICE_CHANGES,
            context.getString(R.string.notification_channel_price_changes),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_price_changes_description)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}

interface NotificationSender {
    fun canNotify(): Boolean
    fun wasDelivered(eventId: String): Boolean
    fun send(event: NotificationEvent, productName: String)
}

class AndroidNotificationSender(
    private val context: Context,
    private val channelId: String = OfferNotificationChannels.PRICE_CHANGES
) : NotificationSender {
    private val manager = NotificationManagerCompat.from(context)

    override fun canNotify(): Boolean = canDeliverIgnoringPermission() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)

    internal fun canDeliverIgnoringPermission(): Boolean =
        manager.areNotificationsEnabled() && !isNotificationChannelBlocked()

    internal fun isNotificationChannelBlocked(): Boolean {
        val systemManager = context.getSystemService(NotificationManager::class.java)
        return systemManager.getNotificationChannel(channelId)?.importance ==
            NotificationManager.IMPORTANCE_NONE
    }

    override fun wasDelivered(eventId: String): Boolean =
        context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.id == NOTIFICATION_ID && it.tag == eventId
        }

    @SuppressLint("MissingPermission")
    override fun send(event: NotificationEvent, productName: String) {
        val deepLink = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("aioffer://product/${Uri.encode(event.productId)}"),
            context,
            MainActivity::class.java
        ).apply {
            putExtra(AppRoutes.PRODUCT_ID, event.productId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            event.eventId.hashCode(),
            deepLink,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_product_placeholder)
            .setContentTitle(productName)
            .setContentText(context.getString(event.eventType.messageResource()))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(event.eventType.messageResource()))
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(event.eventId, NOTIFICATION_ID, notification)
    }

    private fun NotificationEventType.messageResource(): Int = when (this) {
        NotificationEventType.NEW_OFFER -> R.string.notification_new_offer
        NotificationEventType.PRICE_INCREASE -> R.string.notification_price_increase
        NotificationEventType.PRICE_DECREASE -> R.string.notification_price_decrease
        NotificationEventType.OFFER_ACTIVATED -> R.string.notification_offer_activated
        NotificationEventType.OFFER_EXPIRED -> R.string.notification_offer_expired
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}

class NotificationDispatcher(
    private val database: RadarDatabase,
    private val sender: NotificationSender,
    private val clock: Clock
) {
    suspend fun dispatchPending(): Int {
        var sentCount = 0
        val pendingEvents = database.notificationEventDao().getPending().map { it.toDomain() }
        for (event in pendingEvents) {
            if (!database.userSubscriptionDao().isSubscribed(event.productId)) {
                database.notificationEventDao().updateStatus(
                    event.eventId,
                    NotificationEventStatus.CANCELLED.name,
                    null
                )
                continue
            }
            if (sender.wasDelivered(event.eventId)) {
                markSent(event.eventId)
                sentCount++
                continue
            }
            if (!sender.canNotify()) break
            val product = database.productDao().getById(event.productId) ?: continue
            sender.send(event, product.name)
            markSent(event.eventId)
            sentCount++
        }
        return sentCount
    }

    private suspend fun markSent(eventId: String) {
        database.notificationEventDao().updateStatus(
            eventId,
            NotificationEventStatus.SENT.name,
            clock.instant().toEpochMilli()
        )
    }
}

private fun com.aioffer.radar.core.database.NotificationEventEntity.toDomain() = NotificationEvent(
    eventId = eventId,
    productId = productId,
    offerId = offerId,
    eventType = NotificationEventType.valueOf(eventType),
    createdAt = java.time.Instant.ofEpochMilli(createdAtEpochMillis),
    status = NotificationEventStatus.valueOf(status),
    sentAt = sentAtEpochMillis?.let(java.time.Instant::ofEpochMilli)
)
