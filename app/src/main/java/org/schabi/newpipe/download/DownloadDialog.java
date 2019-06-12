package org.schabi.newpipe.download;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
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

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.Localization;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.util.FilenameUtils;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.SecondaryStreamHelper;
import org.schabi.newpipe.util.StreamItemAdapter;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;
import org.schabi.newpipe.util.ThemeHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import icepick.Icepick;
import icepick.State;
import io.reactivex.disposables.CompositeDisposable;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManagerService;

public class DownloadDialog extends DialogFragment implements RadioGroup.OnCheckedChangeListener, AdapterView.OnItemSelectedListener {
    private static final String TAG = "DialogFragment";
    private static final boolean DEBUG = MainActivity.DEBUG;

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
    private RadioGroup radioVideoAudioGroup;
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

        setStyle(STYLE_NO_TITLE, ThemeHelper.getDialogTheme(getContext()));
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

        this.videoStreamsAdapter = new StreamItemAdapter<>(getContext(), wrappedVideoStreams, secondaryStreams);
        this.audioStreamsAdapter = new StreamItemAdapter<>(getContext(), wrappedAudioStreams);
        this.subtitleStreamsAdapter = new StreamItemAdapter<>(getContext(), wrappedSubtitleStreams);
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

        nameEditText.setText(createFilename());
        selectedAudioIndex = ListHelper.getDefaultAudioFormat(getContext(), currentInfo.getAudioStreams());

        selectedSubtitleIndex = getSubtitleIndexBy(subtitleStreamsAdapter.getAll());

        streamsSpinner = view.findViewById(R.id.quality_spinner);
        streamsSpinner.setOnItemSelectedListener(this);

        threadsCountTextView = view.findViewById(R.id.threads_count);
        threadsSeekBar = view.findViewById(R.id.threads);

        radioVideoAudioGroup = view.findViewById(R.id.video_audio_group);
        radioVideoAudioGroup.setOnCheckedChangeListener(this);

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
            if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.video_button) {
                setupVideoSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedAudioStreams).subscribe(result -> {
            if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.audio_button) {
                setupAudioSpinner();
            }
        }));
        disposables.add(StreamSizeWrapper.fetchSizeForWrapper(wrappedSubtitleStreams).subscribe(result -> {
            if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.subtitle_button) {
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
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Inits
    //////////////////////////////////////////////////////////////////////////*/
    private void initToolbar(Toolbar toolbar) {
        if (DEBUG) Log.d(TAG, "initToolbar() called with: toolbar = [" + toolbar + "]");
        toolbar.setTitle(R.string.download_dialog_title);
        toolbar.setNavigationIcon(ThemeHelper.isLightThemeSelected(getActivity()) ? R.drawable.ic_arrow_back_black_24dp : R.drawable.ic_arrow_back_white_24dp);
        toolbar.inflateMenu(R.menu.dialog_url);
        toolbar.setNavigationOnClickListener(v -> getDialog().dismiss());

        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.okay) {
                prepareSelectedDownload();
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
        switch (radioVideoAudioGroup.getCheckedRadioButtonId()) {
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

        final RadioButton audioButton = radioVideoAudioGroup.findViewById(R.id.audio_button);
        final RadioButton videoButton = radioVideoAudioGroup.findViewById(R.id.video_button);
        final RadioButton subtitleButton = radioVideoAudioGroup.findViewById(R.id.subtitle_button);
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
        radioVideoAudioGroup.findViewById(R.id.audio_button).setEnabled(enabled);
        radioVideoAudioGroup.findViewById(R.id.video_button).setEnabled(enabled);
        radioVideoAudioGroup.findViewById(R.id.subtitle_button).setEnabled(enabled);
    }

    private int getSubtitleIndexBy(List<SubtitlesStream> streams) {
        Localization loc = NewPipe.getPreferredLocalization();

        for (int i = 0; i < streams.size(); i++) {
            Locale streamLocale = streams.get(i).getLocale();
            String tag = streamLocale.getLanguage().concat("-").concat(streamLocale.getCountry());
            if (tag.equalsIgnoreCase(loc.getLanguage())) {
                return i;
            }
        }

        // fallback
        // 1st loop match country & language
        // 2nd loop match language only
        int index = loc.getLanguage().indexOf("-");
        String lang = index > 0 ? loc.getLanguage().substring(0, index) : loc.getLanguage();

        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < streams.size(); i++) {
                Locale streamLocale = streams.get(i).getLocale();

                if (streamLocale.getLanguage().equalsIgnoreCase(lang)) {
                    if (j > 0 || streamLocale.getCountry().equalsIgnoreCase(loc.getCountry())) {
                        return i;
                    }
                }
            }
        }

        return 0;
    }

    private void prepareSelectedDownload() {
        final Context context = getContext();
        Stream stream;
        String location;
        char kind;

        String fileName = nameEditText.getText().toString().trim();
        if (fileName.isEmpty())
            fileName = FilenameUtils.createFilename(context, currentInfo.getName());

        switch (radioVideoAudioGroup.getCheckedRadioButtonId()) {
            case R.id.audio_button:
                stream = audioStreamsAdapter.getItem(selectedAudioIndex);
                location = NewPipeSettings.getAudioDownloadPath(context);
                kind = 'a';
                break;
            case R.id.video_button:
                stream = videoStreamsAdapter.getItem(selectedVideoIndex);
                location = NewPipeSettings.getVideoDownloadPath(context);
                kind = 'v';
                break;
            case R.id.subtitle_button:
                stream = subtitleStreamsAdapter.getItem(selectedSubtitleIndex);
                location = NewPipeSettings.getVideoDownloadPath(context);// assume that subtitle & video files go together
                kind = 's';
                break;
            default:
                return;
        }

        int threads;

        if (radioVideoAudioGroup.getCheckedRadioButtonId() == R.id.subtitle_button) {
            threads = 1;// use unique thread for subtitles due small file size
            fileName += ".srt";// final subtitle format
        } else {
            threads = threadsSeekBar.getProgress() + 1;
            fileName += "." + stream.getFormat().getSuffix();
        }

        final String finalFileName = fileName;

        DownloadManagerService.checkForRunningMission(context, location, fileName, (listed, finished) -> {
            if (listed) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle(R.string.download_dialog_title)
                        .setMessage(finished ? R.string.overwrite_warning : R.string.download_already_running)
                        .setPositiveButton(
                                finished ? R.string.overwrite : R.string.generate_unique_name,
                                (dialog, which) -> downloadSelected(context, stream, location, finalFileName, kind, threads)
                        )
                        .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                            dialog.cancel();
                        })
                        .create()
                        .show();
            } else {
                downloadSelected(context, stream, location, finalFileName, kind, threads);
            }
        });
    }

    private void downloadSelected(Context context, Stream selectedStream, String location, String fileName, char kind, int threads) {
        String[] urls;
        String psName = null;
        String[] psArgs = null;
        String secondaryStreamUrl = null;
        long nearLength = 0;

        if (selectedStream instanceof VideoStream) {
            SecondaryStreamHelper<AudioStream> secondaryStream = videoStreamsAdapter
                    .getAllSecondary()
                    .get(wrappedVideoStreams.getStreamsList().indexOf(selectedStream));

            if (secondaryStream != null) {
                secondaryStreamUrl = secondaryStream.getStream().getUrl();
                psName = selectedStream.getFormat() == MediaFormat.MPEG_4 ? Postprocessing.ALGORITHM_MP4_MUXER : Postprocessing.ALGORITHM_WEBM_MUXER;
                psArgs = null;
                long videoSize = wrappedVideoStreams.getSizeInBytes((VideoStream) selectedStream);

                // set nearLength, only, if both sizes are fetched or known. this probably does not work on slow networks
                if (secondaryStream.getSizeInBytes() > 0 && videoSize > 0) {
                    nearLength = secondaryStream.getSizeInBytes() + videoSize;
                }
            }
        } else if ((selectedStream instanceof SubtitlesStream) && selectedStream.getFormat() == MediaFormat.TTML) {
            psName = Postprocessing.ALGORITHM_TTML_CONVERTER;
            psArgs = new String[]{
                    selectedStream.getFormat().getSuffix(),
                    "false",// ignore empty frames
                    "false",// detect youtube duplicate lines
            };
        }

        if (secondaryStreamUrl == null) {
            urls = new String[]{selectedStream.getUrl()};
        } else {
            urls = new String[]{selectedStream.getUrl(), secondaryStreamUrl};
        }

        DownloadManagerService.startMission(context, urls, location, fileName, kind, threads, currentInfo.getUrl(), psName, psArgs, nearLength);

        getDialog().dismiss();
    }

    private String createFilename() {
        String defaultDownloadFilenameFormat = requireContext().getString(
                R.string.download_filename_format_default_value);

        String currentDownloadFilenameFormat = PreferenceManager.getDefaultSharedPreferences(getContext())
                .getString(
                        requireContext().getString(R.string.download_filename_format_key),
                        defaultDownloadFilenameFormat);

        if (currentDownloadFilenameFormat == null) {
            throw new NullPointerException("currentDownloadFilenameFormat is null");
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMM", Locale.US);
        String currentDate = sdf.format(new Date());

        String secondParam = currentInfo.getName();
        String thirdParam = currentDate;

        if (!currentDownloadFilenameFormat.equals(defaultDownloadFilenameFormat)) {
            secondParam = currentDate;
            thirdParam = currentInfo.getName();
        }

        String filename = String.format(
                "%s-%s-%s",
                currentInfo.getUploaderName(),
                secondParam,
                thirdParam);

        return FilenameUtils.createFilename(getContext(), filename);
    }
}
