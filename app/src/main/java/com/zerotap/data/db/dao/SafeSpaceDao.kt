package com.zerotap.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zerotap.data.db.entity.SafeSpaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SafeSpaceDao {

    @Query("SELECT * FROM safe_spaces ORDER BY priority DESC, name ASC")
    fun getAll(): Flow<List<SafeSpaceEntity>>

    @Query("SELECT * FROM safe_spaces ORDER BY priority DESC, name ASC")
    suspend fun getAllSnapshot(): List<SafeSpaceEntity>

    @Query("SELECT * FROM safe_spaces WHERE type = :type ORDER BY priority DESC, name ASC")
    fun getByType(type: String): Flow<List<SafeSpaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(spaces: List<SafeSpaceEntity>)

    @Query("SELECT COUNT(*) FROM safe_spaces")
    suspend fun getCount(): Int

    @Query("DELETE FROM safe_spaces")
    suspend fun deleteAll()
}
