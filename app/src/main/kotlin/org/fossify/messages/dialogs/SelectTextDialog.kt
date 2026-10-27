package org.fossify.messages.dialogs

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogSelectTextBinding

// helper dialog for selecting just a part of a message, not copying the whole into clipboard
class SelectTextDialog(
    val activity: BaseSimpleActivity,
    val text: String,
    // net.wzmn.messages: when set, the selection menu also offers "Spam keyword", called back once one is saved
    private val onSpamKeywordAdded: (() -> Unit)? = null
) {
    private var dialog: AlertDialog? = null

    init {
        val binding = DialogSelectTextBinding.inflate(activity.layoutInflater).apply {
            dialogSelectTextValue.text = text
            if (onSpamKeywordAdded != null) {
                dialogSelectTextValue.customSelectionActionModeCallback = spamKeywordAction(dialogSelectTextValue)
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> { } }
            .apply {
                val titleId = if (onSpamKeywordAdded != null) R.string.select_spam_keyword_title else 0
                activity.setupDialogStuff(binding.root, this, titleId = titleId) { alertDialog ->
                    dialog = alertDialog
                }
            }
    }

    private fun spamKeywordAction(textView: android.widget.TextView) = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add(Menu.NONE, R.id.add_as_spam_keyword, Menu.FIRST, R.string.add_as_spam_keyword)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != R.id.add_as_spam_keyword) {
                return false
            }

            val start = minOf(textView.selectionStart, textView.selectionEnd).coerceAtLeast(0)
            val end = maxOf(textView.selectionStart, textView.selectionEnd).coerceAtLeast(0)
            val selected = text.substring(start, end).trim()
            mode.finish()
            dialog?.dismiss()
            AddBlockedKeywordDialog(activity, initialKeyword = selected) {
                onSpamKeywordAdded?.invoke()
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) = Unit
    }
}
