package org.schabi.newpipe.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnticipateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.view.DisplayCutoutCompat;
import androidx.core.view.ViewCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.video.VideoListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.databinding.PlayerPopupCloseOverlayBinding;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamSegment;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.info_list.StreamSegmentAdapter;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.MainPlayer.PlayerType;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.event.PlayerGestureListener;
import org.schabi.newpipe.player.event.PlayerServiceEventListener;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.MediaSessionManager;
import org.schabi.newpipe.player.helper.PlaybackParameterDialog;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playback.CustomTrackSelector;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.player.playback.PlayerMediaSession;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.KoreUtil;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.SerializedCache;
import org.schabi.newpipe.util.ShareUtils;
import org.schabi.newpipe.views.ExpandableSurfaceView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.disposables.SerialDisposable;

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_AD_INSERTION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_PERIOD_TRANSITION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;
import static com.google.android.exoplayer2.Player.DiscontinuityReason;
import static com.google.android.exoplayer2.Player.EventListener;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static com.google.android.exoplayer2.Player.RepeatMode;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.schabi.newpipe.extractor.ServiceList.YouTube;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.MainPlayer.ACTION_CLOSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_FORWARD;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_REWIND;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PAUSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.MainPlayer.ACTION_RECREATE_NOTIFICATION;
import static org.schabi.newpipe.player.MainPlayer.ACTION_REPEAT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_SHUFFLE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;
import static org.schabi.newpipe.player.helper.PlayerHelper.buildCloseOverlayLayoutParams;
import static org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimizeOnExitAction;
import static org.schabi.newpipe.player.helper.PlayerHelper.getMinimumVideoHeight;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.helper.PlayerHelper.isPlaybackResumeEnabled;
import static org.schabi.newpipe.player.helper.PlayerHelper.nextRepeatMode;
import static org.schabi.newpipe.player.helper.PlayerHelper.nextResizeModeAndSaveToPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePlaybackParametersFromPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePlayerTypeFromIntent;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrievePopupLayoutParamsFromPrefs;
import static org.schabi.newpipe.player.helper.PlayerHelper.retrieveSeekDurationFromPreferences;
import static org.schabi.newpipe.player.helper.PlayerHelper.savePlaybackParametersToPrefs;
import static org.schabi.newpipe.util.ListHelper.getPopupResolutionIndex;
import static org.schabi.newpipe.util.ListHelper.getResolutionIndex;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;
import static org.schabi.newpipe.util.Localization.containsCaseInsensitive;

public final class Player implements
        EventListener,
        PlaybackListener,
        ImageLoadingListener,
        VideoListener,
        SeekBar.OnSeekBarChangeListener,
        View.OnClickListener,
        PopupMenu.OnMenuItemClickListener,
        PopupMenu.OnDismissListener,
        View.OnLongClickListener {
    public static final boolean DEBUG = MainActivity.DEBUG;
    public static final String TAG = Player.class.getSimpleName();

    /*//////////////////////////////////////////////////////////////////////////
    // States
    //////////////////////////////////////////////////////////////////////////*/

    public static final int STATE_PREFLIGHT = -1;
    public static final int STATE_BLOCKED = 123;
    public static final int STATE_PLAYING = 124;
    public static final int STATE_BUFFERING = 125;
    public static final int STATE_PAUSED = 126;
    public static final int STATE_PAUSED_SEEK = 127;
    public static final int STATE_COMPLETED = 128;

    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    public static final String REPEAT_MODE = "repeat_mode";
    public static final String PLAYBACK_QUALITY = "playback_quality";
    public static final String PLAY_QUEUE_KEY = "play_queue_key";
    public static final String APPEND_ONLY = "append_only";
    public static final String RESUME_PLAYBACK = "resume_playback";
    public static final String PLAY_WHEN_READY = "play_when_ready";
    public static final String SELECT_ON_APPEND = "select_on_append";
    public static final String PLAYER_TYPE = "player_type";
    public static final String IS_MUTED = "is_muted";

    /*//////////////////////////////////////////////////////////////////////////
    // Time constants
    //////////////////////////////////////////////////////////////////////////*/

    public static final int PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000; // 5 seconds
    public static final int PROGRESS_LOOP_INTERVAL_MILLIS = 500; // 500 millis
    public static final int DEFAULT_CONTROLS_DURATION = 300; // 300 millis
    public static final int DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds
    public static final int DPAD_CONTROLS_HIDE_TIME = 7000;  // 7 Seconds

    /*//////////////////////////////////////////////////////////////////////////
    // Other constants
    //////////////////////////////////////////////////////////////////////////*/

    private static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f};

    private static final int RENDERER_UNAVAILABLE = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    //////////////////////////////////////////////////////////////////////////*/

    private PlayQueue playQueue;
    private PlayQueueAdapter playQueueAdapter;
    private StreamSegmentAdapter segmentAdapter;

    @Nullable private MediaSourceManager playQueueManager;

    @Nullable private PlayQueueItem currentItem;
    @Nullable private MediaSourceTag currentMetadata;
    @Nullable private Bitmap currentThumbnail;

    @Nullable private Toast errorToast;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    private SimpleExoPlayer simpleExoPlayer;
    private AudioReactor audioReactor;
    private MediaSessionManager mediaSessionManager;

    @NonNull private final CustomTrackSelector trackSelector;
    @NonNull private final LoadController loadController;
    @NonNull private final RenderersFactory renderFactory;

    @NonNull private final VideoPlaybackResolver videoResolver;
    @NonNull private final AudioPlaybackResolver audioResolver;

    private final MainPlayer service; //TODO try to remove and replace everything with context

    /*//////////////////////////////////////////////////////////////////////////
    // Player states
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerType playerType = PlayerType.VIDEO;
    private int currentState = STATE_PREFLIGHT;

    // audio only mode does not mean that player type is background, but that the player was
    // minimized to background but will resume automatically to the original player type
    private boolean isAudioOnly = false;
    private boolean isPrepared = false;
    private boolean wasPlaying = false;
    private boolean isFullscreen = false;
    private boolean isVerticalVideo = false;
    private boolean fragmentIsVisible = false;
    private boolean isLocked = false;

    private List<VideoStream> availableStreams;
    private int selectedStreamIndex;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerBinding binding;

    private ValueAnimator controlViewAnimator;
    private final Handler controlsVisibilityHandler = new Handler();

    // fullscreen player
    private boolean isQueueVisible = false;
    private boolean areSegmentsVisible = false;
    private ItemTouchHelper itemTouchHelper;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    //////////////////////////////////////////////////////////////////////////*/

    private static final int POPUP_MENU_ID_QUALITY = 69;
    private static final int POPUP_MENU_ID_PLAYBACK_SPEED = 79;
    private static final int POPUP_MENU_ID_CAPTION = 89;

    private boolean isSomePopupMenuVisible = false;
    private PopupMenu qualityPopupMenu;
    private PopupMenu playbackSpeedPopupMenu;
    private PopupMenu captionPopupMenu;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup player
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerPopupCloseOverlayBinding closeOverlayBinding;

    private boolean isPopupClosing = false;

    private float screenWidth;
    private float screenHeight;

    /*//////////////////////////////////////////////////////////////////////////
    // Popup player window manager
    //////////////////////////////////////////////////////////////////////////*/

    public static final int IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
    public static final int ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    @Nullable private WindowManager.LayoutParams popupLayoutParams; // null if player is not popup
    @Nullable private final WindowManager windowManager;

    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/

    private static final float MAX_GESTURE_LENGTH = 0.75f;

    private int maxGestureLength; // scaled
    private GestureDetector gestureDetector;

    /*//////////////////////////////////////////////////////////////////////////
    // Listeners and disposables
    //////////////////////////////////////////////////////////////////////////*/

    private BroadcastReceiver broadcastReceiver;
    private IntentFilter intentFilter;
    private PlayerServiceEventListener fragmentListener;
    private PlayerEventListener activityListener;
    private ContentObserver settingsContentObserver;

    @NonNull private final SerialDisposable progressUpdateDisposable = new SerialDisposable();
    @NonNull private final CompositeDisposable databaseUpdateDisposable = new CompositeDisposable();

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull private final Context context;
    @NonNull private final SharedPreferences prefs;
    @NonNull private final HistoryRecordManager recordManager;



    /*//////////////////////////////////////////////////////////////////////////
    // Constructor
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public Player(@NonNull final MainPlayer service) {
        this.service = service;
        context = service;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        recordManager = new HistoryRecordManager(context);

        setupBroadcastReceiver();

        trackSelector = new CustomTrackSelector(context, PlayerHelper.getQualitySelector());
        final PlayerDataSource dataSource = new PlayerDataSource(context, DownloaderImpl.USER_AGENT,
                new DefaultBandwidthMeter.Builder(context).build());
        loadController = new LoadController();
        renderFactory = new DefaultRenderersFactory(context);

        videoResolver = new VideoPlaybackResolver(context, dataSource, getQualityResolver());
        audioResolver = new AudioPlaybackResolver(context, dataSource);

        windowManager = ContextCompat.getSystemService(context, WindowManager.class);
    }

    private VideoPlaybackResolver.QualityResolver getQualityResolver() {
        return new VideoPlaybackResolver.QualityResolver() {
            @Override
            public int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
                return videoPlayerSelected()
                        ? ListHelper.getDefaultResolutionIndex(context, sortedVideos)
                        : ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
            }

            @Override
            public int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                  final String playbackQuality) {
                return videoPlayerSelected()
                        ? getResolutionIndex(context, sortedVideos, playbackQuality)
                        : getPopupResolutionIndex(context, sortedVideos, playbackQuality);
            }
        };
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Setup and initialization
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public void setupFromView(@NonNull final PlayerBinding playerBinding) {
        initViews(playerBinding);
        if (exoPlayerIsNull()) {
            initPlayer(true);
        }
        initListeners();
    }

    private void initViews(@NonNull final PlayerBinding playerBinding) {
        binding = playerBinding;
        setupSubtitleView();

        binding.resizeTextView
                .setText(PlayerHelper.resizeTypeOf(context, binding.surfaceView.getResizeMode()));

        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN));
        binding.playbackSeekBar.getProgressDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY));

        qualityPopupMenu = new PopupMenu(context, binding.qualityTextView);
        playbackSpeedPopupMenu = new PopupMenu(context, binding.playbackSpeed);
        captionPopupMenu = new PopupMenu(context, binding.captionTextView);

        binding.progressBarLoadingPanel.getIndeterminateDrawable()
                .setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));

        binding.titleTextView.setSelected(true);
        binding.channelTextView.setSelected(true);

        // Prevent hiding of bottom sheet via swipe inside queue
        binding.itemsList.setNestedScrollingEnabled(false);
    }

    private void initPlayer(final boolean playOnReady) {
        if (DEBUG) {
            Log.d(TAG, "initPlayer() called with: playOnReady = [" + playOnReady + "]");
        }

        simpleExoPlayer = new SimpleExoPlayer.Builder(context, renderFactory)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadController)
                .build();
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(playOnReady);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context));
        simpleExoPlayer.setWakeMode(C.WAKE_MODE_NETWORK);
        simpleExoPlayer.setHandleAudioBecomingNoisy(true);

        audioReactor = new AudioReactor(context, simpleExoPlayer);
        mediaSessionManager = new MediaSessionManager(context, simpleExoPlayer,
                new PlayerMediaSession(this));

        registerBroadcastReceiver();

        // Setup video view
        simpleExoPlayer.setVideoSurfaceView(binding.surfaceView);
        simpleExoPlayer.addVideoListener(this);

        // Setup subtitle view
        simpleExoPlayer.addTextOutput(binding.subtitleView);

        // Setup audio session with onboard equalizer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingAudioSessionId(C.generateAudioSessionIdV21(context)));
        }
    }

    private void initListeners() {
        binding.playbackSeekBar.setOnSeekBarChangeListener(this);
        binding.playbackSpeed.setOnClickListener(this);
        binding.qualityTextView.setOnClickListener(this);
        binding.captionTextView.setOnClickListener(this);
        binding.resizeTextView.setOnClickListener(this);
        binding.playbackLiveSync.setOnClickListener(this);

        final PlayerGestureListener listener = new PlayerGestureListener(this, service);
        gestureDetector = new GestureDetector(context, listener);
        binding.getRoot().setOnTouchListener(listener);

        binding.queueButton.setOnClickListener(this);
        binding.segmentsButton.setOnClickListener(this);
        binding.repeatButton.setOnClickListener(this);
        binding.shuffleButton.setOnClickListener(this);

        binding.playPauseButton.setOnClickListener(this);
        binding.playPreviousButton.setOnClickListener(this);
        binding.playNextButton.setOnClickListener(this);
        binding.unlockButton.setOnLongClickListener(this);

        binding.moreOptionsButton.setOnClickListener(this);
        binding.moreOptionsButton.setOnLongClickListener(this);
        binding.share.setOnClickListener(this);
        binding.fullScreenButton.setOnClickListener(this);
        binding.screenRotationButton.setOnClickListener(this);
        binding.playWithKodi.setOnClickListener(this);
        binding.openInBrowser.setOnClickListener(this);
        binding.screenLock.setOnClickListener(this);
        binding.playerCloseButton.setOnClickListener(this);
        binding.switchMute.setOnClickListener(this);

        settingsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(final boolean selfChange) {
                setupScreenRotationButton();
            }
        };
        context.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
        binding.getRoot().addOnLayoutChangeListener(this::onLayoutChange);

        ViewCompat.setOnApplyWindowInsetsListener(binding.itemsListPanel, (view, windowInsets) -> {
            final DisplayCutoutCompat cutout = windowInsets.getDisplayCutout();
            if (cutout != null) {
                view.setPadding(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                        cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
            }
            return windowInsets;
        });

        // PlaybackControlRoot already consumed window insets but we should pass them to
        // player_overlays too. Without it they will be off-centered
        binding.playbackControlRoot.addOnLayoutChangeListener(
                (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        binding.playerOverlays.setPadding(
                                v.getPaddingLeft(),
                                v.getPaddingTop(),
                                v.getPaddingRight(),
                                v.getPaddingBottom()));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback initialization via intent
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public void handleIntent(@NonNull final Intent intent) {
        // fail fast if no play queue was provided
        final String queueCache = intent.getStringExtra(PLAY_QUEUE_KEY);
        if (queueCache == null) {
            return;
        }
        final PlayQueue newQueue = SerializedCache.getInstance().take(queueCache, PlayQueue.class);
        if (newQueue == null) {
            return;
        }

        final PlayerType oldPlayerType = playerType;
        playerType = retrievePlayerTypeFromIntent(intent);
        // We need to setup audioOnly before super(), see "sourceOf"
        isAudioOnly = audioPlayerSelected();

        if (intent.hasExtra(PLAYBACK_QUALITY)) {
            setPlaybackQuality(intent.getStringExtra(PLAYBACK_QUALITY));
        }

        // Resolve append intents
        if (intent.getBooleanExtra(APPEND_ONLY, false) && playQueue != null) {
            final int sizeBeforeAppend = playQueue.size();
            playQueue.append(newQueue.getStreams());

            if ((intent.getBooleanExtra(SELECT_ON_APPEND, false)
                    || currentState == STATE_COMPLETED) && newQueue.getStreams().size() > 0) {
                playQueue.setIndex(sizeBeforeAppend);
            }

            return;
        }

        final PlaybackParameters savedParameters = retrievePlaybackParametersFromPrefs(this);
        final float playbackSpeed = savedParameters.speed;
        final float playbackPitch = savedParameters.pitch;
        final boolean playbackSkipSilence = savedParameters.skipSilence;

        final boolean samePlayQueue = playQueue != null && playQueue.equals(newQueue);
        final int repeatMode = intent.getIntExtra(REPEAT_MODE, getRepeatMode());
        final boolean playWhenReady = intent.getBooleanExtra(PLAY_WHEN_READY, true);
        final boolean isMuted = intent.getBooleanExtra(IS_MUTED, isMuted());

        /*
         * There are 3 situations when playback shouldn't be started from scratch (zero timestamp):
         * 1. User pressed on a timestamp link and the same video should be rewound to the timestamp
         * 2. User changed a player from, for example. main to popup, or from audio to main, etc
         * 3. User chose to resume a video based on a saved timestamp from history of played videos
         * In those cases time will be saved because re-init of the play queue is a not an instant
         *  task and requires network calls
         * */
        // seek to timestamp if stream is already playing
        if (!exoPlayerIsNull()
                && newQueue.size() == 1 && newQueue.getItem() != null
                && playQueue != null && playQueue.size() == 1 && playQueue.getItem() != null
                && newQueue.getItem().getUrl().equals(playQueue.getItem().getUrl())
                && newQueue.getItem().getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET) {
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry() in this case
            if (simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_IDLE) {
                simpleExoPlayer.retry();
            }
            simpleExoPlayer.seekTo(playQueue.getIndex(), newQueue.getItem().getRecoveryPosition());
            simpleExoPlayer.setPlayWhenReady(playWhenReady);

        } else if (!exoPlayerIsNull()
                && samePlayQueue
                && playQueue != null
                && !playQueue.isDisposed()) {
            // Do not re-init the same PlayQueue. Save time
            // Player can have state = IDLE when playback is stopped or failed
            // and we should retry() in this case
            if (simpleExoPlayer.getPlaybackState()
                    == com.google.android.exoplayer2.Player.STATE_IDLE) {
                simpleExoPlayer.retry();
            }
            simpleExoPlayer.setPlayWhenReady(playWhenReady);

        } else if (intent.getBooleanExtra(RESUME_PLAYBACK, false)
                && isPlaybackResumeEnabled(this)
                && !samePlayQueue
                && !newQueue.isEmpty()
                && newQueue.getItem().getRecoveryPosition() == PlayQueueItem.RECOVERY_UNSET) {
            databaseUpdateDisposable.add(recordManager.loadStreamState(newQueue.getItem())
                    .observeOn(AndroidSchedulers.mainThread())
                    // Do not place initPlayback() in doFinally() because
                    // it restarts playback after destroy()
                    //.doFinally()
                    .subscribe(
                            state -> {
                                newQueue.setRecovery(newQueue.getIndex(), state.getProgressTime());
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted);
                            },
                            error -> {
                                if (DEBUG) {
                                    error.printStackTrace();
                                }
                                // In case any error we can start playback without history
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted);
                            },
                            () -> {
                                // Completed but not found in history
                                initPlayback(newQueue, repeatMode, playbackSpeed, playbackPitch,
                                        playbackSkipSilence, playWhenReady, isMuted);
                            }
                    ));
        } else {
            // Good to go...
            // In a case of equal PlayQueues we can re-init old one but only when it is disposed
            initPlayback(samePlayQueue ? playQueue : newQueue, repeatMode, playbackSpeed,
                    playbackPitch, playbackSkipSilence, playWhenReady, isMuted);
        }

        if (oldPlayerType != playerType && playQueue != null) {
            // If playerType changes from one to another we should reload the player
            // (to disable/enable video stream or to set quality)
            setRecovery();
            reloadPlayQueueManager();
        }

        setupElementsVisibility();
        setupElementsSize();

        if (audioPlayerSelected()) {
            service.removeViewFromParent();
        } else if (popupPlayerSelected()) {
            binding.getRoot().setVisibility(View.VISIBLE);
            initPopup();
            initPopupCloseOverlay();
            binding.playPauseButton.requestFocus();
        } else {
            binding.getRoot().setVisibility(View.VISIBLE);
            initVideoPlayer();
            closeItemsList();
            // Android TV: without it focus will frame the whole player
            binding.playPauseButton.requestFocus();

            if (simpleExoPlayer.getPlayWhenReady()) {
                play();
            } else {
                pause();
            }
        }
        NavigationHelper.sendPlayerStartedEvent(context);
    }

    private void initPlayback(@NonNull final PlayQueue queue,
                              @RepeatMode final int repeatMode,
                              final float playbackSpeed,
                              final float playbackPitch,
                              final boolean playbackSkipSilence,
                              final boolean playOnReady,
                              final boolean isMuted) {
        destroyPlayer();
        initPlayer(playOnReady);
        setRepeatMode(repeatMode);
        setPlaybackParameters(playbackSpeed, playbackPitch, playbackSkipSilence);

        playQueue = queue;
        playQueue.init();
        reloadPlayQueueManager();

        if (playQueueAdapter != null) {
            playQueueAdapter.dispose();
        }
        playQueueAdapter = new PlayQueueAdapter(context, playQueue);
        segmentAdapter = new StreamSegmentAdapter(getStreamSegmentListener());

        simpleExoPlayer.setVolume(isMuted ? 0 : 1);
        notifyQueueUpdateToListeners();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Destroy and recovery
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void destroyPlayer() {
        if (DEBUG) {
            Log.d(TAG, "destroyPlayer() called");
        }
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.removeListener(this);
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }
        if (playQueue != null) {
            playQueue.dispose();
        }
        if (audioReactor != null) {
            audioReactor.dispose();
        }
        if (playQueueManager != null) {
            playQueueManager.dispose();
        }
        if (mediaSessionManager != null) {
            mediaSessionManager.dispose();
        }

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
    }

    public void destroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        destroyPlayer();
        unregisterBroadcastReceiver();

        databaseUpdateDisposable.clear();
        progressUpdateDisposable.set(null);
        ImageLoader.getInstance().stop();

        if (binding != null) {
            binding.endScreen.setImageBitmap(null);
        }

        context.getContentResolver().unregisterContentObserver(settingsContentObserver);
    }

    public void setRecovery() {
        if (playQueue == null || exoPlayerIsNull()) {
            return;
        }

        final int queuePos = playQueue.getIndex();
        final long windowPos = simpleExoPlayer.getCurrentPosition();

        if (windowPos > 0 && windowPos <= simpleExoPlayer.getDuration()) {
            setRecovery(queuePos, windowPos);
        }
    }

    private void setRecovery(final int queuePos, final long windowPos) {
        if (playQueue.size() <= queuePos) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Setting recovery, queue: " + queuePos + ", pos: " + windowPos);
        }
        playQueue.setRecovery(queuePos, windowPos);
    }

    private void reloadPlayQueueManager() {
        if (playQueueManager != null) {
            playQueueManager.dispose();
        }

        if (playQueue != null) {
            playQueueManager = new MediaSourceManager(this, playQueue);
        }
    }

    @Override // own playback listener
    public void onPlaybackShutdown() {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackShutdown() called");
        }
        // destroys the service, which in turn will destroy the player
        service.onDestroy();
    }

    public void smoothStopPlayer() {
        // Pausing would make transition from one stream to a new stream not smooth, so only stop
        simpleExoPlayer.stop(false);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Player type specific setup
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void initVideoPlayer() {
        // restore last resize mode
        setResizeMode(PlayerHelper.retrieveResizeModeFromPrefs(this));
        binding.getRoot().setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) {
            Log.d(TAG, "initPopup() called");
        }

        // Popup is already added to windowManager
        if (popupHasParent()) {
            return;
        }

        updateScreenSize();

        popupLayoutParams = retrievePopupLayoutParamsFromPrefs(this);
        binding.surfaceView.setHeights(popupLayoutParams.height, popupLayoutParams.height);

        checkPopupPositionBounds();

        binding.loadingPanel.setMinimumWidth(popupLayoutParams.width);
        binding.loadingPanel.setMinimumHeight(popupLayoutParams.height);

        service.removeViewFromParent();
        Objects.requireNonNull(windowManager).addView(binding.getRoot(), popupLayoutParams);

        // Popup doesn't have aspectRatio selector, using FIT automatically
        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    @SuppressLint("RtlHardcoded")
    private void initPopupCloseOverlay() {
        if (DEBUG) {
            Log.d(TAG, "initPopupCloseOverlay() called");
        }

        // closeOverlayView is already added to windowManager
        if (closeOverlayBinding != null) {
            return;
        }

        closeOverlayBinding = PlayerPopupCloseOverlayBinding.inflate(LayoutInflater.from(context));

        final WindowManager.LayoutParams closeOverlayLayoutParams = buildCloseOverlayLayoutParams();
        closeOverlayBinding.closeButton.setVisibility(View.GONE);
        Objects.requireNonNull(windowManager).addView(
                closeOverlayBinding.getRoot(), closeOverlayLayoutParams);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Elements visibility and size: popup and main players have different look
    //////////////////////////////////////////////////////////////////////////*/
    //region

    /**
     * This method ensures that popup and main players have different look.
     * We use one layout for both players and need to decide what to show and what to hide.
     * Additional measuring should be done inside {@link #setupElementsSize}.
     */
    private void setupElementsVisibility() {
        if (popupPlayerSelected()) {
            binding.fullScreenButton.setVisibility(View.VISIBLE);
            binding.screenRotationButton.setVisibility(View.GONE);
            binding.resizeTextView.setVisibility(View.GONE);
            binding.getRoot().findViewById(R.id.metadataView).setVisibility(View.GONE);
            binding.queueButton.setVisibility(View.GONE);
            binding.segmentsButton.setVisibility(View.GONE);
            binding.moreOptionsButton.setVisibility(View.GONE);
            binding.topControls.setOrientation(LinearLayout.HORIZONTAL);
            binding.primaryControls.getLayoutParams().width
                    = LinearLayout.LayoutParams.WRAP_CONTENT;
            binding.secondaryControls.setAlpha(1.0f);
            binding.secondaryControls.setVisibility(View.VISIBLE);
            binding.secondaryControls.setTranslationY(0);
            binding.share.setVisibility(View.GONE);
            binding.playWithKodi.setVisibility(View.GONE);
            binding.screenLock.setVisibility(View.GONE);
            binding.openInBrowser.setVisibility(View.GONE);
            binding.switchMute.setVisibility(View.GONE);
            binding.playerCloseButton.setVisibility(View.GONE);
            binding.topControls.bringToFront();
            binding.topControls.setClickable(false);
            binding.topControls.setFocusable(false);
            binding.bottomControls.bringToFront();
            closeItemsList();
        } else if (videoPlayerSelected()) {
            binding.fullScreenButton.setVisibility(View.GONE);
            setupScreenRotationButton();
            binding.resizeTextView.setVisibility(View.VISIBLE);
            binding.getRoot().findViewById(R.id.metadataView).setVisibility(View.VISIBLE);
            binding.moreOptionsButton.setVisibility(View.VISIBLE);
            binding.topControls.setOrientation(LinearLayout.VERTICAL);
            binding.primaryControls.getLayoutParams().width
                    = LinearLayout.LayoutParams.MATCH_PARENT;
            binding.secondaryControls.setVisibility(View.INVISIBLE);
            binding.moreOptionsButton.setImageDrawable(AppCompatResources.getDrawable(context,
                    R.drawable.ic_expand_more_white_24dp));
            binding.share.setVisibility(View.VISIBLE);
            binding.openInBrowser.setVisibility(View.VISIBLE);
            binding.switchMute.setVisibility(View.VISIBLE);
            binding.playerCloseButton.setVisibility(isFullscreen ? View.GONE : View.VISIBLE);
            // Top controls have a large minHeight which is allows to drag the player
            // down in fullscreen mode (just larger area to make easy to locate by finger)
            binding.topControls.setClickable(true);
            binding.topControls.setFocusable(true);
        }
        showHideLockButton();
        showHideKodiButton();

        if (isFullscreen) {
            binding.titleTextView.setVisibility(View.VISIBLE);
            binding.channelTextView.setVisibility(View.VISIBLE);
        } else {
            binding.titleTextView.setVisibility(View.GONE);
            binding.channelTextView.setVisibility(View.GONE);
        }
        setMuteButton(binding.switchMute, isMuted());

        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION, 0);
    }

    /**
     * Changes padding, size of elements based on player selected right now.
     * Popup player has small padding in comparison with the main player
     */
    private void setupElementsSize() {
        final Resources res = context.getResources();
        final int buttonsMinWidth;
        final int playerTopPad;
        final int controlsPad;
        final int buttonsPad;

        if (popupPlayerSelected()) {
            buttonsMinWidth = 0;
            playerTopPad = 0;
            controlsPad = res.getDimensionPixelSize(R.dimen.player_popup_controls_padding);
            buttonsPad = res.getDimensionPixelSize(R.dimen.player_popup_buttons_padding);
        } else if (videoPlayerSelected()) {
            buttonsMinWidth = res.getDimensionPixelSize(R.dimen.player_main_buttons_min_width);
            playerTopPad = res.getDimensionPixelSize(R.dimen.player_main_top_padding);
            controlsPad = res.getDimensionPixelSize(R.dimen.player_main_controls_padding);
            buttonsPad = res.getDimensionPixelSize(R.dimen.player_main_buttons_padding);
        } else {
            return;
        }

        binding.topControls.setPaddingRelative(controlsPad, playerTopPad, controlsPad, 0);
        binding.bottomControls.setPaddingRelative(controlsPad, 0, controlsPad, 0);
        binding.qualityTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
        binding.playbackSpeed.setMinimumWidth(buttonsMinWidth);
        binding.captionTextView.setPadding(buttonsPad, buttonsPad, buttonsPad, buttonsPad);
    }

    private void showHideLockButton() {
        final boolean showLockButton = !DeviceUtils.isTv(context) && isFullscreen;
        binding.screenLock.setVisibility(showLockButton ? View.VISIBLE : View.GONE);
    }

    private void showHideKodiButton() {
        // show kodi button if it supports the current service and it is enabled in settings
        binding.playWithKodi.setVisibility(videoPlayerSelected()
                && playQueue != null && playQueue.getItem() != null
                && KoreUtil.shouldShowPlayWithKodi(context, playQueue.getItem().getServiceId())
                ? View.VISIBLE : View.GONE);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast receiver
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void setupBroadcastReceiver() {
        if (DEBUG) {
            Log.d(TAG, "setupBroadcastReceiver() called");
        }

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context ctx, final Intent intent) {
                onBroadcastReceived(intent);
            }
        };
        intentFilter = new IntentFilter();

        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

        intentFilter.addAction(ACTION_CLOSE);
        intentFilter.addAction(ACTION_PLAY_PAUSE);
        intentFilter.addAction(ACTION_PLAY_PREVIOUS);
        intentFilter.addAction(ACTION_PLAY_NEXT);
        intentFilter.addAction(ACTION_FAST_REWIND);
        intentFilter.addAction(ACTION_FAST_FORWARD);
        intentFilter.addAction(ACTION_REPEAT);
        intentFilter.addAction(ACTION_SHUFFLE);
        intentFilter.addAction(ACTION_RECREATE_NOTIFICATION);

        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED);
        intentFilter.addAction(VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED);

        intentFilter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
    }

    private void onBroadcastReceived(final Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
        }

        switch (intent.getAction()) {
            case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                pause();
                break;
            case ACTION_CLOSE:
                service.onDestroy();
                break;
            case ACTION_PLAY_PAUSE:
                playPause();
                if (!fragmentIsVisible) {
                    // Ensure that we have audio-only stream playing when a user
                    // started to play from notification's play button from outside of the app
                    onFragmentStopped();
                }
                break;
            case ACTION_PLAY_PREVIOUS:
                playPrevious();
                break;
            case ACTION_PLAY_NEXT:
                playNext();
                break;
            case ACTION_FAST_REWIND:
                fastRewind();
                break;
            case ACTION_FAST_FORWARD:
                fastForward();
                break;
            case ACTION_REPEAT:
                onRepeatClicked();
                break;
            case ACTION_SHUFFLE:
                onShuffleClicked();
                break;
            case ACTION_RECREATE_NOTIFICATION:
                NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, true);
                break;
            case VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED:
                fragmentIsVisible = true;
                useVideoSource(true);
                break;
            case VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED:
                fragmentIsVisible = false;
                onFragmentStopped();
                break;
            case Intent.ACTION_CONFIGURATION_CHANGED:
                assureCorrectAppLanguage(service);
                if (DEBUG) {
                    Log.d(TAG, "onConfigurationChanged() called");
                }
                if (popupPlayerSelected()) {
                    updateScreenSize();
                    changePopupSize(popupLayoutParams.width);
                    checkPopupPositionBounds();
                }
                // Close it because when changing orientation from portrait
                // (in fullscreen mode) the size of queue layout can be larger than the screen size
                closeItemsList();
                break;
            case Intent.ACTION_SCREEN_ON:
                // Interrupt playback only when screen turns on
                // and user is watching video in popup player.
                // Same actions for video player will be handled in ACTION_VIDEO_FRAGMENT_RESUMED
                if (popupPlayerSelected() && (isPlaying() || isLoading())) {
                    useVideoSource(true);
                }
                break;
            case Intent.ACTION_SCREEN_OFF:
                // Interrupt playback only when screen turns off with popup player working
                if (popupPlayerSelected() && (isPlaying() || isLoading())) {
                    useVideoSource(false);
                }
                break;
            case Intent.ACTION_HEADSET_PLUG: //FIXME
                /*notificationManager.cancel(NOTIFICATION_ID);
                mediaSessionManager.dispose();
                mediaSessionManager.enable(getBaseContext(), basePlayerImpl.simpleExoPlayer);*/
                break;
        }
    }

    private void registerBroadcastReceiver() {
        // Try to unregister current first
        unregisterBroadcastReceiver();
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    private void unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (final IllegalArgumentException unregisteredException) {
            Log.w(TAG, "Broadcast receiver already unregistered: "
                    + unregisteredException.getMessage());
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail loading
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void initThumbnail(final String url) {
        if (DEBUG) {
            Log.d(TAG, "Thumbnail - initThumbnail() called");
        }
        if (url == null || url.isEmpty()) {
            return;
        }
        ImageLoader.getInstance().resume();
        ImageLoader.getInstance()
                .loadImage(url, ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, this);
    }

    @Override
    public void onLoadingStarted(final String imageUri, final View view) {
        if (DEBUG) {
            Log.d(TAG, "Thumbnail - onLoadingStarted() called on: "
                    + "imageUri = [" + imageUri + "], view = [" + view + "]");
        }
    }

    @Override
    public void onLoadingFailed(final String imageUri, final View view,
                                final FailReason failReason) {
        Log.e(TAG, "Thumbnail - onLoadingFailed() called on imageUri = [" + imageUri + "]",
                failReason.getCause());
        currentThumbnail = null;
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    @Override
    public void onLoadingComplete(final String imageUri, final View view,
                                  final Bitmap loadedImage) {
        final float width = Math.min(
                context.getResources().getDimension(R.dimen.player_notification_thumbnail_width),
                loadedImage.getWidth());

        if (DEBUG) {
            Log.d(TAG, "Thumbnail - onLoadingComplete() called with: "
                    + "imageUri = [" + imageUri + "], view = [" + view + "], "
                    + "loadedImage = [" + loadedImage + "], "
                    + loadedImage.getWidth() + "x" + loadedImage.getHeight()
                    + ", scaled width = " + width);
        }

        currentThumbnail = Bitmap.createScaledBitmap(loadedImage,
                (int) width,
                (int) (loadedImage.getHeight() / (loadedImage.getWidth() / width)), true);
        binding.endScreen.setImageBitmap(loadedImage);
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    @Override
    public void onLoadingCancelled(final String imageUri, final View view) {
        if (DEBUG) {
            Log.d(TAG, "Thumbnail - onLoadingCancelled() called with: "
                    + "imageUri = [" + imageUri + "], view = [" + view + "]");
        }
        currentThumbnail = null;
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Popup player utils
    //////////////////////////////////////////////////////////////////////////*/
    //region

    /**
     * Check if {@link #popupLayoutParams}' position is within a arbitrary boundary
     * that goes from (0, 0) to (screenWidth, screenHeight).
     * <p>
     * If it's out of these boundaries, {@link #popupLayoutParams}' position is changed
     * and {@code true} is returned to represent this change.
     * </p>
     */
    public void checkPopupPositionBounds() {
        if (DEBUG) {
            Log.d(TAG, "checkPopupPositionBounds() called with: "
                    + "screenWidth = [" + screenWidth + "], "
                    + "screenHeight = [" + screenHeight + "]");
        }
        if (popupLayoutParams == null) {
            return;
        }

        if (popupLayoutParams.x < 0) {
            popupLayoutParams.x = 0;
        } else if (popupLayoutParams.x > screenWidth - popupLayoutParams.width) {
            popupLayoutParams.x = (int) (screenWidth - popupLayoutParams.width);
        }

        if (popupLayoutParams.y < 0) {
            popupLayoutParams.y = 0;
        } else if (popupLayoutParams.y > screenHeight - popupLayoutParams.height) {
            popupLayoutParams.y = (int) (screenHeight - popupLayoutParams.height);
        }
    }

    public void updateScreenSize() {
        if (windowManager != null) {
            final DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getMetrics(metrics);

            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            if (DEBUG) {
                Log.d(TAG, "updateScreenSize() called: screenWidth = ["
                        + screenWidth + "], screenHeight = [" + screenHeight + "]");
            }
        }
    }

    /**
     * Changes the size of the popup based on the width.
     * @param width the new width, height is calculated with
     *              {@link PlayerHelper#getMinimumVideoHeight(float)}
     */
    public void changePopupSize(final int width) {
        if (DEBUG) {
            Log.d(TAG, "changePopupSize() called with: width = [" + width + "]");
        }

        if (anyPopupViewIsNull()) {
            return;
        }

        final float minimumWidth = context.getResources().getDimension(R.dimen.popup_minimum_width);
        final int actualWidth = (int) (width > screenWidth ? screenWidth
                : (width < minimumWidth ? minimumWidth : width));
        final int actualHeight = (int) getMinimumVideoHeight(width);
        if (DEBUG) {
            Log.d(TAG, "updatePopupSize() updated values:"
                    + "  width = [" + actualWidth + "], height = [" + actualHeight + "]");
        }

        popupLayoutParams.width = actualWidth;
        popupLayoutParams.height = actualHeight;
        binding.surfaceView.setHeights(popupLayoutParams.height, popupLayoutParams.height);
        Objects.requireNonNull(windowManager)
                .updateViewLayout(binding.getRoot(), popupLayoutParams);
    }

    private void changePopupWindowFlags(final int flags) {
        if (DEBUG) {
            Log.d(TAG, "changePopupWindowFlags() called with: flags = [" + flags + "]");
        }

        if (!anyPopupViewIsNull()) {
            popupLayoutParams.flags = flags;
            Objects.requireNonNull(windowManager)
                    .updateViewLayout(binding.getRoot(), popupLayoutParams);
        }
    }

    public void closePopup() {
        if (DEBUG) {
            Log.d(TAG, "closePopup() called, isPopupClosing = " + isPopupClosing);
        }
        if (isPopupClosing) {
            return;
        }
        isPopupClosing = true;

        saveStreamProgressState();
        Objects.requireNonNull(windowManager).removeView(binding.getRoot());

        animatePopupOverlayAndFinishService();
    }

    public void removePopupFromView() {
        if (windowManager != null) {
            // wrap in try-catch since it could sometimes generate errors randomly
            try {
                if (popupHasParent()) {
                    windowManager.removeView(binding.getRoot());
                }
            } catch (final IllegalArgumentException e) {
                Log.w(TAG, "Failed to remove popup from window manager", e);
            }

            try {
                final boolean closeOverlayHasParent = closeOverlayBinding != null
                        && closeOverlayBinding.getRoot().getParent() != null;
                if (closeOverlayHasParent) {
                    windowManager.removeView(closeOverlayBinding.getRoot());
                }
            } catch (final IllegalArgumentException e) {
                Log.w(TAG, "Failed to remove popup overlay from window manager", e);
            }
        }
    }

    private void animatePopupOverlayAndFinishService() {
        final int targetTranslationY =
                (int) (closeOverlayBinding.closeButton.getRootView().getHeight()
                        - closeOverlayBinding.closeButton.getY());

        closeOverlayBinding.closeButton.animate().setListener(null).cancel();
        closeOverlayBinding.closeButton.animate()
                .setInterpolator(new AnticipateInterpolator())
                .translationY(targetTranslationY)
                .setDuration(400)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationCancel(final Animator animation) {
                        end();
                    }

                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        end();
                    }

                    private void end() {
                        Objects.requireNonNull(windowManager)
                                .removeView(closeOverlayBinding.getRoot());
                        closeOverlayBinding = null;
                        service.onDestroy();
                    }
                }).start();
    }

    private boolean popupHasParent() {
        return binding != null
                && binding.getRoot().getLayoutParams() instanceof WindowManager.LayoutParams
                && binding.getRoot().getParent() != null;
    }

    private boolean anyPopupViewIsNull() {
        // TODO understand why checking getParentActivity() != null
        return popupLayoutParams == null || windowManager == null
                || getParentActivity() != null || binding.getRoot().getParent() == null;
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback parameters
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public float getPlaybackSpeed() {
        return getPlaybackParameters().speed;
    }

    private void setPlaybackSpeed(final float speed) {
        setPlaybackParameters(speed, getPlaybackPitch(), getPlaybackSkipSilence());
    }

    public float getPlaybackPitch() {
        return getPlaybackParameters().pitch;
    }

    public boolean getPlaybackSkipSilence() {
        return getPlaybackParameters().skipSilence;
    }

    public PlaybackParameters getPlaybackParameters() {
        if (exoPlayerIsNull()) {
            return PlaybackParameters.DEFAULT;
        }
        return simpleExoPlayer.getPlaybackParameters();
    }

    /**
     * Sets the playback parameters of the player, and also saves them to shared preferences.
     * Speed and pitch are rounded up to 2 decimal places before being used or saved.
     *
     * @param speed       the playback speed, will be rounded to up to 2 decimal places
     * @param pitch       the playback pitch, will be rounded to up to 2 decimal places
     * @param skipSilence skip silence during playback
     */
    public void setPlaybackParameters(final float speed, final float pitch,
                                      final boolean skipSilence) {
        final float roundedSpeed = Math.round(speed * 100.0f) / 100.0f;
        final float roundedPitch = Math.round(pitch * 100.0f) / 100.0f;

        savePlaybackParametersToPrefs(this, roundedSpeed, roundedPitch, skipSilence);
        simpleExoPlayer.setPlaybackParameters(
                new PlaybackParameters(roundedSpeed, roundedPitch, skipSilence));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Progress loop and updates
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void onUpdateProgress(final int currentProgress,
                                  final int duration,
                                  final int bufferPercent) {
        if (!isPrepared) {
            return;
        }

        if (duration != binding.playbackSeekBar.getMax()) {
            binding.playbackEndTime.setText(getTimeString(duration));
            binding.playbackSeekBar.setMax(duration);
        }
        if (currentState != STATE_PAUSED) {
            if (currentState != STATE_PAUSED_SEEK) {
                binding.playbackSeekBar.setProgress(currentProgress);
            }
            binding.playbackCurrentTime.setText(getTimeString(currentProgress));
        }
        if (simpleExoPlayer.isLoading() || bufferPercent > 90) {
            binding.playbackSeekBar.setSecondaryProgress(
                    (int) (binding.playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "notifyProgressUpdateToListeners() called with: "
                    + "isVisible = " + isControlsVisible() + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
        binding.playbackLiveSync.setClickable(!isLiveEdge());

        notifyProgressUpdateToListeners(currentProgress, duration, bufferPercent);

        if (areSegmentsVisible) {
            segmentAdapter.selectSegmentAt(getNearestStreamSegmentPosition(currentProgress));
        }

        final boolean showThumbnail = prefs.getBoolean(
                context.getString(R.string.show_thumbnail_key), true);
        // setMetadata only updates the metadata when any of the metadata keys are null
        mediaSessionManager.setMetadata(getVideoTitle(), getUploaderName(),
                showThumbnail ? getThumbnail() : null, duration);
    }

    private void startProgressLoop() {
        progressUpdateDisposable.set(getProgressUpdateDisposable());
    }

    private void stopProgressLoop() {
        progressUpdateDisposable.set(null);
    }

    private boolean isProgressLoopRunning() {
        return progressUpdateDisposable.get() != null;
    }

    private void triggerProgressUpdate() {
        if (exoPlayerIsNull()) {
            return;
        }
        onUpdateProgress(
                Math.max((int) simpleExoPlayer.getCurrentPosition(), 0),
                (int) simpleExoPlayer.getDuration(),
                simpleExoPlayer.getBufferedPercentage()
        );
    }

    private Disposable getProgressUpdateDisposable() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, MILLISECONDS,
                AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> triggerProgressUpdate(),
                        error -> Log.e(TAG, "Progress update failure: ", error));
    }

    @Override // seekbar listener
    public void onProgressChanged(final SeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        if (DEBUG && fromUser) {
            Log.d(TAG, "onProgressChanged() called with: "
                    + "seekBar = [" + seekBar + "], progress = [" + progress + "]");
        }
        if (fromUser) {
            binding.currentDisplaySeek.setText(getTimeString(progress));
        }
    }

    @Override // seekbar listener
    public void onStartTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }
        if (currentState != STATE_PAUSED_SEEK) {
            changeState(STATE_PAUSED_SEEK);
        }

        saveWasPlaying();
        if (isPlaying()) {
            simpleExoPlayer.setPlayWhenReady(false);
        }

        showControls(0);
        animate(binding.currentDisplaySeek, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SCALE_AND_ALPHA);
    }

    @Override // seekbar listener
    public void onStopTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }

        seekTo(seekBar.getProgress());
        if (wasPlaying || simpleExoPlayer.getDuration() == seekBar.getProgress()) {
            simpleExoPlayer.setPlayWhenReady(true);
        }

        binding.playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);

        if (currentState == STATE_PAUSED_SEEK) {
            changeState(STATE_BUFFERING);
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }
        if (wasPlaying) {
            showControlsThenHide();
        }
    }

    public void saveWasPlaying() {
        this.wasPlaying = simpleExoPlayer.getPlayWhenReady();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Controls showing / hiding
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public boolean isControlsVisible() {
        return binding != null && (
            binding.playbackControlRoot.getVisibility() == View.VISIBLE
            || binding.lockedRoot.getVisibility() == View.VISIBLE
        );
    }

    /**
     * Show a animation, and depending on goneOnEnd, will stay on the screen or be gone.
     *
     * @param drawableId the drawable that will be used to animate,
     *                   pass -1 to clear any animation that is visible
     * @param goneOnEnd  will set the animation view to GONE on the end of the animation
     */
    public void showAndAnimateControl(final int drawableId, final boolean goneOnEnd) {
        if (DEBUG) {
            Log.d(TAG, "showAndAnimateControl() called with: "
                    + "drawableId = [" + drawableId + "], goneOnEnd = [" + goneOnEnd + "]");
        }
        if (controlViewAnimator != null && controlViewAnimator.isRunning()) {
            if (DEBUG) {
                Log.d(TAG, "showAndAnimateControl: controlViewAnimator.isRunning");
            }
            controlViewAnimator.end();
        }

        if (drawableId == -1) {
            if (binding.controlAnimationView.getVisibility() == View.VISIBLE) {
                controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        binding.controlAnimationView,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1.0f, 0.0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, 1.0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, 1.0f)
                ).setDuration(DEFAULT_CONTROLS_DURATION);
                controlViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        binding.controlAnimationView.setVisibility(View.GONE);
                    }
                });
                controlViewAnimator.start();
            }
            return;
        }

        final float scaleFrom = goneOnEnd ? 1f : 1f;
        final float scaleTo = goneOnEnd ? 1.8f : 1.4f;
        final float alphaFrom = goneOnEnd ? 1f : 0f;
        final float alphaTo = goneOnEnd ? 0f : 1f;


        controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(
                binding.controlAnimationView,
                PropertyValuesHolder.ofFloat(View.ALPHA, alphaFrom, alphaTo),
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleFrom, scaleTo),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleFrom, scaleTo)
        );
        controlViewAnimator.setDuration(goneOnEnd ? 1000 : 500);
        controlViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                binding.controlAnimationView.setVisibility(goneOnEnd ? View.GONE : View.VISIBLE);
            }
        });


        binding.controlAnimationView.setVisibility(View.VISIBLE);
        binding.controlAnimationView.setImageDrawable(
                AppCompatResources.getDrawable(context, drawableId));
        controlViewAnimator.start();
    }

    public void showControlsThenHide() {
        if (DEBUG) {
            Log.d(TAG, "showControlsThenHide() called");
        }
        final int hideTime = binding.playbackControlRoot.isInTouchMode()
            ? DEFAULT_CONTROLS_HIDE_TIME
            : DPAD_CONTROLS_HIDE_TIME;
        showControls(
            DEFAULT_CONTROLS_DURATION,
            () -> hideControls(DEFAULT_CONTROLS_DURATION, hideTime)
        );
    }

    public void showControls(final long duration) {
        showControls(duration, null);
    }

    public void showControls(final long duration, @Nullable final Runnable callback) {
        if (DEBUG) {
            Log.d(TAG, "showControls() called");
        }

        final RelativeLayout layoutToShow;
        if (!isLocked) {
            showOrHideButtons();
            showSystemUIPartially();
            layoutToShow = binding.playbackControlRoot;
        } else {
            layoutToShow = binding.lockedRoot;
        }

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        showHideShadow(true, duration);
        animate(
            layoutToShow,
            true,
            duration,
            AnimationType.ALPHA,
            0,
            callback
        );
    }

    public void hideControls(final long duration, final long delay) {
        hideControls(duration, delay, null);
    }

    public void hideControls(
        final long duration,
        final long delay,
        @Nullable final Runnable callback) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: duration = [" + duration
                    + "], delay = [" + delay + "]");
        }

        final RelativeLayout layoutToHide;
        if (!isLocked) {
            showOrHideButtons();
            layoutToHide = binding.playbackControlRoot;
        } else {
            layoutToHide = binding.lockedRoot;
        }

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() -> {
            showHideShadow(false, duration);
            animate(
                layoutToHide,
                false,
                duration,
                AnimationType.ALPHA,
                0,
                () -> {
                    hideSystemUIIfNeeded();
                    if (callback != null) {
                        callback.run();
                    }
                }
            );
        }, delay);
    }

    private void showHideShadow(final boolean show, final long duration) {
        animate(binding.playerTopShadow, show, duration, AnimationType.ALPHA, 0, null);
        animate(binding.playerBottomShadow, show, duration, AnimationType.ALPHA, 0, null);
    }

    private void showOrHideButtons() {
        if (playQueue == null) {
            return;
        }

        final boolean showPrev = playQueue.getIndex() != 0;
        final boolean showNext = playQueue.getIndex() + 1 != playQueue.getStreams().size();
        final boolean showQueue = playQueue.getStreams().size() > 1 && !popupPlayerSelected();
        boolean showSegment = false;
        if (currentMetadata != null) {
            showSegment = !currentMetadata.getMetadata().getStreamSegments().isEmpty()
                    && !popupPlayerSelected();
        }

        binding.playPreviousButton.setVisibility(showPrev ? View.VISIBLE : View.INVISIBLE);
        binding.playPreviousButton.setAlpha(showPrev ? 1.0f : 0.0f);
        binding.playNextButton.setVisibility(showNext ? View.VISIBLE : View.INVISIBLE);
        binding.playNextButton.setAlpha(showNext ? 1.0f : 0.0f);
        binding.queueButton.setVisibility(showQueue ? View.VISIBLE : View.GONE);
        binding.queueButton.setAlpha(showQueue ? 1.0f : 0.0f);
        binding.segmentsButton.setVisibility(showSegment ? View.VISIBLE : View.GONE);
        binding.segmentsButton.setAlpha(showSegment ? 1.0f : 0.0f);
    }

    private void showSystemUIPartially() {
        final AppCompatActivity activity = getParentActivity();
        if (isFullscreen && activity != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
                activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
            }
            final int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            activity.getWindow().getDecorView().setSystemUiVisibility(visibility);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    private void hideSystemUIIfNeeded() {
        if (fragmentListener != null) {
            fragmentListener.hideSystemUiIfNeeded();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback states
    //////////////////////////////////////////////////////////////////////////*/
    //region

    @Override // exoplayer listener
    public void onPlayerStateChanged(final boolean playWhenReady, final int playbackState) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPlayerStateChanged() called with: "
                    + "playWhenReady = [" + playWhenReady + "], "
                    + "playbackState = [" + playbackState + "]");
        }

        if (currentState == STATE_PAUSED_SEEK) {
            if (DEBUG) {
                Log.d(TAG, "ExoPlayer - onPlayerStateChanged() is currently blocked");
            }
            return;
        }

        switch (playbackState) {
            case com.google.android.exoplayer2.Player.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case com.google.android.exoplayer2.Player.STATE_BUFFERING: // 2
                if (isPrepared) {
                    changeState(STATE_BUFFERING);
                }
                break;
            case com.google.android.exoplayer2.Player.STATE_READY: //3
                maybeUpdateCurrentMetadata();
                maybeCorrectSeekPosition();
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                }
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case com.google.android.exoplayer2.Player.STATE_ENDED: // 4
                changeState(STATE_COMPLETED);
                if (currentMetadata != null) {
                    resetStreamProgressState(currentMetadata.getMetadata());
                }
                isPrepared = false;
                break;
        }
    }

    @Override // exoplayer listener
    public void onLoadingChanged(final boolean isLoading) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onLoadingChanged() called with: "
                    + "isLoading = [" + isLoading + "]");
        }

        if (!isLoading && currentState == STATE_PAUSED && isProgressLoopRunning()) {
            stopProgressLoop();
        } else if (isLoading && !isProgressLoopRunning()) {
            startProgressLoop();
        }

        maybeUpdateCurrentMetadata();
    }

    @Override // own playback listener
    public void onPlaybackBlock() {
        if (exoPlayerIsNull()) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackBlock() called");
        }

        currentItem = null;
        currentMetadata = null;
        simpleExoPlayer.stop();
        isPrepared = false;

        changeState(STATE_BLOCKED);
    }

    @Override // own playback listener
    public void onPlaybackUnblock(final MediaSource mediaSource) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackUnblock() called");
        }

        if (exoPlayerIsNull()) {
            return;
        }
        if (currentState == STATE_BLOCKED) {
            changeState(STATE_BUFFERING);
        }
        simpleExoPlayer.prepare(mediaSource);
    }

    public void changeState(final int state) {
        if (DEBUG) {
            Log.d(TAG, "changeState() called with: state = [" + state + "]");
        }
        currentState = state;
        switch (state) {
            case STATE_BLOCKED:
                onBlocked();
                break;
            case STATE_PLAYING:
                onPlaying();
                break;
            case STATE_BUFFERING:
                onBuffering();
                break;
            case STATE_PAUSED:
                onPaused();
                break;
            case STATE_PAUSED_SEEK:
                onPausedSeek();
                break;
            case STATE_COMPLETED:
                onCompleted();
                break;
        }
        notifyPlaybackUpdateToListeners();
    }

    private void onPrepared(final boolean playWhenReady) {
        if (DEBUG) {
            Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        }

        binding.playbackSeekBar.setMax((int) simpleExoPlayer.getDuration());
        binding.playbackEndTime.setText(getTimeString((int) simpleExoPlayer.getDuration()));
        binding.playbackSpeed.setText(formatSpeed(getPlaybackSpeed()));

        if (playWhenReady) {
            audioReactor.requestAudioFocus();
        }
    }

    private void onBlocked() {
        if (DEBUG) {
            Log.d(TAG, "onBlocked() called");
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        animate(binding.playbackControlRoot, false, DEFAULT_CONTROLS_DURATION);

        binding.playbackSeekBar.setEnabled(false);
        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN));

        binding.loadingPanel.setBackgroundColor(Color.BLACK);
        animate(binding.loadingPanel, true, 0);
        animate(binding.surfaceForeground, true, 100);

        binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(false);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onPlaying() {
        if (DEBUG) {
            Log.d(TAG, "onPlaying() called");
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }

        updateStreamRelatedViews();

        showAndAnimateControl(-1, true);

        binding.playbackSeekBar.setEnabled(true);
        binding.playbackSeekBar.getThumb()
                .setColorFilter(new PorterDuffColorFilter(Color.RED, PorterDuff.Mode.SRC_IN));

        binding.loadingPanel.setVisibility(View.GONE);

        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);

        animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    binding.playPauseButton.setImageResource(R.drawable.ic_pause_white_24dp);
                    animatePlayButtons(true, 200);
                    if (!isQueueVisible) {
                        binding.playPauseButton.requestFocus();
                    }
                });

        changePopupWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS);
        checkLandscape();
        binding.getRoot().setKeepScreenOn(true);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onBuffering() {
        if (DEBUG) {
            Log.d(TAG, "onBuffering() called");
        }
        binding.loadingPanel.setBackgroundColor(Color.TRANSPARENT);

        binding.getRoot().setKeepScreenOn(true);

        if (NotificationUtil.getInstance().shouldUpdateBufferingSlot()) {
            NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        }
    }

    private void onPaused() {
        if (DEBUG) {
            Log.d(TAG, "onPaused() called");
        }

        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }

        showControls(400);
        binding.loadingPanel.setVisibility(View.GONE);

        animate(binding.playPauseButton, false, 80, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    binding.playPauseButton.setImageResource(R.drawable.ic_play_arrow_white_24dp);
                    animatePlayButtons(true, 200);
                    if (!isQueueVisible) {
                        binding.playPauseButton.requestFocus();
                    }
                });

        changePopupWindowFlags(IDLE_WINDOW_FLAGS);

        // Remove running notification when user does not want minimization to background or popup
        if (PlayerHelper.getMinimizeOnExitAction(context) == MINIMIZE_ON_EXIT_MODE_NONE
                && videoPlayerSelected()) {
            NotificationUtil.getInstance().cancelNotificationAndStopForeground(service);
        } else {
            NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        }

        binding.getRoot().setKeepScreenOn(false);
    }

    private void onPausedSeek() {
        if (DEBUG) {
            Log.d(TAG, "onPausedSeek() called");
        }
        showAndAnimateControl(-1, true);

        animatePlayButtons(false, 100);
        binding.getRoot().setKeepScreenOn(true);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onCompleted() {
        if (DEBUG) {
            Log.d(TAG, "onCompleted() called");
        }

        animate(binding.playPauseButton, false, 0, AnimationType.SCALE_AND_ALPHA, 0,
                () -> {
                    binding.playPauseButton.setImageResource(R.drawable.ic_replay_white_24dp);
                    animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
                });

        binding.getRoot().setKeepScreenOn(false);
        changePopupWindowFlags(IDLE_WINDOW_FLAGS);

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);

        if (isLocked) {
            unlockScreen();
        }

        if (isFullscreen) {
            toggleFullscreen();
        }

        if (playQueue.getIndex() < playQueue.size() - 1) {
            playQueue.offsetIndex(+1);
        }
        if (isProgressLoopRunning()) {
            stopProgressLoop();
        }

        showControls(500);
        animate(binding.currentDisplaySeek, false, 200, AnimationType.SCALE_AND_ALPHA);
        binding.loadingPanel.setVisibility(View.GONE);
        animate(binding.surfaceForeground, true, 100);
    }

    private void animatePlayButtons(final boolean show, final int duration) {
        animate(binding.playPauseButton, show, duration, AnimationType.SCALE_AND_ALPHA);

        boolean showQueueButtons = show;
        if (playQueue == null) {
            showQueueButtons = false;
        }

        if (!showQueueButtons || playQueue.getIndex() > 0) {
            animate(
                    binding.playPreviousButton,
                    showQueueButtons,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
        if (!showQueueButtons || playQueue.getIndex() + 1 < playQueue.getStreams().size()) {
            animate(
                    binding.playNextButton,
                    showQueueButtons,
                    duration,
                    AnimationType.SCALE_AND_ALPHA);
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public void onRepeatClicked() {
        if (DEBUG) {
            Log.d(TAG, "onRepeatClicked() called");
        }
        setRepeatMode(nextRepeatMode(getRepeatMode()));
    }

    public void onShuffleClicked() {
        if (DEBUG) {
            Log.d(TAG, "onShuffleClicked() called");
        }

        if (exoPlayerIsNull()) {
            return;
        }
        simpleExoPlayer.setShuffleModeEnabled(!simpleExoPlayer.getShuffleModeEnabled());
    }

    @RepeatMode
    public int getRepeatMode() {
        return exoPlayerIsNull() ? REPEAT_MODE_OFF : simpleExoPlayer.getRepeatMode();
    }

    private void setRepeatMode(@RepeatMode final int repeatMode) {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.setRepeatMode(repeatMode);
        }
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode final int repeatMode) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onRepeatModeChanged() called with: "
                    + "repeatMode = [" + repeatMode + "]");
        }
        setRepeatModeButton(binding.repeatButton, repeatMode);
        onShuffleOrRepeatModeChanged();
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onShuffleModeEnabledChanged() called with: "
                    + "mode = [" + shuffleModeEnabled + "]");
        }

        if (playQueue != null) {
            if (shuffleModeEnabled) {
                playQueue.shuffle();
            } else {
                playQueue.unshuffle();
            }
        }

        setShuffleButton(binding.shuffleButton, shuffleModeEnabled);
        onShuffleOrRepeatModeChanged();
    }

    private void onShuffleOrRepeatModeChanged() {
        notifyPlaybackUpdateToListeners();
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void setRepeatModeButton(final ImageButton imageButton, final int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_OFF:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case REPEAT_MODE_ONE:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case REPEAT_MODE_ALL:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private void setShuffleButton(final ImageButton button, final boolean shuffled) {
        button.setImageAlpha(shuffled ? 255 : 77);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Mute / Unmute
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public void onMuteUnmuteButtonClicked() {
        if (DEBUG) {
            Log.d(TAG, "onMuteUnmuteButtonClicked() called");
        }
        simpleExoPlayer.setVolume(isMuted() ? 1 : 0);
        notifyPlaybackUpdateToListeners();
        setMuteButton(binding.switchMute, isMuted());
    }

    boolean isMuted() {
        return !exoPlayerIsNull() && simpleExoPlayer.getVolume() == 0;
    }

    private void setMuteButton(final ImageButton button, final boolean isMuted) {
        button.setImageDrawable(AppCompatResources.getDrawable(context, isMuted
                ? R.drawable.ic_volume_off_white_24dp : R.drawable.ic_volume_up_white_24dp));
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer listeners (that didn't fit in other categories)
    //////////////////////////////////////////////////////////////////////////*/
    //region

    @Override
    public void onTimelineChanged(@NonNull final Timeline timeline, final int reason) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onTimelineChanged() called with "
                    + "timeline size = [" + timeline.getWindowCount() + "], "
                    + "reason = [" + reason + "]");
        }

        maybeUpdateCurrentMetadata();
        // force recreate notification to ensure seek bar is shown when preparation finishes
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, true);
    }

    @Override
    public void onTracksChanged(@NonNull final TrackGroupArray trackGroups,
                                @NonNull final TrackSelectionArray trackSelections) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onTracksChanged(), "
                    + "track group size = " + trackGroups.length);
        }
        maybeUpdateCurrentMetadata();
        onTextTracksChanged();
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull final PlaybackParameters playbackParameters) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - playbackParameters(), speed = [" + playbackParameters.speed
                    + "], pitch = [" + playbackParameters.pitch + "]");
        }
        binding.playbackSpeed.setText(formatSpeed(playbackParameters.speed));
    }

    @Override
    public void onPositionDiscontinuity(@DiscontinuityReason final int discontinuityReason) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPositionDiscontinuity() called with "
                    + "discontinuityReason = [" + discontinuityReason + "]");
        }
        if (playQueue == null) {
            return;
        }

        // Refresh the playback if there is a transition to the next video
        final int newWindowIndex = simpleExoPlayer.getCurrentWindowIndex();
        switch (discontinuityReason) {
            case DISCONTINUITY_REASON_PERIOD_TRANSITION:
                // When player is in single repeat mode and a period transition occurs,
                // we need to register a view count here since no metadata has changed
                if (getRepeatMode() == REPEAT_MODE_ONE && newWindowIndex == playQueue.getIndex()) {
                    registerStreamViewed();
                    break;
                }
            case DISCONTINUITY_REASON_SEEK:
            case DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
            case DISCONTINUITY_REASON_INTERNAL:
                if (playQueue.getIndex() != newWindowIndex) {
                    resetStreamProgressState(playQueue.getItem());
                    playQueue.setIndex(newWindowIndex);
                }
                break;
            case DISCONTINUITY_REASON_AD_INSERTION:
                break; // only makes Android Studio linter happy, as there are no ads
        }

        maybeUpdateCurrentMetadata();
    }

    @Override
    public void onRenderedFirstFrame() {
        //TODO check if this causes black screen when switching to fullscreen
        animate(binding.surfaceForeground, false, DEFAULT_CONTROLS_DURATION);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Errors
    //////////////////////////////////////////////////////////////////////////*/
    //region
    /**
     * Process exceptions produced by {@link com.google.android.exoplayer2.ExoPlayer ExoPlayer}.
     * <p>There are multiple types of errors:</p>
     * <ul>
     * <li>{@link ExoPlaybackException#TYPE_SOURCE TYPE_SOURCE}</li>
     * <li>{@link ExoPlaybackException#TYPE_UNEXPECTED TYPE_UNEXPECTED}:
     * If a runtime error occurred, then we can try to recover it by restarting the playback
     * after setting the timestamp recovery.</li>
     * <li>{@link ExoPlaybackException#TYPE_RENDERER TYPE_RENDERER}:
     * If the renderer failed, treat the error as unrecoverable.</li>
     * </ul>
     *
     * @see #processSourceError(IOException)
     * @see com.google.android.exoplayer2.Player.EventListener#onPlayerError(ExoPlaybackException)
     */
    @Override
    public void onPlayerError(@NonNull final ExoPlaybackException error) {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onPlayerError() called with: " + "error = [" + error + "]");
        }
        if (errorToast != null) {
            errorToast.cancel();
            errorToast = null;
        }

        saveStreamProgressState();

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                processSourceError(error.getSourceException());
                showStreamError(error);
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                showRecoverableError(error);
                setRecovery();
                reloadPlayQueueManager();
                break;
            case ExoPlaybackException.TYPE_OUT_OF_MEMORY:
            case ExoPlaybackException.TYPE_REMOTE:
            case ExoPlaybackException.TYPE_RENDERER:
            default:
                showUnrecoverableError(error);
                onPlaybackShutdown();
                break;
        }

        if (fragmentListener != null) {
            fragmentListener.onPlayerError(error);
        }
    }

    private void processSourceError(final IOException error) {
        if (exoPlayerIsNull() || playQueue == null) {
            return;
        }
        setRecovery();

        if (error instanceof BehindLiveWindowException) {
            reloadPlayQueueManager();
        } else {
            playQueue.error();
        }
    }

    private void showStreamError(final Exception exception) {
        exception.printStackTrace();

        if (errorToast == null) {
            errorToast = Toast
                    .makeText(context, R.string.player_stream_failure, Toast.LENGTH_SHORT);
            errorToast.show();
        }
    }

    private void showRecoverableError(final Exception exception) {
        exception.printStackTrace();

        if (errorToast == null) {
            errorToast = Toast
                    .makeText(context, R.string.player_recoverable_failure, Toast.LENGTH_SHORT);
            errorToast.show();
        }
    }

    private void showUnrecoverableError(final Exception exception) {
        exception.printStackTrace();

        if (errorToast != null) {
            errorToast.cancel();
        }
        errorToast = Toast
                .makeText(context, R.string.player_unrecoverable_failure, Toast.LENGTH_SHORT);
        errorToast.show();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Playback position and seek
    //////////////////////////////////////////////////////////////////////////*/
    //region

    @Override // own playback listener (this is a getter)
    public boolean isApproachingPlaybackEdge(final long timeToEndMillis) {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (exoPlayerIsNull() || isLive() || !isPlaying()) {
            return false;
        }

        final long currentPositionMillis = simpleExoPlayer.getCurrentPosition();
        final long currentDurationMillis = simpleExoPlayer.getDuration();
        return currentDurationMillis - currentPositionMillis < timeToEndMillis;
    }

    /**
     * Checks if the current playback is a livestream AND is playing at or beyond the live edge.
     *
     * @return whether the livestream is playing at or beyond the edge
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLiveEdge() {
        if (exoPlayerIsNull() || !isLive()) {
            return false;
        }

        final Timeline currentTimeline = simpleExoPlayer.getCurrentTimeline();
        final int currentWindowIndex = simpleExoPlayer.getCurrentWindowIndex();
        if (currentTimeline.isEmpty() || currentWindowIndex < 0
                || currentWindowIndex >= currentTimeline.getWindowCount()) {
            return false;
        }

        final Timeline.Window timelineWindow = new Timeline.Window();
        currentTimeline.getWindow(currentWindowIndex, timelineWindow);
        return timelineWindow.getDefaultPositionMs() <= simpleExoPlayer.getCurrentPosition();
    }

    @Override // own playback listener
    public void onPlaybackSynchronize(@NonNull final PlayQueueItem item) {
        if (DEBUG) {
            Log.d(TAG, "Playback - onPlaybackSynchronize() called with "
                    + "item=[" + item.getTitle() + "], url=[" + item.getUrl() + "]");
        }
        if (exoPlayerIsNull() || playQueue == null) {
            return;
        }

        final boolean onPlaybackInitial = currentItem == null;
        final boolean hasPlayQueueItemChanged = currentItem != item;

        final int currentPlayQueueIndex = playQueue.indexOf(item);
        final int currentPlaylistIndex = simpleExoPlayer.getCurrentWindowIndex();
        final int currentPlaylistSize = simpleExoPlayer.getCurrentTimeline().getWindowCount();

        // If nothing to synchronize
        if (!hasPlayQueueItemChanged) {
            return;
        }
        currentItem = item;

        // Check if on wrong window
        if (currentPlayQueueIndex != playQueue.getIndex()) {
            Log.e(TAG, "Playback - Play Queue may be desynchronized: item "
                    + "index=[" + currentPlayQueueIndex + "], "
                    + "queue index=[" + playQueue.getIndex() + "]");

            // Check if bad seek position
        } else if ((currentPlaylistSize > 0 && currentPlayQueueIndex >= currentPlaylistSize)
                || currentPlayQueueIndex < 0) {
            Log.e(TAG, "Playback - Trying to seek to invalid "
                    + "index=[" + currentPlayQueueIndex + "] with "
                    + "playlist length=[" + currentPlaylistSize + "]");

        } else if (currentPlaylistIndex != currentPlayQueueIndex || onPlaybackInitial
                || !isPlaying()) {
            if (DEBUG) {
                Log.d(TAG, "Playback - Rewinding to correct "
                        + "index=[" + currentPlayQueueIndex + "], "
                        + "from=[" + currentPlaylistIndex + "], "
                        + "size=[" + currentPlaylistSize + "].");
            }

            if (item.getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET) {
                simpleExoPlayer.seekTo(currentPlayQueueIndex, item.getRecoveryPosition());
                playQueue.unsetRecovery(currentPlayQueueIndex);
            } else {
                simpleExoPlayer.seekToDefaultPosition(currentPlayQueueIndex);
            }
        }
    }

    private void maybeCorrectSeekPosition() {
        if (playQueue == null || exoPlayerIsNull() || currentMetadata == null) {
            return;
        }

        final PlayQueueItem currentSourceItem = playQueue.getItem();
        if (currentSourceItem == null) {
            return;
        }

        final StreamInfo currentInfo = currentMetadata.getMetadata();
        final long presetStartPositionMillis = currentInfo.getStartPosition() * 1000;
        if (presetStartPositionMillis > 0L) {
            // Has another start position?
            if (DEBUG) {
                Log.d(TAG, "Playback - Seeking to preset start "
                        + "position=[" + presetStartPositionMillis + "]");
            }
            seekTo(presetStartPositionMillis);
        }
    }

    public void seekTo(final long positionMillis) {
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: position = [" + positionMillis + "]");
        }
        if (!exoPlayerIsNull()) {
            // prevent invalid positions when fast-forwarding/-rewinding
            long normalizedPositionMillis = positionMillis;
            if (normalizedPositionMillis < 0) {
                normalizedPositionMillis = 0;
            } else if (normalizedPositionMillis > simpleExoPlayer.getDuration()) {
                normalizedPositionMillis = simpleExoPlayer.getDuration();
            }

            simpleExoPlayer.seekTo(normalizedPositionMillis);
        }
    }

    private void seekBy(final long offsetMillis) {
        if (DEBUG) {
            Log.d(TAG, "seekBy() called with: offsetMillis = [" + offsetMillis + "]");
        }
        seekTo(simpleExoPlayer.getCurrentPosition() + offsetMillis);
    }

    public void seekToDefault() {
        if (!exoPlayerIsNull()) {
            simpleExoPlayer.seekToDefaultPosition();
        }
    }

    @Override // exoplayer override
    public void onSeekProcessed() {
        if (DEBUG) {
            Log.d(TAG, "ExoPlayer - onSeekProcessed() called");
        }
        if (isPrepared) {
            saveStreamProgressState();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Player actions (play, pause, previous, fast-forward, ...)
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public void play() {
        if (DEBUG) {
            Log.d(TAG, "play() called");
        }
        if (audioReactor == null || playQueue == null || exoPlayerIsNull()) {
            return;
        }

        audioReactor.requestAudioFocus();

        if (currentState == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                seekToDefault();
            } else {
                playQueue.setIndex(0);
            }
        }

        simpleExoPlayer.setPlayWhenReady(true);
        saveStreamProgressState();
    }

    public void pause() {
        if (DEBUG) {
            Log.d(TAG, "pause() called");
        }
        if (audioReactor == null || exoPlayerIsNull()) {
            return;
        }

        audioReactor.abandonAudioFocus();
        simpleExoPlayer.setPlayWhenReady(false);
        saveStreamProgressState();
    }

    public void playPause() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPause() called");
        }

        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void playPrevious() {
        if (DEBUG) {
            Log.d(TAG, "onPlayPrevious() called");
        }
        if (exoPlayerIsNull() || playQueue == null) {
            return;
        }

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
         * restart current track. Also restart the track if the current track
         * is the first in a queue.*/
        if (simpleExoPlayer.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT_MILLIS
                || playQueue.getIndex() == 0) {
            seekToDefault();
            playQueue.offsetIndex(0);
        } else {
            saveStreamProgressState();
            playQueue.offsetIndex(-1);
        }
        triggerProgressUpdate();
    }

    public void playNext() {
        if (DEBUG) {
            Log.d(TAG, "onPlayNext() called");
        }
        if (playQueue == null) {
            return;
        }

        saveStreamProgressState();
        playQueue.offsetIndex(+1);
        triggerProgressUpdate();
    }

    public void fastForward() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called");
        }
        seekBy(retrieveSeekDurationFromPreferences(this));
        triggerProgressUpdate();
        showAndAnimateControl(R.drawable.ic_fast_forward_white_24dp, true);
    }

    public void fastRewind() {
        if (DEBUG) {
            Log.d(TAG, "fastRewind() called");
        }
        seekBy(-retrieveSeekDurationFromPreferences(this));
        triggerProgressUpdate();
        showAndAnimateControl(R.drawable.ic_fast_rewind_white_24dp, true);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // StreamInfo history: views and progress
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void registerStreamViewed() {
        if (currentMetadata != null) {
            databaseUpdateDisposable.add(recordManager.onViewed(currentMetadata.getMetadata())
                    .onErrorComplete().subscribe());
        }
    }

    private void saveStreamProgressState(final StreamInfo info, final long progress) {
        if (info == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "saveStreamProgressState() called");
        }
        if (prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
            final Disposable stateSaver = recordManager.saveStreamState(info, progress)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError((e) -> {
                        if (DEBUG) {
                            e.printStackTrace();
                        }
                    })
                    .onErrorComplete()
                    .subscribe();
            databaseUpdateDisposable.add(stateSaver);
        }
    }

    private void resetStreamProgressState(final PlayQueueItem queueItem) {
        if (queueItem == null) {
            return;
        }
        if (prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
            final Disposable stateSaver = queueItem.getStream()
                    .flatMapCompletable(info -> recordManager.saveStreamState(info, 0))
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError((e) -> {
                        if (DEBUG) {
                            e.printStackTrace();
                        }
                    })
                    .onErrorComplete()
                    .subscribe();
            databaseUpdateDisposable.add(stateSaver);
        }
    }

    private void resetStreamProgressState(final StreamInfo info) {
        saveStreamProgressState(info, 0);
    }

    public void saveStreamProgressState() {
        if (exoPlayerIsNull() || currentMetadata == null) {
            return;
        }
        final StreamInfo currentInfo = currentMetadata.getMetadata();
        if (playQueue != null) {
            // Save current position. It will help to restore this position once a user
            // wants to play prev or next stream from the queue
            playQueue.setRecovery(playQueue.getIndex(), simpleExoPlayer.getContentPosition());
        }
        saveStreamProgressState(currentInfo, simpleExoPlayer.getCurrentPosition());
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Metadata
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void onMetadataChanged(@NonNull final MediaSourceTag tag) {
        final StreamInfo info = tag.getMetadata();
        if (DEBUG) {
            Log.d(TAG, "Playback - onMetadataChanged() called, playing: " + info.getName());
        }

        initThumbnail(info.getThumbnailUrl());
        registerStreamViewed();
        updateStreamRelatedViews();
        showHideKodiButton();
        showHideLockButton();

        binding.titleTextView.setText(tag.getMetadata().getName());
        binding.channelTextView.setText(tag.getMetadata().getUploaderName());

        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
        notifyMetadataUpdateToListeners();

        if (areSegmentsVisible) {
            if (segmentAdapter.setItems(info)) {
                final int adapterPosition = getNearestStreamSegmentPosition(
                        simpleExoPlayer.getCurrentPosition());
                segmentAdapter.selectSegmentAt(adapterPosition);
                binding.itemsList.scrollToPosition(adapterPosition);
            } else {
                closeItemsList();
            }
        }
    }

    private void maybeUpdateCurrentMetadata() {
        if (exoPlayerIsNull()) {
            return;
        }

        final MediaSourceTag metadata;
        try {
            metadata = (MediaSourceTag) simpleExoPlayer.getCurrentTag();
        } catch (IndexOutOfBoundsException | ClassCastException error) {
            if (DEBUG) {
                Log.d(TAG, "Could not update metadata: " + error.getMessage());
                error.printStackTrace();
            }
            return;
        }

        if (metadata == null) {
            return;
        }
        maybeAutoQueueNextStream(metadata);

        if (currentMetadata == metadata) {
            return;
        }
        currentMetadata = metadata;
        onMetadataChanged(metadata);
    }

    @NonNull
    private String getVideoUrl() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getMetadata().getUrl();
    }

    @NonNull
    public String getVideoTitle() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getMetadata().getName();
    }

    @NonNull
    public String getUploaderName() {
        return currentMetadata == null
                ? context.getString(R.string.unknown_content)
                : currentMetadata.getMetadata().getUploaderName();
    }

    @Nullable
    public Bitmap getThumbnail() {
        return currentThumbnail == null
                ? BitmapFactory.decodeResource(context.getResources(), R.drawable.dummy_thumbnail)
                : currentThumbnail;
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Play queue, segments and streams
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void maybeAutoQueueNextStream(@NonNull final MediaSourceTag metadata) {
        if (playQueue == null || playQueue.getIndex() != playQueue.size() - 1
                || getRepeatMode() != REPEAT_MODE_OFF
                || !PlayerHelper.isAutoQueueEnabled(context)) {
            return;
        }
        // auto queue when starting playback on the last item when not repeating
        final PlayQueue autoQueue = PlayerHelper.autoQueueOf(metadata.getMetadata(),
                playQueue.getStreams());
        if (autoQueue != null) {
            playQueue.append(autoQueue.getStreams());
        }
    }

    public void selectQueueItem(final PlayQueueItem item) {
        if (playQueue == null || exoPlayerIsNull()) {
            return;
        }

        final int index = playQueue.indexOf(item);
        if (index == -1) {
            return;
        }

        if (playQueue.getIndex() == index && simpleExoPlayer.getCurrentWindowIndex() == index) {
            seekToDefault();
        } else {
            saveStreamProgressState();
        }
        playQueue.setIndex(index);
    }

    @Override
    public void onPlayQueueEdited() {
        notifyPlaybackUpdateToListeners();
        showOrHideButtons();
        NotificationUtil.getInstance().createNotificationIfNeededAndUpdate(this, false);
    }

    private void onQueueClicked() {
        isQueueVisible = true;

        hideSystemUIIfNeeded();
        buildQueue();

        binding.itemsListHeaderTitle.setVisibility(View.GONE);
        binding.shuffleButton.setVisibility(View.VISIBLE);
        binding.repeatButton.setVisibility(View.VISIBLE);

        hideControls(0, 0);
        binding.itemsListPanel.requestFocus();
        animate(binding.itemsListPanel, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SLIDE_AND_ALPHA);

        binding.itemsList.scrollToPosition(playQueue.getIndex());
    }

    private void buildQueue() {
        binding.itemsList.setAdapter(playQueueAdapter);
        binding.itemsList.setClickable(true);
        binding.itemsList.setLongClickable(true);

        binding.itemsList.clearOnScrollListeners();
        binding.itemsList.addOnScrollListener(getQueueScrollListener());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(binding.itemsList);

        playQueueAdapter.setSelectedListener(getOnSelectedListener());

        binding.itemsListClose.setOnClickListener(view -> closeItemsList());
    }

    private void onSegmentsClicked() {
        areSegmentsVisible = true;

        hideSystemUIIfNeeded();
        buildSegments();

        binding.itemsListHeaderTitle.setVisibility(View.VISIBLE);
        binding.shuffleButton.setVisibility(View.GONE);
        binding.repeatButton.setVisibility(View.GONE);

        hideControls(0, 0);
        binding.itemsListPanel.requestFocus();
        animate(binding.itemsListPanel, true, DEFAULT_CONTROLS_DURATION,
                AnimationType.SLIDE_AND_ALPHA);

        final int adapterPosition = getNearestStreamSegmentPosition(simpleExoPlayer
                .getCurrentPosition());
        segmentAdapter.selectSegmentAt(adapterPosition);
        binding.itemsList.scrollToPosition(adapterPosition);
    }

    private void buildSegments() {
        binding.itemsList.setAdapter(segmentAdapter);
        binding.itemsList.setClickable(true);
        binding.itemsList.setLongClickable(false);

        binding.itemsList.clearOnScrollListeners();
        if (itemTouchHelper != null) {
            itemTouchHelper.attachToRecyclerView(null);
        }

        if (currentMetadata != null) {
            segmentAdapter.setItems(currentMetadata.getMetadata());
        }

        binding.shuffleButton.setVisibility(View.GONE);
        binding.repeatButton.setVisibility(View.GONE);
        binding.itemsListClose.setOnClickListener(view -> closeItemsList());
    }

    public void closeItemsList() {
        if (isQueueVisible || areSegmentsVisible) {
            isQueueVisible = false;
            areSegmentsVisible = false;

            if (itemTouchHelper != null) {
                itemTouchHelper.attachToRecyclerView(null);
            }

            animate(binding.itemsListPanel, false, DEFAULT_CONTROLS_DURATION,
                    AnimationType.SLIDE_AND_ALPHA, 0, () -> {
                        // Even when queueLayout is GONE it receives touch events
                        // and ruins normal behavior of the app. This line fixes it
                        binding.itemsListPanel.setTranslationY(
                                -binding.itemsListPanel.getHeight() * 5);
                    });
            binding.playPauseButton.requestFocus();
        }
    }

    private OnScrollBelowItemsListener getQueueScrollListener() {
        return new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(final RecyclerView recyclerView) {
                if (playQueue != null && !playQueue.isComplete()) {
                    playQueue.fetch();
                } else if (binding != null) {
                    binding.itemsList.clearOnScrollListeners();
                }
            }
        };
    }

    private StreamSegmentAdapter.StreamSegmentListener getStreamSegmentListener() {
        return (item, seconds) -> {
            segmentAdapter.selectSegment(item);
            seekTo(seconds * 1000);
            triggerProgressUpdate();
        };
    }

    private int getNearestStreamSegmentPosition(final long playbackPosition) {
        int nearestPosition = 0;
        final List<StreamSegment> segments = currentMetadata.getMetadata().getStreamSegments();

        for (int i = 0; i < segments.size(); i++) {
            if (segments.get(i).getStartTimeSeconds() * 1000 > playbackPosition) {
                break;
            }
            nearestPosition++;
        }
        return Math.max(0, nearestPosition - 1);
    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new PlayQueueItemTouchCallback() {
            @Override
            public void onMove(final int sourceIndex, final int targetIndex) {
                if (playQueue != null) {
                    playQueue.move(sourceIndex, targetIndex);
                }
            }

            @Override
            public void onSwiped(final int index) {
                if (index != -1) {
                    playQueue.remove(index);
                }
            }
        };
    }

    private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
        return new PlayQueueItemBuilder.OnSelectedListener() {
            @Override
            public void selected(final PlayQueueItem item, final View view) {
                selectQueueItem(item);
            }

            @Override
            public void held(final PlayQueueItem item, final View view) {
                final int index = playQueue.indexOf(item);
                if (index != -1) {
                    playQueue.remove(index);
                }
            }

            @Override
            public void onStartDrag(final PlayQueueItemHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        };
    }

    @Override // own playback listener
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        return (isAudioOnly ? audioResolver : videoResolver).resolve(info);
    }

    public void disablePreloadingOfCurrentTrack() {
        loadController.disablePreloadingOfCurrentTrack();
    }

    @Nullable
    public VideoStream getSelectedVideoStream() {
        return (selectedStreamIndex >= 0 && availableStreams != null
                && availableStreams.size() > selectedStreamIndex)
                ? availableStreams.get(selectedStreamIndex) : null;
    }

    private void updateStreamRelatedViews() {
        if (currentMetadata == null) {
            return;
        }
        final StreamInfo info = currentMetadata.getMetadata();

        binding.qualityTextView.setVisibility(View.GONE);
        binding.playbackSpeed.setVisibility(View.GONE);

        binding.playbackEndTime.setVisibility(View.GONE);
        binding.playbackLiveSync.setVisibility(View.GONE);

        switch (info.getStreamType()) {
            case AUDIO_STREAM:
                binding.surfaceView.setVisibility(View.GONE);
                binding.endScreen.setVisibility(View.VISIBLE);
                binding.playbackEndTime.setVisibility(View.VISIBLE);
                break;

            case AUDIO_LIVE_STREAM:
                binding.surfaceView.setVisibility(View.GONE);
                binding.endScreen.setVisibility(View.VISIBLE);
                binding.playbackLiveSync.setVisibility(View.VISIBLE);
                break;

            case LIVE_STREAM:
                binding.surfaceView.setVisibility(View.VISIBLE);
                binding.endScreen.setVisibility(View.GONE);
                binding.playbackLiveSync.setVisibility(View.VISIBLE);
                break;

            case VIDEO_STREAM:
                if (info.getVideoStreams().size() + info.getVideoOnlyStreams().size() == 0) {
                    break;
                }

                availableStreams = currentMetadata.getSortedAvailableVideoStreams();
                selectedStreamIndex = currentMetadata.getSelectedVideoStreamIndex();
                buildQualityMenu();

                binding.qualityTextView.setVisibility(View.VISIBLE);
                binding.surfaceView.setVisibility(View.VISIBLE);
            default:
                binding.endScreen.setVisibility(View.GONE);
                binding.playbackEndTime.setVisibility(View.VISIBLE);
                break;
        }

        buildPlaybackSpeedMenu();
        binding.playbackSpeed.setVisibility(View.VISIBLE);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Popup menus ("popup" means that they pop up, not that they belong to the popup player)
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void buildQualityMenu() {
        if (qualityPopupMenu == null) {
            return;
        }
        qualityPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_QUALITY);

        for (int i = 0; i < availableStreams.size(); i++) {
            final VideoStream videoStream = availableStreams.get(i);
            qualityPopupMenu.getMenu().add(POPUP_MENU_ID_QUALITY, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.resolution);
        }
        if (getSelectedVideoStream() != null) {
            binding.qualityTextView.setText(getSelectedVideoStream().resolution);
        }
        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
    }

    private void buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) {
            return;
        }
        playbackSpeedPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_PLAYBACK_SPEED);

        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            playbackSpeedPopupMenu.getMenu().add(POPUP_MENU_ID_PLAYBACK_SPEED, i, Menu.NONE,
                    formatSpeed(PLAYBACK_SPEEDS[i]));
        }
        binding.playbackSpeed.setText(formatSpeed(getPlaybackSpeed()));
        playbackSpeedPopupMenu.setOnMenuItemClickListener(this);
        playbackSpeedPopupMenu.setOnDismissListener(this);
    }

    private void buildCaptionMenu(final List<String> availableLanguages) {
        if (captionPopupMenu == null) {
            return;
        }
        captionPopupMenu.getMenu().removeGroup(POPUP_MENU_ID_CAPTION);

        final String userPreferredLanguage =
                prefs.getString(context.getString(R.string.caption_user_set_key), null);
        /*
         * only search for autogenerated cc as fallback
         * if "(auto-generated)" was not already selected
         * we are only looking for "(" instead of "(auto-generated)" to hopefully get all
         * internationalized variants such as "(automatisch-erzeugt)" and so on
         */
        boolean searchForAutogenerated = userPreferredLanguage != null
                && !userPreferredLanguage.contains("(");

        // Add option for turning off caption
        final MenuItem captionOffItem = captionPopupMenu.getMenu().add(POPUP_MENU_ID_CAPTION,
                0, Menu.NONE, R.string.caption_none);
        captionOffItem.setOnMenuItemClickListener(menuItem -> {
            final int textRendererIndex = getCaptionRendererIndex();
            if (textRendererIndex != RENDERER_UNAVAILABLE) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true));
            }
            prefs.edit().remove(context.getString(R.string.caption_user_set_key)).apply();
            return true;
        });

        // Add all available captions
        for (int i = 0; i < availableLanguages.size(); i++) {
            final String captionLanguage = availableLanguages.get(i);
            final MenuItem captionItem = captionPopupMenu.getMenu().add(POPUP_MENU_ID_CAPTION,
                    i + 1, Menu.NONE, captionLanguage);
            captionItem.setOnMenuItemClickListener(menuItem -> {
                final int textRendererIndex = getCaptionRendererIndex();
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    trackSelector.setPreferredTextLanguage(captionLanguage);
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(textRendererIndex, false));
                    prefs.edit().putString(context.getString(R.string.caption_user_set_key),
                            captionLanguage).apply();
                }
                return true;
            });
            // apply caption language from previous user preference
            if (userPreferredLanguage != null
                    && (captionLanguage.equals(userPreferredLanguage)
                    || (searchForAutogenerated && captionLanguage.startsWith(userPreferredLanguage))
                    || (userPreferredLanguage.contains("(") && captionLanguage.startsWith(
                    userPreferredLanguage.substring(0, userPreferredLanguage.indexOf('(')))))) {
                final int textRendererIndex = getCaptionRendererIndex();
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    trackSelector.setPreferredTextLanguage(captionLanguage);
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(textRendererIndex, false));
                }
                searchForAutogenerated = false;
            }
        }
        captionPopupMenu.setOnDismissListener(this);
    }

    /**
     * Called when an item of the quality selector or the playback speed selector is selected.
     */
    @Override
    public boolean onMenuItemClick(final MenuItem menuItem) {
        if (DEBUG) {
            Log.d(TAG, "onMenuItemClick() called with: "
                    + "menuItem = [" + menuItem + "], "
                    + "menuItem.getItemId = [" + menuItem.getItemId() + "]");
        }

        if (menuItem.getGroupId() == POPUP_MENU_ID_QUALITY) {
            final int menuItemIndex = menuItem.getItemId();
            if (selectedStreamIndex == menuItemIndex || availableStreams == null
                    || availableStreams.size() <= menuItemIndex) {
                return true;
            }

            saveStreamProgressState(); //TODO added, check if good
            final String newResolution = availableStreams.get(menuItemIndex).resolution;
            setRecovery();
            setPlaybackQuality(newResolution);
            reloadPlayQueueManager();

            binding.qualityTextView.setText(menuItem.getTitle());
            return true;
        } else if (menuItem.getGroupId() == POPUP_MENU_ID_PLAYBACK_SPEED) {
            final int speedIndex = menuItem.getItemId();
            final float speed = PLAYBACK_SPEEDS[speedIndex];

            setPlaybackSpeed(speed);
            binding.playbackSpeed.setText(formatSpeed(speed));
        }

        return false;
    }

    /**
     * Called when some popup menu is dismissed.
     */
    @Override
    public void onDismiss(final PopupMenu menu) {
        if (DEBUG) {
            Log.d(TAG, "onDismiss() called with: menu = [" + menu + "]");
        }
        isSomePopupMenuVisible = false; //TODO check if this works
        if (getSelectedVideoStream() != null) {
            binding.qualityTextView.setText(getSelectedVideoStream().resolution);
        }
        if (isPlaying()) {
            hideControls(DEFAULT_CONTROLS_DURATION, 0);
            hideSystemUIIfNeeded();
        }
    }

    private void onQualitySelectorClicked() {
        if (DEBUG) {
            Log.d(TAG, "onQualitySelectorClicked() called");
        }
        qualityPopupMenu.show();
        isSomePopupMenuVisible = true;

        final VideoStream videoStream = getSelectedVideoStream();
        if (videoStream != null) {
            final String qualityText = MediaFormat.getNameById(videoStream.getFormatId()) + " "
                    + videoStream.resolution;
            binding.qualityTextView.setText(qualityText);
        }

        saveWasPlaying();
    }

    private void onPlaybackSpeedClicked() {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackSpeedClicked() called");
        }
        if (videoPlayerSelected()) {
            PlaybackParameterDialog.newInstance(getPlaybackSpeed(), getPlaybackPitch(),
                    getPlaybackSkipSilence(), this::setPlaybackParameters)
                    .show(getParentActivity().getSupportFragmentManager(), null);
        } else {
            playbackSpeedPopupMenu.show();
            isSomePopupMenuVisible = true;
        }
    }

    private void onCaptionClicked() {
        if (DEBUG) {
            Log.d(TAG, "onCaptionClicked() called");
        }
        captionPopupMenu.show();
        isSomePopupMenuVisible = true;
    }

    private void setPlaybackQuality(final String quality) {
        videoResolver.setPlaybackQuality(quality);
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Captions (text tracks)
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void setupSubtitleView() {
        final float captionScale = PlayerHelper.getCaptionScale(context);
        final CaptionStyleCompat captionStyle = PlayerHelper.getCaptionStyle(context);
        if (popupPlayerSelected()) {
            final float captionRatio = (captionScale - 1.0f) / 5.0f + 1.0f;
            binding.subtitleView.setFractionalTextSize(
                    SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionRatio);
        } else {
            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final int minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels);
            final float captionRatioInverse = 20f + 4f * (1.0f - captionScale);
            binding.subtitleView.setFixedTextSize(
                    TypedValue.COMPLEX_UNIT_PX, (float) minimumLength / captionRatioInverse);
        }
        binding.subtitleView.setApplyEmbeddedStyles(captionStyle == CaptionStyleCompat.DEFAULT);
        binding.subtitleView.setStyle(captionStyle);
    }

    private void onTextTracksChanged() {
        final int textRenderer = getCaptionRendererIndex();

        if (binding == null) {
            return;
        }
        if (trackSelector.getCurrentMappedTrackInfo() == null
                || textRenderer == RENDERER_UNAVAILABLE) {
            binding.captionTextView.setVisibility(View.GONE);
            return;
        }

        final TrackGroupArray textTracks = trackSelector.getCurrentMappedTrackInfo()
                .getTrackGroups(textRenderer);

        // Extract all loaded languages
        final List<String> availableLanguages = new ArrayList<>(textTracks.length);
        for (int i = 0; i < textTracks.length; i++) {
            final TrackGroup textTrack = textTracks.get(i);
            if (textTrack.length > 0 && textTrack.getFormat(0) != null) {
                availableLanguages.add(textTrack.getFormat(0).language);
            }
        }

        // Normalize mismatching language strings
        final String preferredLanguage = trackSelector.getPreferredTextLanguage();
        // Build UI
        buildCaptionMenu(availableLanguages);
        if (trackSelector.getParameters().getRendererDisabled(textRenderer)
                || preferredLanguage == null || (!availableLanguages.contains(preferredLanguage)
                && !containsCaseInsensitive(availableLanguages, preferredLanguage))) {
            binding.captionTextView.setText(R.string.caption_none);
        } else {
            binding.captionTextView.setText(preferredLanguage);
        }
        binding.captionTextView.setVisibility(
                availableLanguages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private int getCaptionRendererIndex() {
        if (exoPlayerIsNull()) {
            return RENDERER_UNAVAILABLE;
        }

        for (int t = 0; t < simpleExoPlayer.getRendererCount(); t++) {
            if (simpleExoPlayer.getRendererType(t) == C.TRACK_TYPE_TEXT) {
                return t;
            }
        }

        return RENDERER_UNAVAILABLE;
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Click listeners
    //////////////////////////////////////////////////////////////////////////*/
    //region

    @Override
    public void onClick(final View v) {
        if (DEBUG) {
            Log.d(TAG, "onClick() called with: v = [" + v + "]");
        }
        if (v.getId() == binding.qualityTextView.getId()) {
            onQualitySelectorClicked();
        } else if (v.getId() == binding.playbackSpeed.getId()) {
            onPlaybackSpeedClicked();
        } else if (v.getId() == binding.resizeTextView.getId()) {
            onResizeClicked();
        } else if (v.getId() == binding.captionTextView.getId()) {
            onCaptionClicked();
        } else if (v.getId() == binding.playbackLiveSync.getId()) {
            seekToDefault();
        } else if (v.getId() == binding.playPauseButton.getId()) {
            playPause();
        } else if (v.getId() == binding.playPreviousButton.getId()) {
            playPrevious();
        } else if (v.getId() == binding.playNextButton.getId()) {
            playNext();
        } else if (v.getId() == binding.queueButton.getId()) {
            onQueueClicked();
            return;
        } else if (v.getId() == binding.segmentsButton.getId()) {
            onSegmentsClicked();
            return;
        } else if (v.getId() == binding.repeatButton.getId()) {
            onRepeatClicked();
            return;
        } else if (v.getId() == binding.shuffleButton.getId()) {
            onShuffleClicked();
            return;
        } else if (v.getId() == binding.moreOptionsButton.getId()) {
            onMoreOptionsClicked();
        } else if (v.getId() == binding.share.getId()) {
            onShareClicked();
        } else if (v.getId() == binding.playWithKodi.getId()) {
            onPlayWithKodiClicked();
        } else if (v.getId() == binding.openInBrowser.getId()) {
            onOpenInBrowserClicked();
        } else if (v.getId() == binding.screenLock.getId()) {
            onScreenLockClicked();
            return;
        } else if (v.getId() == binding.fullScreenButton.getId()) {
            setRecovery();
            NavigationHelper.playOnMainPlayer(context, playQueue, true);
            return;
        } else if (v.getId() == binding.screenRotationButton.getId()) {
            // Only if it's not a vertical video or vertical video but in landscape with locked
            // orientation a screen orientation can be changed automatically
            if (!isVerticalVideo
                    || (service.isLandscape() && globalScreenOrientationLocked(context))) {
                fragmentListener.onScreenRotationButtonClicked();
            } else {
                toggleFullscreen();
            }
        } else if (v.getId() == binding.switchMute.getId()) {
            onMuteUnmuteButtonClicked();
        } else if (v.getId() == binding.playerCloseButton.getId()) {
            context.sendBroadcast(new Intent(VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER));
        }

        if (currentState != STATE_COMPLETED) {
            controlsVisibilityHandler.removeCallbacksAndMessages(null);
            showHideShadow(true, DEFAULT_CONTROLS_DURATION);
            animate(binding.playbackControlRoot, true, DEFAULT_CONTROLS_DURATION,
                    AnimationType.ALPHA, 0, () -> {
                        if (currentState == STATE_PLAYING && !isSomePopupMenuVisible) {
                            if (v.getId() == binding.playPauseButton.getId()
                                    // Hide controls in fullscreen immediately
                                    || (v.getId() == binding.screenRotationButton.getId()
                                    && isFullscreen)) {
                                hideControls(0, 0);
                            } else {
                                hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
                            }
                        }
                    });
        }
    }

    @Override
    public boolean onLongClick(final View v) {
        if (v.getId() == binding.moreOptionsButton.getId() && isFullscreen) {
            fragmentListener.onMoreOptionsLongClicked();
            hideControls(0, 0);
            hideSystemUIIfNeeded();
        } else if (v.getId() == binding.unlockButton.getId()) {
            onUnlockClicked();
        }
        return true;
    }

    public boolean onKeyDown(final int keyCode) {
        switch (keyCode) {
            default:
                break;
            case KeyEvent.KEYCODE_SPACE:
                if (isFullscreen) {
                    playPause();
                }
                break;
            case KeyEvent.KEYCODE_BACK:
                if (DeviceUtils.isTv(context) && isControlsVisible()) {
                    hideControls(0, 0);
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (binding.getRoot().hasFocus() && !binding.playbackControlRoot.hasFocus()) {
                    // do not interfere with focus in playlist etc.
                    return false;
                }

                if (currentState == Player.STATE_BLOCKED) {
                    return true;
                }

                if (!isControlsVisible()) {
                    if (!isQueueVisible) {
                        binding.playPauseButton.requestFocus();
                    }
                    showControlsThenHide();
                    showSystemUIPartially();
                    return true;
                } else {
                    hideControls(DEFAULT_CONTROLS_DURATION, DPAD_CONTROLS_HIDE_TIME);
                }
                break;
        }

        return false;
    }

    private void onMoreOptionsClicked() {
        if (DEBUG) {
            Log.d(TAG, "onMoreOptionsClicked() called");
        }

        final boolean isMoreControlsVisible =
                binding.secondaryControls.getVisibility() == View.VISIBLE;

        animateRotation(binding.moreOptionsButton, DEFAULT_CONTROLS_DURATION,
                isMoreControlsVisible ? 0 : 180);
        animate(binding.secondaryControls, !isMoreControlsVisible, DEFAULT_CONTROLS_DURATION,
                AnimationType.SLIDE_AND_ALPHA, 0, () -> {
                    // Fix for a ripple effect on background drawable.
                    // When view returns from GONE state it takes more milliseconds than returning
                    // from INVISIBLE state. And the delay makes ripple background end to fast
                    if (isMoreControlsVisible) {
                        binding.secondaryControls.setVisibility(View.INVISIBLE);
                    }
                });
        showControls(DEFAULT_CONTROLS_DURATION);
    }

    private void onShareClicked() {
        // share video at the current time (youtube.com/watch?v=ID&t=SECONDS)
        // Timestamp doesn't make sense in a live stream so drop it

        final int ts = binding.playbackSeekBar.getProgress() / 1000;
        String videoUrl = getVideoUrl();
        if (!isLive() && ts >= 0 && currentMetadata != null
                && currentMetadata.getMetadata().getServiceId() == YouTube.getServiceId()) {
            videoUrl += ("&t=" + ts);
        }
        ShareUtils.shareText(context, getVideoTitle(), videoUrl);
    }

    private void onPlayWithKodiClicked() {
        if (currentMetadata != null) {
            pause();
            try {
                NavigationHelper.playWithKore(context, Uri.parse(getVideoUrl()));
            } catch (final Exception e) {
                if (DEBUG) {
                    Log.i(TAG, "Failed to start kore", e);
                }
                KoreUtil.showInstallKoreDialog(getParentActivity());
            }
        }
    }

    private void onOpenInBrowserClicked() {
        if (currentMetadata != null) {
            ShareUtils.openUrlInBrowser(getParentActivity(),
                    currentMetadata.getMetadata().getOriginalUrl());
        }
    }

    private void onScreenLockClicked() {
        hideControls(DEFAULT_CONTROLS_DURATION, 0, () -> {
            getParentActivity().setRequestedOrientation(isVerticalVideo
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            );
            this.isLocked = true;
        });
    }

    private void unlockScreen() {
        this.isLocked = false;
        getParentActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    }

    private void onUnlockClicked() {
        hideControls(DEFAULT_CONTROLS_DURATION, 0, () -> {
            unlockScreen();
            showControlsThenHide();
        });
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Video size, resize, orientation, fullscreen
    //////////////////////////////////////////////////////////////////////////*/
    //region

    private void setupScreenRotationButton() {
        binding.screenRotationButton.setVisibility(videoPlayerSelected()
                && (globalScreenOrientationLocked(context) || isVerticalVideo
                        || DeviceUtils.isTablet(context))
                ? View.VISIBLE : View.GONE);
        binding.screenRotationButton.setImageDrawable(AppCompatResources.getDrawable(context,
                isFullscreen ? R.drawable.ic_fullscreen_exit_white_24dp
                : R.drawable.ic_fullscreen_white_24dp));
    }

    private void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int resizeMode) {
        binding.surfaceView.setResizeMode(resizeMode);
        binding.resizeTextView.setText(PlayerHelper.resizeTypeOf(context, resizeMode));
    }

    void onResizeClicked() {
        if (binding != null) {
            setResizeMode(nextResizeModeAndSaveToPrefs(this, binding.surfaceView.getResizeMode()));
        }
    }

    @Override // exoplayer listener
    public void onVideoSizeChanged(final int width, final int height,
                                   final int unappliedRotationDegrees,
                                   final float pixelWidthHeightRatio) {
        if (DEBUG) {
            Log.d(TAG, "onVideoSizeChanged() called with: "
                    + "width / height = [" + width + " / " + height
                    + " = " + (((float) width) / height) + "], "
                    + "unappliedRotationDegrees = [" + unappliedRotationDegrees + "], "
                    + "pixelWidthHeightRatio = [" + pixelWidthHeightRatio + "]");
        }

        binding.surfaceView.setAspectRatio(((float) width) / height);
        isVerticalVideo = width < height;

        if (globalScreenOrientationLocked(context)
                && isFullscreen
                && service.isLandscape() == isVerticalVideo
                && !DeviceUtils.isTv(context)
                && !DeviceUtils.isTablet(context)
                && fragmentListener != null) {
            // set correct orientation
            fragmentListener.onScreenRotationButtonClicked();
        }

        setupScreenRotationButton();
    }

    public void toggleFullscreen() {
        if (DEBUG) {
            Log.d(TAG, "toggleFullscreen() called");
        }
        if (popupPlayerSelected() || exoPlayerIsNull() || currentMetadata == null
                || fragmentListener == null) {
            return;
        }
        //changeState(STATE_BLOCKED); TODO check what this does

        isFullscreen = !isFullscreen;
        isLocked = false;
        binding.lockedRoot.setVisibility(View.GONE);
        if (!isFullscreen) {
            // Apply window insets because Android will not do it when orientation changes
            // from landscape to portrait (open vertical video to reproduce)
            binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        } else {
            // Android needs tens milliseconds to send new insets but a user is able to see
            // how controls changes it's position from `0` to `nav bar height` padding.
            // So just hide the controls to hide this visual inconsistency
            hideControls(0, 0, () -> isLocked = false);
        }
        fragmentListener.onFullscreenStateChanged(isFullscreen);

        if (isFullscreen) {
            binding.titleTextView.setVisibility(View.VISIBLE);
            binding.channelTextView.setVisibility(View.VISIBLE);
            binding.playerCloseButton.setVisibility(View.GONE);
        } else {
            binding.titleTextView.setVisibility(View.GONE);
            binding.channelTextView.setVisibility(View.GONE);
            binding.playerCloseButton.setVisibility(
                    videoPlayerSelected() ? View.VISIBLE : View.GONE);
        }
        setupScreenRotationButton();
        showHideLockButton();
    }

    public void checkLandscape() {
        final AppCompatActivity parent = getParentActivity();
        final boolean videoInLandscapeButNotInFullscreen =
                service.isLandscape() && !isFullscreen && videoPlayerSelected() && !isAudioOnly;

        final boolean notPaused = currentState != STATE_COMPLETED && currentState != STATE_PAUSED;
        if (parent != null
                && videoInLandscapeButNotInFullscreen
                && notPaused
                && !DeviceUtils.isTablet(context)) {
            toggleFullscreen();
        }
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Gestures
    //////////////////////////////////////////////////////////////////////////*/
    //region

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void onLayoutChange(final View view, final int l, final int t, final int r, final int b,
                                final int ol, final int ot, final int or, final int ob) {
        if (l != ol || t != ot || r != or || b != ob) {
            // Use smaller value to be consistent between screen orientations
            // (and to make usage easier)
            final int width = r - l;
            final int height = b - t;
            final int min = Math.min(width, height);
            maxGestureLength = (int) (min * MAX_GESTURE_LENGTH);

            if (DEBUG) {
                Log.d(TAG, "maxGestureLength = " + maxGestureLength);
            }

            binding.volumeProgressBar.setMax(maxGestureLength);
            binding.brightnessProgressBar.setMax(maxGestureLength);

            setInitialGestureValues();
            binding.itemsListPanel.getLayoutParams().height
                    = height - binding.itemsListPanel.getTop();
        }
    }

    private void setInitialGestureValues() {
        if (audioReactor != null) {
            final float currentVolumeNormalized =
                    (float) audioReactor.getVolume() / audioReactor.getMaxVolume();
            binding.volumeProgressBar.setProgress(
                    (int) (binding.volumeProgressBar.getMax() * currentVolumeNormalized));
        }
    }

    private int distanceFromCloseButton(final MotionEvent popupMotionEvent) {
        final int closeOverlayButtonX = closeOverlayBinding.closeButton.getLeft()
                + closeOverlayBinding.closeButton.getWidth() / 2;
        final int closeOverlayButtonY = closeOverlayBinding.closeButton.getTop()
                + closeOverlayBinding.closeButton.getHeight() / 2;

        final float fingerX = popupLayoutParams.x + popupMotionEvent.getX();
        final float fingerY = popupLayoutParams.y + popupMotionEvent.getY();

        return (int) Math.sqrt(Math.pow(closeOverlayButtonX - fingerX, 2)
                + Math.pow(closeOverlayButtonY - fingerY, 2));
    }

    private float getClosingRadius() {
        final int buttonRadius = closeOverlayBinding.closeButton.getWidth() / 2;
        // 20% wider than the button itself
        return buttonRadius * 1.2f;
    }

    public boolean isInsideClosingRadius(final MotionEvent popupMotionEvent) {
        return distanceFromCloseButton(popupMotionEvent) <= getClosingRadius();
    }
    //endregion



    /*//////////////////////////////////////////////////////////////////////////
    // Activity / fragment binding
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public void setFragmentListener(final PlayerServiceEventListener listener) {
        fragmentListener = listener;
        fragmentIsVisible = true;
        // Apply window insets because Android will not do it when orientation changes
        // from landscape to portrait
        if (!isFullscreen) {
            binding.playbackControlRoot.setPadding(0, 0, 0, 0);
        }
        binding.itemsListPanel.setPadding(0, 0, 0, 0);
        notifyQueueUpdateToListeners();
        notifyMetadataUpdateToListeners();
        notifyPlaybackUpdateToListeners();
        triggerProgressUpdate();
    }

    public void removeFragmentListener(final PlayerServiceEventListener listener) {
        if (fragmentListener == listener) {
            fragmentListener = null;
        }
    }

    void setActivityListener(final PlayerEventListener listener) {
        activityListener = listener;
        // TODO why not queue update?
        notifyMetadataUpdateToListeners();
        notifyPlaybackUpdateToListeners();
        triggerProgressUpdate();
    }

    void removeActivityListener(final PlayerEventListener listener) {
        if (activityListener == listener) {
            activityListener = null;
        }
    }

    void stopActivityBinding() {
        if (fragmentListener != null) {
            fragmentListener.onServiceStopped();
            fragmentListener = null;
        }
        if (activityListener != null) {
            activityListener.onServiceStopped();
            activityListener = null;
        }
    }

    /**
     * This will be called when a user goes to another app/activity, turns off a screen.
     * We don't want to interrupt playback and don't want to see notification so
     * next lines of code will enable audio-only playback only if needed
     */
    private void onFragmentStopped() {
        if (videoPlayerSelected() && (isPlaying() || isLoading())) {
            switch (getMinimizeOnExitAction(context)) {
                case MINIMIZE_ON_EXIT_MODE_BACKGROUND:
                    useVideoSource(false);
                    break;
                case MINIMIZE_ON_EXIT_MODE_POPUP:
                    setRecovery();
                    NavigationHelper.playOnPopupPlayer(getParentActivity(), playQueue, true);
                    break;
                case MINIMIZE_ON_EXIT_MODE_NONE: default:
                    pause();
                    break;
            }
        }
    }

    private void notifyQueueUpdateToListeners() {
        if (fragmentListener != null && playQueue != null) {
            fragmentListener.onQueueUpdate(playQueue);
        }
        if (activityListener != null && playQueue != null) {
            activityListener.onQueueUpdate(playQueue);
        }
    }

    private void notifyMetadataUpdateToListeners() {
        if (fragmentListener != null && currentMetadata != null) {
            fragmentListener.onMetadataUpdate(currentMetadata.getMetadata(), playQueue);
        }
        if (activityListener != null && currentMetadata != null) {
            activityListener.onMetadataUpdate(currentMetadata.getMetadata(), playQueue);
        }
    }

    private void notifyPlaybackUpdateToListeners() {
        if (fragmentListener != null && !exoPlayerIsNull() && playQueue != null) {
            fragmentListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
        }
        if (activityListener != null && !exoPlayerIsNull() && playQueue != null) {
            activityListener.onPlaybackUpdate(currentState, getRepeatMode(),
                    playQueue.isShuffled(), getPlaybackParameters());
        }
    }

    private void notifyProgressUpdateToListeners(final int currentProgress,
                                                 final int duration,
                                                 final int bufferPercent) {
        if (fragmentListener != null) {
            fragmentListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
        if (activityListener != null) {
            activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
        }
    }

    public AppCompatActivity getParentActivity() {
        // ! instanceof ViewGroup means that view was added via windowManager for Popup
        if (binding == null || !(binding.getRoot().getParent() instanceof ViewGroup)) {
            return null;
        }

        return (AppCompatActivity) ((ViewGroup) binding.getRoot().getParent()).getContext();
    }

    private void useVideoSource(final boolean video) {
        if (playQueue == null || isAudioOnly == !video || audioPlayerSelected()) {
            return;
        }

        isAudioOnly = !video;
        // When a user returns from background controls could be hidden
        // but systemUI will be shown 100%. Hide it
        if (!isAudioOnly && !isControlsVisible()) {
            hideSystemUIIfNeeded();
        }
        setRecovery();
        reloadPlayQueueManager();
    }
    //endregion


    /*//////////////////////////////////////////////////////////////////////////
    // Getters
    //////////////////////////////////////////////////////////////////////////*/
    //region

    public int getCurrentState() {
        return currentState;
    }

    public boolean exoPlayerIsNull() {
        return simpleExoPlayer == null;
    }

    public boolean isStopped() {
        return exoPlayerIsNull()
                || simpleExoPlayer.getPlaybackState() == SimpleExoPlayer.STATE_IDLE;
    }

    public boolean isPlaying() {
        return !exoPlayerIsNull() && simpleExoPlayer.isPlaying();
    }

    private boolean isLoading() {
        return !exoPlayerIsNull() && simpleExoPlayer.isLoading();
    }

    private boolean isLive() {
        try {
            return !exoPlayerIsNull() && simpleExoPlayer.isCurrentWindowDynamic();
        } catch (@NonNull final IndexOutOfBoundsException e) {
            // Why would this even happen =(... but lets log it anyway, better safe than sorry
            if (DEBUG) {
                Log.d(TAG, "player.isCurrentWindowDynamic() failed: " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }


    @NonNull
    public Context getContext() {
        return context;
    }

    @NonNull
    public SharedPreferences getPrefs() {
        return prefs;
    }

    public MediaSessionManager getMediaSessionManager() {
        return mediaSessionManager;
    }


    public PlayerType getPlayerType() {
        return playerType;
    }

    public boolean audioPlayerSelected() {
        return playerType == PlayerType.AUDIO;
    }

    public boolean videoPlayerSelected() {
        return playerType == PlayerType.VIDEO;
    }

    public boolean popupPlayerSelected() {
        return playerType == PlayerType.POPUP;
    }


    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    public AudioReactor getAudioReactor() {
        return audioReactor;
    }

    public GestureDetector getGestureDetector() {
        return gestureDetector;
    }

    public boolean isFullscreen() {
        return isFullscreen;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public boolean isVerticalVideo() {
        return isVerticalVideo;
    }

    public boolean isPopupClosing() {
        return isPopupClosing;
    }


    public boolean isSomePopupMenuVisible() {
        return isSomePopupMenuVisible;
    }

    public ImageButton getPlayPauseButton() {
        return binding.playPauseButton;
    }

    public View getClosingOverlayView() {
        return binding.closingOverlay;
    }

    public ProgressBar getVolumeProgressBar() {
        return binding.volumeProgressBar;
    }

    public ProgressBar getBrightnessProgressBar() {
        return binding.brightnessProgressBar;
    }

    public int getMaxGestureLength() {
        return maxGestureLength;
    }

    public ImageView getVolumeImageView() {
        return binding.volumeImageView;
    }

    public RelativeLayout getVolumeRelativeLayout() {
        return binding.volumeRelativeLayout;
    }

    public ImageView getBrightnessImageView() {
        return binding.brightnessImageView;
    }

    public RelativeLayout getBrightnessRelativeLayout() {
        return binding.brightnessRelativeLayout;
    }

    public FloatingActionButton getCloseOverlayButton() {
        return closeOverlayBinding.closeButton;
    }

    public View getLoadingPanel() {
        return binding.loadingPanel;
    }

    public TextView getCurrentDisplaySeek() {
        return binding.currentDisplaySeek;
    }

    @Nullable
    public WindowManager.LayoutParams getPopupLayoutParams() {
        return popupLayoutParams;
    }

    @Nullable
    public WindowManager getWindowManager() {
        return windowManager;
    }

    public float getScreenWidth() {
        return screenWidth;
    }

    public float getScreenHeight() {
        return screenHeight;
    }

    public View getRootView() {
        return binding.getRoot();
    }

    public ExpandableSurfaceView getSurfaceView() {
        return binding.surfaceView;
    }

    public PlayQueueAdapter getPlayQueueAdapter() {
        return playQueueAdapter;
    }

    //endregion
}
