package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.nononsenseapps.filepicker.AbstractFilePickerFragment;
import com.nononsenseapps.filepicker.FilePickerFragment;

import org.schabi.newpipe.R;

import java.io.File;

public class FilePickerActivityHelper extends com.nononsenseapps.filepicker.FilePickerActivity {
    public static final String EXTRA_FILTER_EXTENSION = "nononsense.intent" + ".FILTER_EXTENSION";

    private CustomFilePickerFragment currentFragment;
    private String filteredExtension = null;

    public static Intent chooseSingleFile(@NonNull final Context context) {
        return new Intent(context, FilePickerActivityHelper.class)
                .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, false)
                .putExtra(FilePickerActivityHelper.EXTRA_SINGLE_CLICK, true)
                .putExtra(FilePickerActivityHelper.EXTRA_MODE, FilePickerActivityHelper.MODE_FILE);
    }

    public static Intent chooseFileToSave(@NonNull final Context context,
                                          @Nullable final String startPath) {
        return new Intent(context, FilePickerActivityHelper.class)
                .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_EXISTING_FILE, true)
                .putExtra(FilePickerActivityHelper.EXTRA_START_PATH, startPath)
                .putExtra(FilePickerActivityHelper.EXTRA_MODE,
                        FilePickerActivityHelper.MODE_NEW_FILE);
    }

    public static boolean isOwnFileUri(@NonNull final Context context, @NonNull final Uri uri) {
        if (uri.getAuthority() == null) {
            return false;
        }
        return uri.getAuthority().startsWith(context.getPackageName());
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if (ThemeHelper.isLightThemeSelected(this)) {
            this.setTheme(R.style.FilePickerThemeLight);
        } else {
            this.setTheme(R.style.FilePickerThemeDark);
        }
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();
        if (intent != null) {
            filteredExtension = intent.getStringExtra(EXTRA_FILTER_EXTENSION);
        }
    }

    @Override
    public void onBackPressed() {
        // If at top most level, normal behaviour
        if (currentFragment.isBackTop()) {
            super.onBackPressed();
        } else {
            // Else go up
            currentFragment.goUp();
        }
    }

    @Override
    protected AbstractFilePickerFragment<File> getFragment(@Nullable final String startPath,
                                                           final int mode,
                                                           final boolean allowMultiple,
                                                           final boolean allowCreateDir,
                                                           final boolean allowExistingFile,
                                                           final boolean singleClick) {
        final CustomFilePickerFragment fragment = new CustomFilePickerFragment();
        fragment.setFilterFileExtension(filteredExtension);
        fragment.setArgs(startPath != null ? startPath
                        : Environment.getExternalStorageDirectory().getPath(),
                mode, allowMultiple, allowCreateDir, allowExistingFile, singleClick);
        currentFragment = fragment;
        return currentFragment;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Internal
    //////////////////////////////////////////////////////////////////////////*/

    public static class CustomFilePickerFragment extends FilePickerFragment {
        private String filterFileExtension = "";

        @Override
        public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                                 final Bundle savedInstanceState) {
            return super.onCreateView(inflater, container, savedInstanceState);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                          final int viewType) {
            final RecyclerView.ViewHolder viewHolder = super.onCreateViewHolder(parent, viewType);

            final View view = viewHolder.itemView.findViewById(android.R.id.text1);
            if (view instanceof TextView) {
                ((TextView) view).setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        getResources().getDimension(R.dimen.file_picker_items_text_size));
            }

            return viewHolder;
        }

        @Override
        public void onClickOk(@NonNull final View view) {
            if (mode == MODE_NEW_FILE && getNewFileName().isEmpty()) {
                if (mToast != null) {
                    mToast.cancel();
                }
                mToast = Toast.makeText(getActivity(), R.string.file_name_empty_error,
                        Toast.LENGTH_SHORT);
                mToast.show();
                return;
            }

            super.onClickOk(view);
        }

        /**
         * @param file File getting the extension from.
         * @return The file extension. If file has no extension, it returns null.
         */
        @Nullable
        private String getExtension(@NonNull final File file) {
            final String path = file.getPath();
            final int i = path.lastIndexOf(".");
            if (i < 0) {
                return null;
            } else {
                return path.substring(i);
            }
        }

        @Override
        protected boolean isItemVisible(@NonNull final File file) {
            if (file.isDirectory() && file.isHidden()) {
                return true;
            }
            final boolean ret = super.isItemVisible(file);
            if (ret && !isDir(file) && !filterFileExtension.isEmpty()
                    && (mode == MODE_FILE || mode == MODE_FILE_AND_DIR)) {
                final String ext = getExtension(file);
                return ext != null && filterFileExtension.equalsIgnoreCase(ext);
            }
            return ret;
        }

        /**
         * Limits the shown file types within the picker to the provided one.
         *
         * @param extension Extension of the files which should filtered, for example, "zip".
         */
        public void setFilterFileExtension(@Nullable final String extension) {
            if (extension != null && !extension.isEmpty()) {
                this.filterFileExtension = "." + extension;
            }
        }

        public File getBackTop() {
            if (getArguments() == null) {
                return Environment.getExternalStorageDirectory();
            }

            final String path = getArguments().getString(KEY_START_PATH, "/");
            if (path.contains(Environment.getExternalStorageDirectory().getPath())) {
                return Environment.getExternalStorageDirectory();
            }

            return getPath(path);
        }

        public boolean isBackTop() {
            return compareFiles(mCurrentPath,
                    getBackTop()) == 0 || compareFiles(mCurrentPath, new File("/")) == 0;
        }

        @Override
        public void onLoadFinished(final Loader<SortedList<File>> loader,
                                   final SortedList<File> data) {
            super.onLoadFinished(loader, data);
            layoutManager.scrollToPosition(0);
        }
    }
}
