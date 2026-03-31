package com.example.smsgateway.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.smsgateway.data.local.dao.ConversationDao
import com.example.smsgateway.data.local.dao.MessageDao
import com.example.smsgateway.data.local.dao.PendingActionDao
import com.example.smsgateway.data.local.entity.ConversationEntity
import com.example.smsgateway.data.local.entity.MessageEntity
import com.example.smsgateway.data.local.entity.PendingActionEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, PendingActionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingActionDao(): PendingActionDao
}

