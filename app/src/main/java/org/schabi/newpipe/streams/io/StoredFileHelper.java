package org.schabi.newpipe.streams.io;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.FilePickerActivityHelper;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;

import us.shandian.giga.io.FileStream;
import us.shandian.giga.io.FileStreamSAF;

public class StoredFileHelper implements Serializable {
    private static final long serialVersionUID = 0L;
    public static final String DEFAULT_MIME = "application/octet-stream";

    private transient DocumentFile docFile;
    private transient DocumentFile docTree;
    private transient File ioFile;
    private transient Context context;

    protected String source;
    private String sourceTree;

    protected String tag;

    private String srcName;
    private String srcType;

    public StoredFileHelper(final Context context, final Uri uri, final String mime) {
        if (FilePickerActivityHelper.isOwnFileUri(context, uri)) {
            ioFile = Utils.getFileForUri(uri);
            source = Uri.fromFile(ioFile).toString();
        } else {
            docFile = DocumentFile.fromSingleUri(context, uri);
            source = uri.toString();
        }

        this.context = context;
        this.srcType = mime;
    }

    public StoredFileHelper(@Nullable final Uri parent, final String filename, final String mime,
                            final String tag) {
        this.source = null; // this instance will be "invalid" see invalidate()/isInvalid() methods

        this.srcName = filename;
        this.srcType = mime == null ? DEFAULT_MIME : mime;
        if (parent != null) {
            this.sourceTree = parent.toString();
        }

        this.tag = tag;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    StoredFileHelper(@Nullable final Context context, final DocumentFile tree,
                     final String filename, final String mime, final boolean safe)
            throws IOException {
        this.docTree = tree;
        this.context = context;

        final DocumentFile res;

        if (safe) {
            // no conflicts (the filename is not in use)
            res = this.docTree.createFile(mime, filename);
            if (res == null) {
                throw new IOException("Cannot create the file");
            }
        } else {
            res = createSAF(context, mime, filename);
        }

        this.docFile = res;

        this.source = docFile.getUri().toString();
        this.sourceTree = docTree.getUri().toString();

        this.srcName = this.docFile.getName();
        this.srcType = this.docFile.getType();
    }

    StoredFileHelper(final File location, final String filename, final String mime)
            throws IOException {
        this.ioFile = new File(location, filename);

        if (this.ioFile.exists()) {
            if (!this.ioFile.isFile() && !this.ioFile.delete()) {
                throw new IOException("The filename is already in use by non-file entity "
                        + "and cannot overwrite it");
            }
        } else {
            if (!this.ioFile.createNewFile()) {
                throw new IOException("Cannot create the file");
            }
        }

        this.source = Uri.fromFile(this.ioFile).toString();
        this.sourceTree = Uri.fromFile(location).toString();

        this.srcName = ioFile.getName();
        this.srcType = mime;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    public StoredFileHelper(final Context context, @Nullable final Uri parent,
                            @NonNull final Uri path, final String tag) throws IOException {
        this.tag = tag;
        this.source = path.toString();

        if (path.getScheme() == null
                || path.getScheme().equalsIgnoreCase(ContentResolver.SCHEME_FILE)) {
            this.ioFile = new File(URI.create(this.source));
        } else {
            final DocumentFile file = DocumentFile.fromSingleUri(context, path);

            if (file == null) {
                throw new RuntimeException("SAF not available");
            }

            this.context = context;

            if (file.getName() == null) {
                this.source = null;
                return;
            } else {
                this.docFile = file;
                takePermissionSAF();
            }
        }

        if (parent != null) {
            if (!ContentResolver.SCHEME_FILE.equals(parent.getScheme())) {
                this.docTree = DocumentFile.fromTreeUri(context, parent);
            }

            this.sourceTree = parent.toString();
        }

        this.srcName = getName();
        this.srcType = getType();
    }


    public static StoredFileHelper deserialize(@NonNull final StoredFileHelper storage,
                                               final Context context) throws IOException {
        final Uri treeUri = storage.sourceTree == null ? null : Uri.parse(storage.sourceTree);

        if (storage.isInvalid()) {
            return new StoredFileHelper(treeUri, storage.srcName, storage.srcType, storage.tag);
        }

        final StoredFileHelper instance = new StoredFileHelper(context, treeUri,
                Uri.parse(storage.source), storage.tag);

        // under SAF, if the target document is deleted, conserve the filename and mime
        if (instance.srcName == null) {
            instance.srcName = storage.srcName;
        }
        if (instance.srcType == null) {
            instance.srcType = storage.srcType;
        }

        return instance;
    }

    public SharpStream getStream() throws IOException {
        assertValid();

        if (docFile == null) {
            return new FileStream(ioFile);
        } else {
            return new FileStreamSAF(context.getContentResolver(), docFile.getUri());
        }
    }

    /**
     * Indicates whether it's using the {@code java.io} API.
     *
     * @return {@code true} for Java I/O API, otherwise, {@code false} for Storage Access Framework
     */
    public boolean isDirect() {
        assertValid();

        return docFile == null;
    }

    public boolean isInvalid() {
        return source == null;
    }

    public Uri getUri() {
        assertValid();

        return docFile == null ? Uri.fromFile(ioFile) : docFile.getUri();
    }

    public Uri getParentUri() {
        assertValid();

        return sourceTree == null ? null : Uri.parse(sourceTree);
    }

    public void truncate() throws IOException {
        assertValid();

        try (SharpStream fs = getStream()) {
            fs.setLength(0);
        }
    }

    public boolean delete() {
        if (source == null) {
            return true;
        }
        if (docFile == null) {
            return ioFile.delete();
        }

        final boolean res = docFile.delete();

        try {
            final int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            context.getContentResolver().releasePersistableUriPermission(docFile.getUri(), flags);
        } catch (final Exception ex) {
            // nothing to do
        }

        return res;
    }

    public long length() {
        assertValid();

        return docFile == null ? ioFile.length() : docFile.length();
    }

    public boolean canWrite() {
        if (source == null) {
            return false;
        }
        return docFile == null ? ioFile.canWrite() : docFile.canWrite();
    }

    public String getName() {
        if (source == null) {
            return srcName;
        } else if (docFile == null) {
            return ioFile.getName();
        }

        final String name = docFile.getName();
        return name == null ? srcName : name;
    }

    public String getType() {
        if (source == null || docFile == null) {
            return srcType;
        }

        final String type = docFile.getType();
        return type == null ? srcType : type;
    }

    public String getTag() {
        return tag;
    }

    public boolean existsAsFile() {
        if (source == null) {
            return false;
        }

        // WARNING: DocumentFile.exists() and DocumentFile.isFile() methods are slow
        final boolean exists = docFile == null ? ioFile.exists() : docFile.exists();
        // ¿docFile.isVirtual() means is no-physical?
        final boolean isFile = docFile == null ? ioFile.isFile() : docFile.isFile();

        return exists && isFile;
    }

    public boolean create() {
        assertValid();
        final boolean result;

        if (docFile == null) {
            try {
                result = ioFile.createNewFile();
            } catch (final IOException e) {
                return false;
            }
        } else if (docTree == null) {
            result = false;
        } else {
            if (!docTree.canRead() || !docTree.canWrite()) {
                return false;
            }
            try {
                docFile = createSAF(context, srcType, srcName);
                if (docFile == null || docFile.getName() == null) {
                    return false;
                }
                result = true;
            } catch (final IOException e) {
                return false;
            }
        }

        if (result) {
            source = (docFile == null ? Uri.fromFile(ioFile) : docFile.getUri()).toString();
            srcName = getName();
            srcType = getType();
        }

        return result;
    }

    public void invalidate() {
        if (source == null) {
            return;
        }

        srcName = getName();
        srcType = getType();

        source = null;

        docTree = null;
        docFile = null;
        ioFile = null;
        context = null;
    }

    public boolean equals(final StoredFileHelper storage) {
        if (this == storage) {
            return true;
        }

        // note: do not compare tags, files can have the same parent folder
        //if (stringMismatch(this.tag, storage.tag)) return false;

        if (stringMismatch(getLowerCase(this.sourceTree), getLowerCase(this.sourceTree))) {
            return false;
        }

        if (this.isInvalid() || storage.isInvalid()) {
            if (this.srcName == null || storage.srcName == null || this.srcType == null
                    || storage.srcType == null) {
                return false;
            }

            return this.srcName.equalsIgnoreCase(storage.srcName)
                    && this.srcType.equalsIgnoreCase(storage.srcType);
        }

        if (this.isDirect() != storage.isDirect()) {
            return false;
        }

        if (this.isDirect()) {
            return this.ioFile.getPath().equalsIgnoreCase(storage.ioFile.getPath());
        }

        return DocumentsContract.getDocumentId(this.docFile.getUri())
                .equalsIgnoreCase(DocumentsContract.getDocumentId(storage.docFile.getUri()));
    }

    @NonNull
    @Override
    public String toString() {
        if (source == null) {
            return "[Invalid state] name=" + srcName + "  type=" + srcType + "  tag=" + tag;
        } else {
            return "sourceFile=" + source + "  treeSource=" + (sourceTree == null ? "" : sourceTree)
                    + "  tag=" + tag;
        }
    }


    private void assertValid() {
        if (source == null) {
            throw new IllegalStateException("In invalid state");
        }
    }

    private void takePermissionSAF() throws IOException {
        try {
            context.getContentResolver().takePersistableUriPermission(docFile.getUri(),
                    StoredDirectoryHelper.PERMISSION_FLAGS);
        } catch (final Exception e) {
            if (docFile.getName() == null) {
                throw new IOException(e);
            }
        }
    }

    private DocumentFile createSAF(@Nullable final Context ctx, final String mime,
                                   final String filename) throws IOException {
        DocumentFile res = StoredDirectoryHelper.findFileSAFHelper(ctx, docTree, filename);

        if (res != null && res.exists() && res.isDirectory()) {
            if (!res.delete()) {
                throw new IOException("Directory with the same name found but cannot delete");
            }
            res = null;
        }

        if (res == null) {
            res = this.docTree.createFile(srcType == null ? DEFAULT_MIME : mime, filename);
            if (res == null) {
                throw new IOException("Cannot create the file");
            }
        }

        return res;
    }

    private String getLowerCase(final String str) {
        return str == null ? null : str.toLowerCase();
    }

    private boolean stringMismatch(final String str1, final String str2) {
        if (str1 == null && str2 == null) {
            return false;
        }
        if ((str1 == null) != (str2 == null)) {
            return true;
        }

        return !str1.equals(str2);
    }

    public static Intent getPicker(final Context ctx) {
        if (NewPipeSettings.useStorageAccessFramework(ctx)) {
            return new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .putExtra("android.content.extra.SHOW_ADVANCED", true)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | StoredDirectoryHelper.PERMISSION_FLAGS);
        } else {
            return new Intent(ctx, FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_SINGLE_CLICK, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE,
                            FilePickerActivityHelper.MODE_FILE);
        }
    }

    public static Intent getNewPicker(@NonNull final Context ctx, @Nullable final String startPath,
                                      @Nullable final String filename) {
        final Intent i;
        if (NewPipeSettings.useStorageAccessFramework(ctx)) {
            i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .putExtra("android.content.extra.SHOW_ADVANCED", true)
                    .setType("*/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | StoredDirectoryHelper.PERMISSION_FLAGS);

            if (startPath != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Uri.parse(startPath));
            }
            if (filename != null) {
                i.putExtra(Intent.EXTRA_TITLE, filename);
            }
        } else {
            i = new Intent(ctx, FilePickerActivityHelper.class)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_MULTIPLE, false)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_CREATE_DIR, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_ALLOW_EXISTING_FILE, true)
                    .putExtra(FilePickerActivityHelper.EXTRA_MODE,
                            FilePickerActivityHelper.MODE_NEW_FILE);

            if (startPath != null || filename != null) {
                File fullStartPath;
                if (startPath == null) {
                    fullStartPath = Environment.getExternalStorageDirectory();
                } else {
                    fullStartPath = new File(startPath);
                }
                if (filename != null) {
                    fullStartPath = new File(fullStartPath, filename);
                }
                i.putExtra(FilePickerActivityHelper.EXTRA_START_PATH,
                        fullStartPath.getAbsolutePath());
            }
        }
        return i;
    }
}