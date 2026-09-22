package com.zerotap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zerotap.data.db.entity.AlertAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertAttemptDao {
    @Query("SELECT * FROM alert_attempts WHERE incidentId = :incidentId ORDER BY timestamp DESC")
    fun getByIncidentId(incidentId: String): Flow<List<AlertAttemptEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: AlertAttemptEntity)
}
