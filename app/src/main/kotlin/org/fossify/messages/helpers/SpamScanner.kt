package org.fossify.messages.helpers

import android.content.Context
import android.provider.Telephony.Mms
import android.provider.Telephony.Sms
import androidx.core.net.toUri
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.helpers.isQPlus
import org.fossify.messages.extensions.getMMSSender
import org.fossify.messages.extensions.getMessages
import org.fossify.messages.extensions.messagesDB
import org.fossify.messages.models.SpamMessage

/**
 * Applies the current spam keywords and spam numbers to the received messages already on the phone: marks the
 * ones that match and unmarks automatic marks that no longer match. The owner's own "Mark as spam" / "Not spam"
 * choices are left as they are. Returns (newly marked, unmarked). Run it off the main thread.
 */
@Suppress("SpreadOperator")
fun Context.applySpamRulesToExistingMessages(): Pair<Int, Int> {
    val matcher = SpamMatcher(this)
    val marks = messagesDB.getAllSpamMarks().associateBy { it.id to it.isMMS }
    val toMark = ArrayList<SpamMessage>()
    val toUnmark = ArrayList<SpamMessage>()

    forEachReceivedMessage { message ->
        val mark = marks[message.id to message.isMMS]
        if (mark != null && mark.kind != SpamMessage.KIND_AUTO) {
            return@forEachReceivedMessage
        }

        val isSpam = matcher.isSpam(message.address, message.body)
        if (isSpam && mark == null) {
            toMark += SpamMessage(message.id, message.isMMS, message.threadId)
        } else if (!isSpam && mark != null) {
            toUnmark += mark
        }
    }

    messagesDB.insertSpam(*toMark.toTypedArray())
    toUnmark.forEach { messagesDB.deleteSpam(it.id, it.isMMS) }
    (toMark + toUnmark).map { it.threadId }.distinct().forEach { cacheLatestNonSpamMessage(it) }
    if (toMark.isNotEmpty() || toUnmark.isNotEmpty()) {
        refreshConversations()
    }
    return toMark.size to toUnmark.size
}

/** A received SMS or MMS, as the spam rules see it. [date] is in milliseconds. */
data class ReceivedMessage(
    val id: Long,
    val isMMS: Boolean,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long
)

/** All received messages that have text, newest first, e.g. to preview what a keyword would match. Slow. */
fun Context.getReceivedMessages(): List<ReceivedMessage> {
    val messages = ArrayList<ReceivedMessage>()
    forEachReceivedMessage { if (it.body.isNotBlank()) messages += it }
    return messages.sortedByDescending { it.date }
}

private fun Context.forEachReceivedMessage(callback: (ReceivedMessage) -> Unit) {
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE),
        selection = "${Sms.TYPE} = ${Sms.MESSAGE_TYPE_INBOX}",
        showErrors = true
    ) { cursor ->
        callback(
            ReceivedMessage(
                id = cursor.getLongValue(Sms._ID),
                isMMS = false,
                threadId = cursor.getLongValue(Sms.THREAD_ID),
                address = cursor.getStringValue(Sms.ADDRESS) ?: "",
                body = cursor.getStringValue(Sms.BODY) ?: "",
                date = cursor.getLongValue(Sms.DATE)
            )
        )
    }

    val mmsTexts = getMmsTexts()
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms._ID, Mms.THREAD_ID, Mms.DATE),
        selection = "${Mms.MESSAGE_BOX} = ${Mms.MESSAGE_BOX_INBOX}",
        showErrors = true
    ) { cursor ->
        val id = cursor.getLongValue(Mms._ID)
        callback(
            ReceivedMessage(
                id = id,
                isMMS = true,
                threadId = cursor.getLongValue(Mms.THREAD_ID),
                address = getMMSSender(id),
                body = mmsTexts[id] ?: "",
                date = cursor.getLongValue(Mms.DATE) * MMS_DATE_TO_MILLIS
            )
        )
    }
}

// MMS dates are in seconds
private const val MMS_DATE_TO_MILLIS = 1000L

// The text parts of all MMS, by message id.
private fun Context.getMmsTexts(): Map<Long, String> {
    val uri = if (isQPlus()) Mms.Part.CONTENT_URI else "content://mms/part".toUri()
    val texts = HashMap<Long, String>()
    queryCursor(
        uri = uri,
        projection = arrayOf(Mms.Part.MSG_ID, Mms.Part.TEXT),
        selection = "${Mms.Part.CONTENT_TYPE} = 'text/plain'",
        showErrors = true
    ) { cursor ->
        val id = cursor.getLongValue(Mms.Part.MSG_ID)
        val text = cursor.getStringValue(Mms.Part.TEXT) ?: return@queryCursor
        texts[id] = texts[id]?.let { "$it $text" } ?: text
    }
    return texts
}

/**
 * Whether a conversation counts as spam-only is decided from the locally cached messages (see ConversationsDao), so
 * a conversation that has spam needs its latest messages cached, and at least one that isn't spam if there is one.
 */
@Suppress("SpreadOperator")
private fun Context.cacheLatestNonSpamMessage(threadId: Long) {
    val spam = messagesDB.getThreadSpam(threadId).map { it.id to it.isMMS }.toSet()
    val latest = getMessages(threadId, includeScheduledMessages = false)
    messagesDB.insertMessages(*latest.toTypedArray())
    if (latest.any { (it.id to it.isMMS) !in spam }) {
        return
    }

    val spamSmsIds = spam.filterNot { it.second }.joinToString(",") { it.first.toString() }
    var latestNonSpamDate = 0L
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms.DATE),
        selection = "${Sms.THREAD_ID} = $threadId" +
            if (spamSmsIds.isEmpty()) "" else " AND ${Sms._ID} NOT IN ($spamSmsIds)",
        sortOrder = "${Sms.DATE} DESC LIMIT 1"
    ) { cursor ->
        latestNonSpamDate = cursor.getLongValue(Sms.DATE)
    }

    if (latestNonSpamDate > 0) {
        val older = getMessages(
            threadId = threadId,
            dateFrom = (latestNonSpamDate / 1000 + 1).toInt(),
            includeScheduledMessages = false,
            limit = 1
        )
        messagesDB.insertMessages(*older.toTypedArray())
    }
}
