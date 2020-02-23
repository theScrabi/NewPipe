package org.schabi.newpipe.about;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class AboutActivity extends AppCompatActivity {

    /**
     * List of all software components
     */
    private static final SoftwareComponent[] SOFTWARE_COMPONENTS = new SoftwareComponent[]{
            new SoftwareComponent("Giga Get", "2014 - 2015", "Peter Cai", "https://github.com/PaperAirplane-Dev-Team/GigaGet", StandardLicenses.GPL2),
            new SoftwareComponent("NewPipe Extractor", "2017 - 2020", "Christian Schabesberger", "https://github.com/TeamNewPipe/NewPipeExtractor", StandardLicenses.GPL3),
            new SoftwareComponent("Jsoup", "2017", "Jonathan Hedley", "https://github.com/jhy/jsoup", StandardLicenses.MIT),
            new SoftwareComponent("Rhino", "2015", "Mozilla", "https://www.mozilla.org/rhino/", StandardLicenses.MPL2),
            new SoftwareComponent("ACRA", "2013", "Kevin Gaudin", "http://www.acra.ch", StandardLicenses.APACHE2),
            new SoftwareComponent("Universal Image Loader", "2011 - 2015", "Sergey Tarasevich", "https://github.com/nostra13/Android-Universal-Image-Loader", StandardLicenses.APACHE2),
            new SoftwareComponent("CircleImageView", "2014 - 2020", "Henning Dodenhof", "https://github.com/hdodenhof/CircleImageView", StandardLicenses.APACHE2),
            new SoftwareComponent("NoNonsense-FilePicker", "2016", "Jonas Kalderstam", "https://github.com/spacecowboy/NoNonsense-FilePicker", StandardLicenses.MPL2),
            new SoftwareComponent("ExoPlayer", "2014 - 2020", "Google Inc", "https://github.com/google/ExoPlayer", StandardLicenses.APACHE2),
            new SoftwareComponent("RxAndroid", "2015 - 2018", "The RxAndroid authors", "https://github.com/ReactiveX/RxAndroid", StandardLicenses.APACHE2),
            new SoftwareComponent("RxJava", "2016 - 2020", "RxJava Contributors", "https://github.com/ReactiveX/RxJava", StandardLicenses.APACHE2),
            new SoftwareComponent("RxBinding", "2015 - 2018", "Jake Wharton", "https://github.com/JakeWharton/RxBinding", StandardLicenses.APACHE2),
            new SoftwareComponent("PrettyTime", "2012 - 2020", "Lincoln Baxter, III", "https://github.com/ocpsoft/prettytime", StandardLicenses.APACHE2),
            new SoftwareComponent("Markwon", "2017 - 2020", "Noties", "https://github.com/noties/Markwon", StandardLicenses.APACHE2)
    };

    /**
     * The {@link PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     * {@link FragmentStatePagerAdapter}.
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceState);
        ThemeHelper.setTheme(this);
        this.setTitle(getString(R.string.title_activity_about));

        setContentView(R.layout.activity_about);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        TabLayout tabLayout = findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class AboutFragment extends Fragment {

        public AboutFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static AboutFragment newInstance() {
            return new AboutFragment();
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_about, container, false);
            Context context = this.getContext();

            TextView version = rootView.findViewById(R.id.app_version);
            version.setText(BuildConfig.VERSION_NAME);

            View githubLink = rootView.findViewById(R.id.github_link);
            githubLink.setOnClickListener(nv -> openWebsite(context.getString(R.string.github_url), context));

            View donationLink = rootView.findViewById(R.id.donation_link);
            donationLink.setOnClickListener(v -> openWebsite(context.getString(R.string.donation_url), context));

            View websiteLink = rootView.findViewById(R.id.website_link);
            websiteLink.setOnClickListener(nv -> openWebsite(context.getString(R.string.website_url), context));

            View privacyPolicyLink = rootView.findViewById(R.id.privacy_policy_link);
            privacyPolicyLink.setOnClickListener(v -> openWebsite(context.getString(R.string.privacy_policy_url), context));

            return rootView;
        }

        private void openWebsite(String url, Context context) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            context.startActivity(intent);
        }

    }


    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0:
                    return AboutFragment.newInstance();
                case 1:
                    return LicenseFragment.newInstance(SOFTWARE_COMPONENTS);
            }
            return null;
        }

        @Override
        public int getCount() {
            // Show 2 total pages.
            return 2;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.tab_about);
                case 1:
                    return getString(R.string.tab_licenses);
            }
            return null;
        }
    }
}
