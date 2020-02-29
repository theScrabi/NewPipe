/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * BasePlayer.java is part of NewPipe
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

package org.schabi.newpipe.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.DownloaderImpl;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.helper.AudioReactor;
import org.schabi.newpipe.player.helper.LoadController;
import org.schabi.newpipe.player.helper.MediaSessionManager;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.mediasource.FailedMediaSource;
import org.schabi.newpipe.player.playback.BasePlayerMediaSession;
import org.schabi.newpipe.player.playback.CustomTrackSelector;
import org.schabi.newpipe.player.playback.MediaSourceManager;
import org.schabi.newpipe.player.playback.PlaybackListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueAdapter;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.SerializedCache;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.SerialDisposable;

import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_INTERNAL;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_PERIOD_TRANSITION;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK;
import static com.google.android.exoplayer2.Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT;

/**
 * Base for the players, joining the common properties
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess"})
public abstract class BasePlayer implements
        Player.EventListener, PlaybackListener, ImageLoadingListener {

    public static final boolean DEBUG = !BuildConfig.BUILD_TYPE.equals("release");
    @NonNull
    public static final String TAG = "BasePlayer";

    @NonNull
    final protected Context context;

    @NonNull
    final protected BroadcastReceiver broadcastReceiver;
    @NonNull
    final protected IntentFilter intentFilter;

    @NonNull
    final protected HistoryRecordManager recordManager;

    @NonNull
    final protected CustomTrackSelector trackSelector;
    @NonNull
    final protected PlayerDataSource dataSource;

    @NonNull
    final private LoadControl loadControl;
    @NonNull
    final private RenderersFactory renderFactory;

    @NonNull
    final private SerialDisposable progressUpdateReactor;
    @NonNull
    final private CompositeDisposable databaseUpdateReactor;
    /*//////////////////////////////////////////////////////////////////////////
    // Intent
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    public static final String REPEAT_MODE = "repeat_mode";
    @NonNull
    public static final String PLAYBACK_PITCH = "playback_pitch";
    @NonNull
    public static final String PLAYBACK_SPEED = "playback_speed";
    @NonNull
    public static final String PLAYBACK_SKIP_SILENCE = "playback_skip_silence";
    @NonNull
    public static final String PLAYBACK_QUALITY = "playback_quality";
    @NonNull
    public static final String PLAY_QUEUE_KEY = "play_queue_key";
    @NonNull
    public static final String APPEND_ONLY = "append_only";
    @NonNull
    public static final String RESUME_PLAYBACK = "resume_playback";
    @NonNull
    public static final String START_PAUSED = "start_paused";
    @NonNull
    public static final String SELECT_ON_APPEND = "select_on_append";

    /*//////////////////////////////////////////////////////////////////////////
    // Playback
    //////////////////////////////////////////////////////////////////////////*/

    protected static final float[] PLAYBACK_SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    protected PlayQueue playQueue;
    protected PlayQueueAdapter playQueueAdapter;

    @Nullable
    protected MediaSourceManager playbackManager;

    @Nullable
    private PlayQueueItem currentItem;
    @Nullable
    private MediaSourceTag currentMetadata;
    @Nullable
    private Bitmap currentThumbnail;

    @Nullable
    protected Toast errorToast;

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/

    protected final static int PLAY_PREV_ACTIVATION_LIMIT_MILLIS = 5000; // 5 seconds
    protected final static int PROGRESS_LOOP_INTERVAL_MILLIS = 500;
    protected final static int RECOVERY_SKIP_THRESHOLD_MILLIS = 3000; // 3 seconds

    protected SimpleExoPlayer simpleExoPlayer;
    protected AudioReactor audioReactor;
    protected MediaSessionManager mediaSessionManager;

    private boolean isPrepared = false;
    private Disposable stateLoader;

    //////////////////////////////////////////////////////////////////////////*/


    /*//////////////////////////////////////////////////////////////////////////
    // Timer
    //////////////////////////////////////////////////////////////////////////*/

    private Timer timer = new Timer();
    private int timerHour = 0;
    private int timerMinute = 0;

    //////////////////////////////////////////////////////////////////////////*/

    public BasePlayer(@NonNull final Context context) {
        this.context = context;

        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onBroadcastReceived(intent);
            }
        };
        this.intentFilter = new IntentFilter();
        setupBroadcastReceiver(intentFilter);

        this.recordManager = new HistoryRecordManager(context);

        this.progressUpdateReactor = new SerialDisposable();
        this.databaseUpdateReactor = new CompositeDisposable();

        final String userAgent = DownloaderImpl.USER_AGENT;
        final DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(context).build();
        this.dataSource = new PlayerDataSource(context, userAgent, bandwidthMeter);

        final TrackSelection.Factory trackSelectionFactory = PlayerHelper.getQualitySelector(context);
        this.trackSelector = new CustomTrackSelector(trackSelectionFactory);

        this.loadControl = new LoadController(context);
        this.renderFactory = new DefaultRenderersFactory(context);
    }

    public void setup() {
        if (simpleExoPlayer == null) {
            initPlayer(/*playOnInit=*/true);
        }
        initListeners();
    }

    public void initPlayer(final boolean playOnReady) {
        if (DEBUG) Log.d(TAG, "initPlayer() called with: context = [" + context + "]");

        simpleExoPlayer = ExoPlayerFactory.newSimpleInstance(context, renderFactory, trackSelector, loadControl);
        simpleExoPlayer.addListener(this);
        simpleExoPlayer.setPlayWhenReady(playOnReady);
        simpleExoPlayer.setSeekParameters(PlayerHelper.getSeekParameters(context));

        audioReactor = new AudioReactor(context, simpleExoPlayer);
        mediaSessionManager = new MediaSessionManager(context, simpleExoPlayer,
                new BasePlayerMediaSession(this));

        registerBroadcastReceiver();
    }

    public void initListeners() {
    }

    public void handleIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
        if (intent == null) return;

        // Resolve play queue
        if (!intent.hasExtra(PLAY_QUEUE_KEY)) return;
        final String intentCacheKey = intent.getStringExtra(PLAY_QUEUE_KEY);
        final PlayQueue queue = SerializedCache.getInstance().take(intentCacheKey, PlayQueue.class);
        if (queue == null) return;

        // Resolve append intents
        if (intent.getBooleanExtra(APPEND_ONLY, false) && playQueue != null) {
            int sizeBeforeAppend = playQueue.size();
            playQueue.append(queue.getStreams());

            if ((intent.getBooleanExtra(SELECT_ON_APPEND, false) ||
                    getCurrentState() == STATE_COMPLETED) &&
                    queue.getStreams().size() > 0) {
                playQueue.setIndex(sizeBeforeAppend);
            }

            return;
        }

        final int repeatMode = intent.getIntExtra(REPEAT_MODE, getRepeatMode());
        final float playbackSpeed = intent.getFloatExtra(PLAYBACK_SPEED, getPlaybackSpeed());
        final float playbackPitch = intent.getFloatExtra(PLAYBACK_PITCH, getPlaybackPitch());
        final boolean playbackSkipSilence = intent.getBooleanExtra(PLAYBACK_SKIP_SILENCE,
                getPlaybackSkipSilence());

        // seek to timestamp if stream is already playing
        if (simpleExoPlayer != null
                && queue.size() == 1
                && playQueue != null
                && playQueue.getItem() != null
                && queue.getItem().getUrl().equals(playQueue.getItem().getUrl())
                && queue.getItem().getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET
                ) {
            simpleExoPlayer.seekTo(playQueue.getIndex(), queue.getItem().getRecoveryPosition());
            return;

        } else if (intent.getBooleanExtra(RESUME_PLAYBACK, false) && isPlaybackResumeEnabled()) {
            final PlayQueueItem item = queue.getItem();
            if (item != null && item.getRecoveryPosition() == PlayQueueItem.RECOVERY_UNSET) {
                stateLoader = recordManager.loadStreamState(item)
                        .observeOn(AndroidSchedulers.mainThread())
                        .doFinally(() -> initPlayback(queue, repeatMode, playbackSpeed, playbackPitch, playbackSkipSilence,
                                /*playOnInit=*/true))
                        .subscribe(
                                state -> queue.setRecovery(queue.getIndex(), state.getProgressTime()),
                                error -> {
                                    if (DEBUG) error.printStackTrace();
                                }
                        );
                databaseUpdateReactor.add(stateLoader);
                return;
            }
        }
        // Good to go...
        initPlayback(queue, repeatMode, playbackSpeed, playbackPitch, playbackSkipSilence,
                /*playOnInit=*/!intent.getBooleanExtra(START_PAUSED, false));
    }

    protected void initPlayback(@NonNull final PlayQueue queue,
                                @Player.RepeatMode final int repeatMode,
                                final float playbackSpeed,
                                final float playbackPitch,
                                final boolean playbackSkipSilence,
                                final boolean playOnReady) {
        destroyPlayer();
        initPlayer(playOnReady);
        setRepeatMode(repeatMode);
        setPlaybackParameters(playbackSpeed, playbackPitch, playbackSkipSilence);

        playQueue = queue;
        playQueue.init();
        if (playbackManager != null) playbackManager.dispose();
        playbackManager = new MediaSourceManager(this, playQueue);

        if (playQueueAdapter != null) playQueueAdapter.dispose();
        playQueueAdapter = new PlayQueueAdapter(context, playQueue);
    }

    public void destroyPlayer() {
        if (DEBUG) Log.d(TAG, "destroyPlayer() called");
        if (simpleExoPlayer != null) {
            simpleExoPlayer.removeListener(this);
            simpleExoPlayer.stop();
            simpleExoPlayer.release();
        }
        if (isProgressLoopRunning()) stopProgressLoop();
        if (playQueue != null) playQueue.dispose();
        if (audioReactor != null) audioReactor.dispose();
        if (playbackManager != null) playbackManager.dispose();
        if (mediaSessionManager != null) mediaSessionManager.dispose();
        if (stateLoader != null) stateLoader.dispose();

        if (playQueueAdapter != null) {
            playQueueAdapter.unsetSelectedListener();
            playQueueAdapter.dispose();
        }
    }

    public void destroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        destroyPlayer();
        unregisterBroadcastReceiver();

        databaseUpdateReactor.clear();
        progressUpdateReactor.set(null);

    }

    /*//////////////////////////////////////////////////////////////////////////
    // Thumbnail Loading
    //////////////////////////////////////////////////////////////////////////*/

    private void initThumbnail(final String url) {
        if (DEBUG) Log.d(TAG, "Thumbnail - initThumbnail() called");
        if (url == null || url.isEmpty()) return;
        ImageLoader.getInstance().resume();
        ImageLoader.getInstance().loadImage(url, ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS,
                this);
    }

    @Override
    public void onLoadingStarted(String imageUri, View view) {
        if (DEBUG) Log.d(TAG, "Thumbnail - onLoadingStarted() called on: " +
                "imageUri = [" + imageUri + "], view = [" + view + "]");
    }

    @Override
    public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
        Log.e(TAG, "Thumbnail - onLoadingFailed() called on imageUri = [" + imageUri + "]",
                failReason.getCause());
        currentThumbnail = null;
    }

    @Override
    public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
        if (DEBUG) Log.d(TAG, "Thumbnail - onLoadingComplete() called with: " +
                "imageUri = [" + imageUri + "], view = [" + view + "], " +
                "loadedImage = [" + loadedImage + "]");
        currentThumbnail = loadedImage;
    }

    @Override
    public void onLoadingCancelled(String imageUri, View view) {
        if (DEBUG) Log.d(TAG, "Thumbnail - onLoadingCancelled() called with: " +
                "imageUri = [" + imageUri + "], view = [" + view + "]");
        currentThumbnail = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Broadcast Receiver
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Add your action in the intentFilter
     *
     * @param intentFilter intent filter that will be used for register the receiver
     */
    protected void setupBroadcastReceiver(IntentFilter intentFilter) {
        intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
    }

    public void onBroadcastReceived(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        switch (intent.getAction()) {
            case AudioManager.ACTION_AUDIO_BECOMING_NOISY:
                onPause();
                break;
        }
    }

    protected void registerBroadcastReceiver() {
        // Try to unregister current first
        unregisterBroadcastReceiver();
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    protected void unregisterBroadcastReceiver() {
        try {
            context.unregisterReceiver(broadcastReceiver);
        } catch (final IllegalArgumentException unregisteredException) {
            Log.w(TAG, "Broadcast receiver already unregistered (" + unregisteredException.getMessage() + ")");
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // States Implementation
    //////////////////////////////////////////////////////////////////////////*/

    public static final int STATE_PREFLIGHT = -1;
    public static final int STATE_BLOCKED = 123;
    public static final int STATE_PLAYING = 124;
    public static final int STATE_BUFFERING = 125;
    public static final int STATE_PAUSED = 126;
    public static final int STATE_PAUSED_SEEK = 127;
    public static final int STATE_COMPLETED = 128;

    protected int currentState = STATE_PREFLIGHT;

    public void changeState(int state) {
        if (DEBUG) Log.d(TAG, "changeState() called with: state = [" + state + "]");
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
    }

    public void onBlocked() {
        if (DEBUG) Log.d(TAG, "onBlocked() called");
        if (!isProgressLoopRunning()) startProgressLoop();
    }

    public void onPlaying() {
        if (DEBUG) Log.d(TAG, "onPlaying() called");
        if (!isProgressLoopRunning()) startProgressLoop();
    }

    public void onBuffering() {
    }

    public void onPaused() {
        if (isProgressLoopRunning()) stopProgressLoop();
    }

    public void onPausedSeek() {
    }

    public void onCompleted() {
        if (DEBUG) Log.d(TAG, "onCompleted() called");
        if (playQueue.getIndex() < playQueue.size() - 1) playQueue.offsetIndex(+1);
        if (isProgressLoopRunning()) stopProgressLoop();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Repeat and shuffle
    //////////////////////////////////////////////////////////////////////////*/

    public void onRepeatClicked() {
        if (DEBUG) Log.d(TAG, "onRepeatClicked() called");

        final int mode;

        switch (getRepeatMode()) {
            case Player.REPEAT_MODE_OFF:
                mode = Player.REPEAT_MODE_ONE;
                break;
            case Player.REPEAT_MODE_ONE:
                mode = Player.REPEAT_MODE_ALL;
                break;
            case Player.REPEAT_MODE_ALL:
            default:
                mode = Player.REPEAT_MODE_OFF;
                break;
        }

        setRepeatMode(mode);
        if (DEBUG) Log.d(TAG, "onRepeatClicked() currentRepeatMode = " + getRepeatMode());
    }

    public void onShuffleClicked() {
        if (DEBUG) Log.d(TAG, "onShuffleClicked() called");

        if (simpleExoPlayer == null) return;
        simpleExoPlayer.setShuffleModeEnabled(!simpleExoPlayer.getShuffleModeEnabled());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Progress Updates
    //////////////////////////////////////////////////////////////////////////*/

    public abstract void onUpdateProgress(int currentProgress, int duration, int bufferPercent);

    protected void startProgressLoop() {
        progressUpdateReactor.set(getProgressReactor());
    }

    protected void stopProgressLoop() {
        progressUpdateReactor.set(null);
    }

    public void triggerProgressUpdate() {
        if (simpleExoPlayer == null) return;
        onUpdateProgress(
                Math.max((int) simpleExoPlayer.getCurrentPosition(), 0),
                (int) simpleExoPlayer.getDuration(),
                simpleExoPlayer.getBufferedPercentage()
        );
    }

    private Disposable getProgressReactor() {
        return Observable.interval(PROGRESS_LOOP_INTERVAL_MILLIS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> triggerProgressUpdate(),
                        error -> Log.e(TAG, "Progress update failure: ", error));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest,
                                  @Player.TimelineChangeReason final int reason) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onTimelineChanged() called with " +
                (manifest == null ? "no manifest" : "available manifest") + ", " +
                "timeline size = [" + timeline.getWindowCount() + "], " +
                "reason = [" + reason + "]");

        maybeUpdateCurrentMetadata();
    }

    @Override
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onTracksChanged(), " +
                "track group size = " + trackGroups.length);

        maybeUpdateCurrentMetadata();
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - playbackParameters(), " +
                "speed: " + playbackParameters.speed + ", " +
                "pitch: " + playbackParameters.pitch);
    }

    @Override
    public void onLoadingChanged(final boolean isLoading) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onLoadingChanged() called with: " +
                "isLoading = [" + isLoading + "]");

        if (!isLoading && getCurrentState() == STATE_PAUSED && isProgressLoopRunning()) {
            stopProgressLoop();
        } else if (isLoading && !isProgressLoopRunning()) {
            startProgressLoop();
        }

        maybeUpdateCurrentMetadata();
    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onPlayerStateChanged() called with: " +
                "playWhenReady = [" + playWhenReady + "], " +
                "playbackState = [" + playbackState + "]");

        if (getCurrentState() == STATE_PAUSED_SEEK) {
            if (DEBUG) Log.d(TAG, "ExoPlayer - onPlayerStateChanged() is currently blocked");
            return;
        }

        switch (playbackState) {
            case Player.STATE_IDLE: // 1
                isPrepared = false;
                break;
            case Player.STATE_BUFFERING: // 2
                if (isPrepared) {
                    changeState(STATE_BUFFERING);
                }
                break;
            case Player.STATE_READY: //3
                maybeUpdateCurrentMetadata();
                maybeCorrectSeekPosition();
                if (!isPrepared) {
                    isPrepared = true;
                    onPrepared(playWhenReady);
                    break;
                }
                changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
                break;
            case Player.STATE_ENDED: // 4
                changeState(STATE_COMPLETED);
                if (currentMetadata != null) {
                    resetPlaybackState(currentMetadata.getMetadata());
                }
                isPrepared = false;
                break;
        }
    }

    private void maybeCorrectSeekPosition() {
        if (playQueue == null || simpleExoPlayer == null || currentMetadata == null) return;

        final PlayQueueItem currentSourceItem = playQueue.getItem();
        if (currentSourceItem == null) return;

        final StreamInfo currentInfo = currentMetadata.getMetadata();
        final long presetStartPositionMillis = currentInfo.getStartPosition() * 1000;
        if (presetStartPositionMillis > 0L) {
            // Has another start position?
            if (DEBUG) Log.d(TAG, "Playback - Seeking to preset start " +
                    "position=[" + presetStartPositionMillis + "]");
            seekTo(presetStartPositionMillis);
        }
    }

    /**
     * Processes the exceptions produced by {@link com.google.android.exoplayer2.ExoPlayer ExoPlayer}.
     * There are multiple types of errors: <br><br>
     * <p>
     * {@link ExoPlaybackException#TYPE_SOURCE TYPE_SOURCE}: <br><br>
     * <p>
     * {@link ExoPlaybackException#TYPE_UNEXPECTED TYPE_UNEXPECTED}: <br><br>
     * If a runtime error occurred, then we can try to recover it by restarting the playback
     * after setting the timestamp recovery. <br><br>
     * <p>
     * {@link ExoPlaybackException#TYPE_RENDERER TYPE_RENDERER}: <br><br>
     * If the renderer failed, treat the error as unrecoverable.
     *
     * @see #processSourceError(IOException)
     * @see Player.EventListener#onPlayerError(ExoPlaybackException)
     */
    @Override
    public void onPlayerError(ExoPlaybackException error) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onPlayerError() called with: " +
                "error = [" + error + "]");
        if (errorToast != null) {
            errorToast.cancel();
            errorToast = null;
        }

        savePlaybackState();

        switch (error.type) {
            case ExoPlaybackException.TYPE_SOURCE:
                processSourceError(error.getSourceException());
                showStreamError(error);
                break;
            case ExoPlaybackException.TYPE_UNEXPECTED:
                showRecoverableError(error);
                setRecovery();
                reload();
                break;
            default:
                showUnrecoverableError(error);
                onPlaybackShutdown();
                break;
        }
    }

    private void processSourceError(final IOException error) {
        if (simpleExoPlayer == null || playQueue == null) return;
        setRecovery();

        final Throwable cause = error.getCause();
        if (error instanceof BehindLiveWindowException) {
            reload();
        } else if (cause instanceof UnknownHostException) {
            playQueue.error(/*isNetworkProblem=*/true);
        } else if (isCurrentWindowValid()) {
            playQueue.error(/*isTransitioningToBadStream=*/true);
        } else if (cause instanceof FailedMediaSource.MediaSourceResolutionException) {
            playQueue.error(/*recoverableWithNoAvailableStream=*/false);
        } else if (cause instanceof FailedMediaSource.StreamInfoLoadException) {
            playQueue.error(/*recoverableIfLoadFailsWhenNetworkIsFine=*/false);
        } else {
            playQueue.error(/*noIdeaWhatHappenedAndLetUserChooseWhatToDo=*/true);
        }
    }

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason final int reason) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onPositionDiscontinuity() called with " +
                "reason = [" + reason + "]");
        if (playQueue == null) return;

        // Refresh the playback if there is a transition to the next video
        final int newWindowIndex = simpleExoPlayer.getCurrentWindowIndex();
        switch (reason) {
            case DISCONTINUITY_REASON_PERIOD_TRANSITION:
                // When player is in single repeat mode and a period transition occurs,
                // we need to register a view count here since no metadata has changed
                if (getRepeatMode() == Player.REPEAT_MODE_ONE &&
                        newWindowIndex == playQueue.getIndex()) {
                    registerView();
                    break;
                }
            case DISCONTINUITY_REASON_SEEK:
            case DISCONTINUITY_REASON_SEEK_ADJUSTMENT:
            case DISCONTINUITY_REASON_INTERNAL:
                if (playQueue.getIndex() != newWindowIndex) {
                    resetPlaybackState(playQueue.getItem());
                    playQueue.setIndex(newWindowIndex);
                }
                break;
        }

        maybeUpdateCurrentMetadata();
    }

    @Override
    public void onRepeatModeChanged(@Player.RepeatMode final int reason) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onRepeatModeChanged() called with: " +
                "mode = [" + reason + "]");
    }

    @Override
    public void onShuffleModeEnabledChanged(final boolean shuffleModeEnabled) {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onShuffleModeEnabledChanged() called with: " +
                "mode = [" + shuffleModeEnabled + "]");
        if (playQueue == null) return;
        if (shuffleModeEnabled) {
            playQueue.shuffle();
        } else {
            playQueue.unshuffle();
        }
    }

    @Override
    public void onSeekProcessed() {
        if (DEBUG) Log.d(TAG, "ExoPlayer - onSeekProcessed() called");
        if (isPrepared) {
            savePlaybackState();
        }
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Playback Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean isApproachingPlaybackEdge(final long timeToEndMillis) {
        // If live, then not near playback edge
        // If not playing, then not approaching playback edge
        if (simpleExoPlayer == null || isLive() || !isPlaying()) return false;

        final long currentPositionMillis = simpleExoPlayer.getCurrentPosition();
        final long currentDurationMillis = simpleExoPlayer.getDuration();
        return currentDurationMillis - currentPositionMillis < timeToEndMillis;
    }

    @Override
    public void onPlaybackBlock() {
        if (simpleExoPlayer == null) return;
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackBlock() called");

        currentItem = null;
        currentMetadata = null;
        simpleExoPlayer.stop();
        isPrepared = false;

        changeState(STATE_BLOCKED);
    }

    @Override
    public void onPlaybackUnblock(final MediaSource mediaSource) {
        if (simpleExoPlayer == null) return;
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackUnblock() called");

        if (getCurrentState() == STATE_BLOCKED) changeState(STATE_BUFFERING);

        simpleExoPlayer.prepare(mediaSource);
    }

    public void onPlaybackSynchronize(@NonNull final PlayQueueItem item) {
        if (DEBUG) Log.d(TAG, "Playback - onPlaybackSynchronize() called with " +
                "item=[" + item.getTitle() + "], url=[" + item.getUrl() + "]");
        if (simpleExoPlayer == null || playQueue == null) return;

        final boolean onPlaybackInitial = currentItem == null;
        final boolean hasPlayQueueItemChanged = currentItem != item;

        final int currentPlayQueueIndex = playQueue.indexOf(item);
        final int currentPlaylistIndex = simpleExoPlayer.getCurrentWindowIndex();
        final int currentPlaylistSize = simpleExoPlayer.getCurrentTimeline().getWindowCount();

        // If nothing to synchronize
        if (!hasPlayQueueItemChanged) return;
        currentItem = item;

        // Check if on wrong window
        if (currentPlayQueueIndex != playQueue.getIndex()) {
            Log.e(TAG, "Playback - Play Queue may be desynchronized: item " +
                    "index=[" + currentPlayQueueIndex + "], " +
                    "queue index=[" + playQueue.getIndex() + "]");

            // Check if bad seek position
        } else if ((currentPlaylistSize > 0 && currentPlayQueueIndex >= currentPlaylistSize) ||
                currentPlayQueueIndex < 0) {
            Log.e(TAG, "Playback - Trying to seek to invalid " +
                    "index=[" + currentPlayQueueIndex + "] with " +
                    "playlist length=[" + currentPlaylistSize + "]");

        } else if (currentPlaylistIndex != currentPlayQueueIndex || onPlaybackInitial ||
                !isPlaying()) {
            if (DEBUG) Log.d(TAG, "Playback - Rewinding to correct" +
                    " index=[" + currentPlayQueueIndex + "]," +
                    " from=[" + currentPlaylistIndex + "], size=[" + currentPlaylistSize + "].");

            if (item.getRecoveryPosition() != PlayQueueItem.RECOVERY_UNSET) {
                simpleExoPlayer.seekTo(currentPlayQueueIndex, item.getRecoveryPosition());
                playQueue.unsetRecovery(currentPlayQueueIndex);
            } else {
                simpleExoPlayer.seekToDefaultPosition(currentPlayQueueIndex);
            }
        }
    }

    protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
        final StreamInfo info = tag.getMetadata();
        if (DEBUG) {
            Log.d(TAG, "Playback - onMetadataChanged() called, playing: " + info.getName());
        }

        initThumbnail(info.getThumbnailUrl());
        registerView();
    }

    @Override
    public void onPlaybackShutdown() {
        if (DEBUG) Log.d(TAG, "Shutting down...");
        destroy();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // General Player
    //////////////////////////////////////////////////////////////////////////*/

    public void showStreamError(Exception exception) {
        exception.printStackTrace();

        if (errorToast == null) {
            errorToast = Toast.makeText(context, R.string.player_stream_failure, Toast.LENGTH_SHORT);
            errorToast.show();
        }
    }

    public void showRecoverableError(Exception exception) {
        exception.printStackTrace();

        if (errorToast == null) {
            errorToast = Toast.makeText(context, R.string.player_recoverable_failure, Toast.LENGTH_SHORT);
            errorToast.show();
        }
    }

    public void showUnrecoverableError(Exception exception) {
        exception.printStackTrace();

        if (errorToast != null) {
            errorToast.cancel();
        }
        errorToast = Toast.makeText(context, R.string.player_unrecoverable_failure, Toast.LENGTH_SHORT);
        errorToast.show();
    }

    public void onPrepared(boolean playWhenReady) {
        if (DEBUG) Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        if (playWhenReady) audioReactor.requestAudioFocus();
        changeState(playWhenReady ? STATE_PLAYING : STATE_PAUSED);
    }

    public void onPlay() {
        if (DEBUG) Log.d(TAG, "onPlay() called");
        if (audioReactor == null || playQueue == null || simpleExoPlayer == null) return;

        audioReactor.requestAudioFocus();

        if (getCurrentState() == STATE_COMPLETED) {
            if (playQueue.getIndex() == 0) {
                seekToDefault();
            } else {
                playQueue.setIndex(0);
            }
        }

        simpleExoPlayer.setPlayWhenReady(true);
    }

    public void onPause() {
        if (DEBUG) Log.d(TAG, "onPause() called");
        if (audioReactor == null || simpleExoPlayer == null) return;

        audioReactor.abandonAudioFocus();
        simpleExoPlayer.setPlayWhenReady(false);
    }

    public void onPlayPause() {
        if (DEBUG) Log.d(TAG, "onPlayPause() called");

        if (isPlaying()) {
            onPause();
        } else {
            onPlay();
        }
    }

    public void onFastRewind() {
        if (DEBUG) Log.d(TAG, "onFastRewind() called");
        seekBy(-getSeekDuration());
    }

    public void onFastForward() {
        if (DEBUG) Log.d(TAG, "onFastForward() called");
        seekBy(getSeekDuration());
    }

    private int getSeekDuration() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String key = context.getString(R.string.seek_duration_key);
        final String value = prefs.getString(key, context.getString(R.string.seek_duration_default_value));
        return Integer.parseInt(value);
    }

    public void onPlayPrevious() {
        if (simpleExoPlayer == null || playQueue == null) return;
        if (DEBUG) Log.d(TAG, "onPlayPrevious() called");

        /* If current playback has run for PLAY_PREV_ACTIVATION_LIMIT_MILLIS milliseconds,
         * restart current track. Also restart the track if the current track
         * is the first in a queue.*/
        if (simpleExoPlayer.getCurrentPosition() > PLAY_PREV_ACTIVATION_LIMIT_MILLIS ||
                playQueue.getIndex() == 0) {
            seekToDefault();
            playQueue.offsetIndex(0);
        } else {
            savePlaybackState();
            playQueue.offsetIndex(-1);
        }
    }

    public void onPlayNext() {
        if (playQueue == null) return;
        if (DEBUG) Log.d(TAG, "onPlayNext() called");

        savePlaybackState();
        playQueue.offsetIndex(+1);
    }

    public void onSelected(final PlayQueueItem item) {
        if (playQueue == null || simpleExoPlayer == null) return;

        final int index = playQueue.indexOf(item);
        if (index == -1) return;

        if (playQueue.getIndex() == index && simpleExoPlayer.getCurrentWindowIndex() == index) {
            seekToDefault();
        } else {
            savePlaybackState();
        }
        playQueue.setIndex(index);
    }

    public void seekTo(long positionMillis) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: position = [" + positionMillis + "]");
        if (simpleExoPlayer != null) simpleExoPlayer.seekTo(positionMillis);
    }

    public void seekBy(long offsetMillis) {
        if (DEBUG) Log.d(TAG, "seekBy() called with: offsetMillis = [" + offsetMillis + "]");
        seekTo(simpleExoPlayer.getCurrentPosition() + offsetMillis);
    }

    public boolean isCurrentWindowValid() {
        return simpleExoPlayer != null && simpleExoPlayer.getDuration() >= 0
                && simpleExoPlayer.getCurrentPosition() >= 0;
    }

    public void seekToDefault() {
        if (simpleExoPlayer != null) {
            simpleExoPlayer.seekToDefaultPosition();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void registerView() {
        if (currentMetadata == null) return;
        final StreamInfo currentInfo = currentMetadata.getMetadata();
        final Disposable viewRegister = recordManager.onViewed(currentInfo).onErrorComplete()
                .subscribe(
                        ignored -> {/* successful */},
                        error -> Log.e(TAG, "Player onViewed() failure: ", error)
                );
        databaseUpdateReactor.add(viewRegister);
    }

    protected void reload() {
        if (playbackManager != null) {
            playbackManager.dispose();
        }

        if (playQueue != null) {
            playbackManager = new MediaSourceManager(this, playQueue);
        }
    }

    private void savePlaybackState(final StreamInfo info, final long progress) {
        if (info == null) return;
        if (DEBUG) Log.d(TAG, "savePlaybackState() called");
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
            final Disposable stateSaver = recordManager.saveStreamState(info, progress)
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError((e) -> {
                        if (DEBUG) e.printStackTrace();
                    })
                    .onErrorComplete()
                    .subscribe();
            databaseUpdateReactor.add(stateSaver);
        }
    }

    private void resetPlaybackState(final PlayQueueItem queueItem) {
        if (queueItem == null) return;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)) {
            final Disposable stateSaver = queueItem.getStream()
                    .flatMapCompletable(info -> recordManager.saveStreamState(info, 0))
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnError((e) -> {
                        if (DEBUG) e.printStackTrace();
                    })
                    .onErrorComplete()
                    .subscribe();
            databaseUpdateReactor.add(stateSaver);
        }
    }

    public void resetPlaybackState(final StreamInfo info) {
        savePlaybackState(info, 0);
    }

    public void savePlaybackState() {
        if (simpleExoPlayer == null || currentMetadata == null) return;
        final StreamInfo currentInfo = currentMetadata.getMetadata();
        savePlaybackState(currentInfo, simpleExoPlayer.getCurrentPosition());
    }

    private void maybeUpdateCurrentMetadata() {
        if (simpleExoPlayer == null) return;

        final MediaSourceTag metadata;
        try {
            metadata = (MediaSourceTag) simpleExoPlayer.getCurrentTag();
        } catch (IndexOutOfBoundsException | ClassCastException error) {
            if (DEBUG) Log.d(TAG, "Could not update metadata: " + error.getMessage());
            if (DEBUG) error.printStackTrace();
            return;
        }

        if (metadata == null) return;
        maybeAutoQueueNextStream(metadata);

        if (currentMetadata == metadata) return;
        currentMetadata = metadata;
        onMetadataChanged(metadata);
    }

    private void maybeAutoQueueNextStream(@NonNull final MediaSourceTag currentMetadata) {
        if (playQueue == null || playQueue.getIndex() != playQueue.size() - 1 ||
                getRepeatMode() != Player.REPEAT_MODE_OFF ||
                !PlayerHelper.isAutoQueueEnabled(context)) return;
        // auto queue when starting playback on the last item when not repeating
        final PlayQueue autoQueue = PlayerHelper.autoQueueOf(currentMetadata.getMetadata(),
                playQueue.getStreams());
        if (autoQueue != null) playQueue.append(autoQueue.getStreams());
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    //////////////////////////////////////////////////////////////////////////*/

    public SimpleExoPlayer getPlayer() {
        return simpleExoPlayer;
    }

    public AudioReactor getAudioReactor() {
        return audioReactor;
    }

    public int getCurrentState() {
        return currentState;
    }

    @Nullable
    public MediaSourceTag getCurrentMetadata() {
        return currentMetadata;
    }

    @NonNull
    public String getVideoUrl() {
        return currentMetadata == null ? context.getString(R.string.unknown_content) : currentMetadata.getMetadata().getUrl();
    }

    @NonNull
    public String getVideoTitle() {
        return currentMetadata == null ? context.getString(R.string.unknown_content) : currentMetadata.getMetadata().getName();
    }

    @NonNull
    public String getUploaderName() {
        return currentMetadata == null ? context.getString(R.string.unknown_content) : currentMetadata.getMetadata().getUploaderName();
    }

    @Nullable
    public Bitmap getThumbnail() {
        return currentThumbnail == null ?
                BitmapFactory.decodeResource(context.getResources(), R.drawable.dummy_thumbnail) :
                currentThumbnail;
    }

    /**
     * Checks if the current playback is a livestream AND is playing at or beyond the live edge
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isLiveEdge() {
        if (simpleExoPlayer == null || !isLive()) return false;

        final Timeline currentTimeline = simpleExoPlayer.getCurrentTimeline();
        final int currentWindowIndex = simpleExoPlayer.getCurrentWindowIndex();
        if (currentTimeline.isEmpty() || currentWindowIndex < 0 ||
                currentWindowIndex >= currentTimeline.getWindowCount()) {
            return false;
        }

        Timeline.Window timelineWindow = new Timeline.Window();
        currentTimeline.getWindow(currentWindowIndex, timelineWindow);
        return timelineWindow.getDefaultPositionMs() <= simpleExoPlayer.getCurrentPosition();
    }

    public boolean isLive() {
        if (simpleExoPlayer == null) return false;
        try {
            return simpleExoPlayer.isCurrentWindowDynamic();
        } catch (@NonNull IndexOutOfBoundsException ignored) {
            // Why would this even happen =(
            // But lets log it anyway. Save is save
            if (DEBUG) Log.d(TAG, "Could not update metadata: " + ignored.getMessage());
            if (DEBUG) ignored.printStackTrace();
            return false;
        }
    }

    public boolean isPlaying() {
        return simpleExoPlayer != null && simpleExoPlayer.isPlaying();
    }

    @Player.RepeatMode
    public int getRepeatMode() {
        return simpleExoPlayer == null
                ? Player.REPEAT_MODE_OFF
                : simpleExoPlayer.getRepeatMode();
    }

    public void setRepeatMode(@Player.RepeatMode final int repeatMode) {
        if (simpleExoPlayer != null) simpleExoPlayer.setRepeatMode(repeatMode);
    }

    public float getPlaybackSpeed() {
        return getPlaybackParameters().speed;
    }

    public float getPlaybackPitch() {
        return getPlaybackParameters().pitch;
    }

    public boolean getPlaybackSkipSilence() {
        return getPlaybackParameters().skipSilence;
    }

    public void setPlaybackSpeed(float speed) {
        setPlaybackParameters(speed, getPlaybackPitch(), getPlaybackSkipSilence());
    }

    public PlaybackParameters getPlaybackParameters() {
        if (simpleExoPlayer == null) return PlaybackParameters.DEFAULT;
        final PlaybackParameters parameters = simpleExoPlayer.getPlaybackParameters();
        return parameters == null ? PlaybackParameters.DEFAULT : parameters;
    }

    public void setPlaybackParameters(float speed, float pitch, boolean skipSilence) {
        simpleExoPlayer.setPlaybackParameters(new PlaybackParameters(speed, pitch, skipSilence));
    }

    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    public PlayQueueAdapter getPlayQueueAdapter() {
        return playQueueAdapter;
    }

    public boolean isPrepared() {
        return isPrepared;
    }

    public boolean isProgressLoopRunning() {
        return progressUpdateReactor.get() != null;
    }

    public void setRecovery() {
        if (playQueue == null || simpleExoPlayer == null) return;

        final int queuePos = playQueue.getIndex();
        final long windowPos = simpleExoPlayer.getCurrentPosition();

        if (windowPos > 0 && windowPos <= simpleExoPlayer.getDuration()) {
            setRecovery(queuePos, windowPos);
        }
    }

    public void setRecovery(final int queuePos, final long windowPos) {
        if (playQueue.size() <= queuePos) return;

        if (DEBUG) Log.d(TAG, "Setting recovery, queue: " + queuePos + ", pos: " + windowPos);
        playQueue.setRecovery(queuePos, windowPos);
    }

    public boolean gotDestroyed() {
        return simpleExoPlayer == null;
    }

    private boolean isPlaybackResumeEnabled() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(context.getString(R.string.enable_watch_history_key), true)
                && prefs.getBoolean(context.getString(R.string.enable_playback_resume_key), true);
    }

    public void setTimer(int hourOfDay, int minute, Context ctx) {
        long time = ((hourOfDay * 60) + minute) * 60 * 1000;
        if (time == 0) {
            Toast.makeText(ctx, ctx.getString(R.string.timer_select_time_message), Toast.LENGTH_SHORT).show();
            return;
        }
        timer.cancel();
        timer.purge();
        timerHour = hourOfDay;
        timerMinute = minute;
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (DEBUG) Log.d(TAG, "Timer finished: Stopping the player");
                    onPause();
                });

            }
        }, time);
        Date d = new Date();
        d.setTime(System.currentTimeMillis() + time);
        Toast.makeText(ctx.getApplicationContext(), String.format(ctx.getString(R.string.player_stop_message), d.toString()), Toast.LENGTH_LONG).show();
    }

    public void cancelTimer() {
        timer.cancel();
        timer.purge();
    }

    public int getHourOfDay() {
        return this.timerHour;
    }

    public int getMinutes() {
        return this.timerMinute;
    }

}
