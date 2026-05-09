package com.example.smsgateway.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.smsgateway.data.api.GatewayApi
import com.example.smsgateway.data.api.HttpException
import com.example.smsgateway.data.api.IncomingMessageRequest
import com.example.smsgateway.data.api.MessageStatusRequest
import com.example.smsgateway.data.api.OutgoingMessageRequest
import com.example.smsgateway.data.auth.SessionStore
import com.example.smsgateway.data.local.GatewayDatabase
import com.example.smsgateway.data.local.entity.ConversationEntity
import com.example.smsgateway.data.local.entity.MessageEntity
import com.example.smsgateway.data.local.entity.PendingActionEntity
import com.example.smsgateway.domain.ConversationSummary
import com.example.smsgateway.domain.MessageItem
import com.example.smsgateway.sms.DeviceSmsGateway
import com.example.smsgateway.worker.SyncPendingMessagesWorker
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MessageRepository(
    appContext: Context,
    private val api: GatewayApi,
    private val sessionStore: SessionStore,
    private val database: GatewayDatabase,
    private val smsGateway: DeviceSmsGateway,
) {
    private val workManager = WorkManager.getInstance(appContext)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun observeConversations(): Flow<List<ConversationSummary>> =
        database.conversationDao().observeAll().map { rows ->
            rows.map {
                ConversationSummary(
                    address = it.address,
                    title = it.title,
                    preview = it.lastMessageBody,
                    timestamp = it.lastMessageAt,
                )
            }
        }

    fun observeMessages(address: String): Flow<List<MessageItem>> =
        database.messageDao().observeByAddress(address).map { rows ->
            rows.map {
                MessageItem(
                    id = it.id,
                    address = it.address,
                    body = it.body,
                    direction = it.direction,
                    timestamp = it.timestamp,
                    smsStatus = it.smsStatus,
                    syncStatus = it.syncStatus,
                )
            }
        }

    fun observePendingActionCount(): Flow<Int> =
        database.pendingActionDao().observeCount()

    suspend fun saveSettings(baseUrl: String, apiKey: String, apiSecret: String) {
        sessionStore.saveBaseUrl(baseUrl)
        sessionStore.saveApiKey(apiKey)
        sessionStore.saveApiSecret(apiSecret)
        sessionStore.clearJwt()
    }

    suspend fun importDeviceHistory() {
        smsGateway.readDeviceInbox().forEach { item ->
            upsertMessage(
                MessageEntity(
                    address = item.address,
                    body = item.body,
                    direction = item.direction,
                    source = "device",
                    timestamp = item.timestamp,
                    deviceMessageId = item.id,
                    smsStatus = item.smsStatus,
                    syncStatus = item.syncStatus,
                ),
            )
        }
    }

    suspend fun onIncomingSms(address: String, body: String, timestamp: Long, deviceMessageId: Long? = null) {
        val messageId = upsertMessage(
            MessageEntity(
                address = address,
                body = body,
                direction = "incoming",
                source = "device",
                timestamp = timestamp,
                deviceMessageId = deviceMessageId,
                smsStatus = "received",
                syncStatus = "pending_push",
            ),
        )
        database.pendingActionDao().insert(
            PendingActionEntity(
                type = "push_incoming",
                messageId = messageId,
                payload = json.encodeToString(
                    IncomingMessageRequest(
                        phoneNumber = address,
                        body = body,
                        sentAt = Instant.ofEpochMilli(timestamp).toString(),
                        deviceMessageId = deviceMessageId,
                    ),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        enqueueSync()
    }

    suspend fun sendFromPhone(address: String, body: String): Long {
        val now = System.currentTimeMillis()
        val messageId = upsertMessage(
            MessageEntity(
                address = address,
                body = body,
                direction = "outgoing",
                source = "device",
                timestamp = now,
                smsStatus = "pending",
                syncStatus = "pending_push",
            ),
        )
        database.pendingActionDao().insert(
            PendingActionEntity(
                type = "push_outgoing",
                messageId = messageId,
                payload = json.encodeToString(
                    OutgoingMessageRequest(
                        phoneNumber = address,
                        body = body,
                        createdAt = Instant.ofEpochMilli(now).toString(),
                        deviceMessageId = messageId,
                    ),
                ),
                createdAt = now,
            ),
        )
        enqueueSync()
        smsGateway.sendText(messageId, address, body)
        return messageId
    }

    suspend fun processOutgoingUlid(ulid: String): Result<Unit> {
        if (database.messageDao().getByUlid(ulid) != null) return Result.success(Unit)
        return api.fetchRemoteMessage(ulid).mapCatching { remote ->
            val messageId = upsertMessage(
                MessageEntity(
                    ulid = remote.ulid,
                    address = remote.phoneNumber,
                    body = remote.body,
                    direction = "outgoing",
                    source = "server",
                    timestamp = System.currentTimeMillis(),
                    smsStatus = "accepted_local",
                    syncStatus = "pending_status",
                ),
            )
            val stored = database.messageDao().getById(messageId)
            if (stored != null) queueStatusUpdate(stored, "accepted")
            smsGateway.sendText(messageId, remote.phoneNumber, remote.body)
        }
    }

    suspend fun onSmsSent(messageId: Long, success: Boolean, error: String? = null) {
        val message = database.messageDao().getById(messageId) ?: return
        val updated = message.copy(
            smsStatus = if (success) "sent" else "failed",
            syncStatus = if (message.ulid != null) "pending_status" else message.syncStatus,
            errorMessage = error,
        )
        database.messageDao().update(updated)
        if (updated.ulid != null) {
            if (success && message.smsStatus != "sent" && message.smsStatus != "delivered") {
                queueOutgoingMessage(updated)
            }
            queueStatusUpdate(updated, if (success) "sent" else "failed")
        }
    }

    suspend fun onSmsDelivered(messageId: Long) {
        val message = database.messageDao().getById(messageId) ?: return
        val updated = message.copy(smsStatus = "delivered", syncStatus = "pending_status")
        database.messageDao().update(updated)
        if (updated.ulid != null) {
            queueStatusUpdate(updated, "delivered")
        }
    }

    suspend fun syncPendingActions() {
        val actions = database.pendingActionDao().getAll()
        actions.forEach { action ->
            if (action.retryCount >= MAX_RETRIES) {
                android.util.Log.w(
                    "MessageRepository",
                    "Dropping action ${action.id} (type=${action.type}) after ${action.retryCount} retries",
                )
                database.pendingActionDao().deleteById(action.id)
                return@forEach
            }

            val result = when (action.type) {
                "push_incoming" -> {
                    val payload = json.decodeFromString<IncomingMessageRequest>(action.payload)
                    api.publishIncomingMessage(payload)
                }
                "push_status" -> {
                    val payload = json.decodeFromString<MessageStatusRequest>(action.payload)
                    api.publishStatus(action.ulid.orEmpty(), payload)
                }
                "push_outgoing" -> {
                    val payload = json.decodeFromString<OutgoingMessageRequest>(action.payload)
                    api.publishOutgoingMessage(payload)
                }
                else -> Result.failure(IllegalArgumentException("Unknown action type ${action.type}"))
            }

            when {
                result.isSuccess -> database.pendingActionDao().deleteById(action.id)
                isFatalClientError(result.exceptionOrNull()) -> {
                    android.util.Log.w(
                        "MessageRepository",
                        "Dropping action ${action.id} on fatal client error: ${result.exceptionOrNull()?.message}",
                    )
                    database.pendingActionDao().deleteById(action.id)
                }
                else -> database.pendingActionDao().update(action.copy(retryCount = action.retryCount + 1))
            }
        }
    }

    private fun isFatalClientError(error: Throwable?): Boolean {
        val http = error as? HttpException ?: return false
        return http.code in 400..499 && http.code !in retriableClientCodes
    }

    companion object {
        const val MAX_RETRIES = 10
        private val retriableClientCodes = setOf(401, 408, 425, 429)
    }

    private suspend fun queueStatusUpdate(message: MessageEntity, status: String) {
        database.pendingActionDao().insert(
            PendingActionEntity(
                type = "push_status",
                messageId = message.id,
                ulid = message.ulid,
                payload = json.encodeToString(
                    MessageStatusRequest(
                        status = status,
                        deviceMessageId = message.deviceMessageId,
                        sentAt = Instant.now().toString(),
                        deliveredAt = if (status == "delivered") Instant.now().toString() else null,
                        errorMessage = message.errorMessage,
                    ),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        enqueueSync()
    }

    private suspend fun queueOutgoingMessage(message: MessageEntity) {
        database.pendingActionDao().insert(
            PendingActionEntity(
                type = "push_outgoing",
                messageId = message.id,
                ulid = message.ulid,
                payload = json.encodeToString(
                    OutgoingMessageRequest(
                        phoneNumber = message.address,
                        body = message.body,
                        createdAt = Instant.ofEpochMilli(message.timestamp).toString(),
                        deviceMessageId = message.deviceMessageId ?: message.id,
                    ),
                ),
                createdAt = System.currentTimeMillis(),
            ),
        )
        enqueueSync()
    }

    private suspend fun upsertMessage(entity: MessageEntity): Long {
        val inserted = database.messageDao().insert(entity)
        val finalId = if (inserted == -1L) {
            val existing = when {
                entity.ulid != null -> database.messageDao().getByUlid(entity.ulid)
                entity.deviceMessageId != null -> database.messageDao().getByDeviceMessageId(entity.deviceMessageId)
                else -> null
            } ?: error(
                "Insert conflict without resolvable existing row (ulid=${entity.ulid}, " +
                    "deviceMessageId=${entity.deviceMessageId})",
            )
            database.messageDao().update(
                existing.copy(
                    ulid = entity.ulid ?: existing.ulid,
                    body = entity.body,
                    smsStatus = entity.smsStatus,
                    syncStatus = entity.syncStatus,
                    errorMessage = entity.errorMessage,
                    timestamp = entity.timestamp,
                ),
            )
            existing.id
        } else {
            inserted
        }
        database.conversationDao().upsert(
            ConversationEntity(
                address = entity.address,
                title = entity.address,
                lastMessageBody = entity.body,
                lastMessageAt = entity.timestamp,
            ),
        )
        return finalId
    }

    private fun enqueueSync() {
        val request = OneTimeWorkRequestBuilder<SyncPendingMessagesWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(
            SyncPendingMessagesWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }
}
