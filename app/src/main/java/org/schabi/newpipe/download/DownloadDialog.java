package org.schabi.newpipe.download;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.menu.ActionMenuItemView;
import androidx.appcompat.widget.Toolbar;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.RouterActivity;
import org.schabi.newpipe.databinding.DownloadDialogBinding;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.ErrorInfo;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import org.schabi.newpipe.util.StreamItemAdapter;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;
import org.schabi.newpipe.util.ThemeHelper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import icepick.Icepick;
import icepick.State;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.io.StoredDirectoryHelper;
import us.shandian.giga.io.StoredFileHelper;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;
import us.shandian.giga.service.MissionState;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class DownloadDialog extends DialogFragment
        implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "DialogFragment";
    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final int REQUEST_DOWNLOAD_SAVE_AS = 0x1230;

    @State
    StreamInfo currentInfo;
    @State
    StreamSizeWrapper<AudioStream> wrappedAudioStreams = StreamSizeWrapper.empty();
    @State
    StreamSizeWrapper<VideoStream> wrappedVideoStreams = StreamSizeWrapper.empty();
    @State
    StreamSizeWrapper<SubtitlesStream> wrappedSubtitleStreams = StreamSizeWrapper.empty();
    @State
    int selectedVideoIndex = 0;
    @State
    int selectedAudioIndex = 0;
    @State
    int selectedSubtitleIndex = 0;

    private StoredDirectoryHelper mainStorageAudio = null;
    private StoredDirectoryHelper mainStorageVideo = null;
    private DownloadManager downloadManager = null;
    private ActionMenuItemView okButton = null;
    private Context context;
    private boolean askForSavePath;

    private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;
    private StreamItemAdapter<SubtitlesStream, Stream> subtitleStreamsAdapter;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private DownloadDialogBinding dialogBinding;

    private SharedPreferences prefs;

    public static DownloadDialog newInstance(final StreamInfo info) {
        final DownloadDialog dialog = new DownloadDialog();
        dialog.setInfo(info);
        return dialog;
    }

    public static DownloadDialog newInstance(final Context context, final StreamInfo info) {
        final ArrayList<VideoStream> streamsList = new ArrayList<>(ListHelper
                .getSortedStreamVideosList(context, info.getVideoStreams(),
                        info.getVideoOnlyStreams(), false));
        final int selectedStreamIndex = ListHelper.getDefaultResolutionIndex(context, streamsList);

        final DownloadDialog instance = newInstance(info);
        instance.setVideoStreams(streamsList);
        instance.setSelectedVideoStream(selectedStreamIndex);
        instance.setAudioStreams(info.getAudioStreams());
        instance.setSubtitleStreams(info.getSubtitles());

        return instance;
    }

    private void setInfo(final StreamInfo info) {
        this.currentInfo = info;
    }

    public void setAudioStreams(final List<AudioStream> audioStreams) {
        setAudioStreams(new StreamSizeWrapper<>(audioStreams, getContext()));
    }

    public void setAudioStreams(final StreamSizeWrapper<AudioStream> was) {
        this.wrappedAudioStreams = was;
    }

    public void setVideoStreams(final List<VideoStream> videoStreams) {
        setVideoStreams(new StreamSizeWrapper<>(videoStreams, getContext()));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    public void setVideoStreams(final StreamSizeWrapper<VideoStream> wvs) {
        this.wrappedVideoStreams = wvs;
    }

    public void setSubtitleStreams(final List<SubtitlesStream> subtitleStreams) {
        setSubtitleStreams(new StreamSizeWrapper<>(subtitleStreams, getContext()));
    }

    public void setSubtitleStreams(
            final StreamSizeWrapper<SubtitlesStream> wss) {
        this.wrappedSubtitleStreams = wss;
    }

    public void setSelectedVideoStream(final int svi) {
        this.selectedVideoIndex = svi;
    }

    public void setSelectedAudioStream(final int sai) {
        this.selectedAudioIndex = sai;
    }

    public void setSelectedSubtitleStream(final int ssi) {
        this.selectedSubtitleIndex = ssi;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }

        if (!PermissionHelper.checkStoragePermissions(getActivity(),
                PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            getDialog().dismiss();
            return;
        }

        context = getContext();

        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(context));
        Icepick.restoreInstanceState(this, savedInstanceState);

        final SparseArray<SecondaryStreamHelper<AudioStream>> secondaryStreams
                = new SparseArray<>(4);
        final List<VideoStream> videoStreams = wrappedVideoStreams.getStreamsList();

        for (int i = 0; i < videoStreams.size(); i++) {
            if (!videoStreams.get(i).isVideoOnly()) {
                continue;
            }
            final AudioStream audioStream = SecondaryStreamHelper
                    .getAudioStreamFor(wrappedAudioStreams.getStreamsList(), videoStreams.get(i));

            if (audioStream != null) {
                secondaryStreams
                        .append(i, new SecondaryStreamHelper<>(wrappedAudioStreams, audioStream));
            } else if (DEBUG) {
                Log.w(TAG, "No audio stream candidates for video format "
                        + videoStreams.get(i).getFormat().name());
            }
        }

        this.videoStreamsAdapter = new StreamItemAdapter<>(context, wrappedVideoStreams,
                secondaryStreams);
        this.audioStreamsAdapter = new StreamItemAdapter<>(context, wrappedAudioStreams);
        this.subtitleStreamsAdapter = new StreamItemAdapter<>(context, wrappedSubtitleStreams);

        final Intent intent = new Intent(context, DownloadManagerService.class);
        context.startService(intent);

        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(final ComponentName cname, final IBinder service) {
                final DownloadManagerBinder mgr = (DownloadManagerBinder) service;

                mainStorageAudio = mgr.getMainStorageAudio();
                mainStorageVideo = mgr.getMainStorageVideo();
                downloadManager = mgr.getDownloadManager();
                askForSavePath = mgr.askForSavePath();

                okButton.setEnabled(true);

                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(final ComponentName name) {
                // nothing to do
            }
        }, Context.BIND_AUTO_CREATE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Inits
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreateView() called with: "
                    + "inflater = [" + inflater + "], container = [" + container + "], "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }
        return inflater.inflate(R.layout.download_dialog, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        dialogBinding = DownloadDialogBinding.bind(view);

        dialogBinding.fileName.setText(FilenameUtils.createFilename(getContext(),
                currentInfo.getName()));
        selectedAudioIndex = ListHelper
                .getDefaultAudioFormat(getContext(), currentInfo.getAudioStreams());

        selectedSubtitleIndex = getSubtitleIndexBy(subtitleStreamsAdapter.getAll());

        dialogBinding.qualitySpinner.setOnItemSelectedListener(this);

        dialogBinding.videoAudioGroup.setOnCheckedChangeListener(this);

        initToolbar(dialogBinding.toolbarLayout.toolbar);
        setupDownloadOptions();

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        final int threads = prefs.getInt(getString(R.string.default_download_threads), 3);
        dialogBinding.threadsCount.setText(String.valueOf(threads));
        dialogBinding.threads.setProgress(threads - 1);
        dialogBinding.threads.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(final SeekBar seekbar, final int progress,
                                          final boolean fromUser) {
                final int newProgress = progress + 1;
                prefs.edit().putInt(getString(R.string.default_download_threads), newProgress)
                        .apply();
                dialogBinding.threadsCount.setText(String.valueOf(newProgress));
            }

            @Override
            public void onStartTrackingTouch(final SeekBar p1) { }

            @Override
            public void onStopTrackingTouch(final SeekBar p1) { }
        });

        fetchStreamsSize();
    }

    private void fetchStreamsSize() {
        disposables.clear();

        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedVideoStreams)
                .subscribe(result -> {
            if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
                setupVideoSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedAudioStreams)
                .subscribe(result -> {
            if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                setupAudioSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedSubtitleStreams)
                .subscribe(result -> {
            if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.subtitle_button) {
                setupSubtitleSpinner();
            }
        }));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onDestroyView() {
        dialogBinding = null;
        super.onDestroyView();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Radio group Video&Audio options - Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Streams Spinner Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DOWNLOAD_SAVE_AS && resultCode == Activity.RESULT_OK) {
            if (data.getData() == null) {
                showFailedDialog(R.string.general_error);
                return;
            }

            if (FilePickerActivityHelper.isOwnFileUri(context, data.getData())) {
                final File file = Utils.getFileForUri(data.getData());
                checkSelectedDownload(null, Uri.fromFile(file), file.getName(),
                        StoredFileHelper.DEFAULT_MIME);
                return;
            }

            final DocumentFile docFile = DocumentFile.fromSingleUri(context, data.getData());
            if (docFile == null) {
                showFailedDialog(R.string.general_error);
                return;
            }

            // check if the selected file was previously used
            checkSelectedDownload(null, data.getData(), docFile.getName(),
                    docFile.getType());
        }
    }

    private void initToolbar(final Toolbar toolbar) {
        if (DEBUG) {
            Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");
        }

        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(
            ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_arrow_back));
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(v -> requireDialog().dismiss());
        toolbar.setNavigationContentDescription(R.string.cancel);

        okButton = toolbar.findViewById(R.id.okay);
        okButton.setEnabled(false); // disable until the download service connection is done

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.okay) {
                prepareSelectedDownload();
                if (getActivity() instanceof RouterActivity) {
                    getActivity().finish();
                }
                return true;
            }
            return false;
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void setupAudioSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(audioStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedAudioIndex);
        setRadioButtonsState(true);
    }

    private void setupVideoSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(videoStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedVideoIndex);
        setRadioButtonsState(true);
    }

    private void setupSubtitleSpinner() {
        if (getContext() == null) {
            return;
        }

        dialogBinding.qualitySpinner.setAdapter(subtitleStreamsAdapter);
        dialogBinding.qualitySpinner.setSelection(selectedSubtitleIndex);
        setRadioButtonsState(true);
    }

    @Override
    public void onCheckedChanged(final RadioGroup group, @IdRes final int checkedId) {
        if (DEBUG) {
            Log.d(TAG, "onCheckedChanged() called with: "
                    + "group = [" + group + "], checkedId = [" + checkedId + "]");
        }
        boolean flag = true;

        switch (checkedId) {
            case R.id.audio_button:
                setupAudioSpinner();
                break;
            case R.id.video_button:
                setupVideoSpinner();
                break;
            case R.id.subtitle_button:
                setupSubtitleSpinner();
                flag = false;
                break;
        }

        dialogBinding.threads.setEnabled(flag);
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view,
                               final int position, final long id) {
        if (DEBUG) {
            Log.d(TAG, "onItemSelected() called with: "
                    + "parent = [" + parent + "], view = [" + view + "], "
                    + "position = [" + position + "], id = [" + id + "]");
        }
        switch (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()) {
            case R.id.audio_button:
                selectedAudioIndex = position;
                break;
            case R.id.video_button:
                selectedVideoIndex = position;
                break;
            case R.id.subtitle_button:
                selectedSubtitleIndex = position;
                break;
        }
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
    }

    protected void setupDownloadOptions() {
        setRadioButtonsState(false);

        final boolean isVideoStreamsAvailable = videoStreamsAdapter.getCount() > 0;
        final boolean isAudioStreamsAvailable = audioStreamsAdapter.getCount() > 0;
        final boolean isSubtitleStreamsAvailable = subtitleStreamsAdapter.getCount() > 0;

        dialogBinding.audioButton.setVisibility(isAudioStreamsAvailable ? View.VISIBLE : View.GONE);
        dialogBinding.videoButton.setVisibility(isVideoStreamsAvailable ? View.VISIBLE : View.GONE);
        dialogBinding.subtitleButton.setVisibility(isSubtitleStreamsAvailable
                ? View.VISIBLE : View.GONE);

        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        final String defaultMedia = prefs.getString(getString(R.string.last_used_download_type),
                    getString(R.string.last_download_type_video_key));

        if (isVideoStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_video_key)))) {
            dialogBinding.videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_audio_key)))) {
            dialogBinding.audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable
                && (defaultMedia.equals(getString(R.string.last_download_type_subtitle_key)))) {
            dialogBinding.subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else if (isVideoStreamsAvailable) {
            dialogBinding.videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable) {
            dialogBinding.audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable) {
            dialogBinding.subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else {
            Toast.makeText(getContext(), R.string.no_streams_available_download,
                    Toast.LENGTH_SHORT).show();
            getDialog().dismiss();
        }
    }

    private void setRadioButtonsState(final boolean enabled) {
        dialogBinding.audioButton.setEnabled(enabled);
        dialogBinding.videoButton.setEnabled(enabled);
        dialogBinding.subtitleButton.setEnabled(enabled);
    }

    private int getSubtitleIndexBy(final List<SubtitlesStream> streams) {
        final Localization preferredLocalization = NewPipe.getPreferredLocalization();

        int candidate = 0;
        for (int i = 0; i < streams.size(); i++) {
            final Locale streamLocale = streams.get(i).getLocale();

            final boolean languageEquals = streamLocale.getLanguage() != null
                    && preferredLocalization.getLanguageCode() != null
                    && streamLocale.getLanguage()
                    .equals(new Locale(preferredLocalization.getLanguageCode()).getLanguage());
            final boolean countryEquals = streamLocale.getCountry() != null
                    && streamLocale.getCountry().equals(preferredLocalization.getCountryCode());

            if (languageEquals) {
                if (countryEquals) {
                    return i;
                }

                candidate = i;
            }
        }

        return candidate;
    }

    private String getNameEditText() {
        final String str = dialogBinding.fileName.getText().toString().trim();

        return FilenameUtils.createFilename(context, str.isEmpty() ? currentInfo.getName() : str);
    }

    private void showFailedDialog(@StringRes final int msg) {
        assureCorrectAppLanguage(getContext());
        new AlertDialog.Builder(context)
                .setTitle(R.string.general_error)
                .setMessage(msg)
                .setNegativeButton(getString(R.string.finish), null)
                .create()
                .show();
    }

    private void showErrorActivity(final Exception e) {
        ErrorActivity.reportError(
                context,
                List.of(e),
                null,
                null,
                ErrorInfo
                        .make(UserAction.SOMETHING_ELSE, "-", "-", R.string.general_error)
        );
    }

    private void prepareSelectedDownload() {
        final StoredDirectoryHelper mainStorage;
        final MediaFormat format;
        final String mime;
        final String selectedMediaType;

        // first, build the filename and get the output folder (if possible)
        // later, run a very very very large file checking logic

        String filename = getNameEditText().concat(".");

        switch (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()) {
            case R.id.audio_button:
                selectedMediaType = getString(R.string.last_download_type_audio_key);
                mainStorage = mainStorageAudio;
                format = audioStreamsAdapter.getItem(selectedAudioIndex).getFormat();
                switch (format) {
                    case WEBMA_OPUS:
                        mime = "audio/ogg";
                        filename += "opus";
                        break;
                    default:
                        mime = format.mimeType;
                        filename += format.suffix;
                        break;
                }
                break;
            case R.id.video_button:
                selectedMediaType = getString(R.string.last_download_type_video_key);
                mainStorage = mainStorageVideo;
                format = videoStreamsAdapter.getItem(selectedVideoIndex).getFormat();
                mime = format.mimeType;
                filename += format.suffix;
                break;
            case R.id.subtitle_button:
                selectedMediaType = getString(R.string.last_download_type_subtitle_key);
                mainStorage = mainStorageVideo; // subtitle & video files go together
                format = subtitleStreamsAdapter.getItem(selectedSubtitleIndex).getFormat();
                mime = format.mimeType;
                filename += (format == MediaFormat.TTML ? MediaFormat.SRT : format).suffix;
                break;
            default:
                throw new RuntimeException("No stream selected");
        }

        if (mainStorage == null || askForSavePath) {
            // This part is called if with SAF preferred:
            //  * older android version running
            //  * save path not defined (via download settings)
            //  * the user checked the "ask where to download" option

            if (!askForSavePath) {
                Toast.makeText(context, getString(R.string.no_available_dir),
                        Toast.LENGTH_LONG).show();
            }

            if (NewPipeSettings.useStorageAccessFramework(context)) {
                StoredFileHelper.requestSafWithFileCreation(this, REQUEST_DOWNLOAD_SAVE_AS,
                        filename, mime);
            } else {
                File initialSavePath;
                if (dialogBinding.videoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC);
                } else {
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES);
                }

                initialSavePath = new File(initialSavePath, filename);
                startActivityForResult(FilePickerActivityHelper.chooseFileToSave(context,
                        initialSavePath.getAbsolutePath()), REQUEST_DOWNLOAD_SAVE_AS);
            }

            return;
        }

        // check for existing file with the same name
        checkSelectedDownload(mainStorage, mainStorage.findFile(filename), filename, mime);

        // remember the last media type downloaded by the user
        prefs.edit()
                .putString(getString(R.string.last_used_download_type), selectedMediaType)
                .apply();
    }

    private void checkSelectedDownload(final StoredDirectoryHelper mainStorage,
                                       final Uri targetFile, final String filename,
                                       final String mime) {
        StoredFileHelper storage;

        try {
            if (mainStorage == null) {
                // using SAF on older android version
                storage = new StoredFileHelper(context, null, targetFile, "");
            } else if (targetFile == null) {
                // the file does not exist, but it is probably used in a pending download
                storage = new StoredFileHelper(mainStorage.getUri(), filename, mime,
                        mainStorage.getTag());
            } else {
                // the target filename is already use, attempt to use it
                storage = new StoredFileHelper(context, mainStorage.getUri(), targetFile,
                        mainStorage.getTag());
            }
        } catch (final Exception e) {
            showErrorActivity(e);
            return;
        }

        // check if is our file
        final MissionState state = downloadManager.checkForExistingMission(storage);
        @StringRes
        final int msgBtn;
        @StringRes
        final int msgBody;

        switch (state) {
            case Finished:
                msgBtn = R.string.overwrite;
                msgBody = R.string.overwrite_finished_warning;
                break;
            case Pending:
                msgBtn = R.string.overwrite;
                msgBody = R.string.download_already_pending;
                break;
            case PendingRunning:
                msgBtn = R.string.generate_unique_name;
                msgBody = R.string.download_already_running;
                break;
            case None:
                if (mainStorage == null) {
                    // This part is called if:
                    // * using SAF on older android version
                    // * save path not defined
                    // * if the file exists overwrite it, is not necessary ask
                    if (!storage.existsAsFile() && !storage.create()) {
                        showFailedDialog(R.string.error_file_creation);
                        return;
                    }
                    continueSelectedDownload(storage);
                    return;
                } else if (targetFile == null) {
                    // This part is called if:
                    // * the filename is not used in a pending/finished download
                    // * the file does not exists, create

                    if (!mainStorage.mkdirs()) {
                        showFailedDialog(R.string.error_path_creation);
                        return;
                    }

                    storage = mainStorage.createFile(filename, mime);
                    if (storage == null || !storage.canWrite()) {
                        showFailedDialog(R.string.error_file_creation);
                        return;
                    }

                    continueSelectedDownload(storage);
                    return;
                }
                msgBtn = R.string.overwrite;
                msgBody = R.string.overwrite_unrelated_warning;
                break;
            default:
                return;
        }

        final AlertDialog.Builder askDialog = new AlertDialog.Builder(context)
                .setTitle(R.string.download_dialog_title)
                .setMessage(msgBody)
                .setNegativeButton(android.R.string.cancel, null);
        final StoredFileHelper finalStorage = storage;


        if (mainStorage == null) {
            // This part is called if:
            // * using SAF on older android version
            // * save path not defined
            switch (state) {
                case Pending:
                case Finished:
                    askDialog.setPositiveButton(msgBtn, (dialog, which) -> {
                        dialog.dismiss();
                        downloadManager.forgetMission(finalStorage);
                        continueSelectedDownload(finalStorage);
                    });
                    break;
            }

            askDialog.create().show();
            return;
        }

        askDialog.setPositiveButton(msgBtn, (dialog, which) -> {
            dialog.dismiss();

            StoredFileHelper storageNew;
            switch (state) {
                case Finished:
                case Pending:
                    downloadManager.forgetMission(finalStorage);
                case None:
                    if (targetFile == null) {
                        storageNew = mainStorage.createFile(filename, mime);
                    } else {
                        try {
                            // try take (or steal) the file
                            storageNew = new StoredFileHelper(context, mainStorage.getUri(),
                                    targetFile, mainStorage.getTag());
                        } catch (final IOException e) {
                            Log.e(TAG, "Failed to take (or steal) the file in "
                                    + targetFile.toString());
                            storageNew = null;
                        }
                    }

                    if (storageNew != null && storageNew.canWrite()) {
                        continueSelectedDownload(storageNew);
                    } else {
                        showFailedDialog(R.string.error_file_creation);
                    }
                    break;
                case PendingRunning:
                    storageNew = mainStorage.createUniqueFile(filename, mime);
                    if (storageNew == null) {
                        showFailedDialog(R.string.error_file_creation);
                    } else {
                        continueSelectedDownload(storageNew);
                    }
                    break;
            }
        });

        askDialog.create().show();
    }

    private void continueSelectedDownload(@NonNull final StoredFileHelper storage) {
        if (!storage.canWrite()) {
            showFailedDialog(R.string.permission_denied);
            return;
        }

        // check if the selected file has to be overwritten, by simply checking its length
        try {
            if (storage.length() > 0) {
                storage.truncate();
            }
        } catch (final IOException e) {
            Log.e(TAG, "failed to truncate the file: " + storage.getUri().toString(), e);
            showFailedDialog(R.string.overwrite_failed);
            return;
        }

        final Stream selectedStream;
        Stream secondaryStream = null;
        final char kind;
        int threads = dialogBinding.threads.getProgress() + 1;
        final String[] urls;
        final MissionRecoveryInfo[] recoveryInfo;
        String psName = null;
        String[] psArgs = null;
        long nearLength = 0;

        // more download logic: select muxer, subtitle converter, etc.
        switch (dialogBinding.videoAudioGroup.getCheckedRadioButtonId()) {
            case R.id.audio_button:
                kind = 'a';
                selectedStream = audioStreamsAdapter.getItem(selectedAudioIndex);

                if (selectedStream.getFormat() == MediaFormat.M4A) {
                    psName = Postprocessing.ALGORITHM_M4A_NO_DASH;
                } else if (selectedStream.getFormat() == MediaFormat.WEBMA_OPUS) {
                    psName = Postprocessing.ALGORITHM_OGG_FROM_WEBM_DEMUXER;
                }
                break;
            case R.id.video_button:
                kind = 'v';
                selectedStream = videoStreamsAdapter.getItem(selectedVideoIndex);

                final SecondaryStreamHelper<AudioStream> secondary = videoStreamsAdapter
                        .getAllSecondary()
                        .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));

                if (secondary != null) {
                    secondaryStream = secondary.getStream();

                    if (selectedStream.getFormat() == MediaFormat.MPEG_4) {
                        psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                    } else {
                        psName = Postprocessing.ALGORITHM_WEBM_MUXER;
                    }

                    psArgs = null;
                    final long videoSize = wrappedVideoStreams
                            .getSizeInBytes((VideoStream) selectedStream);

                    // set nearLength, only, if both sizes are fetched or known. This probably
                    // does not work on slow networks but is later updated in the downloader
                    if (secondary.getSizeInBytes() > 0 && videoSize > 0) {
                        nearLength = secondary.getSizeInBytes() + videoSize;
                    }
                }
                break;
            case R.id.subtitle_button:
                threads = 1; // use unique thread for subtitles due small file size
                kind = 's';
                selectedStream = subtitleStreamsAdapter.getItem(selectedSubtitleIndex);

                if (selectedStream.getFormat() == MediaFormat.TTML) {
                    psName = Postprocessing.ALGORITHM_TTML_CONVERTER;
                    psArgs = new String[]{
                            selectedStream.getFormat().getSuffix(),
                            "false" // ignore empty frames
                    };
                }
                break;
            default:
                return;
        }

        if (secondaryStream == null) {
            urls = new String[]{
                    selectedStream.getUrl()
            };
            recoveryInfo = new MissionRecoveryInfo[]{
                    new MissionRecoveryInfo(selectedStream)
            };
        } else {
            urls = new String[]{
                    selectedStream.getUrl(), secondaryStream.getUrl()
            };
            recoveryInfo = new MissionRecoveryInfo[]{new MissionRecoveryInfo(selectedStream),
                    new MissionRecoveryInfo(secondaryStream)};
        }

        DownloadManagerService.startMission(context, urls, storage, kind, threads,
                currentInfo.getUrl(), psName, psArgs, nearLength, recoveryInfo);

        dismiss();
    }
}
