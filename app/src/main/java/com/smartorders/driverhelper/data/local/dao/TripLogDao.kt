package com.smartorders.driverhelper.data.local.dao

import androidx.room.*
import com.smartorders.driverhelper.data.local.entity.TripLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripLogDao {
    @Query("SELECT * FROM trip_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<TripLogEntity>>

    @Query("SELECT * FROM trip_logs WHERE timestamp >= :startOfDay ORDER BY timestamp DESC")
    fun getTodayLogs(startOfDay: Long): Flow<List<TripLogEntity>>

    @Insert
    suspend fun insert(log: TripLogEntity): Long

    @Query("DELETE FROM trip_logs")
    suspend fun clearAll()

    @Query("DELETE FROM trip_logs WHERE timestamp >= :startOfDay")
    suspend fun clearToday(startOfDay: Long)

    @Query("SELECT COUNT(*) FROM trip_logs WHERE timestamp >= :startOfDay")
    suspend fun getTodayCount(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM trip_logs WHERE timestamp >= :startOfDay AND accepted = 1")
    suspend fun getTodayAccepted(startOfDay: Long): Int

    @Query("SELECT COUNT(*) FROM trip_logs WHERE timestamp >= :startOfDay AND accepted = 0")
    suspend fun getTodayRejected(startOfDay: Long): Int

    @Query("SELECT SUM(price) FROM trip_logs WHERE timestamp >= :startOfDay AND accepted = 1")
    suspend fun getTodayTotalSar(startOfDay: Long): Double?
}
