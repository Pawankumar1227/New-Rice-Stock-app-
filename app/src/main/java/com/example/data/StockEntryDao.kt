package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StockEntryDao {
    @Query("SELECT * FROM stock_entries ORDER BY timestamp DESC")
    fun getAllEntriesFlow(): Flow<List<StockEntry>>

    @Query("SELECT * FROM stock_entries WHERE syncStatus = 'PENDING' OR syncStatus = 'FAILED'")
    suspend fun getPendingEntries(): List<StockEntry>

    @Query("SELECT * FROM stock_entries WHERE id = :id")
    suspend fun getEntryById(id: Long): StockEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: StockEntry): Long

    @Update
    suspend fun updateEntry(entry: StockEntry)

    @Delete
    suspend fun deleteEntry(entry: StockEntry)

    @Query("DELETE FROM stock_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("UPDATE stock_entries SET syncStatus = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, error: String?)
}
