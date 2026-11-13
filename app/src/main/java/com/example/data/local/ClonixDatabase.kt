package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ClonedAppEntity::class, SandboxAuditLogEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ClonixDatabase : RoomDatabase() {
    abstract fun clonixDao(): ClonixDao

    companion object {
        @Volatile
        private var INSTANCE: ClonixDatabase? = null

        fun getInstance(context: Context): ClonixDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClonixDatabase::class.java,
                    "clonix_vault.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
