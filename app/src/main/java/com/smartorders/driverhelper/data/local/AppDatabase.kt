package com.smartorders.driverhelper.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smartorders.driverhelper.data.local.dao.TripLogDao
import com.smartorders.driverhelper.data.local.dao.ZoneDao
import com.smartorders.driverhelper.data.local.entity.TripLogEntity
import com.smartorders.driverhelper.data.local.entity.ZoneEntity

@Database(entities = [TripLogEntity::class, ZoneEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripLogDao(): TripLogDao
    abstract fun zoneDao(): ZoneDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_orders_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
