package com.masarif.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.masarif.app.MasarifApp
import com.masarif.app.data.IngestOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * يستقبل SMS_RECEIVED حتى والتطبيق مغلق. يدمج أجزاء الرسالة الطويلة، ثم يمرّرها للمسار الموحّد.
 * goAsync() يمنح نحو 10 ثوانٍ لإتمام الكتابة في قاعدة البيانات قبل أن يقتل النظام العملية.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress
        val body = messages.joinToString(separator = "") { it.messageBody ?: "" }
        val timestamp = messages[0].timestampMillis.takeIf { it > 0 } ?: System.currentTimeMillis()

        val app = context.applicationContext as? MasarifApp ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val outcome = app.repository.ingest(sender, body, timestamp)
                Log.i(TAG, "sender=$sender outcome=$outcome")
                if (outcome == IngestOutcome.INSERTED) {
                    app.repository.notifyPendingChanged()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "SmsReceiver"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
