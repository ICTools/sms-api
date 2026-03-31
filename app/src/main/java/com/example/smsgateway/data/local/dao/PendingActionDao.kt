package com.example.smsgateway.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.smsgateway.data.local.entity.PendingActionEntity

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingActionEntity>

    @Insert
    suspend fun insert(action: PendingActionEntity)

    @Update
    suspend fun update(action: PendingActionEntity)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

