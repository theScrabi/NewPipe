/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * BackgroundPlayer.java is part of NewPipe
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

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.nostra13.universalimageloader.core.assist.FailReason;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.helper.LockManager;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.resolver.AudioPlaybackResolver;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.util.BitmapUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

/**
 * Base players joining the common properties
 *
 * @author mauriciocolli
 */
public final class BackgroundPlayer extends Service {
    private static final String TAG = "BackgroundPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.BackgroundPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE = "org.schabi.newpipe.player.BackgroundPlayer.PLAY_PAUSE";
    public static final String ACTION_REPEAT = "org.schabi.newpipe.player.BackgroundPlayer.REPEAT";
    public static final String ACTION_PLAY_NEXT = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_PLAY_NEXT";
    public static final String ACTION_PLAY_PREVIOUS = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_PLAY_PREVIOUS";
    public static final String ACTION_FAST_REWIND = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_REWIND";
    public static final String ACTION_FAST_FORWARD = "org.schabi.newpipe.player.BackgroundPlayer.ACTION_FAST_FORWARD";

    public static final String SET_IMAGE_RESOURCE_METHOD = "setImageResource";

    private BasePlayerImpl basePlayerImpl;
    private LockManager lockManager;
    private SharedPreferences sharedPreferences;

    /*//////////////////////////////////////////////////////////////////////////
    // Service-Activity Binder
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerEventListener activityListener;
    private IBinder mBinder;

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private static final int NOTIFICATION_ID = 123789;
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;
    private RemoteViews bigNotRemoteView;

    private boolean shouldUpdateOnProgress;

    private static final int NOTIFICATION_UPDATES_BEFORE_RESET = 60;
    private int timesNotificationUpdated;

    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        if (DEBUG) Log.d(TAG, "onCreate() called");
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));
        lockManager = new LockManager(this);
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        assureCorrectAppLanguage(this);
        ThemeHelper.setTheme(this);
        basePlayerImpl = new BasePlayerImpl(this);
        basePlayerImpl.setup();

        mBinder = new PlayerServiceBinder(basePlayerImpl);
        shouldUpdateOnProgress = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) Log.d(TAG, "onStartCommand() called with: intent = [" + intent +
                "], flags = [" + flags + "], startId = [" + startId + "]");
        basePlayerImpl.handleIntent(intent);
        if (basePlayerImpl.mediaSessionManager != null) {
            basePlayerImpl.mediaSessionManager.handleMediaButtonIntent(intent);
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(TAG, "destroy() called");
        onClose();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Actions
    //////////////////////////////////////////////////////////////////////////*/
    private void onClose() {
        if (DEBUG) Log.d(TAG, "onClose() called");

        if (lockManager != null) {
            lockManager.releaseWifiAndCpu();
        }
        if (basePlayerImpl != null) {
            basePlayerImpl.savePlaybackState();
            basePlayerImpl.stopActivityBinding();
            basePlayerImpl.destroy();
        }
        if (notificationManager != null) notificationManager.cancel(NOTIFICATION_ID);
        mBinder = null;
        basePlayerImpl = null;
        lockManager = null;

        stopForeground(true);
        stopSelf();
    }

    private void onScreenOnOff(boolean on) {
        if (DEBUG) Log.d(TAG, "onScreenOnOff() called with: on = [" + on + "]");
        shouldUpdateOnProgress = on;
        basePlayerImpl.triggerProgressUpdate();
        if (on) {
            basePlayerImpl.startProgressLoop();
        } else {
            basePlayerImpl.stopProgressLoop();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private void resetNotification() {
        notBuilder = createNotification();
        timesNotificationUpdated = 0;
    }

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification);
        bigNotRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID, R.layout.player_notification_expanded);

        setupNotification(notRemoteView);
        setupNotification(bigNotRemoteView);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCustomContentView(notRemoteView)
                .setCustomBigContentView(bigNotRemoteView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setLockScreenThumbnail(builder);
        }
        builder.setPriority(NotificationCompat.PRIORITY_MAX);
        return builder;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void setLockScreenThumbnail(NotificationCompat.Builder builder) {
        boolean isLockScreenThumbnailEnabled = sharedPreferences.getBoolean(
                getString(R.string.enable_lock_screen_video_thumbnail_key),
                true
        );

        if (isLockScreenThumbnailEnabled) {
            basePlayerImpl.mediaSessionManager.setLockScreenArt(
                    builder,
                    getCenteredThumbnailBitmap()
            );
        } else {
            basePlayerImpl.mediaSessionManager.clearLockScreenArt(builder);
        }
    }

    @Nullable
    private Bitmap getCenteredThumbnailBitmap() {
        int screenWidth = Resources.getSystem().getDisplayMetrics().widthPixels;
        int screenHeight = Resources.getSystem().getDisplayMetrics().heightPixels;

        return BitmapUtils.centerCrop(
                basePlayerImpl.getThumbnail(),
                screenWidth,
                screenHeight);
    }

    private void setupNotification(RemoteViews remoteViews) {
        if (basePlayerImpl == null) return;

        remoteViews.setTextViewText(R.id.notificationSongName, basePlayerImpl.getVideoTitle());
        remoteViews.setTextViewText(R.id.notificationArtist, basePlayerImpl.getUploaderName());

        remoteViews.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE), PendingIntent.FLAG_UPDATE_CURRENT));
        remoteViews.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT), PendingIntent.FLAG_UPDATE_CURRENT));

        // Starts background player activity -- attempts to unlock lockscreen
        final Intent intent = NavigationHelper.getBackgroundPlayerActivityIntent(this);
        remoteViews.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT));

        if (basePlayerImpl.playQueue != null && basePlayerImpl.playQueue.size() > 1) {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_previous);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_next);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PREVIOUS), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_NEXT), PendingIntent.FLAG_UPDATE_CURRENT));
        } else {
            remoteViews.setInt(R.id.notificationFRewind, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_rewind);
            remoteViews.setInt(R.id.notificationFForward, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_fastforward);
            remoteViews.setOnClickPendingIntent(R.id.notificationFRewind,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_REWIND), PendingIntent.FLAG_UPDATE_CURRENT));
            remoteViews.setOnClickPendingIntent(R.id.notificationFForward,
                    PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_FAST_FORWARD), PendingIntent.FLAG_UPDATE_CURRENT));
        }

        setRepeatModeIcon(remoteViews, basePlayerImpl.getRepeatMode());
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    private synchronized void updateNotification(int drawableId) {
        //if (DEBUG) Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        if (notBuilder == null) return;
        if (drawableId != -1) {
            if (notRemoteView != null)
                notRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
            if (bigNotRemoteView != null)
                bigNotRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
        }
        notificationManager.notify(NOTIFICATION_ID, notBuilder.build());
        timesNotificationUpdated++;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void setRepeatModeIcon(final RemoteViews remoteViews, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat, SET_IMAGE_RESOURCE_METHOD, R.drawable.exo_controls_repeat_all);
                break;
        }
    }
    //////////////////////////////////////////////////////////////////////////

    protected class BasePlayerImpl extends BasePlayer {

        @NonNull
        final private AudioPlaybackResolver resolver;
        private int cachedDuration;
        private String cachedDurationString;

        BasePlayerImpl(Context context) {
            super(context);
            this.resolver = new AudioPlaybackResolver(context, dataSource);
        }

        @Override
        public void initPlayer(boolean playOnReady) {
            super.initPlayer(playOnReady);
        }

        @Override
        public void handleIntent(final Intent intent) {
            super.handleIntent(intent);

            resetNotification();
            if (bigNotRemoteView != null)
                bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            if (notRemoteView != null)
                notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 0, false);
            startForeground(NOTIFICATION_ID, notBuilder.build());
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Thumbnail Loading
        //////////////////////////////////////////////////////////////////////////*/

        private void updateNotificationThumbnail() {
            if (basePlayerImpl == null) return;
            if (notRemoteView != null) {
                notRemoteView.setImageViewBitmap(R.id.notificationCover,
                        basePlayerImpl.getThumbnail());
            }
            if (bigNotRemoteView != null) {
                bigNotRemoteView.setImageViewBitmap(R.id.notificationCover,
                        basePlayerImpl.getThumbnail());
            }
        }

        @Override
        public void onLoadingComplete(String imageUri, View view, Bitmap loadedImage) {
            super.onLoadingComplete(imageUri, view, loadedImage);
            resetNotification();
            updateNotificationThumbnail();
            updateNotification(-1);
        }

        @Override
        public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
            super.onLoadingFailed(imageUri, view, failReason);
            resetNotification();
            updateNotificationThumbnail();
            updateNotification(-1);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States Implementation
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onPrepared(boolean playWhenReady) {
            super.onPrepared(playWhenReady);
            simpleExoPlayer.setVolume(1f);
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            updatePlayback();
        }

        @Override
        public void onUpdateProgress(int currentProgress, int duration, int bufferPercent) {
            updateProgress(currentProgress, duration, bufferPercent);

            if (!shouldUpdateOnProgress) return;
            if (timesNotificationUpdated > NOTIFICATION_UPDATES_BEFORE_RESET) {
                resetNotification();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O /*Oreo*/) {
                    updateNotificationThumbnail();
                }
            }
            if (bigNotRemoteView != null) {
                if (cachedDuration != duration) {
                    cachedDuration = duration;
                    cachedDurationString = getTimeString(duration);
                }
                bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
                bigNotRemoteView.setTextViewText(R.id.notificationTime, getTimeString(currentProgress) + " / " + cachedDurationString);
            }
            if (notRemoteView != null) {
                notRemoteView.setProgressBar(R.id.notificationProgressBar, duration, currentProgress, false);
            }
            updateNotification(-1);
        }

        @Override
        public void onPlayPrevious() {
            super.onPlayPrevious();
            triggerProgressUpdate();
        }

        @Override
        public void onPlayNext() {
            super.onPlayNext();
            triggerProgressUpdate();
        }

        @Override
        public void destroy() {
            super.destroy();
            if (notRemoteView != null)
                notRemoteView.setImageViewBitmap(R.id.notificationCover, null);
            if (bigNotRemoteView != null)
                bigNotRemoteView.setImageViewBitmap(R.id.notificationCover, null);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
            super.onPlaybackParametersChanged(playbackParameters);
            updatePlayback();
        }

        @Override
        public void onLoadingChanged(boolean isLoading) {
            // Disable default behavior
        }

        @Override
        public void onRepeatModeChanged(int i) {
            resetNotification();
            updateNotification(-1);
            updatePlayback();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
            super.onMetadataChanged(tag);
            resetNotification();
            updateNotificationThumbnail();
            updateNotification(-1);
            updateMetadata();
        }

        @Override
        @Nullable
        public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
            return resolver.resolve(info);
        }

        @Override
        public void onPlaybackShutdown() {
            super.onPlaybackShutdown();
            onClose();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Activity Event Listener
        //////////////////////////////////////////////////////////////////////////*/

        /*package-private*/ void setActivityListener(PlayerEventListener listener) {
            activityListener = listener;
            updateMetadata();
            updatePlayback();
            triggerProgressUpdate();
        }

        /*package-private*/ void removeActivityListener(PlayerEventListener listener) {
            if (activityListener == listener) {
                activityListener = null;
            }
        }

        private void updateMetadata() {
            if (activityListener != null && getCurrentMetadata() != null) {
                activityListener.onMetadataUpdate(getCurrentMetadata().getMetadata());
            }
        }

        private void updatePlayback() {
            if (activityListener != null && simpleExoPlayer != null && playQueue != null) {
                activityListener.onPlaybackUpdate(currentState, getRepeatMode(),
                        playQueue.isShuffled(), getPlaybackParameters());
            }
        }

        private void updateProgress(int currentProgress, int duration, int bufferPercent) {
            if (activityListener != null) {
                activityListener.onProgressUpdate(currentProgress, duration, bufferPercent);
            }
        }

        private void stopActivityBinding() {
            if (activityListener != null) {
                activityListener.onServiceStopped();
                activityListener = null;
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Broadcast Receiver
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        protected void setupBroadcastReceiver(IntentFilter intentFilter) {
            super.setupBroadcastReceiver(intentFilter);
            intentFilter.addAction(ACTION_CLOSE);
            intentFilter.addAction(ACTION_PLAY_PAUSE);
            intentFilter.addAction(ACTION_REPEAT);
            intentFilter.addAction(ACTION_PLAY_PREVIOUS);
            intentFilter.addAction(ACTION_PLAY_NEXT);
            intentFilter.addAction(ACTION_FAST_REWIND);
            intentFilter.addAction(ACTION_FAST_FORWARD);

            intentFilter.addAction(Intent.ACTION_SCREEN_ON);
            intentFilter.addAction(Intent.ACTION_SCREEN_OFF);

            intentFilter.addAction(Intent.ACTION_HEADSET_PLUG);
        }

        @Override
        public void onBroadcastReceived(Intent intent) {
            super.onBroadcastReceived(intent);
            if (intent == null || intent.getAction() == null) return;
            if (DEBUG) Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
            switch (intent.getAction()) {
                case ACTION_CLOSE:
                    onClose();
                    break;
                case ACTION_PLAY_PAUSE:
                    onPlayPause();
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
                case ACTION_PLAY_NEXT:
                    onPlayNext();
                    break;
                case ACTION_PLAY_PREVIOUS:
                    onPlayPrevious();
                    break;
                case ACTION_FAST_FORWARD:
                    onFastForward();
                    break;
                case ACTION_FAST_REWIND:
                    onFastRewind();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    onScreenOnOff(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    onScreenOnOff(false);
                    break;
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void changeState(int state) {
            super.changeState(state);
            updatePlayback();
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            resetNotification();
            updateNotificationThumbnail();
            updateNotification(R.drawable.ic_pause_white);
            lockManager.acquireWifiAndCpu();
        }

        @Override
        public void onPaused() {
            super.onPaused();
            resetNotification();
            updateNotificationThumbnail();
            updateNotification(R.drawable.ic_play_arrow_white);
            lockManager.releaseWifiAndCpu();
        }

        @Override
        public void onCompleted() {
            super.onCompleted();
            resetNotification();
            if (bigNotRemoteView != null) {
                bigNotRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 100, false);
            }
            if (notRemoteView != null) {
                notRemoteView.setProgressBar(R.id.notificationProgressBar, 100, 100, false);
            }
            updateNotificationThumbnail();
            updateNotification(R.drawable.ic_replay_white);
            lockManager.releaseWifiAndCpu();
        }
    }
}
