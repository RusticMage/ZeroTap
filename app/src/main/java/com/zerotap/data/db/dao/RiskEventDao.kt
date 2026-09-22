package com.zerotap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zerotap.data.db.entity.RiskEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RiskEventDao {
    @Query("SELECT * FROM risk_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 50): Flow<List<RiskEventEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: RiskEventEntity)
    
    @Query("DELETE FROM risk_events WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)
}
