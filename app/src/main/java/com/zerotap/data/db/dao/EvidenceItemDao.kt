package com.zerotap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zerotap.data.db.entity.EvidenceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EvidenceItemDao {

    @Query("SELECT * FROM evidence_items ORDER BY timestamp DESC")
    fun getAll(): Flow<List<EvidenceItemEntity>>

    @Query("SELECT * FROM evidence_items WHERE incidentId = :incidentId ORDER BY timestamp DESC")
    fun getByIncident(incidentId: String): Flow<List<EvidenceItemEntity>>

    @Query("SELECT * FROM evidence_items WHERE id = :id")
    suspend fun getById(id: String): EvidenceItemEntity?

    @Query("SELECT * FROM evidence_items WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getPendingSync(): List<EvidenceItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: EvidenceItemEntity)

    @Query("UPDATE evidence_items SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("DELETE FROM evidence_items WHERE id = :id")
    suspend fun delete(id: String)
}
