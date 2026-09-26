package com.masarif.app.sms

import android.content.Context
import android.provider.Telephony
import android.util.Log
import com.masarif.app.data.MasarifRepository
import com.masarif.app.data.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * يمسح صندوق الوارد كاملاً (READ_SMS) ويمرّر رسائل المرسلين المعروفين للمسار الموحّد.
 * إعادة المسح آمنة: منع التكرار في المستودع يضمن عدم إنشاء أي نسخة مكررة.
 */
class InboxScanner(private val context: Context, private val repository: MasarifRepository) {

    /** عدد رسائل صندوق الوارد التي يحتوي اسم مرسلها النص المعطى (للتحقق في الترحيب). */
    suspend fun countMessagesFrom(sender: String): Int = withContext(Dispatchers.IO) {
        val needle = sender.trim()
        if (needle.isEmpty()) return@withContext 0
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS), null, null, null
        ) ?: return@withContext 0
        var n = 0
        cursor.use { c ->
            val i = c.getColumnIndex(Telephony.Sms.ADDRESS)
            while (c.moveToNext()) {
                val address = if (i >= 0) c.getString(i) else null
                if (address != null && address.contains(needle, ignoreCase = true)) n++
            }
        }
        n
    }

    suspend fun scan(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): ScanResult = withContext(Dispatchers.IO) {
        val senders = repository.settings.sendersNow()
        if (senders.isEmpty()) return@withContext ScanResult()
        // رسائل أقدم من الفترة بيوم كامل تُتخطى دون تحليل (وقت الرسالة قد يسبق وقت العملية بقليل).
        val cutoff = (repository.settings.importStartNow() - DAY_MS).coerceAtLeast(0L)
        val projection = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT,
        )
        var result = ScanResult()
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection, null, null, Telephony.Sms.DATE + " ASC"
        ) ?: return@withContext result

        cursor.use { c ->
            val total = c.count
            val iAddress = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndex(Telephony.Sms.BODY)
            val iDate = c.getColumnIndex(Telephony.Sms.DATE)
            val iDateSent = c.getColumnIndex(Telephony.Sms.DATE_SENT)
            var done = 0
            while (c.moveToNext()) {
                done++
                result = result.copy(scanned = done)
                val address = if (iAddress >= 0) c.getString(iAddress) else null
                if (address == null || senders.none { address.contains(it, ignoreCase = true) }) {
                    if (done % 200 == 0) onProgress(done, total)
                    continue
                }
                val date = if (iDate >= 0) c.getLong(iDate) else 0L
                val dateSent = if (iDateSent >= 0) c.getLong(iDateSent) else 0L
                val timestamp = if (dateSent > 0) dateSent else date
                if (timestamp in 1 until cutoff) {
                    result = result.copy(matched = result.matched + 1, tooOld = result.tooOld + 1)
                    continue
                }
                val body = if (iBody >= 0) c.getString(iBody) else null
                if (body.isNullOrBlank()) continue
                try {
                    result += repository.ingest(address, body, timestamp)
                } catch (t: Throwable) {
                    Log.e(TAG, "ingest failed for inbox message", t)
                }
                onProgress(done, total)
            }
            onProgress(total, total)
        }
        repository.notifyPendingChanged()
        Log.i(TAG, "scan finished: $result")
        result
    }

    companion object {
        const val TAG = "InboxScanner"
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}
