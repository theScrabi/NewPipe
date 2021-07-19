package org.schabi.newpipe.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.util.DeviceUtils;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.schabi.newpipe.MainActivity.DEBUG;

/**
 * In order to add a migration, follow these steps, given P is the previous version:<br>
 * - in the class body add a new {@code MIGRATION_P_P+1 = new Migration(P, P+1) { ... }} and put in
 *   the {@code migrate()} method the code that need to be run when migrating from P to P+1<br>
 * - add {@code MIGRATION_P_P+1} at the end of {@link SettingMigrations#SETTING_MIGRATIONS}<br>
 * - increment {@link SettingMigrations#VERSION}'s value by 1 (so it should become P+1)
 */
public final class SettingMigrations {

    private static final String TAG = SettingMigrations.class.toString();
    private static SharedPreferences sp;

    public static final Migration MIGRATION_0_1 = new Migration(0, 1) {
        @Override
        public void migrate(final Context context) {
            // We changed the content of the dialog which opens when sharing a link to NewPipe
            // by removing the "open detail page" option.
            // Therefore, show the dialog once again to ensure users need to choose again and are
            // aware of the changed dialog.
            final SharedPreferences.Editor editor = sp.edit();
            editor.putString(context.getString(R.string.preferred_open_action_key),
                    context.getString(R.string.always_ask_open_action_key));
            editor.apply();
        }
    };

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        protected void migrate(final Context context) {
            // The new application workflow introduced in #2907 allows minimizing videos
            // while playing to do other stuff within the app.
            // For an even better workflow, we minimize a stream when switching the app to play in
            // background.
            // Therefore, set default value to background, if it has not been changed yet.
            final String minimizeOnExitKey = context.getString(R.string.minimize_on_exit_key);
            if (sp.getString(minimizeOnExitKey, "")
                    .equals(context.getString(R.string.minimize_on_exit_none_key))) {
                final SharedPreferences.Editor editor = sp.edit();
                editor.putString(minimizeOnExitKey,
                        context.getString(R.string.minimize_on_exit_background_key));
                editor.apply();
            }
        }
    };

    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        protected void migrate(final Context context) {
            // Storage Access Framework implementation was improved in #5415, allowing the modern
            // and standard way to access folders and files to be used consistently everywhere.
            // We reset the setting to its default value, i.e. "use SAF", since now there are no
            // more issues with SAF and users should use that one instead of the old
            // NoNonsenseFilePicker. SAF does not work on KitKat and below, though, so the setting
            // is set to false in that case. Also, there's a bug on FireOS in which SAF open/close
            // dialogs cannot be confirmed with a remote (see #6455).
            sp.edit().putBoolean(context.getString(R.string.storage_use_saf),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            && !DeviceUtils.isFireTv()).apply();
        }
    };

    public static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        protected void migrate(final Context context) {
            // Pull request #3546 added support for choosing the type of search suggestions to
            // show, replacing the on-off switch used before, so migrate the previous user choice

            final String showSearchSuggestionsKey =
                    context.getString(R.string.show_search_suggestions_key);

            boolean addAllSearchSuggestionTypes;
            try {
                addAllSearchSuggestionTypes = sp.getBoolean(showSearchSuggestionsKey, true);
            } catch (final ClassCastException e) {
                // just in case it was not a boolean for some reason, let's consider it a "true"
                addAllSearchSuggestionTypes = true;
            }

            final Set<String> showSearchSuggestionsValueList = new HashSet<>();
            if (addAllSearchSuggestionTypes) {
                // if the preference was true, all suggestions will be shown, otherwise none
                Collections.addAll(showSearchSuggestionsValueList, context.getResources()
                        .getStringArray(R.array.show_search_suggestions_value_list));
            }

            sp.edit().putStringSet(
                    showSearchSuggestionsKey, showSearchSuggestionsValueList).apply();
        }
    };

    /**
     * List of all implemented migrations.
     * <p>
     * <b>Append new migrations to the end of the list</b> to keep it sorted ascending.
     * If not sorted correctly, migrations which depend on each other, may fail.
     */
    private static final Migration[] SETTING_MIGRATIONS = {
            MIGRATION_0_1,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
    };

    /**
     * Version number for preferences. Must be incremented every time a migration is necessary.
     */
    public static final int VERSION = 4;


    public static void initMigrations(final Context context, final boolean isFirstRun) {
        // setup migrations and check if there is something to do
        sp = PreferenceManager.getDefaultSharedPreferences(context);
        final String lastPrefVersionKey = context.getString(R.string.last_used_preferences_version);
        final int lastPrefVersion = sp.getInt(lastPrefVersionKey, 0);

        // no migration to run, already up to date
        if (isFirstRun) {
            sp.edit().putInt(lastPrefVersionKey, VERSION).apply();
            return;
        } else if (lastPrefVersion == VERSION) {
            return;
        }

        // run migrations
        int currentVersion = lastPrefVersion;
        for (final Migration currentMigration : SETTING_MIGRATIONS) {
            try {
                if (currentMigration.shouldMigrate(currentVersion)) {
                    if (DEBUG) {
                        Log.d(TAG, "Migrating preferences from version "
                                + currentVersion + " to " + currentMigration.newVersion);
                    }
                    currentMigration.migrate(context);
                    currentVersion = currentMigration.newVersion;
                }
            } catch (final Exception e) {
                // save the version with the last successful migration and report the error
                sp.edit().putInt(lastPrefVersionKey, currentVersion).apply();
                ErrorActivity.reportError(context, new ErrorInfo(
                        e,
                        UserAction.PREFERENCES_MIGRATION,
                        "Migrating preferences from version " + lastPrefVersion + " to "
                                + VERSION + ". "
                                + "Error at " + currentVersion  + " => " + ++currentVersion
                ));
                return;
            }
        }

        // store the current preferences version
        sp.edit().putInt(lastPrefVersionKey, currentVersion).apply();
    }

    private SettingMigrations() { }

    abstract static class Migration {
        public final int oldVersion;
        public final int newVersion;

        protected Migration(final int oldVersion, final int newVersion) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
        }

        /**
         * @param currentVersion current settings version
         * @return Returns whether this migration should be run.
         * A migration is necessary if the old version of this migration is lower than or equal to
         * the current settings version.
         */
        private boolean shouldMigrate(final int currentVersion) {
            return oldVersion >= currentVersion;
        }

        protected abstract void migrate(Context context);

    }

}
