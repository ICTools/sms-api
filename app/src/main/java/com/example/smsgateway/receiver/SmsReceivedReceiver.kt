package com.example.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.smsgateway.SmsGatewayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceivedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val app = context.applicationContext as SmsGatewayApp
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                messages.forEach { sms ->
                    app.appContainer.repository.onIncomingSms(
                        address = sms.originatingAddress.orEmpty(),
                        body = sms.messageBody.orEmpty(),
                        timestamp = sms.timestampMillis,
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

