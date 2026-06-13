package be.ictools.smsgateway.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val address: String,
    val title: String,
    val lastMessageBody: String,
    val lastMessageAt: Long,
)

