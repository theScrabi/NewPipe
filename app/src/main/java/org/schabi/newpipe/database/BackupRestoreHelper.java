package org.schabi.newpipe.database;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.widget.Toast;

import androidx.annotation.MainThread;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ZipHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

public class BackupRestoreHelper {

    private File databasesDir;
    private File newpipe_db;
    private File newpipe_db_journal;
    private File newpipe_db_shm;
    private File newpipe_db_wal;
    private File newpipe_settings;

    private Context ctx;

    public BackupRestoreHelper(Context ctx) {
        this.ctx = ctx;
        String homeDir = ctx.getApplicationInfo().dataDir;
        databasesDir = new File(homeDir + "/databases");
        newpipe_db = new File(homeDir + "/databases/newpipe.db");
        newpipe_db_journal = new File(homeDir + "/databases/newpipe.db-journal");
        newpipe_db_shm = new File(homeDir + "/databases/newpipe.db-shm");
        newpipe_db_wal = new File(homeDir + "/databases/newpipe.db-wal");

        newpipe_settings = new File(homeDir + "/databases/newpipe.settings");
        newpipe_settings.delete();
    }

    public String getAutoBackupPath(){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(ctx);
        String autoBackupPath = sharedPreferences.getString(ctx.getString(R.string.backup_path_key), null);
        if(null == autoBackupPath){
            autoBackupPath = new File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_DOCUMENTS + File.separator + "NewPipeAutoBackup").getAbsolutePath();
        }
        return autoBackupPath;
    }

    public void exportDatabase(String path, char[] password) throws Exception {

        //checkpoint before export
        NewPipeDatabase.checkpoint();

        ZipHelper.addFileToZip(path, newpipe_db.getPath(), "newpipe.db", password);
        saveSharedPreferencesToFile(newpipe_settings);
        ZipHelper.addFileToZip(path, newpipe_settings.getPath(), "newpipe.settings", password);
    }

    private void saveSharedPreferencesToFile(File dst) {
        ObjectOutputStream output = null;
        try {
            output = new ObjectOutputStream(new FileOutputStream(dst));
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(ctx);
            output.writeObject(pref.getAll());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @MainThread
    public void importDatabase(String filePath, char[] password) throws Exception {

        if (!databasesDir.exists() && !databasesDir.mkdir()) {
            throw new Exception("Could not create databases dir");
        }

        final boolean isDbFileExtracted = ZipHelper.extractFileFromZip(filePath, "newpipe.db", databasesDir.getPath(), password);

        if (isDbFileExtracted) {
            newpipe_db_journal.delete();
            newpipe_db_wal.delete();
            newpipe_db_shm.delete();

        } else {

            Toast.makeText(ctx, R.string.could_not_import_all_files, Toast.LENGTH_LONG)
                    .show();
        }

        //If settings file exist, ask if it should be imported.
        if (ZipHelper.extractFileFromZip(filePath, "newpipe.settings", databasesDir.getPath(), password)) {
            AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
            alert.setTitle(R.string.import_settings);

            alert.setNegativeButton(android.R.string.no, (dialog, which) -> {
                dialog.dismiss();
                // restart app to properly load db
                System.exit(0);
            });
            alert.setPositiveButton(ctx.getString(R.string.finish), (dialog, which) -> {
                dialog.dismiss();
                loadSharedPreferences(newpipe_settings);
                // restart app to properly load db
                System.exit(0);
            });
            alert.show();
        } else {
            // restart app to properly load db
            System.exit(0);
        }
    }

    private void loadSharedPreferences(File src) {
        ObjectInputStream input = null;
        try {
            input = new ObjectInputStream(new FileInputStream(src));
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(ctx).edit();
            prefEdit.clear();
            Map<String, ?> entries = (Map<String, ?>) input.readObject();
            for (Map.Entry<String, ?> entry : entries.entrySet()) {
                Object v = entry.getValue();
                String key = entry.getKey();

                if (v instanceof Boolean)
                    prefEdit.putBoolean(key, (Boolean) v);
                else if (v instanceof Float)
                    prefEdit.putFloat(key, (Float) v);
                else if (v instanceof Integer)
                    prefEdit.putInt(key, (Integer) v);
                else if (v instanceof Long)
                    prefEdit.putLong(key, (Long) v);
                else if (v instanceof String)
                    prefEdit.putString(key, ((String) v));
            }
            prefEdit.commit();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
