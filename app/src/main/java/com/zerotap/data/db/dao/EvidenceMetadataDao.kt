package com.zerotap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zerotap.data.db.entity.EvidenceMetadataEntity

@Dao
interface EvidenceMetadataDao {
    @Query("SELECT * FROM evidence_metadata WHERE incidentId = :incidentId")
    suspend fun getByIncidentId(incidentId: String): EvidenceMetadataEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: EvidenceMetadataEntity)
}
