package be.ictools.smsgateway.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import be.ictools.smsgateway.SmsGatewayApp
import be.ictools.smsgateway.sms.DeviceSmsGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as SmsGatewayApp
        val messageId = intent.getLongExtra(DeviceSmsGateway.EXTRA_MESSAGE_ID, -1L)
        if (messageId < 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.appContainer.repository.onSmsDelivered(messageId)
            } catch (t: Throwable) {
                android.util.Log.e("SmsDeliveredReceiver", "onSmsDelivered failed", t)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
