@file:Suppress("MaxLineLength")
package org.fossify.messages.interfaces

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.ConversationWithSnippetOverride

// Spam: a conversation whose messages are all spam is shown only in the Spam folder. In the normal lists, the
// snippet and date of a conversation that has spam come from its latest message that isn't spam.
// (kind 2 = marked "not spam" by the owner)
private const val SPAM_JOIN = "LEFT OUTER JOIN spam_messages ON messages.id = spam_messages.id AND messages.is_mms = spam_messages.is_mms AND spam_messages.kind != 2"
private const val NOT_RECYCLED = "LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id"
private const val HAS_SPAM = "EXISTS (SELECT 1 FROM spam_messages WHERE spam_messages.thread_id = conversations.thread_id AND spam_messages.kind != 2)"
private const val NON_SPAM_MESSAGES = "FROM messages $NOT_RECYCLED $SPAM_JOIN WHERE recycle_bin_messages.id IS NULL AND spam_messages.id IS NULL AND messages.thread_id = conversations.thread_id"
private const val NON_SPAM_SNIPPET = "(SELECT body $NON_SPAM_MESSAGES ORDER BY messages.date DESC LIMIT 1)"
private const val NON_SPAM_DATE = "(CASE WHEN $HAS_SPAM THEN (SELECT MAX(messages.date) $NON_SPAM_MESSAGES) END)"
private const val NOT_SPAM_ONLY = "NOT ($HAS_SPAM AND NOT EXISTS (SELECT 1 $NON_SPAM_MESSAGES))"
private const val SPAM_MESSAGES = "FROM messages $NOT_RECYCLED $SPAM_JOIN WHERE recycle_bin_messages.id IS NULL AND spam_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id"
private const val SPAM_SNIPPET = "(SELECT body $SPAM_MESSAGES ORDER BY messages.date DESC LIMIT 1)"
private const val SPAM_DATE = "(SELECT MAX(messages.date) $SPAM_MESSAGES)"

@Dao
interface ConversationsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(conversation: Conversation): Long

    @Query("SELECT $NON_SPAM_SNIPPET as new_snippet, $NON_SPAM_DATE as new_date, * FROM conversations WHERE archived = 0 AND $NOT_SPAM_ONLY")
    fun getNonArchivedWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getNonArchived(): List<Conversation> {
        return getNonArchivedWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT $NON_SPAM_SNIPPET as new_snippet, $NON_SPAM_DATE as new_date, * FROM conversations WHERE archived = 1 AND $NOT_SPAM_ONLY")
    fun getAllArchivedWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllArchived(): List<Conversation> {
        return getAllArchivedWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT (SELECT body FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id ORDER BY messages.date DESC LIMIT 1) as new_snippet, NULL as new_date, * FROM conversations WHERE (SELECT COUNT(*) FROM messages LEFT OUTER JOIN recycle_bin_messages ON messages.id = recycle_bin_messages.id WHERE recycle_bin_messages.id IS NOT NULL AND messages.thread_id = conversations.thread_id) > 0")
    fun getAllWithMessagesInRecycleBinWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllWithMessagesInRecycleBin(): List<Conversation> {
        return getAllWithMessagesInRecycleBinWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT $SPAM_SNIPPET as new_snippet, $SPAM_DATE as new_date, * FROM conversations WHERE $HAS_SPAM")
    fun getAllWithSpamWithLatestSnippet(): List<ConversationWithSnippetOverride>

    fun getAllWithSpam(): List<Conversation> {
        return getAllWithSpamWithLatestSnippet().map { it.toConversation() }
    }

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun getConversationWithThreadId(threadId: Long): Conversation?

    @Query("SELECT * FROM conversations WHERE read = 0")
    fun getUnreadConversations(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE title LIKE :text")
    fun getConversationsWithText(text: String): List<Conversation>

    @Query("UPDATE conversations SET read = 1 WHERE thread_id = :threadId")
    fun markRead(threadId: Long)

    @Query("UPDATE conversations SET read = 0 WHERE thread_id = :threadId")
    fun markUnread(threadId: Long)

    @Query("UPDATE conversations SET archived = 1 WHERE thread_id = :threadId")
    fun moveToArchive(threadId: Long)

    @Query("UPDATE conversations SET archived = 0 WHERE thread_id = :threadId")
    fun unarchive(threadId: Long)

    @Query("DELETE FROM conversations WHERE thread_id = :threadId")
    fun deleteThreadId(threadId: Long)
}
