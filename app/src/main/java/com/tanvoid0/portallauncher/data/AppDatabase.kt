package com.tanvoid0.portallauncher.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.TypeConverters

@Database(
    entities = [ProfileEntity::class, AutomationConfigEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : androidx.room.RoomDatabase() {

    abstract fun profileDao(): ProfileDao
    abstract fun automationConfigDao(): AutomationConfigDao

    companion object {
        private const val DB_NAME = "portal_launcher.db"

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
