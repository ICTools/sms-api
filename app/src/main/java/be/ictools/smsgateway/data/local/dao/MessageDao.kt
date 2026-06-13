package be.ictools.smsgateway.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import be.ictools.smsgateway.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE address = :address ORDER BY timestamp ASC")
    fun observeByAddress(address: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE ulid = :ulid LIMIT 1")
    suspend fun getByUlid(ulid: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE deviceMessageId = :deviceMessageId LIMIT 1")
    suspend fun getByDeviceMessageId(deviceMessageId: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)
}
