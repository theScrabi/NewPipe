package org.schabi.newpipe.settings;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;

import com.google.android.material.snackbar.Snackbar;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.PermissionHelper;

public class VideoAudioSettingsFragment extends BasePreferenceFragment {

    private SharedPreferences.OnSharedPreferenceChangeListener listener;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //initializing R.array.seek_duration_description to display the translation of seconds
        Resources res = getResources();
        String[] durationsValues = res.getStringArray(R.array.seek_duration_value);
        String[] durationsDescriptions = res.getStringArray(R.array.seek_duration_description);
        int currentDurationValue;
        for (int i = 0; i < durationsDescriptions.length; i++) {
            currentDurationValue = Integer.parseInt(durationsValues[i]) / 1000;
            try {
                durationsDescriptions[i] = String.format(
                        res.getQuantityString(R.plurals.dynamic_seek_duration_description, currentDurationValue),
                        currentDurationValue);
            } catch (Resources.NotFoundException ignored) {
                //if this happens, the translation is missing, and the english string will be displayed instead
            }
        }
        ListPreference durations = (ListPreference) findPreference(getString(R.string.seek_duration_key));
        durations.setEntries(durationsDescriptions);

        listener = (sharedPreferences, s) -> {

            // on M and above, if user chooses to minimise to popup player on exit and the app doesn't have
            // display over other apps permission, show a snackbar to let the user give permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    s.equals(getString(R.string.minimize_on_exit_key))) {

                String newSetting = sharedPreferences.getString(s, null);
                if (newSetting != null
                        && newSetting.equals(getString(R.string.minimize_on_exit_popup_key))
                        && !Settings.canDrawOverlays(getContext())) {

                    Snackbar.make(getListView(), R.string.permission_display_over_apps, Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.settings,
                                    view -> PermissionHelper.checkSystemAlertWindowPermission(getContext()))
                            .show();

                }
            }
        };
    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.video_audio_settings);
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(listener);

    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(listener);
    }
}
