package com.zerotap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zerotap.data.db.entity.TrustedContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY isPrimary DESC, name ASC")
    fun getAllContacts(): Flow<List<TrustedContactEntity>>
    
    @Query("SELECT * FROM trusted_contacts WHERE id = :id")
    suspend fun getContactById(id: String): TrustedContactEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(contact: TrustedContactEntity)
    
    @Update
    suspend fun update(contact: TrustedContactEntity)
    
    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteById(id: String)
}
