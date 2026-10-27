package org.fossify.messages.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * A message's spam mark. The message itself stays in the phone's message store; this only marks it. SMS and MMS ids
 * come from different tables, hence the `is_mms` key part. [kind] tells automatic marks (by keyword or spam number,
 * redone when the lists change) from the owner's own choices, which re-checking leaves alone.
 */
@Entity(
    tableName = "spam_messages",
    primaryKeys = ["id", "is_mms"],
    indices = [Index(value = ["thread_id"])]
)
data class SpamMessage(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "is_mms") val isMMS: Boolean,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "marked_ts") val markedTS: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "kind", defaultValue = "0") val kind: Int = KIND_AUTO
) {
    companion object {
        const val KIND_AUTO = 0
        const val KIND_MANUAL_SPAM = 1
        const val KIND_NOT_SPAM = 2
    }
}
