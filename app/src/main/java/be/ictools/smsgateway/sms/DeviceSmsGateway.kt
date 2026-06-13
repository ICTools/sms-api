package be.ictools.smsgateway.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.BaseColumns
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.net.toUri
import be.ictools.smsgateway.domain.MessageItem
import be.ictools.smsgateway.receiver.SmsDeliveredReceiver
import be.ictools.smsgateway.receiver.SmsSentReceiver

class DeviceSmsGateway(
    private val context: Context,
) {
    fun sendText(messageId: Long, destination: String, body: String) {
        if (destination.isBlank() || body.isBlank()) {
            throw IllegalArgumentException("destination and body must not be blank")
        }
        val smsManager = context.getSystemService(SmsManager::class.java)
            ?: throw IllegalStateException("SmsManager unavailable")
        val requestCode = (messageId and 0x7FFFFFFFL).toInt()
        val sentIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, SmsSentReceiver::class.java)
                .setAction(ACTION_SMS_SENT)
                .putExtra(EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deliveredIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, SmsDeliveredReceiver::class.java)
                .setAction(ACTION_SMS_DELIVERED)
                .putExtra(EXTRA_MESSAGE_ID, messageId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val parts = smsManager.divideMessage(body)
        try {
            if (parts.size <= 1) {
                smsManager.sendTextMessage(destination, null, body, sentIntent, deliveredIntent)
            } else {
                val sentIntents = ArrayList<PendingIntent>(parts.size).apply {
                    repeat(parts.size) { add(sentIntent) }
                }
                val deliveredIntents = ArrayList<PendingIntent>(parts.size).apply {
                    repeat(parts.size) { add(deliveredIntent) }
                }
                smsManager.sendMultipartTextMessage(
                    destination,
                    null,
                    parts,
                    sentIntents,
                    deliveredIntents,
                )
            }
        } catch (t: Throwable) {
            android.util.Log.e("DeviceSmsGateway", "sendText failed for messageId=$messageId", t)
            throw t
        }
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
        const val ACTION_SMS_SENT = "be.ictools.smsgateway.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "be.ictools.smsgateway.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "message_id"
    }
}
