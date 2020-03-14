package org.schabi.newpipe.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.SwitchPreference;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.textfield.TextInputEditText;
import com.nononsenseapps.filepicker.Utils;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.BackupRestoreHelper;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.PermissionHelper;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class BackupSettingsFragment extends BasePreferenceFragment {

    private static final int REQUEST_IMPORT_PATH = 8945;
    private static final int REQUEST_EXPORT_PATH = 30945;
    private static final int REQUEST_AUTO_BACKUP_PATH = 40945;

    private static final int BACKUP_STORAGE_PERMISSION_REQUEST_CODE = 44543;

    public static final String TAG_AUTO_BACKUP_WORK = "TAG_AUTO_BACKUP_WORK";

    private Context ctx;
    private BackupRestoreHelper backupRestoreHelper;

    private String BACKUP_PATH_PREFERENCE_KEY;
    private Preference autoBackupPathPreference;
    private SwitchPreference autoBackupSwitchPreference;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void showMessageDialog(@StringRes int title, @StringRes int message) {
        AlertDialog.Builder msg = new AlertDialog.Builder(ctx);
        msg.setTitle(title);
        msg.setMessage(message);
        msg.setPositiveButton(getString(R.string.finish), null);
        msg.show();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        backupRestoreHelper = new BackupRestoreHelper(ctx);

        BACKUP_PATH_PREFERENCE_KEY = getString(R.string.backup_path_key);

        addPreferencesFromResource(R.xml.backup_restore_settings);

        Preference importDataPreference = findPreference(getString(R.string.import_data));
        importDataPreference.setOnPreferenceClickListener((Preference p) -> {
            Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_FILE);
            startActivityForResult(i, REQUEST_IMPORT_PATH);
            return true;
        });

        Preference exportDataPreference = findPreference(getString(R.string.export_data));
        exportDataPreference.setOnPreferenceClickListener((Preference p) -> {
            Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_DIR);
            startActivityForResult(i, REQUEST_EXPORT_PATH);
            return true;
        });

        autoBackupPathPreference = findPreference(BACKUP_PATH_PREFERENCE_KEY);
        autoBackupPathPreference.setOnPreferenceClickListener(preference -> {
            Intent i = new Intent(getActivity(), FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_DIR);
            startActivityForResult(i, REQUEST_AUTO_BACKUP_PATH);
            return true;
        });
        autoBackupPathPreference.setSummary(backupRestoreHelper.getAutoBackupPath());

        autoBackupSwitchPreference = findPreference(getString(R.string.scheduled_backups_key));
        autoBackupSwitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if((Boolean) newValue) PermissionHelper.checkStoragePermissions(getActivity(), BACKUP_STORAGE_PERMISSION_REQUEST_CODE);
            return true;
        });

        Boolean autoBackup = defaultPreferences.getBoolean(getString(R.string.scheduled_backups_key), false);
        if(autoBackup) PermissionHelper.checkStoragePermissions(getActivity(), BACKUP_STORAGE_PERMISSION_REQUEST_CODE);

    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        ctx = context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        ctx = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void showPathInSummary(String prefKey, @StringRes int defaultString, Preference target) {
        String rawUri = defaultPreferences.getString(prefKey, null);
        if (rawUri == null || rawUri.isEmpty()) {
            target.setSummary(getString(defaultString));
            return;
        }

        if (rawUri.charAt(0) == File.separatorChar) {
            target.setSummary(rawUri);
            return;
        }
        if (rawUri.startsWith(ContentResolver.SCHEME_FILE)) {
            target.setSummary(new File(URI.create(rawUri)).getPath());
            return;
        }

        try {
            rawUri = URLDecoder.decode(rawUri, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            // nothing to do
        }

        target.setSummary(rawUri);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @NonNull Intent data) {
        assureCorrectAppLanguage(getContext());
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK) return;

        if ((requestCode == REQUEST_IMPORT_PATH || requestCode == REQUEST_EXPORT_PATH) && data.getData() != null) {
            String path = Utils.getFileForUri(data.getData()).getAbsolutePath();
            if (requestCode == REQUEST_EXPORT_PATH) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
                exportDatabase(path + "/NewPipeData-" + sdf.format(new Date()) + ".zip");
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                builder.setMessage(R.string.override_current_data)
                        .setPositiveButton(getString(R.string.finish),
                                (DialogInterface d, int id) -> importDatabase(path))
                        .setNegativeButton(android.R.string.cancel,
                                (DialogInterface d, int id) -> d.cancel());
                builder.create().show();
            }
        } else if (requestCode == REQUEST_AUTO_BACKUP_PATH){
            Uri uri = data.getData();
            if (uri == null) {
                showMessageDialog(R.string.general_error, R.string.invalid_directory);
                return;
            }

            File target = Utils.getFileForUri(uri);
            if (!target.canWrite()) {
                showMessageDialog(R.string.download_to_sdcard_error_title, R.string.download_to_sdcard_error_message);
                return;
            }
            uri = Uri.fromFile(target);

            defaultPreferences.edit().putString(BACKUP_PATH_PREFERENCE_KEY, uri.toString()).apply();
            showPathInSummary(BACKUP_PATH_PREFERENCE_KEY, R.string.download_path_summary, autoBackupPathPreference);
        } else{
            return;
        }
    }

    private void exportDatabase(String path) {

        AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
        LayoutInflater inflater = LayoutInflater.from(ctx);
        View view = inflater.inflate(R.layout.dialog_password, null);
        TextInputEditText editText = view.findViewById(android.R.id.edit);
        alert.setView(view);
        alert.setTitle(R.string.auto_backup_password_title);
        alert.setMessage(R.string.backup_password_message);

        alert.setNegativeButton(R.string.backup_no_password, (dialog, which) -> {
            dialog.dismiss();
            try {
                backupRestoreHelper.exportDatabase(path, null);
                Toast.makeText(ctx, R.string.export_complete_toast, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                onError(e);
            }
        });
        alert.setPositiveButton(R.string.finish, (dialog, which) -> {
            dialog.dismiss();
            char[] password = getPassword(editText);
            try {
                backupRestoreHelper.exportDatabase(path, password);
                Toast.makeText(ctx, R.string.export_complete_toast, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                onError(e);
            } finally {
                clearPassword(password);
            }
        });

        alert.show();
    }

    private char[] getPassword(TextInputEditText editText) {
        int length = editText.length();
        char[] password = new char[length];
        editText.getText().getChars(0, length, password, 0);
        return password;
    }

    private void clearPassword(char[] password){
        Arrays.fill(password,'0');
    }

    private void importDatabase(String filePath) {

        ZipFile zipFile = new ZipFile(filePath);
        if(!zipFile.isValidZipFile()){
            Toast.makeText(ctx, R.string.no_valid_zip_file, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if(zipFile.isEncrypted()){
                AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
                LayoutInflater inflater = LayoutInflater.from(ctx);
                View view = inflater.inflate(R.layout.dialog_password, null);
                TextInputEditText editText = view.findViewById(android.R.id.edit);
                alert.setView(view);

                alert.setTitle(R.string.auto_backup_password_title);

                alert.setNegativeButton(android.R.string.no, (dialog, which) -> {
                    dialog.dismiss();
                });
                alert.setPositiveButton(R.string.finish, (dialog, which) -> {
                    dialog.dismiss();
                    char[] password = getPassword(editText);
                    try {
                        backupRestoreHelper.importDatabase(filePath, password);
                    } catch (Exception e) {
                        onError(e);
                    } finally {
                        clearPassword(password);
                    }
                });

                alert.show();
            }else{
                backupRestoreHelper.importDatabase(filePath, null);
            }
        } catch (Exception e) {
            onError(e);
        }
    }

    private void scheduleWork(String tag) {
        Boolean autoBackup = defaultPreferences.getBoolean(getString(R.string.scheduled_backups_key), false);
        if(autoBackup){
            SharedPreferences encryptedSharedPreferences = EncryptedSharedPreferencesHelper.create(requireContext());
            if(encryptedSharedPreferences == null || TextUtils.isEmpty(encryptedSharedPreferences.getString(ctx.getString(R.string.backup_password_key), null))){
                Toast.makeText(ctx, R.string.auto_backup_password_mandatory, Toast.LENGTH_LONG).show();
            }
            Integer interval = Integer.valueOf(defaultPreferences.getString(getString(R.string.backup_frequency_key), "24"));
            PeriodicWorkRequest.Builder autoBackupRequestBuilder =
                    new PeriodicWorkRequest.Builder(AutoBackupWorker.class, interval, TimeUnit.HOURS);
            autoBackupRequestBuilder.setInitialDelay(15, TimeUnit.MINUTES);
            PeriodicWorkRequest request = autoBackupRequestBuilder.build();
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(tag, ExistingPeriodicWorkPolicy.REPLACE , request);
        }else{
            WorkManager.getInstance(ctx).cancelUniqueWork(tag);
        }
    }


    @Override
    public void onPause() {
        scheduleWork(TAG_AUTO_BACKUP_WORK);
        super.onPause();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error
    //////////////////////////////////////////////////////////////////////////*/

    protected void onError(Throwable e) {

        if(e instanceof ZipException && ((ZipException)e).getType() == ZipException.Type.WRONG_PASSWORD){
            Toast.makeText(ctx, R.string.no_valid_password, Toast.LENGTH_SHORT).show();
        }else{
            final Activity activity = getActivity();
            ErrorActivity.reportError(activity, e,
                    activity.getClass(),
                    null,
                    ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                            "none", "", R.string.app_ui_crash));
        }
    }

}
