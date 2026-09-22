package com.zerotap.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.zerotap.data.db.dao.*
import com.zerotap.data.db.entity.*

@Database(
    entities = [
        IncidentEntity::class,
        RiskEventEntity::class,
        TrustedContactEntity::class,
        EvidenceMetadataEntity::class,
        AlertAttemptEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZeroTapDatabase : RoomDatabase() {
    abstract fun incidentDao(): IncidentDao
    abstract fun riskEventDao(): RiskEventDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun evidenceMetadataDao(): EvidenceMetadataDao
    abstract fun alertAttemptDao(): AlertAttemptDao

    companion object {
        @Volatile private var INSTANCE: ZeroTapDatabase? = null
        
        @Suppress("DEPRECATION")
        fun getInstance(context: Context): ZeroTapDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context.applicationContext, ZeroTapDatabase::class.java, "zerotap.db")
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
        }
    }
}
