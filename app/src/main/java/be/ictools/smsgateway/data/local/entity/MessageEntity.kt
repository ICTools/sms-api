package be.ictools.smsgateway.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["ulid"], unique = true),
        Index(value = ["deviceMessageId"], unique = true),
        Index(value = ["address", "timestamp"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ulid: String? = null,
    val address: String,
    val body: String,
    val direction: String,
    val source: String,
    val timestamp: Long,
    val deviceMessageId: Long? = null,
    val smsStatus: String,
    val syncStatus: String,
    val errorMessage: String? = null,
)
