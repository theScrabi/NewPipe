package org.schabi.newpipe.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.ZipHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;

import static org.schabi.newpipe.extractor.utils.Utils.isBlank;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class ContentSettingsFragment extends BasePreferenceFragment {
    private static final String ZIP_MIME_TYPE = "application/zip";

    private final SimpleDateFormat exportDateFormat
            = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

    private ContentSettingsManager manager;

    private String importExportDataPathKey;
    private String youtubeRestrictedModeEnabledKey;

    private Localization initialSelectedLocalization;
    private ContentCountry initialSelectedContentCountry;
    private String initialLanguage;
    private final ActivityResultLauncher<Intent> requestImportPathLauncher =
            registerForActivityResult(new StartActivityForResult(), this::requestImportPathResult);
    private final ActivityResultLauncher<Intent> requestExportPathLauncher =
            registerForActivityResult(new StartActivityForResult(), this::requestExportPathResult);

    @Override
    public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
        final File homeDir = ContextCompat.getDataDir(requireContext());
        Objects.requireNonNull(homeDir);
        manager = new ContentSettingsManager(new NewPipeFileLocator(homeDir));
        manager.deleteSettingsFile();

        importExportDataPathKey = getString(R.string.import_export_data_path);
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);

        addPreferencesFromResource(R.xml.content_settings);

        final Preference importDataPreference = requirePreference(R.string.import_data);
        importDataPreference.setOnPreferenceClickListener((Preference p) -> {
            requestImportPathLauncher.launch(
                    StoredFileHelper.getPicker(requireContext(), getImportExportDataUri()));
            return true;
        });

        final Preference exportDataPreference = requirePreference(R.string.export_data);
        exportDataPreference.setOnPreferenceClickListener((final Preference p) -> {

            requestExportPathLauncher.launch(
                    StoredFileHelper.getNewPicker(requireContext(),
                            "NewPipeData-" + exportDateFormat.format(new Date()) + ".zip",
                            ZIP_MIME_TYPE, getImportExportDataUri()));
            return true;
        });

        initialSelectedLocalization = org.schabi.newpipe.util.Localization
                .getPreferredLocalization(requireContext());
        initialSelectedContentCountry = org.schabi.newpipe.util.Localization
                .getPreferredContentCountry(requireContext());
        initialLanguage = PreferenceManager
                .getDefaultSharedPreferences(requireContext()).getString("app_language_key", "en");

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

        findPreference(getString(R.string.download_thumbnail_key)).setOnPreferenceChangeListener(
                (preference, newValue) -> {
                    PicassoHelper.setShouldLoadImages((Boolean) newValue);
                    try {
                        PicassoHelper.clearCache(preference.getContext());
                        Toast.makeText(preference.getContext(),
                                R.string.thumbnail_cache_wipe_complete_notice, Toast.LENGTH_SHORT)
                                .show();
                    } catch (final IOException e) {
                        Log.e(TAG, "Unable to clear Picasso cache", e);
                    }
                    return true;
                });
    }

    @Override
    public boolean onPreferenceTreeClick(final Preference preference) {
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

    private void requestExportPathResult(final ActivityResult result) {
        assureCorrectAppLanguage(getContext());
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            // will be saved only on success
            final Uri lastExportDataUri = result.getData().getData();

            final StoredFileHelper file
                    = new StoredFileHelper(getContext(), result.getData().getData(), ZIP_MIME_TYPE);

            exportDatabase(file, lastExportDataUri);
        }
    }

    private void requestImportPathResult(final ActivityResult result) {
        assureCorrectAppLanguage(getContext());
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            // will be saved only on success
            final Uri lastImportDataUri = result.getData().getData();

            final StoredFileHelper file
                    = new StoredFileHelper(getContext(), result.getData().getData(), ZIP_MIME_TYPE);

            new AlertDialog.Builder(requireActivity())
                    .setMessage(R.string.override_current_data)
                    .setPositiveButton(R.string.finish, (d, id) ->
                            importDatabase(file, lastImportDataUri))
                    .setNegativeButton(R.string.cancel, (d, id) ->
                            d.cancel())
                    .create()
                    .show();
        }
    }

    private void exportDatabase(final StoredFileHelper file, final Uri exportDataUri) {
        try {
            //checkpoint before export
            NewPipeDatabase.checkpoint();

            final SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(requireContext());
            manager.exportDatabase(preferences, file);

            saveLastImportExportDataUri(exportDataUri); // save export path only on success
            Toast.makeText(getContext(), R.string.export_complete_toast, Toast.LENGTH_SHORT).show();
        } catch (final Exception e) {
            ErrorActivity.reportUiErrorInSnackbar(this, "Exporting database", e);
        }
    }

    private void importDatabase(final StoredFileHelper file, final Uri importDataUri) {
        // check if file is supported
        if (!ZipHelper.isValidZipFile(file)) {
            Toast.makeText(getContext(), R.string.no_valid_zip_file, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        try {
            if (!manager.ensureDbDirectoryExists()) {
                throw new IOException("Could not create databases dir");
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
                    finishImport(importDataUri);
                });
                alert.setPositiveButton(getString(R.string.finish), (dialog, which) -> {
                    dialog.dismiss();
                    manager.loadSharedPreferences(PreferenceManager
                            .getDefaultSharedPreferences(requireContext()));
                    finishImport(importDataUri);
                });
                alert.show();
            } else {
                finishImport(importDataUri);
            }
        } catch (final Exception e) {
            ErrorActivity.reportUiErrorInSnackbar(this, "Importing database", e);
        }
    }

    /**
     * Save import path and restart system.
     *
     * @param importDataUri The import path to save
     */
    private void finishImport(final Uri importDataUri) {
        // save import path only on success
        saveLastImportExportDataUri(importDataUri);
        // restart app to properly load db
        NavigationHelper.restartApp(requireActivity());
    }

    private Uri getImportExportDataUri() {
        final String path = defaultPreferences.getString(importExportDataPathKey, null);
        return isBlank(path) ? null : Uri.parse(path);
    }

    private void saveLastImportExportDataUri(final Uri importExportDataUri) {
        final SharedPreferences.Editor editor = defaultPreferences.edit()
                .putString(importExportDataPathKey, importExportDataUri.toString());
        editor.apply();
    }
}
