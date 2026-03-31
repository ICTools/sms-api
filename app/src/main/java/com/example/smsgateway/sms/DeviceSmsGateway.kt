package com.example.smsgateway.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.net.toUri
import com.example.smsgateway.domain.MessageItem
import com.example.smsgateway.receiver.SmsDeliveredReceiver
import com.example.smsgateway.receiver.SmsSentReceiver

class DeviceSmsGateway(
    private val context: Context,
) {
    fun sendText(messageId: Long, destination: String, body: String) {
        val smsManager = context.getSystemService(SmsManager::class.java)
        val sentIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            Intent(context, SmsSentReceiver::class.java)
                .setAction(ACTION_SMS_SENT)
                .putExtra(EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            Intent(context, SmsDeliveredReceiver::class.java)
                .setAction(ACTION_SMS_DELIVERED)
                .putExtra(EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        smsManager.sendTextMessage(destination, null, body, sentIntent, deliveredIntent)
    }

    fun readDeviceInbox(limit: Int = 200): List<MessageItem> {
        val projection = arrayOf(
            Telephony.TextBasedSmsColumns.ADDRESS,
            Telephony.TextBasedSmsColumns.BODY,
            Telephony.TextBasedSmsColumns.DATE,
            Telephony.TextBasedSmsColumns.TYPE,
            BaseColumns._ID,
        )
        val uri: Uri = "content://sms".toUri()
        return context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "${Telephony.TextBasedSmsColumns.DATE} DESC LIMIT $limit",
        )?.use { cursor ->
            buildList {
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.TextBasedSmsColumns.TYPE)
                val idIndex = cursor.getColumnIndexOrThrow(BaseColumns._ID)
                while (cursor.moveToNext()) {
                    add(
                        MessageItem(
                            id = cursor.getLong(idIndex),
                            address = cursor.getString(addressIndex).orEmpty(),
                            body = cursor.getString(bodyIndex).orEmpty(),
                            direction = if (cursor.getInt(typeIndex) == Telephony.TextBasedSmsColumns.MESSAGE_TYPE_INBOX) {
                                "incoming"
                            } else {
                                "outgoing"
                            },
                            timestamp = cursor.getLong(dateIndex),
                            smsStatus = "device",
                            syncStatus = "imported",
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    companion object {
        const val ACTION_SMS_SENT = "com.example.smsgateway.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.smsgateway.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "message_id"
    }
}
