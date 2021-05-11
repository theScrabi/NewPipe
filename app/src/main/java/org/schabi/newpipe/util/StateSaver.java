/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * StateSaver.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util;


import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A way to save state to disk or in a in-memory map
 * if it's just changing configurations (i.e. rotating the phone).
 */
public final class StateSaver {
    public static final String KEY_SAVED_STATE = "key_saved_state";
    private static final ConcurrentHashMap<String, Queue<Object>> STATE_OBJECTS_HOLDER
            = new ConcurrentHashMap<>();
    private static final String TAG = "StateSaver";
    private static final String CACHE_DIR_NAME = "state_cache";
    private static String cacheDirPath;

    private StateSaver() {
        //no instance
    }

    /**
     * Initialize the StateSaver, usually you want to call this in the Application class.
     *
     * @param context used to get the available cache dir
     */
    public static void init(final Context context) {
        final File externalCacheDir = context.getExternalCacheDir();
        if (externalCacheDir != null) {
            cacheDirPath = externalCacheDir.getAbsolutePath();
        }
        if (TextUtils.isEmpty(cacheDirPath)) {
            cacheDirPath = context.getCacheDir().getAbsolutePath();
        }
    }

    /**
     * @param outState
     * @param writeRead
     * @return the saved state
     * @see #tryToRestore(SavedState, WriteRead)
     */
    public static SavedState tryToRestore(final Bundle outState, final WriteRead writeRead) {
        if (outState == null || writeRead == null) {
            return null;
        }

        final SavedState savedState = outState.getParcelable(KEY_SAVED_STATE);
        if (savedState == null) {
            return null;
        }

        return tryToRestore(savedState, writeRead);
    }

    /**
     * Try to restore the state from memory and disk,
     * using the {@link StateSaver.WriteRead#readFrom(Queue)} from the writeRead.
     *
     * @param savedState
     * @param writeRead
     * @return the saved state
     */
    @Nullable
    private static SavedState tryToRestore(@NonNull final SavedState savedState,
                                           @NonNull final WriteRead writeRead) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "tryToRestore() called with: savedState = [" + savedState + "], "
                    + "writeRead = [" + writeRead + "]");
        }

        try {
            Queue<Object> savedObjects
                    = STATE_OBJECTS_HOLDER.remove(savedState.getPrefixFileSaved());
            if (savedObjects != null) {
                writeRead.readFrom(savedObjects);
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "tryToSave: reading objects from holder > " + savedObjects
                            + ", stateObjectsHolder > " + STATE_OBJECTS_HOLDER);
                }
                return savedState;
            }

            final File file = new File(savedState.getPathFileSaved());
            if (!file.exists()) {
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "Cache file doesn't exist: " + file.getAbsolutePath());
                }
                return null;
            }

            try (FileInputStream fileInputStream = new FileInputStream(file);
                 ObjectInputStream inputStream = new ObjectInputStream(fileInputStream)) {
                //noinspection unchecked
                savedObjects = (Queue<Object>) inputStream.readObject();
            }

            if (savedObjects != null) {
                writeRead.readFrom(savedObjects);
            }

            return savedState;
        } catch (final Exception e) {
            Log.e(TAG, "Failed to restore state", e);
        }
        return null;
    }

    /**
     * @param isChangingConfig
     * @param savedState
     * @param outState
     * @param writeRead
     * @return the saved state or {@code null}
     * @see #tryToSave(boolean, String, String, WriteRead)
     */
    @Nullable
    public static SavedState tryToSave(final boolean isChangingConfig,
                                       @Nullable final SavedState savedState, final Bundle outState,
                                       final WriteRead writeRead) {
        @NonNull final String currentSavedPrefix;
        if (savedState == null || TextUtils.isEmpty(savedState.getPrefixFileSaved())) {
            // Generate unique prefix
            currentSavedPrefix = System.nanoTime() - writeRead.hashCode() + "";
        } else {
            // Reuse prefix
            currentSavedPrefix = savedState.getPrefixFileSaved();
        }

        final SavedState newSavedState = tryToSave(isChangingConfig, currentSavedPrefix,
                writeRead.generateSuffix(), writeRead);
        if (newSavedState != null) {
            outState.putParcelable(StateSaver.KEY_SAVED_STATE, newSavedState);
            return newSavedState;
        }

        return null;
    }

    /**
     * If it's not changing configuration (i.e. rotating screen),
     * try to write the state from {@link StateSaver.WriteRead#writeTo(Queue)}
     * to the file with the name of prefixFileName + suffixFileName,
     * in a cache folder got from the {@link #init(Context)}.
     * <p>
     * It checks if the file already exists and if it does, just return the path,
     * so a good way to save is:
     * </p>
     * <ul>
     * <li>A fixed prefix for the file</li>
     * <li>A changing suffix</li>
     * </ul>
     *
     * @param isChangingConfig
     * @param prefixFileName
     * @param suffixFileName
     * @param writeRead
     * @return the saved state or {@code null}
     */
    @Nullable
    private static SavedState tryToSave(final boolean isChangingConfig, final String prefixFileName,
                                        final String suffixFileName, final WriteRead writeRead) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "tryToSave() called with: "
                    + "isChangingConfig = [" + isChangingConfig + "], "
                    + "prefixFileName = [" + prefixFileName + "], "
                    + "suffixFileName = [" + suffixFileName + "], "
                    + "writeRead = [" + writeRead + "]");
        }

        final LinkedList<Object> savedObjects = new LinkedList<>();
        writeRead.writeTo(savedObjects);

        if (isChangingConfig) {
            if (savedObjects.size() > 0) {
                STATE_OBJECTS_HOLDER.put(prefixFileName, savedObjects);
                return new SavedState(prefixFileName, "");
            } else {
                if (MainActivity.DEBUG) {
                    Log.d(TAG, "Nothing to save");
                }
                return null;
            }
        }

        try {
            File cacheDir = new File(cacheDirPath);
            if (!cacheDir.exists()) {
                throw new RuntimeException("Cache dir does not exist > " + cacheDirPath);
            }
            cacheDir = new File(cacheDir, CACHE_DIR_NAME);
            if (!cacheDir.exists()) {
                if (!cacheDir.mkdir()) {
                    if (BuildConfig.DEBUG) {
                        Log.e(TAG,
                                "Failed to create cache directory " + cacheDir.getAbsolutePath());
                    }
                    return null;
                }
            }

            final File file = new File(cacheDir, prefixFileName
                    + (TextUtils.isEmpty(suffixFileName) ? ".cache" : suffixFileName));
            if (file.exists() && file.length() > 0) {
                // If the file already exists, just return it
                return new SavedState(prefixFileName, file.getAbsolutePath());
            } else {
                // Delete any file that contains the prefix
                final File[] files = cacheDir.listFiles((dir, name) ->
                        name.contains(prefixFileName));
                for (final File fileToDelete : files) {
                    fileToDelete.delete();
                }
            }

            try (FileOutputStream fileOutputStream = new FileOutputStream(file);
                 ObjectOutputStream outputStream = new ObjectOutputStream(fileOutputStream)) {
                outputStream.writeObject(savedObjects);
            }

            return new SavedState(prefixFileName, file.getAbsolutePath());
        } catch (final Exception e) {
            Log.e(TAG, "Failed to save state", e);
        }
        return null;
    }

    /**
     * Delete the cache file contained in the savedState.
     * Also remove any possible-existing value in the memory-cache.
     *
     * @param savedState the saved state to delete
     */
    public static void onDestroy(final SavedState savedState) {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "onDestroy() called with: savedState = [" + savedState + "]");
        }

        if (savedState != null && !savedState.getPathFileSaved().isEmpty()) {
            STATE_OBJECTS_HOLDER.remove(savedState.getPrefixFileSaved());
            try {
                //noinspection ResultOfMethodCallIgnored
                new File(savedState.getPathFileSaved()).delete();
            } catch (final Exception ignored) {
            }
        }
    }

    /**
     * Clear all the files in cache (in memory and disk).
     */
    public static void clearStateFiles() {
        if (MainActivity.DEBUG) {
            Log.d(TAG, "clearStateFiles() called");
        }

        STATE_OBJECTS_HOLDER.clear();
        File cacheDir = new File(cacheDirPath);
        if (!cacheDir.exists()) {
            return;
        }

        cacheDir = new File(cacheDir, CACHE_DIR_NAME);
        if (cacheDir.exists()) {
            final File[] list = cacheDir.listFiles();
            if (list != null) {
                for (final File file : list) {
                    file.delete();
                }
            }
        }
    }

    /**
     * Used for describe how to save/read the objects.
     * <p>
     * Queue was chosen by its FIFO property.
     */
    public interface WriteRead {
        /**
         * Generate a changing suffix that will name the cache file,
         * and be used to identify if it changed (thus reducing useless reading/saving).
         *
         * @return a unique value
         */
        String generateSuffix();

        /**
         * Add to this queue objects that you want to save.
         *
         * @param objectsToSave the objects to save
         */
        void writeTo(Queue<Object> objectsToSave);

        /**
         * Poll saved objects from the queue in the order they were written.
         *
         * @param savedObjects queue of objects returned by {@link #writeTo(Queue)}
         */
        void readFrom(@NonNull Queue<Object> savedObjects) throws Exception;
    }
}
