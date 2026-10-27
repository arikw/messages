package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Embedded

data class ConversationWithSnippetOverride(
    @ColumnInfo(name = "new_snippet") val snippet: String?,
    @Embedded val conversation: Conversation,
    @ColumnInfo(name = "new_date") val date: Int? = null
) {
    fun toConversation() =
        conversation.copy(
            snippet = snippet ?: conversation.snippet,
            date = date ?: conversation.date
        )
}
