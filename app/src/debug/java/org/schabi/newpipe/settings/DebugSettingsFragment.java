package org.schabi.newpipe.settings;

import android.os.Bundle;

import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PicassoHelper;

import leakcanary.LeakCanary;

public class DebugSettingsFragment extends BasePreferenceFragment {
    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.debug_settings);

        final Preference showMemoryLeaksPreference
                = findPreference(getString(R.string.show_memory_leaks_key));
        final Preference showImageIndicatorsPreference
                = findPreference(getString(R.string.show_image_indicators_key));
        final Preference crashTheAppPreference
                = findPreference(getString(R.string.crash_the_app_key));

        assert showMemoryLeaksPreference != null;
        assert showImageIndicatorsPreference != null;
        assert crashTheAppPreference != null;

        showMemoryLeaksPreference.setOnPreferenceClickListener(preference -> {
            startActivity(LeakCanary.INSTANCE.newLeakDisplayActivityIntent());
            return true;
        });

        showImageIndicatorsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            PicassoHelper.setIndicatorsEnabled((Boolean) newValue);
            return true;
        });

        crashTheAppPreference.setOnPreferenceClickListener(preference -> {
            throw new RuntimeException();
        });
    }
}
