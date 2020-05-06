package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ZipHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static org.schabi.newpipe.extractor.utils.Utils.isBlank;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private static final int REQUEST_IMPORT_PATH = 8945;
    private static final int REQUEST_EXPORT_PATH = 30945;
    private static final String ZIP_MIME_TYPE = "application/zip";
    private static final SimpleDateFormat EXPORT_DATE_FORMAT
            = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private ContentSettingsManager manager;

    private String importExportDataPathKey;
    private String thumbnailLoadToggleKey;
    private String youtubeRestrictedModeEnabledKey;

    @Nullable private Uri lastImportExportDataUri = null;
    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        final File homeDir = ContextCompat.getDataDir(requireContext());
        Objects.requireNonNull(homeDir);
        manager = new ContentSettingsManager(new NewPipeFileLocator(homeDir));
        manager.deleteSettingsFile();

        importExportDataPathKey = getString(R.string.import_export_data_path);
        thumbnailLoadToggleKey = getString(R.string.download_thumbnail_key);
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        addPreferencesFromResource(R.xml.content_settings);

        final Preference importDataPreference = requirePreference(R.string.import_data);
        importDataPreference.setOnPreferenceClickListener((Preference p) -> {
            startActivityForResult(
                    StoredFileHelper.getPicker(requireContext(), getImportExportDataUri()),
                    REQUEST_IMPORT_PATH);
            return true;
        });

        final Preference exportDataPreference = requirePreference(R.string.export_data);
        exportDataPreference.setOnPreferenceClickListener((final Preference p) -> {

            startActivityForResult(
                    StoredFileHelper.getNewPicker(requireContext(),
                            "NewPipeData-" + EXPORT_DATE_FORMAT.format(new Date()) + ".zip",
                            ZIP_MIME_TYPE, getImportExportDataUri()),
                    REQUEST_EXPORT_PATH);
            return true;
        });

        initialSelectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        initialLanguage = defaultPreferences.getString(getString(R.string.app_language_key), "en");

        final Preference clearCookiePref = requirePreference(R.string.clear_cookie_key);
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
        final String selectedLanguage =
                defaultPreferences.getString(getString(R.string.app_language_key), "en");

        if (!selectedLocalization.equals(initialSelectedLocalization)
                || !selectedContentCountry.equals(initialSelectedContentCountry)
                || !selectedLanguage.equals(initialLanguage)) {
            Toast.makeText(requireContext(), R.string.localization_changes_requires_app_restart,
                    Toast.LENGTH_LONG).show();

            NewPipe.setupLocalization(selectedLocalization, selectedContentCountry);
        }
    }

    @Override
    public void onActivityResult(final int requestCode,
                                 final int resultCode,
                                 @Nullable final Intent data) {
        assureCorrectAppLanguage(getContext());
        super.onActivityResult(requestCode, resultCode, data);
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() called with: "
                    + "requestCode = [" + requestCode + "], "
                    + "resultCode = [" + resultCode + "], "
                    + "data = [" + data + "]");
        }

        if ((requestCode == REQUEST_IMPORT_PATH || requestCode == REQUEST_EXPORT_PATH)
                && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {

            lastImportExportDataUri = data.getData(); // will be saved only on success

            final StoredFileHelper file
                    = new StoredFileHelper(getContext(), data.getData(), ZIP_MIME_TYPE);
            if (requestCode == REQUEST_EXPORT_PATH) {
                exportDatabase(file);
            } else {
                final AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
                builder.setMessage(R.string.override_current_data)
                        .setPositiveButton(R.string.finish,
                                (DialogInterface d, int id) -> importDatabase(file))
                        .setNegativeButton(R.string.cancel,
                                (DialogInterface d, int id) -> d.cancel());
                builder.create().show();
            }
        }
    }

    private void exportDatabase(final StoredFileHelper file) {
        try {
            //checkpoint before export
            NewPipeDatabase.checkpoint();

            final SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(requireContext());
            manager.exportDatabase(preferences, file);

            saveLastImportExportDataUri(false); // save export path only on success
            Toast.makeText(getContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT).show();
        } catch (final Exception e) {
            ErrorActivity.reportUiErrorInSnackbar(this, "Exporting database", e);
        }
    }

    private void importDatabase(final StoredFileHelper file) {
        // check if file is supported
        if (!ZipHelper.isValidZipFile(file)) {
            Toast.makeText(getContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                .show();
            return;
        }

        try {
            if (!manager.ensureDbDirectoryExists()) {
                throw new Exception("Could not create databases dir");
            }

            if (!manager.extractDb(file)) {
                Toast.makeText(getContext(), R.string.could_not_import_all_files, Toast.LENGTH_LONG)
                    .show();
            }

            // if settings file exist, ask if it should be imported.
            if (manager.extractSettings(file)) {
                final AlertDialog.Builder alert = new AlertDialog.Builder(requireContext());
                alert.setTitle(R.string.import_settings);

                alert.setNegativeButton(android.R.string.no, (dialog, which) -> {
                    dialog.dismiss();
                    finishImport();
                });
                alert.setPositiveButton(getString(R.string.finish), (dialog, which) -> {
                    dialog.dismiss();
                    manager.loadSharedPreferences(PreferenceManager
                        .getDefaultSharedPreferences(requireContext()));
                    finishImport();
                });
                alert.show();
            } else {
                finishImport();
            }
        } catch (final Exception e) {
            ErrorActivity.reportUiErrorInSnackbar(this, "Importing database", e);
        }
    }

    /**
     * Save import path and restart system.
     */
    private void finishImport() {
        // save import path only on success; save immediately because app is about to exit
        saveLastImportExportDataUri(true);
        // restart app to properly load db
        System.exit(0);
    }

    private Uri getImportExportDataUri() {
        final String path = defaultPreferences.getString(importExportDataPathKey, null);
        return isBlank(path) ? null : Uri.parse(path);
    }

    private void saveLastImportExportDataUri(final boolean immediately) {
        if (lastImportExportDataUri != null) {
            final SharedPreferences.Editor editor = defaultPreferences.edit()
                    .putString(importExportDataPathKey, lastImportExportDataUri.toString());
            if (immediately) {
                // noinspection ApplySharedPref
                editor.commit(); // app about to be restarted, commit immediately
            } else {
                editor.apply();
            }
        }
    }
}
