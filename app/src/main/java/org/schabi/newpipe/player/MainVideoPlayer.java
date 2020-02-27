/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * MainVideoPlayer.java is part of NewPipe
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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Settings;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.player.helper.PlaybackParameterDialog;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItemBuilder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemHolder;
import org.schabi.newpipe.player.playqueue.PlayQueueItemTouchCallback;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.KoreUtil;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ShareUtils;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;
import java.util.Queue;
import java.util.UUID;

import static org.schabi.newpipe.player.BasePlayer.STATE_PLAYING;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_DURATION;
import static org.schabi.newpipe.player.VideoPlayer.DEFAULT_CONTROLS_HIDE_TIME;
import static org.schabi.newpipe.util.AnimationUtils.Type.SCALE_AND_ALPHA;
import static org.schabi.newpipe.util.AnimationUtils.Type.SLIDE_AND_ALPHA;
import static org.schabi.newpipe.util.AnimationUtils.animateRotation;
import static org.schabi.newpipe.util.AnimationUtils.animateView;
import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;
import static org.schabi.newpipe.util.StateSaver.KEY_SAVED_STATE;

/**
 * Activity Player implementing VideoPlayer
 *
 * @author mauriciocolli
 */
public final class MainVideoPlayer extends AppCompatActivity
        implements StateSaver.WriteRead, PlaybackParameterDialog.Callback {
    private static final String TAG = ".MainVideoPlayer";
    private static final boolean DEBUG = BasePlayer.DEBUG;

    private GestureDetector gestureDetector;

    private VideoPlayerImpl playerImpl;

    private SharedPreferences defaultPreferences;

    @Nullable private PlayerState playerState;
    private boolean isInMultiWindow;
    private boolean isBackPressed;

    private ContentObserver rotationObserver;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceState);
        if (DEBUG) Log.d(TAG, "onCreate() called with: savedInstanceState = [" + savedInstanceState + "]");
        defaultPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        ThemeHelper.setTheme(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) getWindow().setStatusBarColor(Color.BLACK);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = PlayerHelper.getScreenBrightness(getApplicationContext());
        getWindow().setAttributes(lp);

        hideSystemUi();
        setContentView(R.layout.activity_main_player);
        playerImpl = new  VideoPlayerImpl(this);
        playerImpl.setup(findViewById(android.R.id.content));

        if (savedInstanceState != null && savedInstanceState.get(KEY_SAVED_STATE) != null) {
            return; // We have saved states, stop here to restore it
        }

        final Intent intent = getIntent();
        if (intent != null) {
            playerImpl.handleIntent(intent);
        } else {
            Toast.makeText(this, R.string.general_error, Toast.LENGTH_SHORT).show();
            finish();
        }

        rotationObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (globalScreenOrientationLocked()) {
                    final boolean lastOrientationWasLandscape = defaultPreferences.getBoolean(
                            getString(R.string.last_orientation_landscape_key), false);
                    setLandscape(lastOrientationWasLandscape);
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
        };
        getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false, rotationObserver);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle bundle) {
        if (DEBUG) Log.d(TAG, "onRestoreInstanceState() called");
        super.onRestoreInstanceState(bundle);
        StateSaver.tryToRestore(bundle, this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (DEBUG) Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        super.onNewIntent(intent);
        if (intent != null) {
            playerState = null;
            playerImpl.handleIntent(intent);
        }
    }

    @Override
    protected void onResume() {
        if (DEBUG) Log.d(TAG, "onResume() called");
        assureCorrectAppLanguage(this);
        super.onResume();

        if (globalScreenOrientationLocked()) {
            boolean lastOrientationWasLandscape = defaultPreferences.getBoolean(
                    getString(R.string.last_orientation_landscape_key), false);
            setLandscape(lastOrientationWasLandscape);
        }

        final int lastResizeMode = defaultPreferences.getInt(
                getString(R.string.last_resize_mode), AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerImpl.setResizeMode(lastResizeMode);

        // Upon going in or out of multiwindow mode, isInMultiWindow will always be false,
        // since the first onResume needs to restore the player.
        // Subsequent onResume calls while multiwindow mode remains the same and the player is
        // prepared should be ignored.
        if (isInMultiWindow) return;
        isInMultiWindow = isInMultiWindow();

        if (playerState != null) {
            playerImpl.setPlaybackQuality(playerState.getPlaybackQuality());
            playerImpl.initPlayback(playerState.getPlayQueue(), playerState.getRepeatMode(),
                    playerState.getPlaybackSpeed(), playerState.getPlaybackPitch(),
                    playerState.isPlaybackSkipSilence(), playerState.wasPlaying());
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        assureCorrectAppLanguage(this);

        if (playerImpl.isSomePopupMenuVisible()) {
            playerImpl.getQualityPopupMenu().dismiss();
            playerImpl.getPlaybackSpeedPopupMenu().dismiss();
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        isBackPressed = true;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (DEBUG) Log.d(TAG, "onSaveInstanceState() called");
        super.onSaveInstanceState(outState);
        if (playerImpl == null) return;

        playerImpl.setRecovery();
        if(!playerImpl.gotDestroyed()) {
            playerState = createPlayerState();
        }
        StateSaver.tryToSave(isChangingConfigurations(), null, outState, this);
    }

    @Override
    protected void onStop() {
        if (DEBUG) Log.d(TAG, "onStop() called");
        super.onStop();
        PlayerHelper.setScreenBrightness(getApplicationContext(),
                getWindow().getAttributes().screenBrightness);

        if (playerImpl == null) return;
        if (!isBackPressed) {
            playerImpl.minimize();
        }
        playerState = createPlayerState();
        playerImpl.destroy();

        if (rotationObserver != null)
            getContentResolver().unregisterContentObserver(rotationObserver);

        isInMultiWindow = false;
        isBackPressed = false;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(newBase));
    }

    @Override
    protected void onPause() {
        playerImpl.savePlaybackState();
        super.onPause();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    private PlayerState createPlayerState() {
        return new PlayerState(playerImpl.getPlayQueue(), playerImpl.getRepeatMode(),
                playerImpl.getPlaybackSpeed(), playerImpl.getPlaybackPitch(),
                playerImpl.getPlaybackQuality(), playerImpl.getPlaybackSkipSilence(),
                playerImpl.isPlaying());
    }

    @Override
    public String generateSuffix() {
        return "." + UUID.randomUUID().toString() + ".player";
    }

    @Override
    public void writeTo(Queue<Object> objectsToSave) {
        if (objectsToSave == null) return;
        objectsToSave.add(playerState);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull Queue<Object> savedObjects) {
        playerState = (PlayerState) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // View
    //////////////////////////////////////////////////////////////////////////*/

    private void showSystemUi() {
        if (DEBUG) Log.d(TAG, "showSystemUi() called");
        if (playerImpl != null && playerImpl.queueVisible) return;

        final int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            @ColorInt final int systenUiColor =
                    ActivityCompat.getColor(getApplicationContext(), R.color.video_overlay_color);
            getWindow().setStatusBarColor(systenUiColor);
            getWindow().setNavigationBarColor(systenUiColor);
        }

        getWindow().getDecorView().setSystemUiVisibility(visibility);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void hideSystemUi() {
        if (DEBUG) Log.d(TAG, "hideSystemUi() called");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            getWindow().getDecorView().setSystemUiVisibility(visibility);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    private void toggleOrientation() {
        setLandscape(!isLandscape());
        defaultPreferences.edit()
                .putBoolean(getString(R.string.last_orientation_landscape_key), !isLandscape())
                .apply();
    }

    private boolean isLandscape() {
        return getResources().getDisplayMetrics().heightPixels < getResources().getDisplayMetrics().widthPixels;
    }

    private void setLandscape(boolean v) {
        setRequestedOrientation(v
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
    }

    private boolean globalScreenOrientationLocked() {
        // 1: Screen orientation changes using accelerometer
        // 0: Screen orientation is locked
        return !(android.provider.Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1);
    }

    protected void setRepeatModeButton(final ImageButton imageButton, final int repeatMode) {
        switch (repeatMode) {
            case Player.REPEAT_MODE_OFF:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_off);
                break;
            case Player.REPEAT_MODE_ONE:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_one);
                break;
            case Player.REPEAT_MODE_ALL:
                imageButton.setImageResource(R.drawable.exo_controls_repeat_all);
                break;
        }
    }

    protected void setShuffleButton(final ImageButton shuffleButton, final boolean shuffled) {
        final int shuffleAlpha = shuffled ? 255 : 77;
        shuffleButton.setImageAlpha(shuffleAlpha);
    }

    private boolean isInMultiWindow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInMultiWindowMode();
    }

    ////////////////////////////////////////////////////////////////////////////
    // Playback Parameters Listener
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPlaybackParameterChanged(float playbackTempo, float playbackPitch,
                                           boolean playbackSkipSilence) {
        if (playerImpl != null) {
            playerImpl.setPlaybackParameters(playbackTempo, playbackPitch, playbackSkipSilence);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings({"unused", "WeakerAccess"})
    private class VideoPlayerImpl extends VideoPlayer {
        private final float MAX_GESTURE_LENGTH = 0.75f;

        private TextView titleTextView;
        private TextView channelTextView;
        private RelativeLayout volumeRelativeLayout;
        private ProgressBar volumeProgressBar;
        private ImageView volumeImageView;
        private RelativeLayout brightnessRelativeLayout;
        private ProgressBar brightnessProgressBar;
        private ImageView brightnessImageView;
        private ImageButton queueButton;
        private ImageButton repeatButton;
        private ImageButton shuffleButton;

        private ImageButton playPauseButton;
        private ImageButton playPreviousButton;
        private ImageButton playNextButton;
        private Button closeButton;

        private RelativeLayout queueLayout;
        private ImageButton itemsListCloseButton;
        private RecyclerView itemsList;
        private ItemTouchHelper itemTouchHelper;

        private boolean queueVisible;

        private ImageButton moreOptionsButton;
        private ImageButton kodiButton;
        private ImageButton shareButton;
        private ImageButton toggleOrientationButton;
        private ImageButton switchPopupButton;
        private ImageButton switchBackgroundButton;

        private RelativeLayout windowRootLayout;
        private View secondaryControls;

        private int maxGestureLength;

        VideoPlayerImpl(final Context context) {
            super("VideoPlayerImpl" + MainVideoPlayer.TAG, context);
        }

        @Override
        public void initViews(View rootView) {
            super.initViews(rootView);
            this.titleTextView = rootView.findViewById(R.id.titleTextView);
            this.channelTextView = rootView.findViewById(R.id.channelTextView);
            this.volumeRelativeLayout = rootView.findViewById(R.id.volumeRelativeLayout);
            this.volumeProgressBar = rootView.findViewById(R.id.volumeProgressBar);
            this.volumeImageView = rootView.findViewById(R.id.volumeImageView);
            this.brightnessRelativeLayout = rootView.findViewById(R.id.brightnessRelativeLayout);
            this.brightnessProgressBar = rootView.findViewById(R.id.brightnessProgressBar);
            this.brightnessImageView = rootView.findViewById(R.id.brightnessImageView);
            this.queueButton = rootView.findViewById(R.id.queueButton);
            this.repeatButton = rootView.findViewById(R.id.repeatButton);
            this.shuffleButton = rootView.findViewById(R.id.shuffleButton);

            this.playPauseButton = rootView.findViewById(R.id.playPauseButton);
            this.playPreviousButton = rootView.findViewById(R.id.playPreviousButton);
            this.playNextButton = rootView.findViewById(R.id.playNextButton);
            this.closeButton = rootView.findViewById(R.id.closeButton);

            this.moreOptionsButton = rootView.findViewById(R.id.moreOptionsButton);
            this.secondaryControls = rootView.findViewById(R.id.secondaryControls);
            this.kodiButton = rootView.findViewById(R.id.kodi);
            this.shareButton = rootView.findViewById(R.id.share);
            this.toggleOrientationButton = rootView.findViewById(R.id.toggleOrientation);
            this.switchBackgroundButton = rootView.findViewById(R.id.switchBackground);
            this.switchPopupButton = rootView.findViewById(R.id.switchPopup);

            this.queueLayout = findViewById(R.id.playQueuePanel);
            this.itemsListCloseButton = findViewById(R.id.playQueueClose);
            this.itemsList = findViewById(R.id.playQueue);

            titleTextView.setSelected(true);
            channelTextView.setSelected(true);
            boolean showKodiButton = PreferenceManager.getDefaultSharedPreferences(this.context).getBoolean(
					this.context.getString(R.string.show_play_with_kodi_key), false);
            kodiButton.setVisibility(showKodiButton ? View.VISIBLE : View.GONE);

            getRootView().setKeepScreenOn(true);
        }

        @Override
        protected void setupSubtitleView(@NonNull SubtitleView view,
                                         final float captionScale,
                                         @NonNull final CaptionStyleCompat captionStyle) {
            final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
            final int minimumLength = Math.min(metrics.heightPixels, metrics.widthPixels);
            final float captionRatioInverse = 20f + 4f * (1f - captionScale);
            view.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) minimumLength / captionRatioInverse);
            view.setApplyEmbeddedStyles(captionStyle.equals(CaptionStyleCompat.DEFAULT));
            view.setStyle(captionStyle);
        }

        @Override
        public void initListeners() {
            super.initListeners();

            PlayerGestureListener listener = new PlayerGestureListener();
            gestureDetector = new GestureDetector(context, listener);
            gestureDetector.setIsLongpressEnabled(false);
            getRootView().setOnTouchListener(listener);

            queueButton.setOnClickListener(this);
            repeatButton.setOnClickListener(this);
            shuffleButton.setOnClickListener(this);

            playPauseButton.setOnClickListener(this);
            playPreviousButton.setOnClickListener(this);
            playNextButton.setOnClickListener(this);
            closeButton.setOnClickListener(this);

            moreOptionsButton.setOnClickListener(this);
            kodiButton.setOnClickListener(this);
            shareButton.setOnClickListener(this);
            toggleOrientationButton.setOnClickListener(this);
            switchBackgroundButton.setOnClickListener(this);
            switchPopupButton.setOnClickListener(this);

            getRootView().addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
                if (l != ol || t != ot || r != or || b != ob) {
                    // Use smaller value to be consistent between screen orientations
                    // (and to make usage easier)
                    int width = r - l, height = b - t;
                    maxGestureLength = (int) (Math.min(width, height) * MAX_GESTURE_LENGTH);

                    if (DEBUG) Log.d(TAG, "maxGestureLength = " + maxGestureLength);

                    volumeProgressBar.setMax(maxGestureLength);
                    brightnessProgressBar.setMax(maxGestureLength);

                    setInitialGestureValues();
                }
            });

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                queueLayout.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
                    @Override
                    public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                        final DisplayCutout cutout = windowInsets.getDisplayCutout();
                        if (cutout != null)
                            view.setPadding(cutout.getSafeInsetLeft(), cutout.getSafeInsetTop(),
                                    cutout.getSafeInsetRight(), cutout.getSafeInsetBottom());
                        return windowInsets;
                    }
                });
            }
        }

        public void minimize() {
            switch (PlayerHelper.getMinimizeOnExitAction(context)) {
                case PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND:
                    onPlayBackgroundButtonClicked();
                    break;
                case PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP:
                    onFullScreenButtonClicked();
                    break;
                case PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE:
                default:
                    // No action
                    break;
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // ExoPlayer Video Listener
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onRepeatModeChanged(int i) {
            super.onRepeatModeChanged(i);
            updatePlaybackButtons();
        }

        @Override
        public void onShuffleClicked() {
            super.onShuffleClicked();
            updatePlaybackButtons();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Playback Listener
        //////////////////////////////////////////////////////////////////////////*/

        protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
            super.onMetadataChanged(tag);

            titleTextView.setText(tag.getMetadata().getName());
            channelTextView.setText(tag.getMetadata().getUploaderName());
        }

        @Override
        public void onPlaybackShutdown() {
            super.onPlaybackShutdown();
            finish();
        }

        public void onKodiShare() {
            onPause();
            try {
                NavigationHelper.playWithKore(this.context, Uri.parse(
                        playerImpl.getVideoUrl().replace("https", "http")));
            } catch (Exception e) {
                if (DEBUG) Log.i(TAG, "Failed to start kore", e);
                KoreUtil.showInstallKoreDialog(this.context);
            }
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Player Overrides
        //////////////////////////////////////////////////////////////////////////*/

        @Override
        public void onFullScreenButtonClicked() {
            super.onFullScreenButtonClicked();

            if (DEBUG) Log.d(TAG, "onFullScreenButtonClicked() called");
            if (simpleExoPlayer == null) return;

            if (!PermissionHelper.isPopupEnabled(context)) {
                PermissionHelper.showPopupEnablementToast(context);
                return;
            }

            setRecovery();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    context,
                    PopupVideoPlayer.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackSkipSilence(),
                    this.getPlaybackQuality(),
                    false,
                    !isPlaying()
            );
            context.startService(intent);

            ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
            destroy();
            finish();
        }

        public void onPlayBackgroundButtonClicked() {
            if (DEBUG) Log.d(TAG, "onPlayBackgroundButtonClicked() called");
            if (playerImpl.getPlayer() == null) return;

            setRecovery();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    context,
                    BackgroundPlayer.class,
                    this.getPlayQueue(),
                    this.getRepeatMode(),
                    this.getPlaybackSpeed(),
                    this.getPlaybackPitch(),
                    this.getPlaybackSkipSilence(),
                    this.getPlaybackQuality(),
                    false,
                    !isPlaying()
            );
            context.startService(intent);

            ((View) getControlAnimationView().getParent()).setVisibility(View.GONE);
            destroy();
            finish();
        }


        @Override
        public void onClick(View v) {
            super.onClick(v);
            if (v.getId() == playPauseButton.getId()) {
                onPlayPause();

            } else if (v.getId() == playPreviousButton.getId()) {
                onPlayPrevious();

            } else if (v.getId() == playNextButton.getId()) {
                onPlayNext();

            } else if (v.getId() == queueButton.getId()) {
                onQueueClicked();
                return;
            } else if (v.getId() == repeatButton.getId()) {
                onRepeatClicked();
                return;
            } else if (v.getId() == shuffleButton.getId()) {
                onShuffleClicked();
                return;
            } else if (v.getId() == moreOptionsButton.getId()) {
                onMoreOptionsClicked();

            } else if (v.getId() == shareButton.getId()) {
                onShareClicked();

            } else if (v.getId() == toggleOrientationButton.getId()) {
                onScreenRotationClicked();

            } else if (v.getId() == switchPopupButton.getId()) {
                onFullScreenButtonClicked();

            } else if (v.getId() == switchBackgroundButton.getId()) {
                onPlayBackgroundButtonClicked();

            } else if (v.getId() == closeButton.getId()) {
                onPlaybackShutdown();
                return;
            } else if (v.getId() == kodiButton.getId()) {
            	onKodiShare();
            }

            if (getCurrentState() != STATE_COMPLETED) {
                getControlsVisibilityHandler().removeCallbacksAndMessages(null);
                animateView(getControlsRoot(), true, DEFAULT_CONTROLS_DURATION, 0, () -> {
                    if (getCurrentState() == STATE_PLAYING && !isSomePopupMenuVisible()) {
                        hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
                    }
                });
            }
        }

        private void onQueueClicked() {
            queueVisible = true;
            hideSystemUi();

            buildQueue();
            updatePlaybackButtons();

            getControlsRoot().setVisibility(View.INVISIBLE);
            animateView(queueLayout, SLIDE_AND_ALPHA, /*visible=*/true,
                    DEFAULT_CONTROLS_DURATION);

            itemsList.scrollToPosition(playQueue.getIndex());
        }

        private void onQueueClosed() {
            animateView(queueLayout, SLIDE_AND_ALPHA, /*visible=*/false,
                    DEFAULT_CONTROLS_DURATION);
            queueVisible = false;
        }

        private void onMoreOptionsClicked() {
            if (DEBUG) Log.d(TAG, "onMoreOptionsClicked() called");

            final boolean isMoreControlsVisible = secondaryControls.getVisibility() == View.VISIBLE;

            animateRotation(moreOptionsButton, DEFAULT_CONTROLS_DURATION,
                    isMoreControlsVisible ? 0 : 180);
            animateView(secondaryControls, SLIDE_AND_ALPHA, !isMoreControlsVisible,
                    DEFAULT_CONTROLS_DURATION);
            showControls(DEFAULT_CONTROLS_DURATION);
        }

        private void onShareClicked() {
            // share video at the current time (youtube.com/watch?v=ID&t=SECONDS)
            ShareUtils.shareUrl(MainVideoPlayer.this,
                    playerImpl.getVideoTitle(),
                    playerImpl.getVideoUrl() + "&t=" + String.valueOf(playerImpl.getPlaybackSeekBar().getProgress()/1000));
        }

        private void onScreenRotationClicked() {
            if (DEBUG) Log.d(TAG, "onScreenRotationClicked() called");
            toggleOrientation();
            showControlsThenHide();
        }

        @Override
        public void onPlaybackSpeedClicked() {
            PlaybackParameterDialog
                    .newInstance(getPlaybackSpeed(), getPlaybackPitch(), getPlaybackSkipSilence())
                    .show(getSupportFragmentManager(), TAG);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            super.onStopTrackingTouch(seekBar);
            if (wasPlaying()) showControlsThenHide();
        }

        @Override
        public void onDismiss(PopupMenu menu) {
            super.onDismiss(menu);
            if (isPlaying()) hideControls(DEFAULT_CONTROLS_DURATION, 0);
            hideSystemUi();
        }

        @Override
        protected int nextResizeMode(int currentResizeMode) {
            final int newResizeMode;
            switch (currentResizeMode) {
                case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                    newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                    break;
                case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                    newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                    break;
                default:
                    newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                    break;
            }

            storeResizeMode(newResizeMode);
            return newResizeMode;
        }

        private void storeResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode) {
            defaultPreferences.edit()
                    .putInt(getString(R.string.last_resize_mode), resizeMode)
                    .apply();
        }

        @Override
        protected VideoPlaybackResolver.QualityResolver getQualityResolver() {
            return new VideoPlaybackResolver.QualityResolver() {
                @Override
                public int getDefaultResolutionIndex(List<VideoStream> sortedVideos) {
                    return ListHelper.getDefaultResolutionIndex(context, sortedVideos);
                }

                @Override
                public int getOverrideResolutionIndex(List<VideoStream> sortedVideos,
                                                      String playbackQuality) {
                    return ListHelper.getResolutionIndex(context, sortedVideos, playbackQuality);
                }
            };
        }

        /*//////////////////////////////////////////////////////////////////////////
        // States
        //////////////////////////////////////////////////////////////////////////*/

        private void animatePlayButtons(final boolean show, final int duration) {
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
            animateView(playPreviousButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
            animateView(playNextButton, AnimationUtils.Type.SCALE_AND_ALPHA, show, duration);
        }

        @Override
        public void onBlocked() {
            super.onBlocked();
            playPauseButton.setImageResource(R.drawable.ic_pause_white);
            animatePlayButtons(false, 100);
            animateView(closeButton, false, DEFAULT_CONTROLS_DURATION);
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onBuffering() {
            super.onBuffering();
            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onPlaying() {
            super.onPlaying();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_pause_white);
                animatePlayButtons(true, 200);
                animateView(closeButton, false, DEFAULT_CONTROLS_DURATION);
            });

            getRootView().setKeepScreenOn(true);
        }

        @Override
        public void onPaused() {
            super.onPaused();
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 80, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_play_arrow_white);
                animatePlayButtons(true, 200);
                animateView(closeButton, false, DEFAULT_CONTROLS_DURATION);
            });

            showSystemUi();
            getRootView().setKeepScreenOn(false);
        }

        @Override
        public void onPausedSeek() {
            super.onPausedSeek();
            animatePlayButtons(false, 100);
            getRootView().setKeepScreenOn(true);
        }


        @Override
        public void onCompleted() {
            animateView(playPauseButton, AnimationUtils.Type.SCALE_AND_ALPHA, false, 0, 0, () -> {
                playPauseButton.setImageResource(R.drawable.ic_replay_white);
                animatePlayButtons(true, DEFAULT_CONTROLS_DURATION);
                animateView(closeButton, true, DEFAULT_CONTROLS_DURATION);
            });
            getRootView().setKeepScreenOn(false);
            super.onCompleted();
        }

        /*//////////////////////////////////////////////////////////////////////////
        // Utils
        //////////////////////////////////////////////////////////////////////////*/

        private void setInitialGestureValues() {
            if (getAudioReactor() != null) {
                final float currentVolumeNormalized = (float) getAudioReactor().getVolume() / getAudioReactor().getMaxVolume();
                volumeProgressBar.setProgress((int) (volumeProgressBar.getMax() * currentVolumeNormalized));
            }

            float screenBrightness = getWindow().getAttributes().screenBrightness;
            if (screenBrightness < 0)
                screenBrightness = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 0) / 255.0f;

            brightnessProgressBar.setProgress((int) (brightnessProgressBar.getMax() * screenBrightness));

            if (DEBUG) Log.d(TAG, "setInitialGestureValues: volumeProgressBar.getProgress() ["
                    + volumeProgressBar.getProgress() + "] "
                    + "brightnessProgressBar.getProgress() ["
                    + brightnessProgressBar.getProgress() + "]");
        }

        @Override
        public void showControlsThenHide() {
            if (queueVisible) return;

            super.showControlsThenHide();
        }

        @Override
        public void showControls(long duration) {
            if (queueVisible) return;

            super.showControls(duration);
        }

        @Override
        public void hideControls(final long duration, long delay) {
            if (DEBUG) Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
            getControlsVisibilityHandler().removeCallbacksAndMessages(null);
            getControlsVisibilityHandler().postDelayed(() ->
                            animateView(getControlsRoot(), false, duration, 0,
                                    MainVideoPlayer.this::hideSystemUi),
                    /*delayMillis=*/delay
            );
        }

        private void updatePlaybackButtons() {
            if (repeatButton == null || shuffleButton == null ||
                    simpleExoPlayer == null || playQueue == null) return;

            setRepeatModeButton(repeatButton, getRepeatMode());
            setShuffleButton(shuffleButton, playQueue.isShuffled());
        }

        private void buildQueue() {
            itemsList.setAdapter(playQueueAdapter);
            itemsList.setClickable(true);
            itemsList.setLongClickable(true);

            itemsList.clearOnScrollListeners();
            itemsList.addOnScrollListener(getQueueScrollListener());

            itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
            itemTouchHelper.attachToRecyclerView(itemsList);

            playQueueAdapter.setSelectedListener(getOnSelectedListener());

            itemsListCloseButton.setOnClickListener(view -> onQueueClosed());
        }

        private OnScrollBelowItemsListener getQueueScrollListener() {
            return new OnScrollBelowItemsListener() {
                @Override
                public void onScrolledDown(RecyclerView recyclerView) {
                    if (playQueue != null && !playQueue.isComplete()) {
                        playQueue.fetch();
                    } else if (itemsList != null) {
                        itemsList.clearOnScrollListeners();
                    }
                }
            };
        }

        private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
            return new PlayQueueItemTouchCallback() {
                @Override
                public void onMove(int sourceIndex, int targetIndex) {
                    if (playQueue != null) playQueue.move(sourceIndex, targetIndex);
                }

                @Override
                public void onSwiped(int index) {
                    if(index != -1) playQueue.remove(index);
                }
            };
        }

        private PlayQueueItemBuilder.OnSelectedListener getOnSelectedListener() {
            return new PlayQueueItemBuilder.OnSelectedListener() {
                @Override
                public void selected(PlayQueueItem item, View view) {
                    onSelected(item);
                }

                @Override
                public void held(PlayQueueItem item, View view) {
                    final int index = playQueue.indexOf(item);
                    if (index != -1) playQueue.remove(index);
                }

                @Override
                public void onStartDrag(PlayQueueItemHolder viewHolder) {
                    if (itemTouchHelper != null) itemTouchHelper.startDrag(viewHolder);
                }
            };
        }

        ///////////////////////////////////////////////////////////////////////////
        // Getters
        ///////////////////////////////////////////////////////////////////////////

        public TextView getTitleTextView() {
            return titleTextView;
        }

        public TextView getChannelTextView() {
            return channelTextView;
        }

        public RelativeLayout getVolumeRelativeLayout() {
            return volumeRelativeLayout;
        }

        public ProgressBar getVolumeProgressBar() {
            return volumeProgressBar;
        }

        public ImageView getVolumeImageView() {
            return volumeImageView;
        }

        public RelativeLayout getBrightnessRelativeLayout() {
            return brightnessRelativeLayout;
        }

        public ProgressBar getBrightnessProgressBar() {
            return brightnessProgressBar;
        }

        public ImageView getBrightnessImageView() {
            return brightnessImageView;
        }

        public ImageButton getRepeatButton() {
            return repeatButton;
        }

        public ImageButton getPlayPauseButton() {
            return playPauseButton;
        }

        public int getMaxGestureLength() {
            return maxGestureLength;
        }
    }

    private class PlayerGestureListener extends GestureDetector.SimpleOnGestureListener implements View.OnTouchListener {
        private boolean isMoving;

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDoubleTap() called with: e = [" + e + "]" + "rawXy = " + e.getRawX() + ", " + e.getRawY() + ", xy = " + e.getX() + ", " + e.getY());

            if (e.getX() > playerImpl.getRootView().getWidth() * 2 / 3) {
                playerImpl.onFastForward();
            } else if (e.getX() < playerImpl.getRootView().getWidth() / 3) {
                playerImpl.onFastRewind();
            } else {
                playerImpl.getPlayPauseButton().performClick();
            }

            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onSingleTapConfirmed() called with: e = [" + e + "]");
            if (playerImpl.getCurrentState() == BasePlayer.STATE_BLOCKED) return true;

            if (playerImpl.isControlsVisible()) {
                playerImpl.hideControls(150, 0);
            } else {
                playerImpl.showControlsThenHide();
                showSystemUi();
            }
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            if (DEBUG) Log.d(TAG, "onDown() called with: e = [" + e + "]");

            return super.onDown(e);
        }

        private static final int MOVEMENT_THRESHOLD = 40;

        private final boolean isVolumeGestureEnabled = PlayerHelper.isVolumeGestureEnabled(getApplicationContext());
        private final boolean isBrightnessGestureEnabled = PlayerHelper.isBrightnessGestureEnabled(getApplicationContext());

        private final int maxVolume = playerImpl.getAudioReactor().getMaxVolume();

        @Override
        public boolean onScroll(MotionEvent initialEvent, MotionEvent movingEvent, float distanceX, float distanceY) {
            if (!isVolumeGestureEnabled && !isBrightnessGestureEnabled) return false;

            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "MainVideoPlayer.onScroll = " +
                    ", e1.getRaw = [" + initialEvent.getRawX() + ", " + initialEvent.getRawY() + "]" +
                    ", e2.getRaw = [" + movingEvent.getRawX() + ", " + movingEvent.getRawY() + "]" +
                    ", distanceXy = [" + distanceX + ", " + distanceY + "]");

            final boolean insideThreshold = Math.abs(movingEvent.getY() - initialEvent.getY()) <= MOVEMENT_THRESHOLD;
            if (!isMoving && (insideThreshold || Math.abs(distanceX) > Math.abs(distanceY))
                    || playerImpl.getCurrentState() == BasePlayer.STATE_COMPLETED) {
                return false;
            }

            isMoving = true;

            boolean acceptAnyArea = isVolumeGestureEnabled != isBrightnessGestureEnabled;
            boolean acceptVolumeArea = acceptAnyArea || initialEvent.getX() > playerImpl.getRootView().getWidth() / 2;
            boolean acceptBrightnessArea = acceptAnyArea || !acceptVolumeArea;

            if (isVolumeGestureEnabled && acceptVolumeArea) {
                playerImpl.getVolumeProgressBar().incrementProgressBy((int) distanceY);
                float currentProgressPercent =
                        (float) playerImpl.getVolumeProgressBar().getProgress() / playerImpl.getMaxGestureLength();
                int currentVolume = (int) (maxVolume * currentProgressPercent);
                playerImpl.getAudioReactor().setVolume(currentVolume);

                if (DEBUG) Log.d(TAG, "onScroll().volumeControl, currentVolume = " + currentVolume);

                final int resId =
                        currentProgressPercent <= 0 ? R.drawable.ic_volume_off_white_72dp
                                : currentProgressPercent < 0.25 ? R.drawable.ic_volume_mute_white_72dp
                                : currentProgressPercent < 0.75 ? R.drawable.ic_volume_down_white_72dp
                                : R.drawable.ic_volume_up_white_72dp;

                playerImpl.getVolumeImageView().setImageDrawable(
                        AppCompatResources.getDrawable(getApplicationContext(), resId)
                );

                if (playerImpl.getVolumeRelativeLayout().getVisibility() != View.VISIBLE) {
                    animateView(playerImpl.getVolumeRelativeLayout(), SCALE_AND_ALPHA, true, 200);
                }
                if (playerImpl.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                    playerImpl.getBrightnessRelativeLayout().setVisibility(View.GONE);
                }
            } else if (isBrightnessGestureEnabled && acceptBrightnessArea) {
                playerImpl.getBrightnessProgressBar().incrementProgressBy((int) distanceY);
                float currentProgressPercent =
                        (float) playerImpl.getBrightnessProgressBar().getProgress() / playerImpl.getMaxGestureLength();
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.screenBrightness = currentProgressPercent;
                getWindow().setAttributes(layoutParams);

                if (DEBUG) Log.d(TAG, "onScroll().brightnessControl, currentBrightness = " + currentProgressPercent);

                final int resId =
                        currentProgressPercent < 0.25 ? R.drawable.ic_brightness_low_white_72dp
                                : currentProgressPercent < 0.75 ? R.drawable.ic_brightness_medium_white_72dp
                                : R.drawable.ic_brightness_high_white_72dp;

                playerImpl.getBrightnessImageView().setImageDrawable(
                        AppCompatResources.getDrawable(getApplicationContext(), resId)
                );

                if (playerImpl.getBrightnessRelativeLayout().getVisibility() != View.VISIBLE) {
                    animateView(playerImpl.getBrightnessRelativeLayout(), SCALE_AND_ALPHA, true, 200);
                }
                if (playerImpl.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                    playerImpl.getVolumeRelativeLayout().setVisibility(View.GONE);
                }
            }
            return true;
        }

        private void onScrollEnd() {
            if (DEBUG) Log.d(TAG, "onScrollEnd() called");

            if (playerImpl.getVolumeRelativeLayout().getVisibility() == View.VISIBLE) {
                animateView(playerImpl.getVolumeRelativeLayout(), SCALE_AND_ALPHA, false, 200, 200);
            }
            if (playerImpl.getBrightnessRelativeLayout().getVisibility() == View.VISIBLE) {
                animateView(playerImpl.getBrightnessRelativeLayout(), SCALE_AND_ALPHA, false, 200, 200);
            }

            if (playerImpl.isControlsVisible() && playerImpl.getCurrentState() == STATE_PLAYING) {
                playerImpl.hideControls(DEFAULT_CONTROLS_DURATION, DEFAULT_CONTROLS_HIDE_TIME);
            }
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            //noinspection PointlessBooleanExpression
            if (DEBUG && false) Log.d(TAG, "onTouch() called with: v = [" + v + "], event = [" + event + "]");
            gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && isMoving) {
                isMoving = false;
                onScrollEnd();
            }
            return true;
        }

    }
}
