package org.fossify.messages.dialogs

import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.onTextChangeListener
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.showKeyboard
import org.fossify.commons.extensions.value
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.databinding.DialogAddBlockedKeywordBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.helpers.ReceivedMessage
import org.fossify.messages.helpers.getReceivedMessages
import org.fossify.messages.helpers.highlightSpamRanges
import org.fossify.messages.helpers.keywordRanges
import java.util.Date
import java.util.concurrent.ConcurrentHashMap

class AddBlockedKeywordDialog(
    val activity: BaseSimpleActivity,
    private val originalKeyword: String? = null,
    private val isSpamNumber: Boolean = false,
    private val isExemptNumber: Boolean = false,
    // a new keyword to start with, e.g. a word selected in a message
    private val initialKeyword: String? = null,
    val callback: () -> Unit
) {
    private val binding = DialogAddBlockedKeywordBinding.inflate(activity.layoutInflater)
    private val previewHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var receivedMessages: List<ReceivedMessage>? = null

    @Volatile
    private var previewGeneration = 0
    private val senderNames = ConcurrentHashMap<String, String>()

    init {
        val isNumber = isSpamNumber || isExemptNumber
        binding.apply {
            if (isNumber) {
                addBlockedKeywordHint.hint = activity.getString(R.string.spam_number_hint)
                addBlockedKeywordEdittext.inputType = InputType.TYPE_CLASS_PHONE
                addBlockedKeywordWholeWord.beGone()
                addBlockedKeywordPreviewCount.beGone()
                addBlockedKeywordPreviewHolder.beGone()
            }
            if (originalKeyword != null) {
                addBlockedKeywordEdittext.setText(originalKeyword)
                addBlockedKeywordWholeWord.isChecked = originalKeyword in activity.config.wholeWordKeywords
            } else if (initialKeyword != null) {
                addBlockedKeywordEdittext.setText(initialKeyword)
            }
        }

        if (!isNumber) {
            setupPreview()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this) { alertDialog ->
                    alertDialog.showKeyboard(binding.addBlockedKeywordEdittext)
                    alertDialog.setOnDismissListener {
                        previewHandler.removeCallbacksAndMessages(null)
                        previewGeneration++
                    }
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val newBlockedKeyword = binding.addBlockedKeywordEdittext.value
                        if (originalKeyword != null && newBlockedKeyword != originalKeyword) {
                            if (isExemptNumber) {
                                activity.config.removeSpamExemptNumber(originalKeyword)
                            } else if (isSpamNumber) {
                                activity.config.removeSpamNumber(originalKeyword)
                            } else {
                                activity.config.removeBlockedKeyword(originalKeyword)
                            }
                        }

                        if (newBlockedKeyword.isNotEmpty()) {
                            if (isExemptNumber) {
                                activity.config.addSpamExemptNumber(newBlockedKeyword)
                            } else if (isSpamNumber) {
                                activity.config.addSpamNumber(newBlockedKeyword)
                            } else {
                                activity.config.addBlockedKeyword(newBlockedKeyword)
                                activity.config.setKeywordWholeWord(
                                    newBlockedKeyword,
                                    binding.addBlockedKeywordWholeWord.isChecked
                                )
                            }
                        }

                        callback()
                        alertDialog.dismiss()
                    }
                }
            }
    }

    // Shows which received messages the keyword would match, so it can be tuned before saving.
    private fun setupPreview() {
        binding.addBlockedKeywordPreviewCount.text = activity.getString(R.string.loading_keyword_preview)
        binding.addBlockedKeywordEdittext.onTextChangeListener { schedulePreview() }
        binding.addBlockedKeywordWholeWord.setOnCheckedChangeListener { _, _ -> schedulePreview() }
        ensureBackgroundThread {
            receivedMessages = activity.getReceivedMessages()
            activity.runOnUiThread { updatePreview() }
        }
    }

    private fun schedulePreview() {
        previewHandler.removeCallbacksAndMessages(null)
        previewHandler.postDelayed({ updatePreview() }, PREVIEW_DELAY_MS)
    }

    private fun updatePreview() {
        val messages = receivedMessages ?: return
        val keyword = binding.addBlockedKeywordEdittext.value
        val wholeWord = binding.addBlockedKeywordWholeWord.isChecked
        val generation = ++previewGeneration
        if (keyword.isEmpty()) {
            binding.addBlockedKeywordPreviewCount.text = ""
            binding.addBlockedKeywordPreview.text = ""
            return
        }

        ensureBackgroundThread {
            val matches = messages.mapNotNull { message ->
                keywordRanges(message.body, keyword, wholeWord).takeIf { it.isNotEmpty() }?.let { message to it }
            }
            val preview = buildPreview(matches)
            activity.runOnUiThread {
                if (generation != previewGeneration) {
                    return@runOnUiThread
                }

                binding.addBlockedKeywordPreviewCount.text = if (matches.isEmpty()) {
                    activity.getString(R.string.keyword_preview_none)
                } else {
                    activity.resources.getQuantityString(R.plurals.keyword_preview_count, matches.size, matches.size)
                }
                binding.addBlockedKeywordPreview.text = preview
                binding.addBlockedKeywordPreviewHolder.scrollTo(0, 0)
            }
        }
    }

    // "date · sender" and the text around the match, keyword highlighted, for the newest few matches
    private fun buildPreview(matches: List<Pair<ReceivedMessage, List<IntRange>>>): CharSequence {
        val dateFormat = DateFormat.getDateFormat(activity)
        val builder = SpannableStringBuilder()
        matches.take(MAX_PREVIEW_MESSAGES).forEach { (message, ranges) ->
            val sender = senderNames.getOrPut(message.address) {
                SimpleContactsHelper(activity).getNameFromPhoneNumber(message.address)
            }
            val start = maxOf(0, ranges.first().first - CONTEXT_BEFORE)
            val end = minOf(message.body.length, ranges.first().last + 1 + CONTEXT_AFTER)
            val excerpt = message.body.substring(start, end).replace('\n', ' ')
            val excerptRanges = ranges
                .filter { it.first >= start && it.last < end }
                .map { it.first - start..it.last - start }

            if (builder.isNotEmpty()) {
                builder.append("\n\n")
            }
            builder.append("${dateFormat.format(Date(message.date))} · $sender\n")
            if (start > 0) builder.append("…")
            builder.append(highlightSpamRanges(excerpt, excerptRanges))
            if (end < message.body.length) builder.append("…")
        }

        if (matches.size > MAX_PREVIEW_MESSAGES) {
            val more = matches.size - MAX_PREVIEW_MESSAGES
            builder.append("\n\n").append(activity.getString(R.string.keyword_preview_more, more))
        }
        return builder
    }

    companion object {
        private const val PREVIEW_DELAY_MS = 300L
        private const val MAX_PREVIEW_MESSAGES = 50
        private const val CONTEXT_BEFORE = 40
        private const val CONTEXT_AFTER = 80
    }
}
