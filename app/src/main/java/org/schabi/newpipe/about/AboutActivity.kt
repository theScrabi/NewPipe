package org.schabi.newpipe.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ActivityAboutBinding
import org.schabi.newpipe.databinding.FragmentAboutBinding
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        Localization.assureCorrectAppLanguage(this)
        super.onCreate(savedInstanceState)
        ThemeHelper.setTheme(this)
        title = getString(R.string.title_activity_about)
        val aboutBinding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(aboutBinding.root)
        setSupportActionBar(aboutBinding.aboutToolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        val mAboutStateAdapter = AboutStateAdapter(this)

        // Set up the ViewPager with the sections adapter.
        aboutBinding.aboutViewPager2.adapter = mAboutStateAdapter
        TabLayoutMediator(
            aboutBinding.aboutTabLayout,
            aboutBinding.aboutViewPager2
        ) { tab: TabLayout.Tab, position: Int ->
            when (position) {
                POS_ABOUT -> tab.setText(R.string.tab_about)
                POS_LICENSE -> tab.setText(R.string.tab_licenses)
                else -> throw IllegalArgumentException("Unknown position for ViewPager2")
            }
        }.attach()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    class AboutFragment : Fragment() {
        private fun Button.openLink(url: Int) {
            setOnClickListener {
                ShareUtils.openUrlInBrowser(
                    context,
                    requireContext().getString(url),
                    false
                )
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            val aboutBinding = FragmentAboutBinding.inflate(inflater, container, false)
            aboutBinding.aboutAppVersion.text = BuildConfig.VERSION_NAME
            aboutBinding.aboutGithubLink.openLink(R.string.github_url)
            aboutBinding.aboutDonationLink.openLink(R.string.donation_url)
            aboutBinding.aboutWebsiteLink.openLink(R.string.website_url)
            aboutBinding.aboutPrivacyPolicyLink.openLink(R.string.privacy_policy_url)
            return aboutBinding.root
        }
    }

    /**
     * A [FragmentStateAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    private class AboutStateAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POS_ABOUT -> AboutFragment()
                POS_LICENSE -> LicenseFragment.newInstance(SOFTWARE_COMPONENTS)
                else -> throw IllegalArgumentException("Unknown position for ViewPager2")
            }
        }

        override fun getItemCount(): Int {
            // Show 2 total pages.
            return TOTAL_COUNT
        }
    }

    companion object {
        /**
         * List of all software components.
         */
        private val SOFTWARE_COMPONENTS = arrayOf(
            SoftwareComponent(
                "ACRA", "2013", "Kevin Gaudin",
                "https://github.com/ACRA/acra", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "AndroidX", "2005 - 2011", "The Android Open Source Project",
                "https://developer.android.com/jetpack", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "CircleImageView", "2014 - 2020", "Henning Dodenhof",
                "https://github.com/hdodenhof/CircleImageView", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "ExoPlayer", "2014 - 2020", "Google, Inc.",
                "https://github.com/google/ExoPlayer", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "GigaGet", "2014 - 2015", "Peter Cai",
                "https://github.com/PaperAirplane-Dev-Team/GigaGet", StandardLicenses.GPL3
            ),
            SoftwareComponent(
                "Groupie", "2016", "Lisa Wray",
                "https://github.com/lisawray/groupie", StandardLicenses.MIT
            ),
            SoftwareComponent(
                "Icepick", "2015", "Frankie Sardo",
                "https://github.com/frankiesardo/icepick", StandardLicenses.EPL1
            ),
            SoftwareComponent(
                "Jsoup", "2009 - 2020", "Jonathan Hedley",
                "https://github.com/jhy/jsoup", StandardLicenses.MIT
            ),
            SoftwareComponent(
                "Markwon", "2019", "Dimitry Ivanov",
                "https://github.com/noties/Markwon", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "Material Components for Android", "2016 - 2020", "Google, Inc.",
                "https://github.com/material-components/material-components-android",
                StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "NewPipe Extractor", "2017 - 2020", "Christian Schabesberger",
                "https://github.com/TeamNewPipe/NewPipeExtractor", StandardLicenses.GPL3
            ),
            SoftwareComponent(
                "NoNonsense-FilePicker", "2016", "Jonas Kalderstam",
                "https://github.com/spacecowboy/NoNonsense-FilePicker", StandardLicenses.MPL2
            ),
            SoftwareComponent(
                "OkHttp", "2019", "Square, Inc.",
                "https://square.github.io/okhttp/", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "Picasso", "2013", "Square, Inc.",
                "https://square.github.io/picasso/", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "PrettyTime", "2012 - 2020", "Lincoln Baxter, III",
                "https://github.com/ocpsoft/prettytime", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "RxAndroid", "2015", "The RxAndroid authors",
                "https://github.com/ReactiveX/RxAndroid", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "RxBinding", "2015", "Jake Wharton",
                "https://github.com/JakeWharton/RxBinding", StandardLicenses.APACHE2
            ),
            SoftwareComponent(
                "RxJava", "2016 - 2020", "RxJava Contributors",
                "https://github.com/ReactiveX/RxJava", StandardLicenses.APACHE2
            )
        )
        private const val POS_ABOUT = 0
        private const val POS_LICENSE = 1
        private const val TOTAL_COUNT = 2
    }
}
