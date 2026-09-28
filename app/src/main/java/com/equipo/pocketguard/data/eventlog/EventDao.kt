package com.equipo.pocketguard.data.eventlog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    /** Del más reciente al más antiguo. */
    @Query("SELECT * FROM events ORDER BY timestampMs DESC, id DESC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("DELETE FROM events")
    suspend fun clear()

    /** Conserva solo los [keep] eventos más recientes. */
    @Query("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
