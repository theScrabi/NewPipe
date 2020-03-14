package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class ContentSettingsFragment extends BasePreferenceFragment {

    private String thumbnailLoadToggleKey;

    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thumbnailLoadToggleKey = getString(R.string.download_thumbnail_key);

        initialSelectedLocalization = org.schabi.newpipe.util.Localization.getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization.getPreferredContentCountry(requireContext());
        initialLanguage = PreferenceManager.getDefaultSharedPreferences(getContext()).getString("app_language_key", "en");
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getKey().equals(thumbnailLoadToggleKey)) {
            final ImageLoader imageLoader = ImageLoader.getInstance();
            imageLoader.stop();
            imageLoader.clearDiskCache();
            imageLoader.clearMemoryCache();
            imageLoader.resume();
            Toast.makeText(preference.getContext(), R.string.thumbnail_cache_wipe_complete_notice,
                    Toast.LENGTH_SHORT).show();
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        addPreferencesFromResource(R.xml.content_settings);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Localization selectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        final ContentCountry selectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        final String selectedLanguage = PreferenceManager.getDefaultSharedPreferences(getContext()).getString("app_language_key", "en");

        if (!selectedLocalization.equals(initialSelectedLocalization)
                || !selectedContentCountry.equals(initialSelectedContentCountry) || !selectedLanguage.equals(initialLanguage)) {
            Toast.makeText(requireContext(), R.string.localization_changes_requires_app_restart, Toast.LENGTH_LONG).show();

            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
        assureCorrectAppLanguage(getContext());
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: requestCode = [" + requestCode + "], resultCode = [" + resultCode + "], data = [" + data + "]");
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error
    //////////////////////////////////////////////////////////////////////////*/

    protected void onError(Throwable e) {
        final Activity activity = getActivity();
        ErrorActivity.reportError(activity, e,
                activity.getClass(),
                null,
                ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        "none", "", R.string.app_ui_crash));
    }
}
