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
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
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

import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.RouterActivity;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.report.ErrorActivity;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import icepick.Icepick;
import icepick.State;
import io.reactivex.disposables.CompositeDisposable;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.io.StoredDirectoryHelper;
import us.shandian.giga.io.StoredFileHelper;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;
import us.shandian.giga.service.MissionState;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class DownloadDialog extends DialogFragment implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "DialogFragment";
    private static final boolean DEBUG = MainActivity.DEBUG;
    private static final int REQUEST_DOWNLOAD_SAVE_AS = 0x1230;

    @State
    protected StreamInfo currentInfo;
    @State
    protected StreamSizeWrapper<AudioStream> wrappedAudioStreams = StreamSizeWrapper.empty();
    @State
    protected StreamSizeWrapper<VideoStream> wrappedVideoStreams = StreamSizeWrapper.empty();
    @State
    protected StreamSizeWrapper<SubtitlesStream> wrappedSubtitleStreams = StreamSizeWrapper.empty();
    @State
    protected int selectedVideoIndex = 0;
    @State
    protected int selectedAudioIndex = 0;
    @State
    protected int selectedSubtitleIndex = 0;

    private StreamItemAdapter<AudioStream, Stream> audioStreamsAdapter;
    private StreamItemAdapter<VideoStream, AudioStream> videoStreamsAdapter;
    private StreamItemAdapter<SubtitlesStream, Stream> subtitleStreamsAdapter;

    private final CompositeDisposable disposables = new CompositeDisposable();

    private EditText nameEditText;
    private Spinner streamsSpinner;
    private RadioGroup radioStreamsGroup;
    private TextView threadsCountTextView;
    private SeekBar threadsSeekBar;

    private SharedPreferences prefs;

    public static DownloadDialog newInstance(StreamInfo info) {
        DownloadDialog dialog = new DownloadDialog();
        dialog.setInfo(info);
        return dialog;
    }

    public static DownloadDialog newInstance(Context context, StreamInfo info) {
        final ArrayList<VideoStream> streamsList = new ArrayList<>(ListHelper.getSortedStreamVideosList(context,
                info.getVideoStreams(), info.getVideoOnlyStreams(), false));
        final int selectedStreamIndex = ListHelper.getDefaultResolutionIndex(context, streamsList);

        final DownloadDialog instance = newInstance(info);
        instance.setVideoStreams(streamsList);
        instance.setSelectedVideoStream(selectedStreamIndex);
        instance.setAudioStreams(info.getAudioStreams());
        instance.setSubtitleStreams(info.getSubtitles());

        return instance;
    }

    private void setInfo(StreamInfo info) {
        this.currentInfo = info;
    }

    public void setAudioStreams(List<AudioStream> audioStreams) {
        setAudioStreams(new StreamSizeWrapper<>(audioStreams, getContext()));
    }

    public void setAudioStreams(StreamSizeWrapper<AudioStream> wrappedAudioStreams) {
        this.wrappedAudioStreams = wrappedAudioStreams;
    }

    public void setVideoStreams(List<VideoStream> videoStreams) {
        setVideoStreams(new StreamSizeWrapper<>(videoStreams, getContext()));
    }

    public void setVideoStreams(StreamSizeWrapper<VideoStream> wrappedVideoStreams) {
        this.wrappedVideoStreams = wrappedVideoStreams;
    }

    public void setSubtitleStreams(List<SubtitlesStream> subtitleStreams) {
        setSubtitleStreams(new StreamSizeWrapper<>(subtitleStreams, getContext()));
    }

    public void setSubtitleStreams(StreamSizeWrapper<SubtitlesStream> wrappedSubtitleStreams) {
        this.wrappedSubtitleStreams = wrappedSubtitleStreams;
    }

    public void setSelectedVideoStream(int selectedVideoIndex) {
        this.selectedVideoIndex = selectedVideoIndex;
    }

    public void setSelectedAudioStream(int selectedAudioIndex) {
        this.selectedAudioIndex = selectedAudioIndex;
    }

    public void setSelectedSubtitleStream(int selectedSubtitleIndex) {
        this.selectedSubtitleIndex = selectedSubtitleIndex;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (DEBUG)
            Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");

        if (!PermissionHelper.checkStoragePermissions(getActivity(), PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
            getDialog().dismiss();
            return;
        }

        context = getContext();

        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(context));
        Icepick.restoreInstanceState(this, savedInstanceState);

        SparseArray<SecondaryStreamHelper<AudioStream>> secondaryStreams = new SparseArray<>(4);
        List<VideoStream> videoStreams = wrappedVideoStreams.getStreamsList();

        for (int i = 0; i < videoStreams.size(); i++) {
            if (!videoStreams.get(i).isVideoOnly()) continue;
            AudioStream audioStream = SecondaryStreamHelper.getAudioStreamFor(wrappedAudioStreams.getStreamsList(), videoStreams.get(i));

            if (audioStream != null) {
                secondaryStreams.append(i, new SecondaryStreamHelper<>(wrappedAudioStreams, audioStream));
            } else if (DEBUG) {
                Log.w(TAG, "No audio stream candidates for video format " + videoStreams.get(i).getFormat().name());
            }
        }

        this.videoStreamsAdapter = new StreamItemAdapter<>(context, wrappedVideoStreams, secondaryStreams);
        this.audioStreamsAdapter = new StreamItemAdapter<>(context, wrappedAudioStreams);
        this.subtitleStreamsAdapter = new StreamItemAdapter<>(context, wrappedSubtitleStreams);

        Intent intent = new Intent(context, DownloadManagerService.class);
        context.startService(intent);

        context.bindService(intent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName cname, IBinder service) {
                DownloadManagerBinder mgr = (DownloadManagerBinder) service;

                mainStorageAudio = mgr.getMainStorageAudio();
                mainStorageVideo = mgr.getMainStorageVideo();
                downloadManager = mgr.getDownloadManager();
                askForSavePath = mgr.askForSavePath();

                okButton.setEnabled(true);

                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // nothing to do
            }
        }, Context.BIND_AUTO_CREATE);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG)
            Log.d(TAG, "onCreateView() called with: inflater = [" + inflater + "], container = [" + container + "], savedInstanceState = [" + savedInstanceState + "]");
        return inflater.inflate(R.layout.download_dialog, container);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        nameEditText = view.findViewById(R.id.file_name);
        nameEditText.setText(FilenameUtils.createFilename(getContext(), currentInfo.getName()));
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(getContext(), currentInfo.getAudioStreams());

        selectedSubtitleIndex = getSubtitleIndexBy(subtitleStreamsAdapter.getAll());

        streamsSpinner = view.findViewById(R.id.quality_spinner);
        streamsSpinner.setOnItemSelectedListener(this);

        threadsCountTextView = view.findViewById(R.id.threads_count);
        threadsSeekBar = view.findViewById(R.id.threads);

        radioStreamsGroup = view.findViewById(R.id.video_audio_group);
        radioStreamsGroup.setOnCheckedChangeListener(this);

        initToolbar(view.findViewById(R.id.toolbar));
        setupDownloadOptions();

        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        int threads = prefs.getInt(getString(R.string.default_download_threads), 3);
        threadsCountTextView.setText(String.valueOf(threads));
        threadsSeekBar.setProgress(threads - 1);
        threadsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
                progress++;
                prefs.edit().putInt(getString(R.string.default_download_threads), progress).apply();
                threadsCountTextView.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar p1) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar p1) {
            }
        });

        fetchStreamsSize();
    }

    private void fetchStreamsSize() {
        disposables.clear();

        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedVideoStreams).subscribe(result -> {
            if (radioStreamsGroup.getCheckedRadioButtonId() == R.id.video_button) {
                setupVideoSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedAudioStreams).subscribe(result -> {
            if (radioStreamsGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                setupAudioSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedSubtitleStreams).subscribe(result -> {
            if (radioStreamsGroup.getCheckedRadioButtonId() == R.id.subtitle_button) {
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
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_DOWNLOAD_SAVE_AS && resultCode == Activity.RESULT_OK) {
            if (data.getData() == null) {
                showFailedDialog(R.string.general_error);
                return;
            }

            if (FilePickerActivityHelper.isOwnFileUri(context, data.getData())) {
                File file = Utils.getFileForUri(data.getData());
                checkSelectedDownload(null, Uri.fromFile(file), file.getName(), StoredFileHelper.DEFAULT_MIME);
                return;
            }

            DocumentFile docFile = DocumentFile.fromSingleUri(context, data.getData());
            if (docFile == null) {
                showFailedDialog(R.string.general_error);
                return;
            }

            // check if the selected file was previously used
            checkSelectedDownload(null, data.getData(), docFile.getName(), docFile.getType());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Inits
    //////////////////////////////////////////////////////////////////////////*/

    private void initToolbar(Toolbar toolbar) {
        if (DEBUG) Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");

        boolean isLight = ThemeHelper.isLightThemeSelected(getActivity());

        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(isLight ? R.drawable.ic_arrow_back_black_24dp : R.drawable.ic_arrow_back_white_24dp);
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(v -> getDialog().dismiss());
        toolbar.setNavigationContentDescription(R.string.cancel);

        okButton = toolbar.findViewById(R.id.okay);
        okButton.setEnabled(false);// disable until the download service connection is done

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

    private void setupAudioSpinner() {
        if (getContext() == null) return;

        streamsSpinner.setAdapter(audioStreamsAdapter);
        streamsSpinner.setSelection(selectedAudioIndex);
        setRadioButtonsState(true);
    }

    private void setupVideoSpinner() {
        if (getContext() == null) return;

        streamsSpinner.setAdapter(videoStreamsAdapter);
        streamsSpinner.setSelection(selectedVideoIndex);
        setRadioButtonsState(true);
    }

    private void setupSubtitleSpinner() {
        if (getContext() == null) return;

        streamsSpinner.setAdapter(subtitleStreamsAdapter);
        streamsSpinner.setSelection(selectedSubtitleIndex);
        setRadioButtonsState(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Radio group Video&Audio options - Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        if (DEBUG)
            Log.d(TAG, "onCheckedChanged() called with: group = [" + group + "], checkedId = [" + checkedId + "]");
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

        threadsSeekBar.setEnabled(flag);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Streams Spinner Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (DEBUG)
            Log.d(TAG, "onItemSelected() called with: parent = [" + parent + "], view = [" + view + "], position = [" + position + "], id = [" + id + "]");
        switch (radioStreamsGroup.getCheckedRadioButtonId()) {
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
    public void onNothingSelected(AdapterView<?> parent) {
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void setupDownloadOptions() {
        setRadioButtonsState(false);

        final RadioButton audioButton = radioStreamsGroup.findViewById(R.id.audio_button);
        final RadioButton videoButton = radioStreamsGroup.findViewById(R.id.video_button);
        final RadioButton subtitleButton = radioStreamsGroup.findViewById(R.id.subtitle_button);
        final boolean isVideoStreamsAvailable = videoStreamsAdapter.getCount() > 0;
        final boolean isAudioStreamsAvailable = audioStreamsAdapter.getCount() > 0;
        final boolean isSubtitleStreamsAvailable = subtitleStreamsAdapter.getCount() > 0;

        audioButton.setVisibility(isAudioStreamsAvailable ? View.VISIBLE : View.GONE);
        videoButton.setVisibility(isVideoStreamsAvailable ? View.VISIBLE : View.GONE);
        subtitleButton.setVisibility(isSubtitleStreamsAvailable ? View.VISIBLE : View.GONE);

        if (isVideoStreamsAvailable) {
            videoButton.setChecked(true);
            setupVideoSpinner();
        } else if (isAudioStreamsAvailable) {
            audioButton.setChecked(true);
            setupAudioSpinner();
        } else if (isSubtitleStreamsAvailable) {
            subtitleButton.setChecked(true);
            setupSubtitleSpinner();
        } else {
            Toast.makeText(getContext(), R.string.no_streams_available_download, Toast.LENGTH_SHORT).show();
            getDialog().dismiss();
        }
    }

    private void setRadioButtonsState(boolean enabled) {
        radioStreamsGroup.findViewById(R.id.audio_button).setEnabled(enabled);
        radioStreamsGroup.findViewById(R.id.video_button).setEnabled(enabled);
        radioStreamsGroup.findViewById(R.id.subtitle_button).setEnabled(enabled);
    }

    private int getSubtitleIndexBy(List<SubtitlesStream> streams) {
        final Localization preferredLocalization = NewPipe.getPreferredLocalization();

        int candidate = 0;
        for (int i = 0; i < streams.size(); i++) {
            final Locale streamLocale = streams.get(i).getLocale();

            final boolean languageEquals = streamLocale.getLanguage() != null && preferredLocalization.getLanguageCode() != null &&
                    streamLocale.getLanguage().equals(new Locale(preferredLocalization.getLanguageCode()).getLanguage());
            final boolean countryEquals = streamLocale.getCountry() != null && streamLocale.getCountry().equals(preferredLocalization.getCountryCode());

            if (languageEquals) {
                if (countryEquals) return i;

                candidate = i;
            }
        }

        return candidate;
    }

    StoredDirectoryHelper mainStorageAudio = null;
    StoredDirectoryHelper mainStorageVideo = null;
    DownloadManager downloadManager = null;
    ActionMenuItemView okButton = null;
    Context context;
    boolean askForSavePath;

    private String getNameEditText() {
        String str = nameEditText.getText().toString().trim();

        return FilenameUtils.createFilename(context, str.isEmpty() ? currentInfo.getName() : str);
    }

    private void showFailedDialog(@StringRes int msg) {
        assureCorrectAppLanguage(getContext());
        new AlertDialog.Builder(context)
                .setTitle(R.string.general_error)
                .setMessage(msg)
                .setNegativeButton(getString(R.string.finish), null)
                .create()
                .show();
    }

    private void showErrorActivity(Exception e) {
        ErrorActivity.reportError(
                context,
                Collections.singletonList(e),
                null,
                null,
                ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "-", "-", R.string.general_error)
        );
    }

    private void prepareSelectedDownload() {
        StoredDirectoryHelper mainStorage;
        MediaFormat format;
        String mime;

        // first, build the filename and get the output folder (if possible)
        // later, run a very very very large file checking logic

        String filename = getNameEditText().concat(".");

        switch (radioStreamsGroup.getCheckedRadioButtonId()) {
            case R.id.audio_button:
                mainStorage = mainStorageAudio;
                format = audioStreamsAdapter.getItem(selectedAudioIndex).getFormat();
                switch(format) {
                    case WEBMA_OPUS:
                        mime = "audio/ogg";
                        filename += "opus";
                        break;
                    default:
                        mime = format.mimeType;
                        filename += "mp3"/*format.suffix*/;
                        break;
                }
                break;
            case R.id.video_button:
                mainStorage = mainStorageVideo;
                format = videoStreamsAdapter.getItem(selectedVideoIndex).getFormat();
                mime = format.mimeType;
                filename += format.suffix;
                break;
            case R.id.subtitle_button:
                mainStorage = mainStorageVideo;// subtitle & video files go together
                format = subtitleStreamsAdapter.getItem(selectedSubtitleIndex).getFormat();
                mime = format.mimeType;
                filename += format == MediaFormat.TTML ? MediaFormat.SRT.suffix : format.suffix;
                break;
            default:
                throw new RuntimeException("No stream selected");
        }

        if (mainStorage == null || askForSavePath) {
            // This part is called if with SAF preferred:
            //  * older android version running
            //  * save path not defined (via download settings)
            //  * the user checked the "ask where to download" option

            if (!askForSavePath)
                Toast.makeText(context, getString(R.string.no_available_dir), Toast.LENGTH_LONG).show();

            if (NewPipeSettings.useStorageAccessFramework(context)) {
                StoredFileHelper.requestSafWithFileCreation(this, REQUEST_DOWNLOAD_SAVE_AS, filename, mime);
            } else {
                File initialSavePath;
                if (radioStreamsGroup.getCheckedRadioButtonId() == R.id.audio_button)
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC);
                else
                    initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES);

                initialSavePath = new File(initialSavePath, filename);
                startActivityForResult(
                        FilePickerActivityHelper.chooseFileToSave(context, initialSavePath.getAbsolutePath()),
                        REQUEST_DOWNLOAD_SAVE_AS
                );
            }

            return;
        }

        // check for existing file with the same name
        checkSelectedDownload(mainStorage, mainStorage.findFile(filename), filename, mime);
    }

    private void checkSelectedDownload(StoredDirectoryHelper mainStorage, Uri targetFile, String filename, String mime) {
        StoredFileHelper storage;

        try {
            if (mainStorage == null) {
                // using SAF on older android version
                storage = new StoredFileHelper(context, null, targetFile, "");
            } else if (targetFile == null) {
                // the file does not exist, but it is probably used in a pending download
                storage = new StoredFileHelper(mainStorage.getUri(), filename, mime, mainStorage.getTag());
            } else {
                // the target filename is already use, attempt to use it
                storage = new StoredFileHelper(context, mainStorage.getUri(), targetFile, mainStorage.getTag());
            }
        } catch (Exception e) {
            showErrorActivity(e);
            return;
        }

        // check if is our file
        MissionState state = downloadManager.checkForExistingMission(storage);
        @StringRes int msgBtn;
        @StringRes int msgBody;

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


        AlertDialog.Builder askDialog = new AlertDialog.Builder(context)
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
                            storageNew = new StoredFileHelper(context, mainStorage.getUri(), targetFile, mainStorage.getTag());
                        } catch (IOException e) {
                            Log.e(TAG, "Failed to take (or steal) the file in " + targetFile.toString());
                            storageNew = null;
                        }
                    }

                    if (storageNew != null && storageNew.canWrite())
                        continueSelectedDownload(storageNew);
                    else
                        showFailedDialog(R.string.error_file_creation);
                    break;
                case PendingRunning:
                    storageNew = mainStorage.createUniqueFile(filename, mime);
                    if (storageNew == null)
                        showFailedDialog(R.string.error_file_creation);
                    else
                        continueSelectedDownload(storageNew);
                    break;
            }
        });

        askDialog.create().show();
    }

    private void continueSelectedDownload(@NonNull StoredFileHelper storage) {
        if (!storage.canWrite()) {
            showFailedDialog(R.string.permission_denied);
            return;
        }

        // check if the selected file has to be overwritten, by simply checking its length
        try {
            if (storage.length() > 0) storage.truncate();
        } catch (IOException e) {
            Log.e(TAG, "failed to truncate the file: " + storage.getUri().toString(), e);
            showFailedDialog(R.string.overwrite_failed);
            return;
        }

        Stream selectedStream;
        Stream secondaryStream = null;
        char kind;
        int threads = threadsSeekBar.getProgress() + 1;
        String[] urls;
        MissionRecoveryInfo[] recoveryInfo;
        String psName = null;
        String[] psArgs = null;
        long nearLength = 0;

        // more download logic: select muxer, subtitle converter, etc.
        switch (radioStreamsGroup.getCheckedRadioButtonId()) {
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

                SecondaryStreamHelper<AudioStream> secondary = videoStreamsAdapter
                        .getAllSecondary()
                        .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));

                if (secondary != null) {
                    secondaryStream = secondary.getStream();

                    if (selectedStream.getFormat() == MediaFormat.MPEG_4)
                        psName = Postprocessing.ALGORITHM_MP4_FROM_DASH_MUXER;
                    else
                        psName = Postprocessing.ALGORITHM_WEBM_MUXER;

                    psArgs = null;
                    long videoSize = wrappedVideoStreams.getSizeInBytes((VideoStream) selectedStream);

                    // set nearLength, only, if both sizes are fetched or known. This probably
                    // does not work on slow networks but is later updated in the downloader
                    if (secondary.getSizeInBytes() > 0 && videoSize > 0) {
                        nearLength = secondary.getSizeInBytes() + videoSize;
                    }
                }
                break;
            case R.id.subtitle_button:
                threads = 1;// use unique thread for subtitles due small file size
                kind = 's';
                selectedStream = subtitleStreamsAdapter.getItem(selectedSubtitleIndex);

                if (selectedStream.getFormat() == MediaFormat.TTML) {
                    psName = Postprocessing.ALGORITHM_TTML_CONVERTER;
                    psArgs = new String[]{
                            selectedStream.getFormat().getSuffix(),
                            "false",// ignore empty frames
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
            recoveryInfo = new MissionRecoveryInfo[]{
                    new MissionRecoveryInfo(selectedStream), new MissionRecoveryInfo(secondaryStream)
            };
        }

        DownloadManagerService.startMission(
                context, urls, storage, kind, threads, currentInfo.getUrl(), psName, psArgs, nearLength, recoveryInfo
        );

        dismiss();
    }
}
