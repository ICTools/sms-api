package be.ictools.smsgateway.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_actions")
data class PendingActionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val messageId: Long,
    val ulid: String? = null,
    val payload: String,
    val createdAt: Long,
    val retryCount: Int = 0,
)

