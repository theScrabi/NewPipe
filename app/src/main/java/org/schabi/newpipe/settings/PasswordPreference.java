package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.preference.EditTextPreference;

public class PasswordPreference extends EditTextPreference {

    private final SharedPreferences encryptedSharedPreferences;

    public PasswordPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        encryptedSharedPreferences = initEncryptedSharedPreferences();
    }

    public PasswordPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        encryptedSharedPreferences = initEncryptedSharedPreferences();
    }

    public PasswordPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        encryptedSharedPreferences = initEncryptedSharedPreferences();
    }

    public PasswordPreference(Context context) {
        super(context);
        encryptedSharedPreferences = initEncryptedSharedPreferences();
    }

    private SharedPreferences initEncryptedSharedPreferences(){
        return EncryptedSharedPreferencesHelper.create(getContext());
    }

    @Override
    public void setText(String text) {
        final boolean wasBlocking = shouldDisableDependents();

        setPassword(text);

        final boolean isBlocking = shouldDisableDependents();
        if (isBlocking != wasBlocking) {
            notifyDependencyChange(isBlocking);
        }

        notifyChanged();
    }

    @Override
    public String getText() {
        return getPassword();
    }

    @Override
    protected void onSetInitialValue(Object defaultValue) {
        String password = getPassword();
        setText(password != null ? password : (String) defaultValue);
    }

    private String getPassword() {
        if (encryptedSharedPreferences == null){
            return null;
        }
        return encryptedSharedPreferences.getString(getKey(), null);
    }

    private void setPassword(String password){
        if(encryptedSharedPreferences == null){
            Toast.makeText(getContext(), "Failed to save password", Toast.LENGTH_SHORT).show();
            return;
        }
        encryptedSharedPreferences.edit().putString(getKey(), password).apply();
    }

    @Override
    public boolean shouldDisableDependents() {
        return TextUtils.isEmpty(getText()) || !isEnabled();
    }
}
