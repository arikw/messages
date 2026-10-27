package org.fossify.messages.activities

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getTempFile
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.underlineText
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.ExportResult
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.messages.R
import org.fossify.messages.databinding.ActivityManageBlockedKeywordsBinding
import org.fossify.messages.dialogs.AddBlockedKeywordDialog
import org.fossify.messages.dialogs.ExportBlockedKeywordsDialog
import org.fossify.messages.dialogs.ManageBlockedKeywordsAdapter
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.toArrayList
import org.fossify.messages.helpers.BlockedKeywordsExporter
import org.fossify.messages.helpers.BlockedKeywordsImporter
import org.fossify.messages.helpers.IS_SPAM_EXEMPT_NUMBERS
import org.fossify.messages.helpers.IS_SPAM_NUMBERS
import org.fossify.messages.helpers.applySpamRulesToExistingMessages
import java.io.FileOutputStream
import java.io.OutputStream

class ManageBlockedKeywordsActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityManageBlockedKeywordsBinding::inflate)

    // The same screen manages the spam numbers and the exempt numbers lists; only keywords can be imported and
    // exported.
    private val isSpamNumbers by lazy { intent.getBooleanExtra(IS_SPAM_NUMBERS, false) }
    private val isExemptNumbers by lazy { intent.getBooleanExtra(IS_SPAM_EXEMPT_NUMBERS, false) }
    private val isNumbers get() = isSpamNumbers || isExemptNumbers

    private fun listText(keywords: Int, spamNumbers: Int, exemptNumbers: Int) =
        getString(if (isExemptNumbers) exemptNumbers else if (isSpamNumbers) spamNumbers else keywords)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateBlockedKeywords()
        setupOptionsMenu()
        setupTexts()

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.manageBlockedKeywordsList))
        setupMaterialScrollListener(
            scrollingView = binding.manageBlockedKeywordsList,
            topAppBar = binding.blockKeywordsAppbar
        )
        updateTextColors(binding.manageBlockedKeywordsWrapper)

        binding.manageBlockedKeywordsPlaceholder2.apply {
            underlineText()
            setTextColor(getProperPrimaryColor())
            setOnClickListener {
                addOrEditBlockedKeyword()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.blockKeywordsAppbar, NavigationIcon.Arrow)
    }

    private fun setupTexts() {
        binding.blockKeywordsToolbar.title =
            listText(R.string.spam_keywords, R.string.spam_numbers, R.string.spam_exempt_numbers)
        binding.manageBlockedKeywordsPlaceholder.text =
            listText(R.string.no_spam_keywords, R.string.no_spam_numbers, R.string.no_spam_exempt_numbers)
        binding.manageBlockedKeywordsPlaceholder2.text =
            listText(R.string.add_spam_keyword, R.string.add_spam_number, R.string.add_spam_exempt_number)
        binding.blockKeywordsToolbar.menu.apply {
            findItem(R.id.add_blocked_keyword).title =
                listText(R.string.add_spam_keyword, R.string.add_spam_number, R.string.add_spam_exempt_number)
            findItem(R.id.import_blocked_keywords).isVisible = !isNumbers
            findItem(R.id.export_blocked_keywords).isVisible = !isNumbers
        }
    }

    private fun setupOptionsMenu() {
        binding.blockKeywordsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_blocked_keyword -> {
                    addOrEditBlockedKeyword()
                    true
                }

                R.id.export_blocked_keywords -> {
                    tryExportBlockedNumbers()
                    true
                }

                R.id.check_existing_messages -> {
                    checkExistingMessages()
                    true
                }

                R.id.import_blocked_keywords -> {
                    tryImportBlockedKeywords()
                    true
                }

                else -> false
            }
        }
    }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            try {
                val outputStream = uri?.let { contentResolver.openOutputStream(it) }
                if (outputStream != null) {
                    exportBlockedKeywordsTo(outputStream)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            try {
                if (uri != null) {
                    tryImportBlockedKeywordsFromFile(uri)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }

    private fun tryImportBlockedKeywords() {
        val mimeType = "text/plain"
        try {
            getContent.launch(mimeType)
        } catch (_: ActivityNotFoundException) {
            toast(org.fossify.commons.R.string.system_service_disabled, Toast.LENGTH_LONG)
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun tryImportBlockedKeywordsFromFile(uri: Uri) {
        when (uri.scheme) {
            "file" -> importBlockedKeywords(uri.path!!)
            "content" -> {
                val tempFile = getTempFile("blocked", "blocked_keywords.txt")
                if (tempFile == null) {
                    toast(org.fossify.commons.R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)
                    importBlockedKeywords(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> toast(org.fossify.commons.R.string.invalid_file_format)
        }
    }

    private fun importBlockedKeywords(path: String) {
        ensureBackgroundThread {
            val result = BlockedKeywordsImporter(this).importBlockedKeywords(path)
            toast(
                when (result) {
                    BlockedKeywordsImporter.ImportResult.IMPORT_OK -> org.fossify.commons.R.string.importing_successful
                    BlockedKeywordsImporter.ImportResult.IMPORT_FAIL -> org.fossify.commons.R.string.no_items_found
                }
            )
            updateBlockedKeywords()
        }
    }

    private fun exportBlockedKeywordsTo(outputStream: OutputStream?) {
        ensureBackgroundThread {
            val blockedKeywords = config.blockedKeywords.toArrayList()
            if (blockedKeywords.isEmpty()) {
                toast(org.fossify.commons.R.string.no_entries_for_exporting)
            } else {
                BlockedKeywordsExporter.exportBlockedKeywords(blockedKeywords, outputStream) {
                    toast(
                        when (it) {
                            ExportResult.EXPORT_OK -> org.fossify.commons.R.string.exporting_successful
                            else -> org.fossify.commons.R.string.exporting_failed
                        }
                    )
                }
            }
        }
    }

    private fun tryExportBlockedNumbers() {
        ExportBlockedKeywordsDialog(
            activity = this,
            path = config.lastBlockedKeywordExportPath,
            hidePath = true
        ) { file ->
            try {
                createDocument.launch(file.name)
            } catch (_: ActivityNotFoundException) {
                toast(
                    org.fossify.commons.R.string.system_service_disabled,
                    Toast.LENGTH_LONG
                )
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun checkExistingMessages() {
        toast(R.string.checking_existing_messages)
        ensureBackgroundThread {
            val (marked, unmarked) = applySpamRulesToExistingMessages()
            toast(getString(R.string.existing_messages_checked, marked, unmarked), Toast.LENGTH_LONG)
        }
    }

    override fun refreshItems() {
        updateBlockedKeywords()
    }

    private fun updateBlockedKeywords() {
        ensureBackgroundThread {
            val blockedKeywords =
                when {
                    isExemptNumbers -> config.spamExemptNumbers
                    isSpamNumbers -> config.spamNumbers
                    else -> config.blockedKeywords
                }.sorted().toArrayList()
            val wholeWords = config.wholeWordKeywords
            runOnUiThread {
                ManageBlockedKeywordsAdapter(
                    activity = this,
                    blockedKeywords = blockedKeywords,
                    listener = this,
                    recyclerView = binding.manageBlockedKeywordsList,
                    removeItem = {
                        when {
                            isExemptNumbers -> config.removeSpamExemptNumber(it)
                            isSpamNumbers -> config.removeSpamNumber(it)
                            else -> config.removeBlockedKeyword(it)
                        }
                    },
                    itemLabel = {
                        if (!isNumbers && it in wholeWords) getString(R.string.keyword_whole_word, it) else it
                    }
                ) {
                    addOrEditBlockedKeyword(it as String)
                }.apply {
                    binding.manageBlockedKeywordsList.adapter = this
                }

                binding.manageBlockedKeywordsPlaceholder.beVisibleIf(blockedKeywords.isEmpty())
                binding.manageBlockedKeywordsPlaceholder2.beVisibleIf(blockedKeywords.isEmpty())
            }
        }
    }

    private fun addOrEditBlockedKeyword(keyword: String? = null) {
        AddBlockedKeywordDialog(this, keyword, isSpamNumbers, isExemptNumbers) {
            updateBlockedKeywords()
        }
    }
}
