package org.schabi.newpipe.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.preference.Preference;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ThemeHelper;

public class AppearanceSettingsFragment extends BasePreferenceFragment {
    private static final boolean CAPTIONING_SETTINGS_ACCESSIBLE =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

    private String captionSettingsKey;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        addPreferencesFromResource(R.xml.appearance_settings);

        final String themeKey = getString(R.string.theme_key);
        // the key of the active theme when settings were opened (or recreated after theme change)
        final String startThemeKey = defaultPreferences
                .getString(themeKey, getString(R.string.default_theme_value));
        final String autoDeviceThemeKey = getString(R.string.auto_device_theme_key);
        findPreference(themeKey).setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue.toString().equals(autoDeviceThemeKey)) {
                Toast.makeText(getContext(), getString(R.string.select_night_theme_toast),
                        Toast.LENGTH_LONG).show();
            }

            applyThemeChange(startThemeKey, themeKey, newValue);
            return false;
        });

        final String nightThemeKey = getString(R.string.night_theme_key);
        if (startThemeKey.equals(autoDeviceThemeKey)) {
            final String startNightThemeKey = defaultPreferences
                    .getString(nightThemeKey, getString(R.string.default_night_theme_value));

            findPreference(nightThemeKey).setOnPreferenceChangeListener((preference, newValue) -> {
                applyThemeChange(startNightThemeKey, nightThemeKey, newValue);
                return false;
            });
        } else {
            removePreference(nightThemeKey);
        }

        captionSettingsKey = getString(R.string.caption_settings_key);
        if (!CAPTIONING_SETTINGS_ACCESSIBLE) {
            removePreference(captionSettingsKey);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (preference.getKey().equals(captionSettingsKey) && CAPTIONING_SETTINGS_ACCESSIBLE) {
            try {
                startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
            } catch (final ActivityNotFoundException e) {
                Toast.makeText(getActivity(), R.string.general_error, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    private void removePreference(final String preferenceKey) {
        final Preference preference = findPreference(preferenceKey);
        if (preference != null) {
            getPreferenceScreen().removePreference(preference);
        }
    }

    private void applyThemeChange(final String beginningThemeKey,
                                  final String themeKey,
                                  final Object newValue) {
        defaultPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, true).apply();
        defaultPreferences.edit().putString(themeKey, newValue.toString()).apply();

        ThemeHelper.setDayNightMode(getContext(), newValue.toString());

        if (!newValue.equals(beginningThemeKey) && getActivity() != null) {
            // if it's not the current theme
            ActivityCompat.recreate(getActivity());
        }
    }
}
