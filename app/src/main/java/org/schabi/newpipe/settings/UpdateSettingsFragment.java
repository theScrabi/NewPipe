package org.schabi.newpipe.settings;

import android.os.Bundle;

import org.schabi.newpipe.R;

import androidx.annotation.Nullable;
import androidx.preference.Preference;

public class UpdateSettingsFragment extends BasePreferenceFragment {

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String updateToggleKey = getString(R.string.update_app_key);
        findPreference(updateToggleKey).setOnPreferenceChangeListener(updatePreferenceChange);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.update_settings);
    }

    private Preference.OnPreferenceChangeListener updatePreferenceChange
            = (preference, newValue) ->  {

        defaultPreferences.edit().putBoolean(getString(R.string.update_app_key),
                (boolean) newValue).apply();

        return true;
    };
}
