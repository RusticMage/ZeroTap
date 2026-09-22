package com.zerotap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zerotap.data.db.entity.IncidentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incidents ORDER BY createdAt DESC")
    fun getAllIncidents(): Flow<List<IncidentEntity>>
    
    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun getIncidentById(id: String): IncidentEntity?
    
    @Query("SELECT * FROM incidents WHERE status IN ('DETECTED', 'ACTIVE', 'ALERTING') ORDER BY createdAt DESC LIMIT 1")
    fun getActiveIncident(): Flow<IncidentEntity?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(incident: IncidentEntity)
    
    @Update
    suspend fun update(incident: IncidentEntity)
    
    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteById(id: String)
}
