package com.zerotap.data.db.dao

import androidx.room.*
import com.zerotap.data.db.entity.TrustedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrustedPlaceDao {
    @Query("SELECT * FROM trusted_places ORDER BY createdAt DESC")
    fun getAllTrustedPlaces(): Flow<List<TrustedPlaceEntity>>

    @Query("SELECT * FROM trusted_places WHERE id = :id")
    suspend fun getPlaceById(id: String): TrustedPlaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: TrustedPlaceEntity)

    @Update
    suspend fun update(place: TrustedPlaceEntity)

    @Query("DELETE FROM trusted_places WHERE id = :id")
    suspend fun deleteById(id: String)
}
