package com.smartorders.driverhelper.data.local.dao

import androidx.room.*
import com.smartorders.driverhelper.data.local.entity.ZoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones ORDER BY name ASC")
    fun getAllZones(): Flow<List<ZoneEntity>>

    @Insert
    suspend fun insert(zone: ZoneEntity): Long

    @Update
    suspend fun update(zone: ZoneEntity)

    @Delete
    suspend fun delete(zone: ZoneEntity)

    @Query("DELETE FROM zones")
    suspend fun clearAll()
}
