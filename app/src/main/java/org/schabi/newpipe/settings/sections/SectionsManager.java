package org.schabi.newpipe.settings.sections;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.List;

public final class SectionsManager {
    private final SharedPreferences sharedPreferences;
    private final String savedSectionsKey;
    private final Context context;
    private SavedSectionsChangeListener savedSectionsChangeListener;
    private SharedPreferences.OnSharedPreferenceChangeListener preferenceChangeListener;

    private SectionsManager(final Context context) {
        this.context = context;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.savedSectionsKey = context.getString(R.string.saved_sections_key);
    }

    public static SectionsManager getManager(final Context context) {
        return new SectionsManager(context);
    }

    public void saveSections(final List<Section> sectionList) {
        final String jsonToSave = SectionsJsonHelper.getJsonToSave(sectionList);
        sharedPreferences.edit().putString(savedSectionsKey, jsonToSave).apply();
    }

    public List<Section> getSections() {
        final String savedJson = sharedPreferences.getString(savedSectionsKey, null);
        try {
            return SectionsJsonHelper.getSectionsFromJson(savedJson);
        } catch (final SectionsJsonHelper.InvalidJsonException e) {
            Toast.makeText(context, R.string.saved_sections_invalid_json, Toast.LENGTH_SHORT)
                    .show();
            return getDefaultSections();
        }
    }

    public void resetSections() {
        sharedPreferences.edit().remove(savedSectionsKey).apply();
    }

    public List<Section> getDefaultSections() {
        return SectionsJsonHelper.getDefaultSections();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Listener
    //////////////////////////////////////////////////////////////////////////*/

    public void setSavedSectionsListener(final SavedSectionsChangeListener listener) {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        savedSectionsChangeListener = listener;
        preferenceChangeListener = getPreferenceChangeListener();
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener);
    }

    public void unsetSavedSectionsListener() {
        if (preferenceChangeListener != null) {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener);
        }
        savedSectionsChangeListener = null;
        preferenceChangeListener = null;
    }

    private SharedPreferences.OnSharedPreferenceChangeListener getPreferenceChangeListener() {
        return (sp, key) -> {
            if (key.equals(savedSectionsKey)) {
                if (savedSectionsChangeListener != null) {
                    savedSectionsChangeListener.onSectionsChanged();
                }
            }
        };
    }

    public interface SavedSectionsChangeListener {
        void onSectionsChanged();
    }
}
