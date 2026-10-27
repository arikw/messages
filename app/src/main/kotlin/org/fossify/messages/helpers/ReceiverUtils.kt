package org.fossify.messages.helpers

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import org.fossify.commons.extensions.isNumberBlocked
import org.fossify.commons.extensions.normalizePhoneNumber
import org.fossify.commons.extensions.trimToComparableNumber
import org.fossify.commons.models.BlockedNumber
import org.fossify.messages.extensions.config

object ReceiverUtils {

    /** Messages with a blocked keyword or from a spam number are kept, silently, in the Spam folder. */
    fun isSpam(context: Context, address: String, body: String): Boolean {
        return SpamMatcher(context).isSpam(address, body)
    }
}

/** The current spam rules, read once; reuse one instance when checking many messages. */
class SpamMatcher(private val context: Context) {
    private val partKeywords: List<String>
    private val wholeWordKeywords: List<Regex>

    // spam and exempt numbers are matched like blocked numbers, wildcard patterns included
    private val spamNumbers = context.config.spamNumbers.toBlockedNumbers()
    private val exemptNumbers = context.config.spamExemptNumbers.toBlockedNumbers()

    init {
        val wholeWords = context.config.wholeWordKeywords
        val (whole, part) = context.config.blockedKeywords.partition { it in wholeWords }
        partKeywords = part
        wholeWordKeywords = whole.map { wholeWordRegex(it) }
    }

    fun isSpam(address: String, body: String): Boolean {
        if (isExemptNumber(address)) {
            return false
        }

        return partKeywords.any { body.contains(it, ignoreCase = true) } ||
            wholeWordKeywords.any { it.containsMatchIn(body) } ||
            isSpamNumber(address)
    }

    fun isSpamNumber(address: String) = spamNumbers.isNotEmpty() && context.isNumberBlocked(address, spamNumbers)

    fun isExemptNumber(address: String) = exemptNumbers.isNotEmpty() && context.isNumberBlocked(address, exemptNumbers)

    // Where the spam keywords occur in the text, to highlight them.
    fun keywordMatches(body: String): List<IntRange> {
        return partKeywords.flatMap { partMatches(body, it) } +
            wholeWordKeywords.flatMap { regex -> regex.findAll(body).map { it.range } }
    }
}

private fun Set<String>.toBlockedNumbers() = mapTo(ArrayList()) {
    BlockedNumber(
        id = 0,
        number = it,
        normalizedNumber = it.normalizePhoneNumber(),
        numberToCompare = it.normalizePhoneNumber().trimToComparableNumber()
    )
}

// Whole word: no letter, digit or underscore right before or after it.
private fun wholeWordRegex(keyword: String) =
    Regex("(?<![\\p{L}\\p{N}_])" + Regex.escape(keyword) + "(?![\\p{L}\\p{N}_])", RegexOption.IGNORE_CASE)

private fun partMatches(body: String, keyword: String): List<IntRange> {
    val ranges = ArrayList<IntRange>()
    if (keyword.isEmpty()) {
        return ranges
    }

    var start = body.indexOf(keyword, ignoreCase = true)
    while (start >= 0) {
        ranges += start until start + keyword.length
        start = body.indexOf(keyword, start + keyword.length, ignoreCase = true)
    }
    return ranges
}

/** Where one keyword occurs in the text, matched the way the spam rules match it. */
fun keywordRanges(body: String, keyword: String, wholeWord: Boolean): List<IntRange> {
    return if (wholeWord) {
        wholeWordRegex(keyword).findAll(body).map { it.range }.toList()
    } else {
        partMatches(body, keyword)
    }
}

private const val SPAM_HIGHLIGHT_COLOR = 0x66E53935

/** Marks the words that make a message spam: bold, underlined, on a red background. */
fun highlightSpamRanges(text: String, ranges: List<IntRange>): CharSequence {
    if (ranges.isEmpty()) {
        return text
    }

    return SpannableString(text).apply {
        ranges.forEach {
            val end = it.last + 1
            setSpan(StyleSpan(Typeface.BOLD), it.first, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(UnderlineSpan(), it.first, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(SPAM_HIGHLIGHT_COLOR), it.first, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
