package org.fossify.messages.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.FAQActivity
import org.fossify.commons.activities.LicenseActivity
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.APP_FAQ
import org.fossify.commons.helpers.APP_LICENSES
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.messages.BuildConfig
import org.fossify.messages.databinding.ActivityForkAboutBinding

/**
 * The fork's own About screen. The one in Fossify Commons carries Fossify's name, links, donations and support email,
 * which shouldn't point at a modified app; this one credits the original instead.
 */
class ForkAboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityForkAboutBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.aboutNestedScrollview))
        setupMaterialScrollListener(
            scrollingView = binding.aboutNestedScrollview,
            topAppBar = binding.aboutAppbar
        )

        binding.aboutVersion.text =
            getString(org.fossify.commons.R.string.version_placeholder, BuildConfig.VERSION_NAME)
        binding.aboutOriginalHolder.setOnClickListener { launchViewIntent(UPSTREAM_URL) }
        binding.aboutFaqHolder.setOnClickListener {
            Intent(applicationContext, FAQActivity::class.java).apply {
                putExtra(APP_FAQ, intent.getSerializableExtra(APP_FAQ))
                startActivity(this)
            }
        }
        binding.aboutLicensesHolder.setOnClickListener {
            Intent(applicationContext, LicenseActivity::class.java).apply {
                putExtra(APP_LICENSES, intent.getLongExtra(APP_LICENSES, 0))
                startActivity(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.aboutNestedScrollview)
        binding.aboutCreditsLabel.setTextColor(getProperPrimaryColor())
    }

    companion object {
        private const val UPSTREAM_URL = "https://github.com/FossifyOrg/Messages"
    }
}
