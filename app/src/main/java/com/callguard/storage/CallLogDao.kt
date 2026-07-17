package com.callguard.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id")
    suspend fun getById(id: Long): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE phoneNumber = :phone ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestByNumber(phone: String): CallLogEntity?

    @Query("SELECT * FROM call_logs WHERE isSpam = 0 ORDER BY timestamp DESC")
    fun getSafeCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE isSpam = 1 ORDER BY timestamp DESC")
    fun getSpamCalls(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CallLogEntity): Long

    @Update
    suspend fun update(log: CallLogEntity)

    @Delete
    suspend fun delete(log: CallLogEntity)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM call_logs WHERE isSpam = 1")
    fun spamCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_logs WHERE isSpam = 0")
    fun safeCount(): Flow<Int>
}
