@file:Suppress("MaxLineLength")
package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import org.fossify.messages.models.Message
import org.fossify.messages.models.RecycleBinMessage
import org.fossify.messages.models.SpamMessage

@Dao
interface MessagesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecycleBinEntry(recycleBinMessage: RecycleBinMessage)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertOrIgnore(message: Message): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMessages(vararg message: Message)

    @Query("SELECT * FROM messages")
    fun getAll(): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL")
    fun getAllRecycleBinMessages(): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL AND is_scheduled = 1")
    fun getAllScheduledMessages(): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND recycle_bin_messages.deleted_ts < :timestamp")
    fun getOldRecycleBinMessages(timestamp: Long): List<Message>

    @Query("SELECT * FROM messages WHERE thread_id = :threadId")
    fun getThreadMessages(threadId: Long): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL AND thread_id = :threadId")
    fun getNonRecycledThreadMessages(threadId: Long): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND thread_id = :threadId")
    fun getThreadMessagesFromRecycleBin(threadId: Long): List<Message>

    @Query("SELECT messages.* FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NULL AND thread_id = :threadId AND is_scheduled = 1")
    fun getScheduledThreadMessages(threadId: Long): List<Message>

    @Query("SELECT * FROM messages WHERE thread_id = :threadId AND id = :messageId AND is_scheduled = 1")
    fun getScheduledMessageWithId(threadId: Long, messageId: Long): Message

    @Query("SELECT COUNT(*) FROM recycle_bin_messages")
    fun getArchivedCount(): Int

    @Query("SELECT * FROM messages WHERE body LIKE :text")
    fun getMessagesWithText(text: String): List<Message>

    @Query("UPDATE messages SET read = 1 WHERE id = :id")
    fun markRead(id: Long)

    @Query("UPDATE messages SET read = 1 WHERE thread_id = :threadId")
    fun markThreadRead(threadId: Long)

    @Query("UPDATE messages SET type = :type WHERE id = :id")
    fun updateType(id: Long, type: Int): Int

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: Int): Int

    @Transaction
    fun delete(id: Long) {
        deleteFromMessages(id)
        deleteFromRecycleBin(id)
    }

    @Query("DELETE FROM messages WHERE id = :id")
    fun deleteFromMessages(id: Long)

    @Query("DELETE FROM recycle_bin_messages WHERE id = :id")
    fun deleteFromRecycleBin(id: Long)

    @Transaction
    fun deleteThreadMessages(threadId: Long) {
        deleteThreadMessagesFromRecycleBin(threadId)
        deleteAllThreadMessages(threadId)
        deleteThreadSpam(threadId)
    }

    @Query("DELETE FROM messages WHERE thread_id = :threadId")
    fun deleteAllThreadMessages(threadId: Long)

    @Query("DELETE FROM recycle_bin_messages WHERE id IN (SELECT id FROM messages WHERE thread_id = :threadId)")
    fun deleteThreadMessagesFromRecycleBin(threadId: Long)

    @Query("DELETE FROM messages")
    fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSpam(vararg spamMessage: SpamMessage)

    @Query("DELETE FROM spam_messages WHERE id = :id AND is_mms = :isMMS")
    fun deleteSpam(id: Long, isMMS: Boolean)

    @Query("DELETE FROM spam_messages WHERE thread_id = :threadId")
    fun deleteThreadSpam(threadId: Long)

    @Query("SELECT * FROM spam_messages WHERE thread_id = :threadId AND kind != 2")
    fun getThreadSpam(threadId: Long): List<SpamMessage>

    @Query("SELECT * FROM spam_messages")
    fun getAllSpamMarks(): List<SpamMessage>

    // all kinds, "not spam" included
    @Query("SELECT * FROM spam_messages WHERE thread_id = :threadId")
    fun getThreadSpamMarks(threadId: Long): List<SpamMessage>

    // conversations whose latest (cached) message is spam
    @Query("SELECT DISTINCT spam_messages.thread_id FROM spam_messages JOIN messages ON messages.id = spam_messages.id AND messages.is_mms = spam_messages.is_mms WHERE spam_messages.kind != 2 AND messages.date = (SELECT MAX(latest.date) FROM messages latest WHERE latest.thread_id = spam_messages.thread_id)")
    fun getThreadIdsEndingWithSpam(): List<Long>

    @Query("UPDATE spam_messages SET kind = 2 WHERE thread_id = :threadId")
    fun markThreadNotSpam(threadId: Long)

    @Query("SELECT COUNT(*) FROM messages LEFT OUTER JOIN spam_messages ON messages.id = spam_messages.id AND messages.is_mms = spam_messages.is_mms AND spam_messages.kind != 2 WHERE messages.thread_id = :threadId AND spam_messages.id IS NULL")
    fun getThreadNonSpamCount(threadId: Long): Int
}
