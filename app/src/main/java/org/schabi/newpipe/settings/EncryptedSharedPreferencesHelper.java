package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.MainActivity;
import org.yausername.encryptedsharedpreferences.EncryptedSharedPreferences;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class EncryptedSharedPreferencesHelper {

    private static final String TAG = "EncSharedPrefHelper";
    private static final boolean DEBUG = MainActivity.DEBUG;

    private static final String FILE_NAME = "encryptedPrefs";
    private static final String MASTER_KEY_ALIAS = "_newpipe_security_master_key_";

    @Nullable
    public static SharedPreferences create(@NonNull Context context){
        try {
            EncryptedSharedPreferences.PrefValueEncryptionScheme prefValueEncryptionScheme;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                prefValueEncryptionScheme = EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM;
            }else{
                prefValueEncryptionScheme = EncryptedSharedPreferences.PrefValueEncryptionScheme.XCHACHA20_POLY1305;
            }
            return EncryptedSharedPreferences.create(FILE_NAME, MASTER_KEY_ALIAS, context, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, prefValueEncryptionScheme);
        } catch (GeneralSecurityException | IOException e) {
            if(DEBUG) Log.e(TAG, "failed to create encrypted preferences", e);
            return null;
        }
    }
}
