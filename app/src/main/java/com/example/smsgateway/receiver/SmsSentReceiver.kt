package com.example.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.smsgateway.SmsGatewayApp
import com.example.smsgateway.sms.DeviceSmsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SmsGatewayApp
        val messageId = intent.getLongExtra(DeviceSmsGateway.EXTRA_MESSAGE_ID, -1L)
        if (messageId < 0) return
        CoroutineScope(Dispatchers.IO).launch {
            app.appContainer.repository.onSmsSent(
                messageId = messageId,
                success = resultCode == android.app.Activity.RESULT_OK,
                error = if (resultCode == android.app.Activity.RESULT_OK) null else "SMS send error code: $resultCode",
            )
        }
    }
}

