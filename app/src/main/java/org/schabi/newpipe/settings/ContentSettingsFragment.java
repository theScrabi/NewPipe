package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.nononsenseapps.filepicker.Utils;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.ZipHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private static final int REQUEST_IMPORT_PATH = 8945;
    private static final int REQUEST_EXPORT_PATH = 30945;

    private ContentSettingsManager manager;

    private String thumbnailLoadToggleKey;
    private String youtubeRestrictedModeEnabledKey;

    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        final File homeDir = ContextCompat.getDataDir(requireContext());
        manager = new ContentSettingsManager(new NewPipeFileLocator(homeDir));
        manager.deleteSettingsFile();

        addPreferencesFromResource(R.xml.content_settings);

        final Preference importDataPreference = findPreference(getString(R.string.import_data));
        importDataPreference.setOnPreferenceClickListener(p -> {
            final Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE,
                            FilePickerActivityHelper.MODE_FILE);
            startActivityForResult(i, REQUEST_IMPORT_PATH);
            return true;
        });

        final Preference exportDataPreference = findPreference(getString(R.string.export_data));
        exportDataPreference.setOnPreferenceClickListener(p -> {
            final Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE,
                            FilePickerActivityHelper.MODE_DIR);
            startActivityForResult(i, REQUEST_EXPORT_PATH);
            return true;
        });

        thumbnailLoadToggleKey = getString(R.string.download_thumbnail_key);
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        initialSelectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        initialLanguage = PreferenceManager
                .getDefaultSharedPreferences(requireContext()).getString("app_language_key", "en");

        final Preference clearCookiePref = findPreference(getString(R.string.clear_cookie_key));

        clearCookiePref.setOnPreferenceClickListener(preference -> {
            defaultPreferences.edit()
                    .putString(getString(R.string.recaptcha_cookies_key), "").apply();
            DownloaderImpl.getInstance().setCookie(ReCaptchaActivity.RECAPTCHA_COOKIES_KEY, "");
            Toast.makeText(getActivity(), R.string.recaptcha_cookies_cleared,
                    Toast.LENGTH_SHORT).show();
            clearCookiePref.setVisible(false);
            return true;
        });

        if (defaultPreferences.getString(getString(R.string.recaptcha_cookies_key), "").isEmpty()) {
            clearCookiePref.setVisible(false);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
        if (preference.getKey().equals(thumbnailLoadToggleKey)) {
            final ImageLoader imageLoader = ImageLoader.getInstance();
            imageLoader.stop();
            imageLoader.clearDiskCache();
            imageLoader.clearMemoryCache();
            imageLoader.resume();
            Toast.makeText(preference.getContext(), R.string.thumbnail_cache_wipe_complete_notice,
                    Toast.LENGTH_SHORT).show();
        }

        if (preference.getKey().equals(youtubeRestrictedModeEnabledKey)) {
            final Context context = getContext();
            if (context != null) {
                DownloaderImpl.getInstance().updateYoutubeRestrictedModeCookies(context);
            } else {
                Log.w(TAG, "onPreferenceTreeClick: null context");
            }
        }

        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Localization selectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        final ContentCountry selectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        final String selectedLanguage = PreferenceManager
                .getDefaultSharedPreferences(requireContext()).getString("app_language_key", "en");

        if (!selectedLocalization.equals(initialSelectedLocalization)
                || !selectedContentCountry.equals(initialSelectedContentCountry)
                || !selectedLanguage.equals(initialLanguage)) {
            Toast.makeText(requireContext(), R.string.localization_changes_requires_app_restart,
                    Toast.LENGTH_LONG).show();

            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode,
                                 @NonNull final Intent data) {
        assureCorrectAppLanguage(getContext());
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: "
                    + "requestCode = [" + requestCode + "], "
                    + "resultCode = [" + resultCode + "], "
                    + "data = [" + data + "]");
        }

        if ((requestCode == REQUEST_IMPORT_PATH || requestCode == REQUEST_EXPORT_PATH)
                && resultCode == Activity.RESULT_OK && data.getData() != null) {
            final String path = Utils.getFileForUri(data.getData()).getAbsolutePath();
            if (requestCode == REQUEST_EXPORT_PATH) {
                final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                exportDatabase(path + "/NewPipeData-" + sdf.format(new Date()) + ".zip");
            } else {
                final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
                builder.setMessage(R.string.override_current_data)
                        .setPositiveButton(getString(R.string.finish),
                                (d, id) -> importDatabase(path))
                        .setNegativeButton(android.R.string.cancel,
                                (d, id) -> d.cancel());
                builder.create().show();
            }
        }
    }

    private void exportDatabase(final String path) {
        try {
            //checkpoint before export
            NewPipeDatabase.checkpoint();

            final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
            manager.exportDatabase(preferences, path);

            Toast.makeText(getContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT).show();
        } catch (final Exception e) {
            ErrorActivity.reportUiErrorInSnackbar(this, "Exporting database", e);
        }
    }

    private void importDatabase(final String filePath) {
        // check if file is supported
        if (!ZipHelper.isValidZipFile(filePath)) {
            Toast.makeText(getContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                .show();
            return;
        }

        try {
            if (!manager.ensureDbDirectoryExists()) {
                throw new Exception("Could not create databases dir");
            }

            if (!manager.extractDb(filePath)) {
                Toast.makeText(getContext(), R.string.could_not_import_all_files, Toast.LENGTH_LONG)
                    .show();
            }

            //If settings file exist, ask if it should be imported.
            if (manager.extractSettings(filePath)) {
                final AlertDialog.Builder alert = new AlertDialog.Builder(requireContext());
                alert.setTitle(R.string.import_settings);

                alert.setNegativeButton(android.R.string.no, (dialog, which) -> {
                    dialog.dismiss();
                    // restart app to properly load db
                    System.exit(0);
                });
                alert.setPositiveButton(getString(R.string.finish), (dialog, which) -> {
                    dialog.dismiss();
                    manager.loadSharedPreferences(PreferenceManager
                        .getDefaultSharedPreferences(requireContext()));
                    // restart app to properly load db
                    System.exit(0);
                });
                alert.show();
            } else {
                // restart app to properly load db
                System.exit(0);
            }
        } catch (final Exception e) {
            ErrorActivity.reportUiErrorInSnackbar(this, "Importing database", e);
        }
    }
}
