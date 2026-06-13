package be.ictools.smsgateway.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import be.ictools.smsgateway.data.local.dao.ConversationDao
import be.ictools.smsgateway.data.local.dao.MessageDao
import be.ictools.smsgateway.data.local.dao.PendingActionDao
import be.ictools.smsgateway.data.local.entity.ConversationEntity
import be.ictools.smsgateway.data.local.entity.MessageEntity
import be.ictools.smsgateway.data.local.entity.PendingActionEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class, PendingActionEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class GatewayDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun pendingActionDao(): PendingActionDao

    companion object {
        val MIGRATIONS: Array<Migration> = emptyArray()
    }
}

