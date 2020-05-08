/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * PopupVideoPlayer.java is part of NewPipe
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AnticipateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nostra13.universalimageloader.core.assist.FailReason;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.event.PlayerEventListener;
import org.schabi.newpipe.player.helper.LockManager;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;

import static org.schabi.newpipe.player.BasePlayer.STATE_PLAYING;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_DURATION;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME;
import static org.schabi.newpipe.util.AnimationUtils.animateView;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

/**
 * Service Popup Player implementing {@link VideoPlayer}.
 *
 * @author mauriciocolli
 */
public final class PopupVideoPlayer extends Service {
    public static final String ACTION_CLOSE = "org.schabi.newpipe.player.PopupVideoPlayer.CLOSE";
    public static final String ACTION_PLAY_PAUSE
            = "org.schabi.newpipe.player.PopupVideoPlayer.PLAY_PAUSE";
    public static final String ACTION_REPEAT = "org.schabi.newpipe.player.PopupVideoPlayer.REPEAT";
    private static final String TAG = ".PopupVideoPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;
    private static final int NOTIFICATION_ID = 40028922;
    private static final String POPUP_SAVED_WIDTH = "popup_saved_width";
    private static final String POPUP_SAVED_X = "popup_saved_x";
    private static final String POPUP_SAVED_Y = "popup_saved_y";

    private static final int MINIMUM_SHOW_EXTRA_WIDTH_DP = 300;

    private static final int IDLE_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
    private static final int ONGOING_PLAYBACK_WINDOW_FLAGS = IDLE_WINDOW_FLAGS
            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

    private WindowManager windowManager;
    private WindowManager.LayoutParams popupLayoutParams;
    private GestureDetector popupGestureDetector;

    private View closeOverlayView;
    private FloatingActionButton closeOverlayButton;

    private int tossFlingVelocity;

    private float screenWidth;
    private float screenHeight;
    private float popupWidth;
    private float popupHeight;

    private float minimumWidth;
    private float minimumHeight;
    private float maximumWidth;
    private float maximumHeight;

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notBuilder;
    private RemoteViews notRemoteView;

    private VideoPlayerImpl playerImpl;
    private LockManager lockManager;
    private boolean isPopupClosing = false;

    /*//////////////////////////////////////////////////////////////////////////
    // Service-Activity Binder
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerEventListener activityListener;
    private IBinder mBinder;

    /*//////////////////////////////////////////////////////////////////////////
    // Service LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        assureCorrectAppLanguage(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        notificationManager = ((NotificationManager) getSystemService(NOTIFICATION_SERVICE));

        lockManager = new LockManager(this);
        playerImpl = new VideoPlayerImpl(this);
        ThemeHelper.setTheme(this);

        mBinder = new PlayerServiceBinder(playerImpl);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent + "], "
                    + "flags = [" + flags + "], startId = [" + startId + "]");
        }
        if (playerImpl.getPlayer() == null) {
            initPopup();
            initPopupCloseOverlay();
        }
        if (!playerImpl.isPlaying()) {
            playerImpl.getPlayer().setPlayWhenReady(true);
        }

        playerImpl.handleIntent(intent);

        return START_NOT_STICKY;
    }

    @Override
    public void onConfigurationChanged(final Configuration newConfig) {
        assureCorrectAppLanguage(this);
        if (DEBUG) {
            Log.d(TAG, "onConfigurationChanged() called with: "
                    + "newConfig = [" + newConfig + "]");
        }
        updateScreenSize();
        updatePopupSize(popupLayoutParams.width, -1);
        checkPopupPositionBounds();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "onDestroy() called");
        }
        closePopup();
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return mBinder;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @SuppressLint("RtlHardcoded")
    private void initPopup() {
        if (DEBUG) {
            Log.d(TAG, "initPopup() called");
        }
        View rootView = View.inflate(this, R.layout.player_popup, null);
        playerImpl.setup(rootView);

        tossFlingVelocity = PlayerHelper.getTossFlingVelocity(this);

        updateScreenSize();

        final boolean popupRememberSizeAndPos = PlayerHelper.isRememberingPopupDimensions(this);
        final float defaultSize = getResources().getDimension(R.dimen.popup_default_width);
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        popupWidth = popupRememberSizeAndPos
                ? sharedPreferences.getFloat(POPUP_SAVED_WIDTH, defaultSize) : defaultSize;

        final int layoutParamType = Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_PHONE
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

        popupLayoutParams = new WindowManager.LayoutParams(
                (int) popupWidth, (int) getMinimumVideoHeight(popupWidth),
                layoutParamType,
                IDLE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT);
        popupLayoutParams.gravity = Gravity.START | Gravity.TOP;
        popupLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        int centerX = (int) (screenWidth / 2f - popupWidth / 2f);
        int centerY = (int) (screenHeight / 2f - popupHeight / 2f);
        popupLayoutParams.x = popupRememberSizeAndPos
                ? sharedPreferences.getInt(POPUP_SAVED_X, centerX) : centerX;
        popupLayoutParams.y = popupRememberSizeAndPos
                ? sharedPreferences.getInt(POPUP_SAVED_Y, centerY) : centerY;

        checkPopupPositionBounds();

        PopupWindowGestureListener listener = new PopupWindowGestureListener();
        popupGestureDetector = new GestureDetector(this, listener);
        rootView.setOnTouchListener(listener);

        playerImpl.getLoadingPanel().setMinimumWidth(popupLayoutParams.width);
        playerImpl.getLoadingPanel().setMinimumHeight(popupLayoutParams.height);
        windowManager.addView(rootView, popupLayoutParams);
    }

    @SuppressLint("RtlHardcoded")
    private void initPopupCloseOverlay() {
        if (DEBUG) {
            Log.d(TAG, "initPopupCloseOverlay() called");
        }
        closeOverlayView = View.inflate(this, R.layout.player_popup_close_overlay, null);
        closeOverlayButton = closeOverlayView.findViewById(R.id.closeButton);

        final int layoutParamType = Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_PHONE
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        final int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

        WindowManager.LayoutParams closeOverlayLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                layoutParamType,
                flags,
                PixelFormat.TRANSLUCENT);
        closeOverlayLayoutParams.gravity = Gravity.START | Gravity.TOP;
        closeOverlayLayoutParams.softInputMode = WindowManager
                .LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        closeOverlayButton.setVisibility(View.GONE);
        windowManager.addView(closeOverlayView, closeOverlayLayoutParams);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Notification
    //////////////////////////////////////////////////////////////////////////*/

    private void resetNotification() {
        notBuilder = createNotification();
    }

    private NotificationCompat.Builder createNotification() {
        notRemoteView = new RemoteViews(BuildConfig.APPLICATION_ID,
                R.layout.player_popup_notification);

        notRemoteView.setTextViewText(R.id.notificationSongName, playerImpl.getVideoTitle());
        notRemoteView.setTextViewText(R.id.notificationArtist, playerImpl.getUploaderName());
        notRemoteView.setImageViewBitmap(R.id.notificationCover, playerImpl.getThumbnail());

        notRemoteView.setOnClickPendingIntent(R.id.notificationPlayPause,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_PLAY_PAUSE),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationStop,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_CLOSE),
                        PendingIntent.FLAG_UPDATE_CURRENT));
        notRemoteView.setOnClickPendingIntent(R.id.notificationRepeat,
                PendingIntent.getBroadcast(this, NOTIFICATION_ID, new Intent(ACTION_REPEAT),
                        PendingIntent.FLAG_UPDATE_CURRENT));

        // Starts popup player activity -- attempts to unlock lockscreen
        final Intent intent = NavigationHelper.getPopupPlayerActivityIntent(this);
        notRemoteView.setOnClickPendingIntent(R.id.notificationContent,
                PendingIntent.getActivity(this, NOTIFICATION_ID, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        setRepeatModeRemote(notRemoteView, playerImpl.getRepeatMode());

        NotificationCompat.Builder builder = new NotificationCompat
                .Builder(this, getString(R.string.notification_channel_id))
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContent(notRemoteView);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            builder.setPriority(NotificationCompat.PRIORITY_MAX);
        }
        return builder;
    }

    /**
     * Updates the notification, and the play/pause button in it.
     * Used for changes on the remoteView
     *
     * @param drawableId if != -1, sets the drawable with that id on the play/pause button
     */
    private void updateNotification(final int drawableId) {
        if (DEBUG) {
            Log.d(TAG, "updateNotification() called with: drawableId = [" + drawableId + "]");
        }
        if (notBuilder == null || notRemoteView == null) {
            return;
        }
        if (drawableId != -1) {
            notRemoteView.setImageViewResource(R.id.notificationPlayPause, drawableId);
        }
        notificationManager.notify(NOTIFICATION_ID, notBuilder.build());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Misc
    //////////////////////////////////////////////////////////////////////////*/

    public void closePopup() {
        if (DEBUG) {
            Log.d(TAG, "closePopup() called, isPopupClosing = " + isPopupClosing);
        }
        if (isPopupClosing) {
            return;
        }
        isPopupClosing = true;

        if (playerImpl != null) {
            playerImpl.savePlaybackState();
            if (playerImpl.getRootView() != null) {
                windowManager.removeView(playerImpl.getRootView());
            }
            playerImpl.setRootView(null);
            playerImpl.stopActivityBinding();
            playerImpl.destroy();
            playerImpl = null;
        }

        mBinder = null;
        if (lockManager != null) {
            lockManager.releaseWifiAndCpu();
        }
        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }

        animateOverlayAndFinishService();
    }

    private void animateOverlayAndFinishService() {
        final int targetTranslationY = (int) (closeOverlayButton.getRootView().getHeight()
                - closeOverlayButton.getY());

        closeOverlayButton.animate().setListener(null).cancel();
        closeOverlayButton.animate()
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
                        windowManager.removeView(closeOverlayView);

                        stopForeground(true);
                        stopSelf();
                    }
                }).start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * @see #checkPopupPositionBounds(float, float)
     * @return if the popup was out of bounds and have been moved back to it
     */
    @SuppressWarnings("UnusedReturnValue")
    private boolean checkPopupPositionBounds() {
        return checkPopupPositionBounds(screenWidth, screenHeight);
    }

    /**
     * Check if {@link #popupLayoutParams}' position is within a arbitrary boundary
     * that goes from (0, 0) to (boundaryWidth, boundaryHeight).
     * <p>
     * If it's out of these boundaries, {@link #popupLayoutParams}' position is changed
     * and {@code true} is returned to represent this change.
     * </p>
     *
     * @param boundaryWidth  width of the boundary
     * @param boundaryHeight height of the boundary
     * @return if the popup was out of bounds and have been moved back to it
     */
    private boolean checkPopupPositionBounds(final float boundaryWidth,
                                             final float boundaryHeight) {
        if (DEBUG) {
            Log.d(TAG, "checkPopupPositionBounds() called with: "
                    + "boundaryWidth = [" + boundaryWidth + "], "
                    + "boundaryHeight = [" + boundaryHeight + "]");
        }

        if (popupLayoutParams.x < 0) {
            popupLayoutParams.x = 0;
            return true;
        } else if (popupLayoutParams.x > boundaryWidth - popupLayoutParams.width) {
            popupLayoutParams.x = (int) (boundaryWidth - popupLayoutParams.width);
            return true;
        }

        if (popupLayoutParams.y < 0) {
            popupLayoutParams.y = 0;
            return true;
        } else if (popupLayoutParams.y > boundaryHeight - popupLayoutParams.height) {
            popupLayoutParams.y = (int) (boundaryHeight - popupLayoutParams.height);
            return true;
        }

        return false;
    }

    private void savePositionAndSize() {
        SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(PopupVideoPlayer.this);
        sharedPreferences.edit().putInt(POPUP_SAVED_X, popupLayoutParams.x).apply();
        sharedPreferences.edit().putInt(POPUP_SAVED_Y, popupLayoutParams.y).apply();
        sharedPreferences.edit().putFloat(POPUP_SAVED_WIDTH, popupLayoutParams.width).apply();
    }

    private float getMinimumVideoHeight(final float width) {
        final float height = width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
//        if (DEBUG) {
//            Log.d(TAG, "getMinimumVideoHeight() called with: width = [" + width + "], "
//                    + "returned: " + height);
//        }
        return height;
    }

    private void updateScreenSize() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        if (DEBUG) {
            Log.d(TAG, "updateScreenSize() called > screenWidth = " + screenWidth + ", "
                    + "screenHeight = " + screenHeight);
        }

        popupWidth = getResources().getDimension(R.dimen.popup_default_width);
        popupHeight = getMinimumVideoHeight(popupWidth);

        minimumWidth = getResources().getDimension(R.dimen.popup_minimum_width);
        minimumHeight = getMinimumVideoHeight(minimumWidth);

        maximumWidth = screenWidth;
        maximumHeight = screenHeight;
    }

    private void updatePopupSize(final int width, final int height) {
        if (playerImpl == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "updatePopupSize() called with: "
                    + "width = [" + width + "], height = [" + height + "]");
        }

        final int actualWidth = (int) (width > maximumWidth ? maximumWidth
                : width < minimumWidth ? minimumWidth : width);

        final int actualHeight;
        if (height == -1) {
            actualHeight = (int) getMinimumVideoHeight(width);
        } else {
            actualHeight = (int) (height > maximumHeight ? maximumHeight
                    : height < minimumHeight ? minimumHeight : height);
        }

        popupLayoutParams.width = actualWidth;
        popupLayoutParams.height = actualHeight;
        popupWidth = actualWidth;
        popupHeight = actualHeight;

        if (DEBUG) {
            Log.d(TAG, "updatePopupSize() updated values: "
                    + "width = [" + actualWidth + "], height = [" + actualHeight + "]");
        }
        windowManager.updateViewLayout(playerImpl.getRootView(), popupLayoutParams);
    }

    protected void setRepeatModeRemote(final RemoteViews remoteViews, final int repeatMode) {
        final String methodName = "setImageResource";

        if (remoteViews == null) {
            return;
        }

        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                remoteViews.setInt(R.id.notificationRepeat, methodName,
                        R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                remoteViews.setInt(R.id.notificationRepeat, methodName,
                        R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                remoteViews.setInt(R.id.notificationRepeat, methodName,
                        R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    private void updateWindowFlags(final int flags) {
        if (popupLayoutParams == null || windowManager == null || playerImpl == null) {
            return;
        }

        popupLayoutParams.flags = flags;
        windowManager.updateViewLayout(playerImpl.getRootView(), popupLayoutParams);
    }
    ///////////////////////////////////////////////////////////////////////////

    protected class VideoPlayerImpl extends VideoPlayer implements View.OnLayoutChangeListener {
        private TextView resizingIndicator;
        private ImageButton fullScreenButton;
        private ImageView videoPlayPause;

        private View extraOptionsView;
        private View closingOverlayView;

        VideoPlayerImpl(final Context context) {
            super("VideoPlayerImpl" + PopupVideoPlayer.TAG, context);
        }

        @Override
        public void handleIntent(final Intent intent) {
            super.handleIntent(intent);

            resetNotification();
            startForeground(NOTIFICATION_ID, notBuilder.build());
        }

        @Override
        public void initViews(final View view) {
            super.initViews(view);
            resizingIndicator = view.findViewById(R.id.resizing_indicator);
            fullScreenButton = view.findViewById(R.id.fullScreenButton);
            fullScreenButton.setOnClickListener(v -> onFullScreenButtonClicked());
            videoPlayPause = view.findViewById(R.id.videoPlayPause);

            extraOptionsView = view.findViewById(R.id.extraOptionsView);
            closingOverlayView = view.findViewById(R.id.closingOverlay);
            view.addOnLayoutChangeListener(this);
        }

        @Override
        public void initListeners() {
            super.initListeners();
            videoPlayPause.setOnClickListener(v -> onPlayPause());
        }

        @Override
        protected void setupSubtitleView(@NonNull final SubtitleView view, final float captionScale,
                                         @NonNull final CaptionStyleCompat captionStyle) {
            float captionRatio = (captionScale - 1f) / 5f + 1f;
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * captionRatio);
            view.setApplyEmbeddedStyles(captionStyle.equals(CaptionStyleCompat.DEFAULT));
            view.setStyle(captionStyle);
        }

        @Override
        public void onLayoutChange(final View view, final int left, final int top, final int right,
                                   final int bottom, final int oldLeft, final int oldTop,
                                   final int oldRight, final int oldBottom) {
            float widthDp = Math.abs(right - left) / getResources().getDisplayMetrics().density;
            final int visibility = widthDp > MINIMUM_SHOW_EXTRA_WIDTH_DP ? View.VISIBLE : View.GONE;
            extraOptionsView.setVisibility(visibility);
        }

        @Override
        public void destroy() {
            if (notRemoteView != null) {
                notRemoteView.setImageViewBitmap(R.id.notificationCover, null);
            }
            super.destroy();
        }

        @Override
        public void onFullScreenButtonClicked() {
            super.onFullScreenButtonClicked();

            if (DEBUG) {
                Log.d(TAG, "onFullScreenButtonClicked() called");
            }

            setRecovery();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    context,
                    MainVideoPlayer.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackSkipSilence(),
                    this.getPlaybackQuality(),
                    false,
                    !isPlaying(),
                    isMuted()
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            closePopup();
        }

        @Override
        public void onDismiss(final PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) {
                hideControls(500, 0);
            }
        }

        @Override
        protected int nextResizeMode(final int resizeMode) {
            if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FILL) {
                return AspectRatioFrameLayout.RESIZE_MODE_FIT;
            } else {
                return AspectRatioFrameLayout.RESIZE_MODE_FILL;
            }
        }

        @Override
        public void onStopTrackingTouch(final SeekBar seekBar) {
            super.onStopTrackingTouch(seekBar);
            if (wasPlaying()) {
                hideControls(100, 0);
            }
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            updatePlayback();
        }

        @Override
        public void onMuteUnmuteButtonClicked() {
            super.onMuteUnmuteButtonClicked();
            updatePlayback();
        }

        @Override
        public void onUpdateProgress(final int currentProgress, final int duration,
                                     final int bufferPercent) {
            updateProgress(currentProgress, duration, bufferPercent);
            super.onUpdateProgress(currentProgress, duration, bufferPercent);
        }

        @Override
        protected VideoPlaybackResolver.QualityResolver getQualityResolver() {
            return new VideoPlaybackResolver.QualityResolver() {
                @Override
                public int getDefaultResolutionIndex(final List<VideoStream> sortedVideos) {
                    return ListHelper.getPopupDefaultResolutionIndex(context, sortedVideos);
                }

                @Override
                public int getOverrideResolutionIndex(final List<VideoStream> sortedVideos,
                                                      final String playbackQuality) {
                    return ListHelper.getPopupResolutionIndex(context, sortedVideos,
                            playbackQuality);
                }
            };
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Thumbnail Loading
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onLoadingComplete(final String imageUri, final View view,
                                      final Bitmap loadedImage) {
            super.onLoadingComplete(imageUri, view, loadedImage);
            if (playerImpl == null) {
                return;
            }
            // rebuild notification here since remote view does not release bitmaps,
            // causing memory leaks
            resetNotification();
            updateNotification(-1);
        }

        @Override
        public void onLoadingFailed(final String imageUri, final View view,
                                    final FailReason failReason) {
            super.onLoadingFailed(imageUri, view, failReason);
            resetNotification();
            updateNotification(-1);
        }

        @Override
        public void onLoadingCancelled(final String imageUri, final View view) {
            super.onLoadingCancelled(imageUri, view);
            resetNotification();
            updateNotification(-1);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Activity Event Listener
        //////////////////////////////////////////////////////////////////////////*/

        /*package-private*/ void setActivityListener(final PlayerEventListener listener) {
            activityListener = listener;
            updateMetadata();
            updatePlayback();
            triggerProgressUpdate();
        }

        /*package-private*/ void removeActivityListener(final PlayerEventListener listener) {
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
                        playQueue.isShuffled(), simpleExoPlayer.getPlaybackParameters());
            }
        }

        private void updateProgress(final int currentProgress, final int duration,
                                    final int bufferPercent) {
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
        // ExoPlayer Video Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onRepeatModeChanged(final int i) {
            super.onRepeatModeChanged(i);
            setRepeatModeRemote(notRemoteView, i);
            updatePlayback();
            resetNotification();
            updateNotification(-1);
        }

        @Override
        public void onPlaybackParametersChanged(final PlaybackParameters playbackParameters) {
            super.onPlaybackParametersChanged(playbackParameters);
            updatePlayback();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
            super.onMetadataChanged(tag);
            resetNotification();
            updateNotification(-1);
            updateMetadata();
        }

        @Override
        public void onPlaybackShutdown() {
            super.onPlaybackShutdown();
            closePopup();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Broadcast Receiver
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        protected void setupBroadcastReceiver(final IntentFilter intentFltr) {
            super.setupBroadcastReceiver(intentFltr);
            if (DEBUG) {
                Log.d(TAG, "setupBroadcastReceiver() called with: "
                        + "intentFilter = [" + intentFltr + "]");
            }
            intentFltr.addAction(ACTION_CLOSE);
            intentFltr.addAction(ACTION_PLAY_PAUSE);
            intentFltr.addAction(ACTION_REPEAT);

            intentFltr.addAction(Intent.ACTION_SCREEN_ON);
            intentFltr.addAction(Intent.ACTION_SCREEN_OFF);
        }

        @Override
        public void onBroadcastReceived(final Intent intent) {
            super.onBroadcastReceived(intent);
            if (intent == null || intent.getAction() == null) {
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onBroadcastReceived() called with: intent = [" + intent + "]");
            }
            switch (intent.getAction()) {
                case ACTION_CLOSE:
                    closePopup();
                    break;
                case ACTION_PLAY_PAUSE:
                    onPlayPause();
                    break;
                case ACTION_REPEAT:
                    onRepeatClicked();
                    break;
                case Intent.ACTION_SCREEN_ON:
                    enableVideoRenderer(true);
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    enableVideoRenderer(false);
                    break;
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void changeState(final int state) {
            super.changeState(state);
            updatePlayback();
        }

        @Override
        public void onBlocked() {
            super.onBlocked();
            resetNotification();
            updateNotification(R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();

            updateWindowFlags(ONGOING_PLAYBACK_WINDOW_FLAGS);

            resetNotification();
            updateNotification(R.drawable.ic_pause_white);

            videoPlayPause.setBackgroundResource(R.drawable.ic_pause_white);
            hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);

            startForeground(NOTIFICATION_ID, notBuilder.build());
            lockManager.acquireWifiAndCpu();
        }

        @Override
        public void onBuffering() {
            super.onBuffering();
            resetNotification();
            updateNotification(R.drawable.ic_play_arrow_white);
        }

        @Override
        public void onPaused() {
            super.onPaused();

            updateWindowFlags(IDLE_WINDOW_FLAGS);

            resetNotification();
            updateNotification(R.drawable.ic_play_arrow_white);

            videoPlayPause.setBackgroundResource(R.drawable.ic_play_arrow_white);
            lockManager.releaseWifiAndCpu();

            stopForeground(false);
        }

        @Override
        public void onPausedSeek() {
            super.onPausedSeek();
            resetNotification();
            updateNotification(R.drawable.ic_play_arrow_white);

            videoPlayPause.setBackgroundResource(R.drawable.ic_pause_white);
        }

        @Override
        public void onCompleted() {
            super.onCompleted();

            updateWindowFlags(IDLE_WINDOW_FLAGS);

            resetNotification();
            updateNotification(R.drawable.ic_replay_white);

            videoPlayPause.setBackgroundResource(R.drawable.ic_replay_white);
            lockManager.releaseWifiAndCpu();

            stopForeground(false);
        }

        @Override
        public void showControlsThenHide() {
            videoPlayPause.setVisibility(View.VISIBLE);
            super.showControlsThenHide();
        }

        public void showControls(final long duration) {
            videoPlayPause.setVisibility(View.VISIBLE);
            super.showControls(duration);
        }

        public void hideControls(final long duration, final long delay) {
            super.hideControlsAndButton(duration, delay, videoPlayPause);
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        /*package-private*/ void enableVideoRenderer(final boolean enable) {
            final int videoRendererIndex = getRendererIndex(C.TRACK_TYPE_VIDEO);
            if (videoRendererIndex != RENDERER_UNAVAILABLE) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(videoRendererIndex, !enable));
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Getters
        //////////////////////////////////////////////////////////////////////////*/

        @SuppressWarnings("WeakerAccess")
        public TextView getResizingIndicator() {
            return resizingIndicator;
        }

        public View getClosingOverlayView() {
            return closingOverlayView;
        }
    }

    private class PopupWindowGestureListener extends GestureDetector.SimpleOnGestureListener
            implements View.OnTouchListener {
        private int initialPopupX;
        private int initialPopupY;
        private boolean isMoving;
        private boolean isResizing;

        //initial co-ordinates and distance between fingers
        private double initPointerDistance = -1;
        private float initFirstPointerX = -1;
        private float initFirstPointerY = -1;
        private float initSecPointerX = -1;
        private float initSecPointerY = -1;


        @Override
        public boolean onDoubleTap(final MotionEvent e) {
            if (DEBUG) {
                Log.d(TAG, "onDoubleTap() called with: e = [" + e + "], "
                        + "rawXy = " + e.getRawX() + ", " + e.getRawY()
                        + ", xy = " + e.getX() + ", " + e.getY());
            }
            if (playerImpl == null || !playerImpl.isPlaying()) {
                return false;
            }

            playerImpl.hideControls(0, 0);

            if (e.getX() > popupWidth / 2) {
                playerImpl.onFastForward();
            } else {
                playerImpl.onFastRewind();
            }

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(final MotionEvent e) {
            if (DEBUG) {
                Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            }
            if (playerImpl == null || playerImpl.getPlayer() == null) {
                return false;
            }
            if (playerImpl.isControlsVisible()) {
                playerImpl.hideControls(100, 100);
            } else {
                playerImpl.showControlsThenHide();

            }
            return true;
        }

        @Override
        public boolean onDown(final MotionEvent e) {
            if (DEBUG) {
                Log.d(TAG, "onDown() called with: e = [" + e + "]");
            }

            // Fix popup position when the user touch it, it may have the wrong one
            // because the soft input is visible (the draggable area is currently resized).
            checkPopupPositionBounds(closeOverlayView.getWidth(), closeOverlayView.getHeight());

            initialPopupX = popupLayoutParams.x;
            initialPopupY = popupLayoutParams.y;
            popupWidth = popupLayoutParams.width;
            popupHeight = popupLayoutParams.height;
            return super.onDown(e);
        }

        @Override
        public void onLongPress(final MotionEvent e) {
            if (DEBUG) {
                Log.d(TAG, "onLongPress() called with: e = [" + e + "]");
            }
            updateScreenSize();
            checkPopupPositionBounds();
            updatePopupSize((int) screenWidth, -1);
        }

        @Override
        public boolean onScroll(final MotionEvent initialEvent, final MotionEvent movingEvent,
                                final float distanceX, final float distanceY) {
            if (isResizing || playerImpl == null) {
                return super.onScroll(initialEvent, movingEvent, distanceX, distanceY);
            }

            if (!isMoving) {
                animateView(closeOverlayButton, true, 200);
            }

            isMoving = true;

            float diffX = (int) (movingEvent.getRawX() - initialEvent.getRawX());
            float posX = (int) (initialPopupX + diffX);
            float diffY = (int) (movingEvent.getRawY() - initialEvent.getRawY());
            float posY = (int) (initialPopupY + diffY);

            if (posX > (screenWidth - popupWidth)) {
                posX = (int) (screenWidth - popupWidth);
            } else if (posX < 0) {
                posX = 0;
            }

            if (posY > (screenHeight - popupHeight)) {
                posY = (int) (screenHeight - popupHeight);
            } else if (posY < 0) {
                posY = 0;
            }

            popupLayoutParams.x = (int) posX;
            popupLayoutParams.y = (int) posY;

            final View closingOverlayView = playerImpl.getClosingOverlayView();
            if (isInsideClosingRadius(movingEvent)) {
                if (closingOverlayView.getVisibility() == View.GONE) {
                    animateView(closingOverlayView, true, 250);
                }
            } else {
                if (closingOverlayView.getVisibility() == View.VISIBLE) {
                    animateView(closingOverlayView, false, 0);
                }
            }

//            if (DEBUG) {
//                Log.d(TAG, "PopupVideoPlayer.onScroll = "
//                        + "e1.getRaw = [" + initialEvent.getRawX() + ", "
//                        + initialEvent.getRawY() + "], "
//                        + "e1.getX,Y = [" + initialEvent.getX() + ", "
//                        + initialEvent.getY() + "], "
//                        + "e2.getRaw = [" + movingEvent.getRawX() + ", "
//                        + movingEvent.getRawY() + "], "
//                        + "e2.getX,Y = [" + movingEvent.getX() + ", " + movingEvent.getY() + "], "
//                        + "distanceX,Y = [" + distanceX + ", " + distanceY + "], "
//                        + "posX,Y = [" + posX + ", " + posY + "], "
//                        + "popupW,H = [" + popupWidth + " x " + popupHeight + "]");
//            }
            windowManager.updateViewLayout(playerImpl.getRootView(), popupLayoutParams);
            return true;
        }

        private void onScrollEnd(final MotionEvent event) {
            if (DEBUG) {
                Log.d(TAG, "onScrollEnd() called");
            }
            if (playerImpl == null) {
                return;
            }
            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == STATE_PLAYING) {
                playerImpl.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
            }

            if (isInsideClosingRadius(event)) {
                closePopup();
            } else {
                animateView(playerImpl.getClosingOverlayView(), false, 0);

                if (!isPopupClosing) {
                    animateView(closeOverlayButton, false, 200);
                }
            }
        }

        @Override
        public boolean onFling(final MotionEvent e1, final MotionEvent e2,
                               final float velocityX, final float velocityY) {
            if (DEBUG) {
                Log.d(TAG, "Fling velocity: dX=[" + velocityX + "], dY=[" + velocityY + "]");
            }
            if (playerImpl == null) {
                return false;
            }

            final float absVelocityX = Math.abs(velocityX);
            final float absVelocityY = Math.abs(velocityY);
            if (Math.max(absVelocityX, absVelocityY) > tossFlingVelocity) {
                if (absVelocityX > tossFlingVelocity) {
                    popupLayoutParams.x = (int) velocityX;
                }
                if (absVelocityY > tossFlingVelocity) {
                    popupLayoutParams.y = (int) velocityY;
                }
                checkPopupPositionBounds();
                windowManager.updateViewLayout(playerImpl.getRootView(), popupLayoutParams);
                return true;
            }
            return false;
        }

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            popupGestureDetector.onTouchEvent(event);
            if (playerImpl == null) {
                return false;
            }
            if (event.getPointerCount() == 2 && !isMoving && !isResizing) {
                if (DEBUG) {
                    Log.d(TAG, "onTouch() 2 finger pointer detected, enabling resizing.");
                }
                playerImpl.showAndAnimateControl(-1, true);
                playerImpl.getLoadingPanel().setVisibility(View.GONE);

                playerImpl.hideControls(0, 0);
                animateView(playerImpl.getCurrentDisplaySeek(), false, 0, 0);
                animateView(playerImpl.getResizingIndicator(), true, 200, 0);

                //record co-ordinates of fingers
                initFirstPointerX = event.getX(0);
                initFirstPointerY = event.getY(0);
                initSecPointerX = event.getX(1);
                initSecPointerY = event.getY(1);
                //record distance between fingers
                initPointerDistance = Math.hypot(initFirstPointerX - initSecPointerX,
                                                 initFirstPointerY - initSecPointerY);

                isResizing = true;
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE && !isMoving && isResizing) {
                if (DEBUG) {
                    Log.d(TAG, "onTouch() ACTION_MOVE > v = [" + v + "], "
                            + "e1.getRaw = [" + event.getRawX() + ", " + event.getRawY() + "]");
                }
                return handleMultiDrag(event);
            }

            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (DEBUG) {
                    Log.d(TAG, "onTouch() ACTION_UP > v = [" + v + "], "
                            + "e1.getRaw = [" + event.getRawX() + ", " + event.getRawY() + "]");
                }
                if (isMoving) {
                    isMoving = false;
                    onScrollEnd(event);
                }

                if (isResizing) {
                    isResizing = false;

                    initPointerDistance = -1;
                    initFirstPointerX = -1;
                    initFirstPointerY = -1;
                    initSecPointerX = -1;
                    initSecPointerY = -1;

                    animateView(playerImpl.getResizingIndicator(), false, 100, 0);
                    playerImpl.changeState(playerImpl.getCurrentState());
                }

                if (!isPopupClosing) {
                    savePositionAndSize();
                }
            }

            v.performClick();
            return true;
        }

        private boolean handleMultiDrag(final MotionEvent event) {
            if (initPointerDistance != -1 && event.getPointerCount() == 2) {
                // get the movements of the fingers
                double firstPointerMove = Math.hypot(event.getX(0) - initFirstPointerX,
                                                     event.getY(0) - initFirstPointerY);
                double secPointerMove = Math.hypot(event.getX(1) - initSecPointerX,
                                                   event.getY(1) - initSecPointerY);

                // minimum threshold beyond which pinch gesture will work
                int minimumMove = ViewConfiguration.get(PopupVideoPlayer.this).getScaledTouchSlop();

                if (Math.max(firstPointerMove, secPointerMove) > minimumMove) {
                    // calculate current distance between the pointers
                    double currentPointerDistance =
                            Math.hypot(event.getX(0) - event.getX(1),
                                       event.getY(0) - event.getY(1));

                    // change co-ordinates of popup so the center stays at the same position
                    double newWidth = (popupWidth * currentPointerDistance / initPointerDistance);
                    initPointerDistance = currentPointerDistance;
                    popupLayoutParams.x += (popupWidth - newWidth) / 2;

                    checkPopupPositionBounds();
                    updateScreenSize();

                    updatePopupSize((int) Math.min(screenWidth, newWidth), -1);
                    return true;
                }
            }
            return false;
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        private int distanceFromCloseButton(final MotionEvent popupMotionEvent) {
            final int closeOverlayButtonX = closeOverlayButton.getLeft()
                    + closeOverlayButton.getWidth() / 2;
            final int closeOverlayButtonY = closeOverlayButton.getTop()
                    + closeOverlayButton.getHeight() / 2;

            float fingerX = popupLayoutParams.x + popupMotionEvent.getX();
            float fingerY = popupLayoutParams.y + popupMotionEvent.getY();

            return (int) Math.sqrt(Math.pow(closeOverlayButtonX - fingerX, 2)
                    + Math.pow(closeOverlayButtonY - fingerY, 2));
        }

        private float getClosingRadius() {
            final int buttonRadius = closeOverlayButton.getWidth() / 2;
            // 20% wider than the button itself
            return buttonRadius * 1.2f;
        }

        private boolean isInsideClosingRadius(final MotionEvent popupMotionEvent) {
            return distanceFromCloseButton(popupMotionEvent) <= getClosingRadius();
        }
    }
}
