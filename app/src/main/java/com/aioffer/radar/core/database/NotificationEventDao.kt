package com.aioffer.radar.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(event: NotificationEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIfAbsent(events: List<NotificationEventEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM notification_events WHERE eventId = :eventId)")
    suspend fun exists(eventId: String): Boolean

    @Query("SELECT * FROM notification_events WHERE eventId = :eventId")
    suspend fun getById(eventId: String): NotificationEventEntity?

    @Query("SELECT * FROM notification_events")
    suspend fun getAll(): List<NotificationEventEntity>

    @Query("SELECT * FROM notification_events WHERE status = 'PENDING' ORDER BY createdAtEpochMillis")
    suspend fun getPending(): List<NotificationEventEntity>

    @Query("SELECT COUNT(*) FROM notification_events")
    suspend fun count(): Int

    @Query(
        """
        UPDATE notification_events
        SET status = :status, sentAtEpochMillis = :sentAtEpochMillis
        WHERE eventId = :eventId
        """
    )
    suspend fun updateStatus(eventId: String, status: String, sentAtEpochMillis: Long?)
}
