package com.aioffer.radar.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidNotificationSenderTest {
    @Test
    fun channelWithImportanceNone_isRecognizedAsBlocked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = "blocked_channel_test"
        try {
            manager.deleteNotificationChannel(channelId)
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Blocked channel test",
                    NotificationManager.IMPORTANCE_NONE
                )
            )
            assertTrue(
                AndroidNotificationSender(context, channelId).isNotificationChannelBlocked()
            )
        } finally {
            manager.deleteNotificationChannel(channelId)
        }
    }
}
