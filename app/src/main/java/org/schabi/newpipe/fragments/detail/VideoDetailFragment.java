package org.schabi.newpipe.fragments.detail;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.tabs.TabLayout;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.App;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentVideoDetailBinding;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ReCaptchaActivity;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.EmptyFragment;
import org.schabi.newpipe.fragments.list.comments.CommentsFragment;
import org.schabi.newpipe.fragments.list.videos.RelatedItemsFragment;
import org.schabi.newpipe.ktx.AnimationType;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.dialog.PlaylistCreationDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.event.OnKeyDownListener;
import org.schabi.newpipe.player.event.PlayerServiceExtendedEventListener;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.KoreUtil;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ShareUtils;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import static android.text.TextUtils.isEmpty;
import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.extractor.stream.StreamExtractor.NO_AGE_LIMIT;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateRotation;
import static org.schabi.newpipe.player.helper.PlayerHelper.globalScreenOrientationLocked;
import static org.schabi.newpipe.player.helper.PlayerHelper.isClearingQueueConfirmationRequired;
import static org.schabi.newpipe.player.playqueue.PlayQueueItem.RECOVERY_UNSET;
import static org.schabi.newpipe.util.ExtractorHelper.showMetaInfoInTextView;

public final class VideoDetailFragment
        extends BaseStateFragment<StreamInfo>
        implements BackPressable,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnClickListener,
        View.OnLongClickListener,
        PlayerServiceExtendedEventListener,
        OnKeyDownListener {
    public static final String KEY_SWITCHING_PLAYERS = "switching_players";

    private static final float MAX_OVERLAY_ALPHA = 0.9f;
    private static final float MAX_PLAYER_HEIGHT = 0.7f;

    public static final String ACTION_SHOW_MAIN_PLAYER =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_SHOW_MAIN_PLAYER";
    public static final String ACTION_HIDE_MAIN_PLAYER =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_HIDE_MAIN_PLAYER";
    public static final String ACTION_PLAYER_STARTED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_PLAYER_STARTED";
    public static final String ACTION_VIDEO_FRAGMENT_RESUMED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_RESUMED";
    public static final String ACTION_VIDEO_FRAGMENT_STOPPED =
            App.PACKAGE_NAME + ".VideoDetailFragment.ACTION_VIDEO_FRAGMENT_STOPPED";

    private static final String COMMENTS_TAB_TAG = "COMMENTS";
    private static final String RELATED_TAB_TAG = "NEXT VIDEO";
    private static final String DESCRIPTION_TAB_TAG = "DESCRIPTION TAB";
    private static final String EMPTY_TAB_TAG = "EMPTY TAB";

    // tabs
    private boolean showComments;
    private boolean showRelatedItems;
    private boolean showDescription;
    private String selectedTabTag;
    @AttrRes @NonNull final List<Integer> tabIcons = new ArrayList<>();
    @StringRes @NonNull final List<Integer> tabContentDescriptions = new ArrayList<>();
    private boolean tabSettingsChanged = false;
    private int lastAppBarVerticalOffset = Integer.MAX_VALUE; // prevents useless updates

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    @NonNull
    protected String title = "";
    @State
    @Nullable
    protected String url = null;
    @Nullable
    protected PlayQueue playQueue = null;
    @State
    int bottomSheetState = BottomSheetBehavior.STATE_EXPANDED;
    @State
    protected boolean autoPlayEnabled = true;

    @Nullable
    private StreamInfo currentInfo = null;
    private Disposable currentWorker;
    @NonNull
    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private Disposable positionSubscriber = null;

    private List<VideoStream> sortedVideoStreams;
    private int selectedVideoStreamIndex = -1;
    private BottomSheetBehavior<FrameLayout> bottomSheetBehavior;
    private BroadcastReceiver broadcastReceiver;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private FragmentVideoDetailBinding binding;

    private TabAdapter pageAdapter;

    private ContentObserver settingsContentObserver;
    @Nullable
    private MainPlayer playerService;
    private Player player;

    /*//////////////////////////////////////////////////////////////////////////
    // Service management
    //////////////////////////////////////////////////////////////////////////*/
    @Override
    public void onServiceConnected(final Player connectedPlayer,
                                   final MainPlayer connectedPlayerService,
                                   final boolean playAfterConnect) {
        player = connectedPlayer;
        playerService = connectedPlayerService;

        // It will do nothing if the player is not in fullscreen mode
        hideSystemUiIfNeeded();

        if (!player.videoPlayerSelected() && !playAfterConnect) {
            return;
        }

        if (isLandscape()) {
            // If the video is playing but orientation changed
            // let's make the video in fullscreen again
            checkLandscape();
        } else if (player.isFullscreen() && !player.isVerticalVideo()
                // Tablet UI has orientation-independent fullscreen
                && !DeviceUtils.isTablet(activity)) {
            // Device is in portrait orientation after rotation but UI is in fullscreen.
            // Return back to non-fullscreen state
            player.toggleFullscreen();
        }

        if (playerIsNotStopped() && player.videoPlayerSelected()) {
            addVideoPlayerView();
        }

        if (playAfterConnect
                || (currentInfo != null
                && isAutoplayEnabled()
                && player.getParentActivity() == null)) {
            autoPlayEnabled = true; // forcefully start playing
            openVideoPlayer();
        }
    }

    @Override
    public void onServiceDisconnected() {
        playerService = null;
        player = null;
        restoreDefaultBrightness();
    }


    /*////////////////////////////////////////////////////////////////////////*/

    public static VideoDetailFragment getInstance(final int serviceId,
                                                  @Nullable final String videoUrl,
                                                  @NonNull final String name,
                                                  @Nullable final PlayQueue queue) {
        final VideoDetailFragment instance = new VideoDetailFragment();
        instance.setInitialData(serviceId, videoUrl, name, queue);
        return instance;
    }

    public static VideoDetailFragment getInstanceInCollapsedState() {
        final VideoDetailFragment instance = new VideoDetailFragment();
        instance.bottomSheetState = BottomSheetBehavior.STATE_COLLAPSED;
        return instance;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        showComments = prefs.getBoolean(getString(R.string.show_comments_key), true);
        showRelatedItems = prefs.getBoolean(getString(R.string.show_next_video_key), true);
        showDescription = prefs.getBoolean(getString(R.string.show_description_key), true);
        selectedTabTag = prefs.getString(
                getString(R.string.stream_info_selected_tab_key), COMMENTS_TAB_TAG);
        prefs.registerOnSharedPreferenceChangeListener(this);

        setupBroadcastReceiver();

        settingsContentObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(final boolean selfChange) {
                if (activity != null && !globalScreenOrientationLocked(activity)) {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            }
        };
        activity.getContentResolver().registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false,
                settingsContentObserver);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_detail, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        restoreDefaultBrightness();
        PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(getString(R.string.stream_info_selected_tab_key),
                        pageAdapter.getItemTitle(binding.viewPager.getCurrentItem()))
                .apply();
    }

    @Override
    public void onResume() {
        super.onResume();

        activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_RESUMED));

        setupBrightness();

        if (tabSettingsChanged) {
            tabSettingsChanged = false;
            initTabs();
            if (currentInfo != null) {
                updateTabs(currentInfo);
            }
        }

        // Check if it was loading when the fragment was stopped/paused
        if (wasLoading.getAndSet(false) && !wasCleared()) {
            startLoading(false);
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        if (!activity.isChangingConfigurations()) {
            activity.sendBroadcast(new Intent(ACTION_VIDEO_FRAGMENT_STOPPED));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        binding = null;

        // Stop the service when user leaves the app with double back press
        // if video player is selected. Otherwise unbind
        if (activity.isFinishing() && player != null && player.videoPlayerSelected()) {
            PlayerHolder.stopService(App.getApp());
        } else {
            PlayerHolder.removeListener();
        }

        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this);
        activity.unregisterReceiver(broadcastReceiver);
        activity.getContentResolver().unregisterContentObserver(settingsContentObserver);

        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        disposables.clear();
        positionSubscriber = null;
        currentWorker = null;
        bottomSheetBehavior.setBottomSheetCallback(null);

        if (activity.isFinishing()) {
            playQueue = null;
            currentInfo = null;
            stack = new LinkedList<>();
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            serviceId, url, title, null, false);
                } else {
                    Log.e(TAG, "ReCaptcha failed");
                }
                break;
            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (key.equals(getString(R.string.show_comments_key))) {
            showComments = sharedPreferences.getBoolean(key, true);
            tabSettingsChanged = true;
        } else if (key.equals(getString(R.string.show_next_video_key))) {
            showRelatedItems = sharedPreferences.getBoolean(key, true);
            tabSettingsChanged = true;
        } else if (key.equals(getString(R.string.show_description_key))) {
            showComments = sharedPreferences.getBoolean(key, true);
            tabSettingsChanged = true;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(false);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(false);
                break;
            case R.id.detail_controls_playlist_append:
                if (getFM() != null && currentInfo != null) {

                    final PlaylistAppendDialog d = PlaylistAppendDialog.fromStreamInfo(currentInfo);
                    disposables.add(
                            PlaylistAppendDialog.onPlaylistFound(getContext(),
                                    () -> d.show(getFM(), TAG),
                                    () -> PlaylistCreationDialog.newInstance(d).show(getFM(), TAG)
                            )
                    );
                }
                break;
            case R.id.detail_controls_download:
                if (PermissionHelper.checkStoragePermissions(activity,
                        PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                    this.openDownloadDialog();
                }
                break;
            case R.id.detail_controls_share:
                if (currentInfo != null) {
                    ShareUtils.shareText(requireContext(),
                            currentInfo.getName(), currentInfo.getUrl());
                }
                break;
            case R.id.detail_controls_open_in_browser:
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(requireContext(), currentInfo.getUrl());
                }
                break;
            case R.id.detail_controls_play_with_kodi:
                if (currentInfo != null) {
                    try {
                        NavigationHelper.playWithKore(
                                requireContext(), Uri.parse(currentInfo.getUrl()));
                    } catch (final Exception e) {
                        if (DEBUG) {
                            Log.i(TAG, "Failed to start kore", e);
                        }
                        KoreUtil.showInstallKoreDialog(requireContext());
                    }
                }
                break;
            case R.id.detail_uploader_root_layout:
                if (isEmpty(currentInfo.getSubChannelUrl())) {
                    if (!isEmpty(currentInfo.getUploaderUrl())) {
                        openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                    }

                    if (DEBUG) {
                        Log.i(TAG, "Can't open sub-channel because we got no channel URL");
                    }
                } else {
                    openChannel(currentInfo.getSubChannelUrl(),
                            currentInfo.getSubChannelName());
                }
                break;
            case R.id.detail_thumbnail_root_layout:
                autoPlayEnabled = true; // forcefully start playing
                openVideoPlayer();
                break;
            case R.id.detail_title_root_layout:
                toggleTitleAndSecondaryControls();
                break;
            case R.id.overlay_thumbnail:
            case R.id.overlay_metadata_layout:
            case R.id.overlay_buttons_layout:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                break;
            case R.id.overlay_play_pause_button:
                if (playerIsNotStopped()) {
                    player.playPause();
                    player.hideControls(0, 0);
                    showSystemUi();
                } else {
                    autoPlayEnabled = true; // forcefully start playing
                    openVideoPlayer();
                }

                setOverlayPlayPauseImage(player != null && player.isPlaying());
                break;
            case R.id.overlay_close_button:
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                break;
        }
    }

    private void openChannel(final String subChannelUrl, final String subChannelName) {
        try {
            NavigationHelper.openChannelFragment(getFM(), currentInfo.getServiceId(),
                    subChannelUrl, subChannelName);
        } catch (final Exception e) {
            ErrorActivity.reportUiErrorInSnackbar(this, "Opening channel fragment", e);
        }
    }

    @Override
    public boolean onLongClick(final View v) {
        if (isLoading.get() || currentInfo == null) {
            return false;
        }

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(true);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(true);
                break;
            case R.id.detail_controls_download:
                NavigationHelper.openDownloads(activity);
                break;
            case R.id.overlay_thumbnail:
            case R.id.overlay_metadata_layout:
                openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                break;
            case R.id.detail_uploader_root_layout:
                if (isEmpty(currentInfo.getSubChannelUrl())) {
                    Log.w(TAG,
                            "Can't open parent channel because we got no parent channel URL");
                } else {
                    openChannel(currentInfo.getUploaderUrl(), currentInfo.getUploaderName());
                }
                break;
            case R.id.detail_title_root_layout:
                ShareUtils.copyToClipboard(requireContext(),
                        binding.detailVideoTitleView.getText().toString());
                break;
        }

        return true;
    }

    private void toggleTitleAndSecondaryControls() {
        if (binding.detailSecondaryControlPanel.getVisibility() == View.GONE) {
            binding.detailVideoTitleView.setMaxLines(10);
            animateRotation(binding.detailToggleSecondaryControlsView,
                    Player.DEFAULT_CONTROLS_DURATION, 180);
            binding.detailSecondaryControlPanel.setVisibility(View.VISIBLE);
        } else {
            binding.detailVideoTitleView.setMaxLines(1);
            animateRotation(binding.detailToggleSecondaryControlsView,
                    Player.DEFAULT_CONTROLS_DURATION, 0);
            binding.detailSecondaryControlPanel.setVisibility(View.GONE);
        }
        // view pager height has changed, update the tab layout
        updateTabLayoutVisibility();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        binding = FragmentVideoDetailBinding.bind(rootView);

        pageAdapter = new TabAdapter(getChildFragmentManager());
        binding.viewPager.setAdapter(pageAdapter);
        binding.tabLayout.setupWithViewPager(binding.viewPager);

        binding.detailThumbnailRootLayout.requestFocus();

        if (DeviceUtils.isTv(getContext())) {
            // remove ripple effects from detail controls
            final int transparent = ContextCompat.getColor(requireContext(),
                    R.color.transparent_background_color);
            binding.detailControlsPlaylistAppend.setBackgroundColor(transparent);
            binding.detailControlsBackground.setBackgroundColor(transparent);
            binding.detailControlsPopup.setBackgroundColor(transparent);
            binding.detailControlsDownload.setBackgroundColor(transparent);
            binding.detailControlsShare.setBackgroundColor(transparent);
            binding.detailControlsOpenInBrowser.setBackgroundColor(transparent);
            binding.detailControlsPlayWithKodi.setBackgroundColor(transparent);
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        binding.detailTitleRootLayout.setOnClickListener(this);
        binding.detailTitleRootLayout.setOnLongClickListener(this);
        binding.detailUploaderRootLayout.setOnClickListener(this);
        binding.detailUploaderRootLayout.setOnLongClickListener(this);
        binding.detailThumbnailRootLayout.setOnClickListener(this);

        binding.detailControlsBackground.setOnClickListener(this);
        binding.detailControlsBackground.setOnLongClickListener(this);
        binding.detailControlsPopup.setOnClickListener(this);
        binding.detailControlsPopup.setOnLongClickListener(this);
        binding.detailControlsPlaylistAppend.setOnClickListener(this);
        binding.detailControlsDownload.setOnClickListener(this);
        binding.detailControlsDownload.setOnLongClickListener(this);
        binding.detailControlsShare.setOnClickListener(this);
        binding.detailControlsOpenInBrowser.setOnClickListener(this);
        binding.detailControlsPlayWithKodi.setOnClickListener(this);
        binding.detailControlsPlayWithKodi.setVisibility(KoreUtil.shouldShowPlayWithKodi(
                requireContext(), serviceId) ? View.VISIBLE : View.GONE);

        binding.overlayThumbnail.setOnClickListener(this);
        binding.overlayThumbnail.setOnLongClickListener(this);
        binding.overlayMetadataLayout.setOnClickListener(this);
        binding.overlayMetadataLayout.setOnLongClickListener(this);
        binding.overlayButtonsLayout.setOnClickListener(this);
        binding.overlayCloseButton.setOnClickListener(this);
        binding.overlayPlayPauseButton.setOnClickListener(this);

        binding.detailControlsBackground.setOnTouchListener(getOnControlsTouchListener());
        binding.detailControlsPopup.setOnTouchListener(getOnControlsTouchListener());

        binding.appBarLayout.addOnOffsetChangedListener((layout, verticalOffset) -> {
            // prevent useless updates to tab layout visibility if nothing changed
            if (verticalOffset != lastAppBarVerticalOffset) {
                lastAppBarVerticalOffset = verticalOffset;
                // the view was scrolled
                updateTabLayoutVisibility();
            }
        });

        setupBottomPlayer();
        if (!PlayerHolder.bound) {
            setHeightThumbnail();
        } else {
            PlayerHolder.startService(App.getApp(), false, this);
        }
    }

    private View.OnTouchListener getOnControlsTouchListener() {
        return (view, motionEvent) -> {
            if (!PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(getString(R.string.show_hold_to_append_key), true)) {
                return false;
            }

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                animate(binding.touchAppendDetail, true, 250, AnimationType.ALPHA,
                        0, () ->
                        animate(binding.touchAppendDetail, false, 1500,
                                AnimationType.ALPHA, 1000));
            }
            return false;
        };
    }

    private void initThumbnailViews(@NonNull final StreamInfo info) {
        binding.detailThumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);

        if (!isEmpty(info.getThumbnailUrl())) {
            final ImageLoadingListener onFailListener = new SimpleImageLoadingListener() {
                @Override
                public void onLoadingFailed(final String imageUri, final View view,
                                            final FailReason failReason) {
                    showSnackBarError(new ErrorInfo(failReason.getCause(), UserAction.LOAD_IMAGE,
                            imageUri, info));
                }
            };

            IMAGE_LOADER.displayImage(info.getThumbnailUrl(), binding.detailThumbnailImageView,
                    ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, onFailListener);
        }

        if (!isEmpty(info.getSubChannelAvatarUrl())) {
            IMAGE_LOADER.displayImage(info.getSubChannelAvatarUrl(),
                    binding.detailSubChannelThumbnailView,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }

        if (!isEmpty(info.getUploaderAvatarUrl())) {
            IMAGE_LOADER.displayImage(info.getUploaderAvatarUrl(),
                    binding.detailUploaderThumbnailView,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    private static LinkedList<StackItem> stack = new LinkedList<>();

    @Override
    public boolean onKeyDown(final int keyCode) {
        return player != null && player.onKeyDown(keyCode);
    }

    @Override
    public boolean onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called");
        }

        // If we are in fullscreen mode just exit from it via first back press
        if (player != null && player.isFullscreen()) {
            if (!DeviceUtils.isTablet(activity)) {
                player.pause();
            }
            restoreDefaultOrientation();
            setAutoPlay(false);
            return true;
        }

        // If we have something in history of played items we replay it here
        if (player != null
                && player.getPlayQueue() != null
                && player.videoPlayerSelected()
                && player.getPlayQueue().previous()) {
            return true;
        }
        // That means that we are on the start of the stack,
        // return false to let the MainActivity handle the onBack
        if (stack.size() <= 1) {
            restoreDefaultOrientation();

            return false;
        }
        // Remove top
        stack.pop();
        // Get stack item from the new top
        assert stack.peek() != null;
        setupFromHistoryItem(stack.peek());

        return true;
    }

    private void setupFromHistoryItem(final StackItem item) {
        setAutoPlay(false);
        hideMainPlayer();

        setInitialData(item.getServiceId(), item.getUrl(),
                item.getTitle() == null ? "" : item.getTitle(), item.getPlayQueue());
        startLoading(false);

        // Maybe an item was deleted in background activity
        if (item.getPlayQueue().getItem() == null) {
            return;
        }

        final PlayQueueItem playQueueItem = item.getPlayQueue().getItem();
        // Update title, url, uploader from the last item in the stack (it's current now)
        final boolean isPlayerStopped = player == null || player.isStopped();
        if (playQueueItem != null && isPlayerStopped) {
            updateOverlayData(playQueueItem.getTitle(),
                    playQueueItem.getUploader(), playQueueItem.getThumbnailUrl());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (wasCleared()) {
            return;
        }

        if (currentInfo == null) {
            prepareAndLoadInfo();
        } else {
            prepareAndHandleInfoIfNeededAfterDelay(currentInfo, false, 50);
        }
    }

    public void selectAndLoadVideo(final int newServiceId,
                                   @Nullable final String newUrl,
                                   @NonNull final String newTitle,
                                   @Nullable final PlayQueue newQueue) {
        if (player != null && newQueue != null && playQueue != null
                && !Objects.equals(newQueue.getItem(), playQueue.getItem())) {
            // Preloading can be disabled since playback is surely being replaced.
            player.disablePreloadingOfCurrentTrack();
        }

        setInitialData(newServiceId, newUrl, newTitle, newQueue);
        startLoading(false, true);
    }

    private void prepareAndHandleInfoIfNeededAfterDelay(final StreamInfo info,
                                                        final boolean scrollToTop,
                                                        final long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (activity == null) {
                return;
            }
            // Data can already be drawn, don't spend time twice
            if (info.getName().equals(binding.detailVideoTitleView.getText().toString())) {
                return;
            }
            prepareAndHandleInfo(info, scrollToTop);
        }, delay);
    }

    private void prepareAndHandleInfo(final StreamInfo info, final boolean scrollToTop) {
        if (DEBUG) {
            Log.d(TAG, "prepareAndHandleInfo() called with: "
                    + "info = [" + info + "], scrollToTop = [" + scrollToTop + "]");
        }

        showLoading();
        initTabs();

        if (scrollToTop) {
            scrollToTop();
        }
        handleResult(info);
        showContent();

    }

    protected void prepareAndLoadInfo() {
        scrollToTop();
        startLoading(false);
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        runWorker(forceLoad, stack.isEmpty());
    }

    private void startLoading(final boolean forceLoad, final boolean addToBackStack) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }

        runWorker(forceLoad, addToBackStack);
    }

    private void runWorker(final boolean forceLoad, final boolean addToBackStack) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    isLoading.set(false);
                    hideMainPlayer();
                    if (result.getAgeLimit() != NO_AGE_LIMIT && !prefs.getBoolean(
                            getString(R.string.show_age_restricted_content), false)) {
                        hideAgeRestrictedContent();
                    } else {
                        handleResult(result);
                        showContent();
                        if (addToBackStack) {
                            if (playQueue == null) {
                                playQueue = new SinglePlayQueue(result);
                            }
                            if (stack.isEmpty() || !stack.peek().getPlayQueue().equals(playQueue)) {
                                stack.push(new StackItem(serviceId, url, title, playQueue));
                            }
                        }
                        if (isAutoplayEnabled()) {
                            openVideoPlayer();
                        }
                    }
                }, throwable -> showError(new ErrorInfo(throwable, UserAction.REQUESTED_STREAM,
                        url == null ? "no url" : url, serviceId)));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void initTabs() {
        if (pageAdapter.getCount() != 0) {
            selectedTabTag = pageAdapter.getItemTitle(binding.viewPager.getCurrentItem());
        }
        pageAdapter.clearAllItems();
        tabIcons.clear();
        tabContentDescriptions.clear();

        if (shouldShowComments()) {
            pageAdapter.addFragment(
                    CommentsFragment.getInstance(serviceId, url, title), COMMENTS_TAB_TAG);
            tabIcons.add(R.drawable.ic_comment);
            tabContentDescriptions.add(R.string.comments_tab_description);
        }

        if (showRelatedItems && binding.relatedItemsLayout == null) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(new EmptyFragment(false), RELATED_TAB_TAG);
            tabIcons.add(R.drawable.ic_art_track);
            tabContentDescriptions.add(R.string.related_items_tab_description);
        }

        if (showDescription) {
            // temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(new EmptyFragment(false), DESCRIPTION_TAB_TAG);
            tabIcons.add(R.drawable.ic_description);
            tabContentDescriptions.add(R.string.description_tab_description);
        }

        if (pageAdapter.getCount() == 0) {
            pageAdapter.addFragment(new EmptyFragment(true), EMPTY_TAB_TAG);
        }
        pageAdapter.notifyDataSetUpdate();

        if (pageAdapter.getCount() >= 2) {
            final int position = pageAdapter.getItemPositionByTitle(selectedTabTag);
            if (position != -1) {
                binding.viewPager.setCurrentItem(position);
            }
            updateTabIconsAndContentDescriptions();
        }
        // the page adapter now contains tabs: show the tab layout
        updateTabLayoutVisibility();
    }

    /**
     * To be called whenever {@link #pageAdapter} is modified, since that triggers a refresh in
     * {@link FragmentVideoDetailBinding#tabLayout} resetting all tab's icons and content
     * descriptions. This reads icons from {@link #tabIcons} and content descriptions from
     * {@link #tabContentDescriptions}, which are all set in {@link #initTabs()}.
     */
    private void updateTabIconsAndContentDescriptions() {
        for (int i = 0; i < tabIcons.size(); ++i) {
            final TabLayout.Tab tab = binding.tabLayout.getTabAt(i);
            if (tab != null) {
                tab.setIcon(tabIcons.get(i));
                tab.setContentDescription(tabContentDescriptions.get(i));
            }
        }
    }

    private void updateTabs(@NonNull final StreamInfo info) {
        if (showRelatedItems) {
            if (binding.relatedItemsLayout == null) { // phone
                pageAdapter.updateItem(RELATED_TAB_TAG, RelatedItemsFragment.getInstance(info));
            } else { // tablet + TV
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.relatedItemsLayout, RelatedItemsFragment.getInstance(info))
                        .commitAllowingStateLoss();
                binding.relatedItemsLayout.setVisibility(
                        player != null && player.isFullscreen() ? View.GONE : View.VISIBLE);
            }
        }

        if (showDescription) {
            pageAdapter.updateItem(DESCRIPTION_TAB_TAG, new DescriptionFragment(info));
        }

        binding.viewPager.setVisibility(View.VISIBLE);
        // make sure the tab layout is visible
        updateTabLayoutVisibility();
        pageAdapter.notifyDataSetUpdate();
        updateTabIconsAndContentDescriptions();
    }

    private boolean shouldShowComments() {
        try {
            return showComments && NewPipe.getService(serviceId)
                    .getServiceInfo()
                    .getMediaCapabilities()
                    .contains(COMMENTS);
        } catch (final ExtractionException e) {
            return false;
        }
    }

    public void updateTabLayoutVisibility() {

        if (binding == null) {
            //If binding is null we do not need to and should not do anything with its object(s)
            return;
        }

        if (pageAdapter.getCount() < 2 || binding.viewPager.getVisibility() != View.VISIBLE) {
            // hide tab layout if there is only one tab or if the view pager is also hidden
            binding.tabLayout.setVisibility(View.GONE);
        } else {
            // call `post()` to be sure `viewPager.getHitRect()`
            // is up to date and not being currently recomputed
            binding.tabLayout.post(() -> {
                if (getContext() != null) {
                    final Rect pagerHitRect = new Rect();
                    binding.viewPager.getHitRect(pagerHitRect);

                    final Point displaySize = new Point();
                    Objects.requireNonNull(ContextCompat.getSystemService(getContext(),
                            WindowManager.class)).getDefaultDisplay().getSize(displaySize);

                    final int viewPagerVisibleHeight = displaySize.y - pagerHitRect.top;
                    // see TabLayout.DEFAULT_HEIGHT, which is equal to 48dp
                    final float tabLayoutHeight = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 48, getResources().getDisplayMetrics());

                    if (viewPagerVisibleHeight > tabLayoutHeight * 2) {
                        // no translation at all when viewPagerVisibleHeight > tabLayout.height * 3
                        binding.tabLayout.setTranslationY(
                                Math.max(0, tabLayoutHeight * 3 - viewPagerVisibleHeight));
                        binding.tabLayout.setVisibility(View.VISIBLE);
                    } else {
                        // view pager is not visible enough
                        binding.tabLayout.setVisibility(View.GONE);
                    }
                }
            });
        }
    }

    public void scrollToTop() {
        binding.appBarLayout.setExpanded(true, true);
        // notify tab layout of scrolling
        updateTabLayoutVisibility();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void openBackgroundPlayer(final boolean append) {
        final AudioStream audioStream = currentInfo.getAudioStreams()
                .get(ListHelper.getDefaultAudioFormat(activity, currentInfo.getAudioStreams()));

        final boolean useExternalAudioPlayer = PreferenceManager
                .getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        //  If a user watched video inside fullscreen mode and than chose another player
        //  return to non-fullscreen mode
        if (player != null && player.isFullscreen()) {
            player.toggleFullscreen();
        }

        if (!useExternalAudioPlayer) {
            openNormalBackgroundPlayer(append);
        } else {
            startOnExternalPlayer(activity, currentInfo, audioStream);
        }
    }

    private void openPopupPlayer(final boolean append) {
        if (!PermissionHelper.isPopupEnabled(activity)) {
            PermissionHelper.showPopupEnablementToast(activity);
            return;
        }

        // See UI changes while remote playQueue changes
        if (player == null) {
            PlayerHolder.startService(App.getApp(), false, this);
        }

        //  If a user watched video inside fullscreen mode and than chose another player
        //  return to non-fullscreen mode
        if (player != null && player.isFullscreen()) {
            player.toggleFullscreen();
        }

        final PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) {
            NavigationHelper.enqueueOnPopupPlayer(activity, queue, false);
        } else {
            replaceQueueIfUserConfirms(() -> NavigationHelper
                    .playOnPopupPlayer(activity, queue, true));
        }
    }

    public void openVideoPlayer() {
        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            showExternalPlaybackDialog();
        } else {
            replaceQueueIfUserConfirms(this::openMainPlayer);
        }
    }

    private void openNormalBackgroundPlayer(final boolean append) {
        // See UI changes while remote playQueue changes
        if (player == null) {
            PlayerHolder.startService(App.getApp(), false, this);
        }

        final PlayQueue queue = setupPlayQueueForIntent(append);
        if (append) {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, queue, false);
        } else {
            replaceQueueIfUserConfirms(() -> NavigationHelper
                    .playOnBackgroundPlayer(activity, queue, true));
        }
    }

    private void openMainPlayer() {
        if (playerService == null) {
            PlayerHolder.startService(App.getApp(), autoPlayEnabled, this);
            return;
        }
        if (currentInfo == null) {
            return;
        }

        final PlayQueue queue = setupPlayQueueForIntent(false);

        // Video view can have elements visible from popup,
        // We hide it here but once it ready the view will be shown in handleIntent()
        if (playerService.getView() != null) {
            playerService.getView().setVisibility(View.GONE);
        }
        addVideoPlayerView();

        final Intent playerIntent = NavigationHelper
                .getPlayerIntent(requireContext(), MainPlayer.class, queue, true, autoPlayEnabled);
        activity.startService(playerIntent);
    }

    private void hideMainPlayer() {
        if (playerService == null
                || playerService.getView() == null
                || !player.videoPlayerSelected()) {
            return;
        }

        removeVideoPlayerView();
        playerService.stop(isAutoplayEnabled());
        playerService.getView().setVisibility(View.GONE);
    }

    private PlayQueue setupPlayQueueForIntent(final boolean append) {
        if (append) {
            return new SinglePlayQueue(currentInfo);
        }

        PlayQueue queue = playQueue;
        // Size can be 0 because queue removes bad stream automatically when error occurs
        if (queue == null || queue.isEmpty()) {
            queue = new SinglePlayQueue(currentInfo);
        }

        return queue;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setAutoPlay(final boolean autoPlay) {
        this.autoPlayEnabled = autoPlay;
    }

    private void startOnExternalPlayer(@NonNull final Context context,
                                       @NonNull final StreamInfo info,
                                       @NonNull final Stream selectedStream) {
        NavigationHelper.playOnExternalPlayer(context, currentInfo.getName(),
                currentInfo.getSubChannelName(), selectedStream);

        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        disposables.add(recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                        ignored -> { /* successful */ },
                        error -> Log.e(TAG, "Register view failure: ", error)
                ));
    }

    private boolean isExternalPlayerEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean(getString(R.string.use_external_video_player_key), false);
    }

    // This method overrides default behaviour when setAutoPlay() is called.
    // Don't auto play if the user selected an external player or disabled it in settings
    private boolean isAutoplayEnabled() {
        return autoPlayEnabled
                && !isExternalPlayerEnabled()
                && (player == null || player.videoPlayerSelected())
                && bottomSheetState != BottomSheetBehavior.STATE_HIDDEN
                && PlayerHelper.isAutoplayAllowedByUser(requireContext());
    }

    private void addVideoPlayerView() {
        if (player == null || getView() == null) {
            return;
        }

        // Check if viewHolder already contains a child
        if (player.getRootView().getParent() != binding.playerPlaceholder) {
            playerService.removeViewFromParent();
        }
        setHeightThumbnail();

        // Prevent from re-adding a view multiple times
        if (player.getRootView().getParent() == null) {
            binding.playerPlaceholder.addView(player.getRootView());
        }
    }

    private void removeVideoPlayerView() {
        makeDefaultHeightForVideoPlaceholder();

        playerService.removeViewFromParent();
    }

    private void makeDefaultHeightForVideoPlaceholder() {
        if (getView() == null) {
            return;
        }

        binding.playerPlaceholder.getLayoutParams().height = FrameLayout.LayoutParams.MATCH_PARENT;
        binding.playerPlaceholder.requestLayout();
    }

    private final ViewTreeObserver.OnPreDrawListener preDrawListener =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    final DisplayMetrics metrics = getResources().getDisplayMetrics();

                    if (getView() != null) {
                        final int height = (isInMultiWindow()
                                ? requireView()
                                : activity.getWindow().getDecorView()).getHeight();
                        setHeightThumbnail(height, metrics);
                        getView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
                    }
                    return false;
                }
            };

    /**
     * Method which controls the size of thumbnail and the size of main player inside
     * a layout with thumbnail. It decides what height the player should have in both
     * screen orientations. It knows about multiWindow feature
     * and about videos with aspectRatio ZOOM (the height for them will be a bit higher,
     * {@link #MAX_PLAYER_HEIGHT})
     */
    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        final boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        requireView().getViewTreeObserver().removeOnPreDrawListener(preDrawListener);

        if (player != null && player.isFullscreen()) {
            final int height = (isInMultiWindow()
                    ? requireView()
                    : activity.getWindow().getDecorView()).getHeight();
            // Height is zero when the view is not yet displayed like after orientation change
            if (height != 0) {
                setHeightThumbnail(height, metrics);
            } else {
                requireView().getViewTreeObserver().addOnPreDrawListener(preDrawListener);
            }
        } else {
            final int height = (int) (isPortrait
                    ? metrics.widthPixels / (16.0f / 9.0f)
                    : metrics.heightPixels / 2.0f);
            setHeightThumbnail(height, metrics);
        }
    }

    private void setHeightThumbnail(final int newHeight, final DisplayMetrics metrics) {
        binding.detailThumbnailImageView.setLayoutParams(
                new FrameLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT, newHeight));
        binding.detailThumbnailImageView.setMinimumHeight(newHeight);
        if (player != null) {
            final int maxHeight = (int) (metrics.heightPixels * MAX_PLAYER_HEIGHT);
            player.getSurfaceView()
                    .setHeights(newHeight, player.isFullscreen() ? newHeight : maxHeight);
        }
    }

    private void showContent() {
        binding.detailContentRootHiding.setVisibility(View.VISIBLE);
    }

    protected void setInitialData(final int newServiceId,
                                  @Nullable final String newUrl,
                                  @NonNull final String newTitle,
                                  @Nullable final PlayQueue newPlayQueue) {
        this.serviceId = newServiceId;
        this.url = newUrl;
        this.title = newTitle;
        this.playQueue = newPlayQueue;
    }

    private void setErrorImage(final int imageResource) {
        if (binding == null || activity == null) {
            return;
        }

        binding.detailThumbnailImageView.setImageDrawable(
                AppCompatResources.getDrawable(requireContext(), imageResource));
        animate(binding.detailThumbnailImageView, false, 0, AnimationType.ALPHA,
                0, () -> animate(binding.detailThumbnailImageView, true, 500));
    }

    @Override
    public void handleError() {
        super.handleError();
        setErrorImage(R.drawable.not_available_monkey);

        if (binding.relatedItemsLayout != null) { // hide related streams for tablets
            binding.relatedItemsLayout.setVisibility(View.INVISIBLE);
        }

        // hide comments / related streams / description tabs
        binding.viewPager.setVisibility(View.GONE);
        binding.tabLayout.setVisibility(View.GONE);
    }

    private void hideAgeRestrictedContent() {
        showTextError(getString(R.string.restricted_video,
                getString(R.string.show_age_restricted_content_title)));
    }

    private void setupBroadcastReceiver() {
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(final Context context, final Intent intent) {
                switch (intent.getAction()) {
                    case ACTION_SHOW_MAIN_PLAYER:
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                        break;
                    case ACTION_HIDE_MAIN_PLAYER:
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                        break;
                    case ACTION_PLAYER_STARTED:
                        // If the state is not hidden we don't need to show the mini player
                        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                        }
                        // Rebound to the service if it was closed via notification or mini player
                        if (!PlayerHolder.bound) {
                            PlayerHolder.startService(
                                    App.getApp(), false, VideoDetailFragment.this);
                        }
                        break;
                }
            }
        };
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHOW_MAIN_PLAYER);
        intentFilter.addAction(ACTION_HIDE_MAIN_PLAYER);
        intentFilter.addAction(ACTION_PLAYER_STARTED);
        activity.registerReceiver(broadcastReceiver, intentFilter);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Orientation listener
    //////////////////////////////////////////////////////////////////////////*/

    private void restoreDefaultOrientation() {
        if (player == null || !player.videoPlayerSelected() || activity == null) {
            return;
        }

        if (player != null && player.isFullscreen()) {
            player.toggleFullscreen();
        }
        // This will show systemUI and pause the player.
        // User can tap on Play button and video will be in fullscreen mode again
        // Note for tablet: trying to avoid orientation changes since it's not easy
        // to physically rotate the tablet every time
        if (!DeviceUtils.isTablet(activity)) {
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {

        super.showLoading();

        //if data is already cached, transition from VISIBLE -> INVISIBLE -> VISIBLE is not required
        if (!ExtractorHelper.isCached(serviceId, url, InfoItem.InfoType.STREAM)) {
            binding.detailContentRootHiding.setVisibility(View.INVISIBLE);
        }

        animate(binding.detailThumbnailPlayButton, false, 50);
        animate(binding.detailDurationView, false, 100);
        animate(binding.detailPositionView, false, 100);
        animate(binding.positionView, false, 50);

        binding.detailVideoTitleView.setText(title);
        binding.detailVideoTitleView.setMaxLines(1);
        animate(binding.detailVideoTitleView, true, 0);

        binding.detailToggleSecondaryControlsView.setVisibility(View.GONE);
        binding.detailTitleRootLayout.setClickable(false);
        binding.detailSecondaryControlPanel.setVisibility(View.GONE);

        if (binding.relatedItemsLayout != null) {
            if (showRelatedItems) {
                binding.relatedItemsLayout.setVisibility(
                        player != null && player.isFullscreen() ? View.GONE : View.INVISIBLE);
            } else {
                binding.relatedItemsLayout.setVisibility(View.GONE);
            }
        }

        IMAGE_LOADER.cancelDisplayTask(binding.detailThumbnailImageView);
        IMAGE_LOADER.cancelDisplayTask(binding.detailSubChannelThumbnailView);
        binding.detailThumbnailImageView.setImageBitmap(null);
        binding.detailSubChannelThumbnailView.setImageBitmap(null);
    }

    @Override
    public void handleResult(@NonNull final StreamInfo info) {
        super.handleResult(info);

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName(), playQueue);

        updateTabs(info);

        animate(binding.detailThumbnailPlayButton, true, 200);
        binding.detailVideoTitleView.setText(title);

        if (!isEmpty(info.getSubChannelName())) {
            displayBothUploaderAndSubChannel(info);
        } else if (!isEmpty(info.getUploaderName())) {
            displayUploaderAsSubChannel(info);
        } else {
            binding.detailUploaderTextView.setVisibility(View.GONE);
            binding.detailUploaderThumbnailView.setVisibility(View.GONE);
        }

        final Drawable buddyDrawable = AppCompatResources.getDrawable(activity, R.drawable.buddy);
        binding.detailSubChannelThumbnailView.setImageDrawable(buddyDrawable);
        binding.detailUploaderThumbnailView.setImageDrawable(buddyDrawable);

        if (info.getViewCount() >= 0) {
            if (info.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                binding.detailViewCountView.setText(Localization.listeningCount(activity,
                        info.getViewCount()));
            } else if (info.getStreamType().equals(StreamType.LIVE_STREAM)) {
                binding.detailViewCountView.setText(Localization
                        .localizeWatchingCount(activity, info.getViewCount()));
            } else {
                binding.detailViewCountView.setText(Localization
                        .localizeViewCount(activity, info.getViewCount()));
            }
            binding.detailViewCountView.setVisibility(View.VISIBLE);
        } else {
            binding.detailViewCountView.setVisibility(View.GONE);
        }

        if (info.getDislikeCount() == -1 && info.getLikeCount() == -1) {
            binding.detailThumbsDownImgView.setVisibility(View.VISIBLE);
            binding.detailThumbsUpImgView.setVisibility(View.VISIBLE);
            binding.detailThumbsUpCountView.setVisibility(View.GONE);
            binding.detailThumbsDownCountView.setVisibility(View.GONE);

            binding.detailThumbsDisabledView.setVisibility(View.VISIBLE);
        } else {
            if (info.getDislikeCount() >= 0) {
                binding.detailThumbsDownCountView.setText(Localization
                        .shortCount(activity, info.getDislikeCount()));
                binding.detailThumbsDownCountView.setVisibility(View.VISIBLE);
                binding.detailThumbsDownImgView.setVisibility(View.VISIBLE);
            } else {
                binding.detailThumbsDownCountView.setVisibility(View.GONE);
                binding.detailThumbsDownImgView.setVisibility(View.GONE);
            }

            if (info.getLikeCount() >= 0) {
                binding.detailThumbsUpCountView.setText(Localization.shortCount(activity,
                        info.getLikeCount()));
                binding.detailThumbsUpCountView.setVisibility(View.VISIBLE);
                binding.detailThumbsUpImgView.setVisibility(View.VISIBLE);
            } else {
                binding.detailThumbsUpCountView.setVisibility(View.GONE);
                binding.detailThumbsUpImgView.setVisibility(View.GONE);
            }
            binding.detailThumbsDisabledView.setVisibility(View.GONE);
        }

        if (info.getDuration() > 0) {
            binding.detailDurationView.setText(Localization.getDurationString(info.getDuration()));
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.duration_background_color));
            animate(binding.detailDurationView, true, 100);
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            binding.detailDurationView.setText(R.string.duration_live);
            binding.detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animate(binding.detailDurationView, true, 100);
        } else {
            binding.detailDurationView.setVisibility(View.GONE);
        }

        binding.detailTitleRootLayout.setClickable(true);
        binding.detailToggleSecondaryControlsView.setRotation(0);
        binding.detailToggleSecondaryControlsView.setVisibility(View.VISIBLE);
        binding.detailSecondaryControlPanel.setVisibility(View.GONE);

        sortedVideoStreams = ListHelper.getSortedStreamVideosList(
                activity,
                info.getVideoStreams(),
                info.getVideoOnlyStreams(),
                false);
        selectedVideoStreamIndex = ListHelper
                .getDefaultResolutionIndex(activity, sortedVideoStreams);
        updateProgressInfo(info);
        initThumbnailViews(info);
        disposables.add(showMetaInfoInTextView(info.getMetaInfo(), binding.detailMetaInfoTextView,
                binding.detailMetaInfoSeparator));

        if (player == null || player.isStopped()) {
            updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnailUrl());
        }

        if (!info.getErrors().isEmpty()) {
            // Bandcamp fan pages are not yet supported and thus a ContentNotAvailableException is
            // thrown. This is not an error and thus should not be shown to the user.
            for (final Throwable throwable : info.getErrors()) {
                if (throwable instanceof ContentNotSupportedException
                        && "Fan pages are not supported".equals(throwable.getMessage())) {
                    info.getErrors().remove(throwable);
                }
            }

            if (!info.getErrors().isEmpty()) {
                showSnackBarError(new ErrorInfo(info.getErrors(),
                        UserAction.REQUESTED_STREAM, info.getUrl(), info));
            }
        }

        binding.detailControlsDownload.setVisibility(info.getStreamType() == StreamType.LIVE_STREAM
                || info.getStreamType() == StreamType.AUDIO_LIVE_STREAM ? View.GONE : View.VISIBLE);
        binding.detailControlsBackground.setVisibility(info.getAudioStreams().isEmpty()
                ? View.GONE : View.VISIBLE);

        final boolean noVideoStreams =
                info.getVideoStreams().isEmpty() && info.getVideoOnlyStreams().isEmpty();
        binding.detailControlsPopup.setVisibility(noVideoStreams ? View.GONE : View.VISIBLE);
        binding.detailThumbnailPlayButton.setImageResource(
                noVideoStreams ? R.drawable.ic_headset_shadow : R.drawable.ic_play_arrow_shadow);
    }

    private void displayUploaderAsSubChannel(final StreamInfo info) {
        binding.detailSubChannelTextView.setText(info.getUploaderName());
        binding.detailSubChannelTextView.setVisibility(View.VISIBLE);
        binding.detailSubChannelTextView.setSelected(true);
        binding.detailUploaderTextView.setVisibility(View.GONE);
    }

    private void displayBothUploaderAndSubChannel(final StreamInfo info) {
        binding.detailSubChannelTextView.setText(info.getSubChannelName());
        binding.detailSubChannelTextView.setVisibility(View.VISIBLE);
        binding.detailSubChannelTextView.setSelected(true);

        binding.detailSubChannelThumbnailView.setVisibility(View.VISIBLE);

        if (!isEmpty(info.getUploaderName())) {
            binding.detailUploaderTextView.setText(
                    String.format(getString(R.string.video_detail_by), info.getUploaderName()));
            binding.detailUploaderTextView.setVisibility(View.VISIBLE);
            binding.detailUploaderTextView.setSelected(true);
        } else {
            binding.detailUploaderTextView.setVisibility(View.GONE);
        }
    }

    public void openDownloadDialog() {
        if (currentInfo == null) {
            return;
        }

        try {
            final DownloadDialog downloadDialog = DownloadDialog.newInstance(currentInfo);
            downloadDialog.setVideoStreams(sortedVideoStreams);
            downloadDialog.setAudioStreams(currentInfo.getAudioStreams());
            downloadDialog.setSelectedVideoStream(selectedVideoStreamIndex);
            downloadDialog.setSubtitleStreams(currentInfo.getSubtitles());

            downloadDialog.show(activity.getSupportFragmentManager(), "downloadDialog");
        } catch (final Exception e) {
            ErrorActivity.reportErrorInSnackbar(activity,
                    new ErrorInfo(e, UserAction.DOWNLOAD_OPEN_DIALOG, "Showing download dialog",
                            currentInfo));
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    private void updateProgressInfo(@NonNull final StreamInfo info) {
        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean playbackResumeEnabled = prefs
                .getBoolean(activity.getString(R.string.enable_watch_history_key), true)
                && prefs.getBoolean(activity.getString(R.string.enable_playback_resume_key), true);
        final boolean showPlaybackPosition = prefs.getBoolean(
                activity.getString(R.string.enable_playback_state_lists_key), true);
        if (!playbackResumeEnabled) {
            if (playQueue == null || playQueue.getStreams().isEmpty()
                    || playQueue.getItem().getRecoveryPosition() == RECOVERY_UNSET
                    || !showPlaybackPosition) {
                binding.positionView.setVisibility(View.INVISIBLE);
                binding.detailPositionView.setVisibility(View.GONE);
                // TODO: Remove this check when separation of concerns is done.
                //  (live streams weren't getting updated because they are mixed)
                if (!info.getStreamType().equals(StreamType.LIVE_STREAM)
                        && !info.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                    return;
                }
            } else {
                // Show saved position from backStack if user allows it
                showPlaybackProgress(playQueue.getItem().getRecoveryPosition(),
                        playQueue.getItem().getDuration() * 1000);
                animate(binding.positionView, true, 500);
                animate(binding.detailPositionView, true, 500);
            }
            return;
        }
        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());

        // TODO: Separate concerns when updating database data.
        //  (move the updating part to when the loading happens)
        positionSubscriber = recordManager.loadStreamState(info)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    showPlaybackProgress(state.getProgressTime(), info.getDuration() * 1000);
                    animate(binding.positionView, true, 500);
                    animate(binding.detailPositionView, true, 500);
                }, e -> {
                    if (DEBUG) {
                        e.printStackTrace();
                    }
                }, () -> {
                    binding.positionView.setVisibility(View.GONE);
                    binding.detailPositionView.setVisibility(View.GONE);
                });
    }

    private void showPlaybackProgress(final long progress, final long duration) {
        final int progressSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(progress);
        final int durationSeconds = (int) TimeUnit.MILLISECONDS.toSeconds(duration);
        // If the old and the new progress values have a big difference then use
        // animation. Otherwise don't because it affects CPU
        final boolean shouldAnimate = Math.abs(binding.positionView.getProgress()
                - progressSeconds) > 2;
        binding.positionView.setMax(durationSeconds);
        if (shouldAnimate) {
            binding.positionView.setProgressAnimated(progressSeconds);
        } else {
            binding.positionView.setProgress(progressSeconds);
        }
        final String position = Localization.getDurationString(progressSeconds);
        if (position != binding.detailPositionView.getText()) {
            binding.detailPositionView.setText(position);
        }
        if (binding.positionView.getVisibility() != View.VISIBLE) {
            animate(binding.positionView, true, 100);
            animate(binding.detailPositionView, true, 100);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player event listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onQueueUpdate(final PlayQueue queue) {
        playQueue = queue;
        if (DEBUG) {
            Log.d(TAG, "onQueueUpdate() called with: serviceId = ["
                    + serviceId + "], videoUrl = [" + url + "], name = ["
                    + title + "], playQueue = [" + playQueue + "]");
        }

        // This should be the only place where we push data to stack.
        // It will allow to have live instance of PlayQueue with actual information about
        // deleted/added items inside Channel/Playlist queue and makes possible to have
        // a history of played items
        @Nullable final StackItem stackPeek = stack.peek();
        if (stackPeek != null && !stackPeek.getPlayQueue().equals(queue)) {
            @Nullable final PlayQueueItem playQueueItem = queue.getItem();
            if (playQueueItem != null) {
                stack.push(new StackItem(playQueueItem.getServiceId(), playQueueItem.getUrl(),
                        playQueueItem.getTitle(), queue));
                return;
            } // else continue below
        }

        @Nullable final StackItem stackWithQueue = findQueueInStack(queue);
        if (stackWithQueue != null) {
            // On every MainPlayer service's destroy() playQueue gets disposed and
            // no longer able to track progress. That's why we update our cached disposed
            // queue with the new one that is active and have the same history.
            // Without that the cached playQueue will have an old recovery position
            stackWithQueue.setPlayQueue(queue);
        }
    }

    @Override
    public void onPlaybackUpdate(final int state,
                                 final int repeatMode,
                                 final boolean shuffled,
                                 final PlaybackParameters parameters) {
        setOverlayPlayPauseImage(player != null && player.isPlaying());

        switch (state) {
            case Player.STATE_PLAYING:
                if (binding.positionView.getAlpha() != 1.0f
                        && player.getPlayQueue() != null
                        && player.getPlayQueue().getItem() != null
                        && player.getPlayQueue().getItem().getUrl().equals(url)) {
                    animate(binding.positionView, true, 100);
                    animate(binding.detailPositionView, true, 100);
                }
                break;
        }
    }

    @Override
    public void onProgressUpdate(final int currentProgress,
                                 final int duration,
                                 final int bufferPercent) {
        // Progress updates every second even if media is paused. It's useless until playing
        if (!player.isPlaying() || playQueue == null) {
            return;
        }

        if (player.getPlayQueue().getItem().getUrl().equals(url)) {
            showPlaybackProgress(currentProgress, duration);
        }
    }

    @Override
    public void onMetadataUpdate(final StreamInfo info, final PlayQueue queue) {
        final StackItem item = findQueueInStack(queue);
        if (item != null) {
            // When PlayQueue can have multiple streams (PlaylistPlayQueue or ChannelPlayQueue)
            // every new played stream gives new title and url.
            // StackItem contains information about first played stream. Let's update it here
            item.setTitle(info.getName());
            item.setUrl(info.getUrl());
        }
        // They are not equal when user watches something in popup while browsing in fragment and
        // then changes screen orientation. In that case the fragment will set itself as
        // a service listener and will receive initial call to onMetadataUpdate()
        if (!queue.equals(playQueue)) {
            return;
        }

        updateOverlayData(info.getName(), info.getUploaderName(), info.getThumbnailUrl());
        if (currentInfo != null && info.getUrl().equals(currentInfo.getUrl())) {
            return;
        }

        currentInfo = info;
        setInitialData(info.getServiceId(), info.getUrl(), info.getName(), queue);
        setAutoPlay(false);
        // Delay execution just because it freezes the main thread, and while playing
        // next/previous video you see visual glitches
        // (when non-vertical video goes after vertical video)
        prepareAndHandleInfoIfNeededAfterDelay(info, true, 200);
    }

    @Override
    public void onPlayerError(final ExoPlaybackException error) {
        if (error.type == ExoPlaybackException.TYPE_SOURCE
                || error.type == ExoPlaybackException.TYPE_UNEXPECTED) {
            // Properly exit from fullscreen
            if (playerService != null && player.isFullscreen()) {
                player.toggleFullscreen();
            }
            hideMainPlayer();
        }
    }

    @Override
    public void onServiceStopped() {
        setOverlayPlayPauseImage(false);
        if (currentInfo != null) {
            updateOverlayData(currentInfo.getName(),
                    currentInfo.getUploaderName(),
                    currentInfo.getThumbnailUrl());
        }
    }

    @Override
    public void onFullscreenStateChanged(final boolean fullscreen) {
        setupBrightness();
        if (playerService.getView() == null || player.getParentActivity() == null) {
            return;
        }

        final View view = playerService.getView();
        final ViewGroup parent = (ViewGroup) view.getParent();
        if (parent == null) {
            return;
        }

        if (fullscreen) {
            hideSystemUiIfNeeded();
            binding.overlayPlayPauseButton.requestFocus();
        } else {
            showSystemUi();
        }

        if (binding.relatedItemsLayout != null) {
            binding.relatedItemsLayout.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        }
        scrollToTop();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            addVideoPlayerView();
        } else {
            // KitKat needs a delay before addVideoPlayerView call or it reports wrong height in
            // activity.getWindow().getDecorView().getHeight()
            new Handler().post(this::addVideoPlayerView);
        }
    }

    @Override
    public void onScreenRotationButtonClicked() {
        // In tablet user experience will be better if screen will not be rotated
        // from landscape to portrait every time.
        // Just turn on fullscreen mode in landscape orientation
        // or portrait & unlocked global orientation
        if (DeviceUtils.isTablet(activity)
                && (!globalScreenOrientationLocked(activity) || isLandscape())) {
            player.toggleFullscreen();
            return;
        }

        final int newOrientation = isLandscape()
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;

        activity.setRequestedOrientation(newOrientation);
    }

    /*
     * Will scroll down to description view after long click on moreOptionsButton
     * */
    @Override
    public void onMoreOptionsLongClicked() {
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) binding.appBarLayout.getLayoutParams();
        final AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();
        final ValueAnimator valueAnimator = ValueAnimator
                .ofInt(0, -binding.playerPlaceholder.getHeight());
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(animation -> {
            behavior.setTopAndBottomOffset((int) animation.getAnimatedValue());
            binding.appBarLayout.requestLayout();
        });
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.setDuration(500);
        valueAnimator.start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Player related utils
    //////////////////////////////////////////////////////////////////////////*/

    private void showSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "showSystemUi() called");
        }

        if (activity == null) {
            return;
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(0);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(ThemeHelper.resolveColorFromAttr(
                    requireContext(), android.R.attr.colorPrimary));
        }
    }

    private void hideSystemUi() {
        if (DEBUG) {
            Log.d(TAG, "hideSystemUi() called");
        }

        if (activity == null) {
            return;
        }

        // Prevent jumping of the player on devices with cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        int visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        // In multiWindow mode status bar is not transparent for devices with cutout
        // if I include this flag. So without it is better in this case
        if (!isInMultiWindow()) {
            visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
        }
        activity.getWindow().getDecorView().setSystemUiVisibility(visibility);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && (isInMultiWindow() || (player != null && player.isFullscreen()))) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    // Listener implementation
    public void hideSystemUiIfNeeded() {
        if (player != null
                && player.isFullscreen()
                && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            hideSystemUi();
        }
    }

    private boolean playerIsNotStopped() {
        return player != null && !player.isStopped();
    }

    private void restoreDefaultBrightness() {
        final WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        if (lp.screenBrightness == -1) {
            return;
        }

        // Restore the old  brightness when fragment.onPause() called or
        // when a player is in portrait
        lp.screenBrightness = -1;
        activity.getWindow().setAttributes(lp);
    }

    private void setupBrightness() {
        if (activity == null) {
            return;
        }

        final WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
        if (player == null
                || !player.videoPlayerSelected()
                || !player.isFullscreen()
                || bottomSheetState != BottomSheetBehavior.STATE_EXPANDED) {
            // Apply system brightness when the player is not in fullscreen
            restoreDefaultBrightness();
        } else {
            // Do not restore if user has disabled brightness gesture
            if (!PlayerHelper.isBrightnessGestureEnabled(activity)) {
                return;
            }
            // Restore already saved brightness level
            final float brightnessLevel = PlayerHelper.getScreenBrightness(activity);
            if (brightnessLevel == lp.screenBrightness) {
                return;
            }
            lp.screenBrightness = brightnessLevel;
            activity.getWindow().setAttributes(lp);
        }
    }

    private void checkLandscape() {
        if ((!player.isPlaying() && player.getPlayQueue() != playQueue)
                || player.getPlayQueue() == null) {
            setAutoPlay(true);
        }

        player.checkLandscape();
        // Let's give a user time to look at video information page if video is not playing
        if (globalScreenOrientationLocked(activity) && !player.isPlaying()) {
            player.play();
        }
    }

    private boolean isLandscape() {
        return getResources().getDisplayMetrics().heightPixels < getResources()
                .getDisplayMetrics().widthPixels;
    }

    private boolean isInMultiWindow() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode();
    }

    /*
     * Means that the player fragment was swiped away via BottomSheetLayout
     * and is empty but ready for any new actions. See cleanUp()
     * */
    private boolean wasCleared() {
        return url == null;
    }

    @Nullable
    private StackItem findQueueInStack(final PlayQueue queue) {
        StackItem item = null;
        final Iterator<StackItem> iterator = stack.descendingIterator();
        while (iterator.hasNext()) {
            final StackItem next = iterator.next();
            if (next.getPlayQueue().equals(queue)) {
                item = next;
                break;
            }
        }
        return item;
    }

    private void replaceQueueIfUserConfirms(final Runnable onAllow) {
        @Nullable final PlayQueue activeQueue = player == null ? null : player.getPlayQueue();

        // Player will have STATE_IDLE when a user pressed back button
        if (isClearingQueueConfirmationRequired(activity)
                && playerIsNotStopped()
                && activeQueue != null
                && !activeQueue.equals(playQueue)) {
            showClearingQueueConfirmation(onAllow);
        } else {
            onAllow.run();
        }
    }

    private void showClearingQueueConfirmation(final Runnable onAllow) {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.clear_queue_confirmation_description)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    onAllow.run();
                    dialog.dismiss();
                }).show();
    }

    private void showExternalPlaybackDialog() {
        if (sortedVideoStreams == null) {
            return;
        }
        final CharSequence[] resolutions = new CharSequence[sortedVideoStreams.size()];
        for (int i = 0; i < sortedVideoStreams.size(); i++) {
            resolutions[i] = sortedVideoStreams.get(i).getResolution();
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.open_in_browser, (dialog, i) ->
                        ShareUtils.openUrlInBrowser(requireActivity(), url)
                );
        // Maybe there are no video streams available, show just `open in browser` button
        if (resolutions.length > 0) {
            builder.setSingleChoiceItems(resolutions, selectedVideoStreamIndex, (dialog, i) -> {
                        dialog.dismiss();
                        startOnExternalPlayer(activity, currentInfo, sortedVideoStreams.get(i));
                    }
            );
        }
        builder.show();
    }

    /*
     * Remove unneeded information while waiting for a next task
     * */
    private void cleanUp() {
        // New beginning
        stack.clear();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        PlayerHolder.stopService(App.getApp());
        setInitialData(0, null, "", null);
        currentInfo = null;
        updateOverlayData(null, null, null);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Bottom mini player
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * That's for Android TV support. Move focus from main fragment to the player or back
     * based on what is currently selected
     *
     * @param toMain if true than the main fragment will be focused or the player otherwise
     */
    private void moveFocusToMainFragment(final boolean toMain) {
        setupBrightness();
        final ViewGroup mainFragment = requireActivity().findViewById(R.id.fragment_holder);
        // Hamburger button steels a focus even under bottomSheet
        final Toolbar toolbar = requireActivity().findViewById(R.id.toolbar);
        final int afterDescendants = ViewGroup.FOCUS_AFTER_DESCENDANTS;
        final int blockDescendants = ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        if (toMain) {
            mainFragment.setDescendantFocusability(afterDescendants);
            toolbar.setDescendantFocusability(afterDescendants);
            ((ViewGroup) requireView()).setDescendantFocusability(blockDescendants);
            mainFragment.requestFocus();
        } else {
            mainFragment.setDescendantFocusability(blockDescendants);
            toolbar.setDescendantFocusability(blockDescendants);
            ((ViewGroup) requireView()).setDescendantFocusability(afterDescendants);
            binding.detailThumbnailRootLayout.requestFocus();
        }
    }

    /**
     * When the mini player exists the view underneath it is not touchable.
     * Bottom padding should be equal to the mini player's height in this case
     *
     * @param showMore whether main fragment should be expanded or not
     */
    private void manageSpaceAtTheBottom(final boolean showMore) {
        final int peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height);
        final ViewGroup holder = requireActivity().findViewById(R.id.fragment_holder);
        final int newBottomPadding;
        if (showMore) {
            newBottomPadding = 0;
        } else {
            newBottomPadding = peekHeight;
        }
        if (holder.getPaddingBottom() == newBottomPadding) {
            return;
        }
        holder.setPadding(holder.getPaddingLeft(),
                holder.getPaddingTop(),
                holder.getPaddingRight(),
                newBottomPadding);
    }

    private void setupBottomPlayer() {
        final CoordinatorLayout.LayoutParams params =
                (CoordinatorLayout.LayoutParams) binding.appBarLayout.getLayoutParams();
        final AppBarLayout.Behavior behavior = (AppBarLayout.Behavior) params.getBehavior();

        final FrameLayout bottomSheetLayout = activity.findViewById(R.id.fragment_player_holder);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetLayout);
        bottomSheetBehavior.setState(bottomSheetState);
        final int peekHeight = getResources().getDimensionPixelSize(R.dimen.mini_player_height);
        if (bottomSheetState != BottomSheetBehavior.STATE_HIDDEN) {
            manageSpaceAtTheBottom(false);
            bottomSheetBehavior.setPeekHeight(peekHeight);
            if (bottomSheetState == BottomSheetBehavior.STATE_COLLAPSED) {
                binding.overlayLayout.setAlpha(MAX_OVERLAY_ALPHA);
            } else if (bottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                binding.overlayLayout.setAlpha(0);
                setOverlayElementsClickable(false);
            }
        }

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull final View bottomSheet, final int newState) {
                bottomSheetState = newState;

                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        moveFocusToMainFragment(true);
                        manageSpaceAtTheBottom(true);

                        bottomSheetBehavior.setPeekHeight(0);
                        cleanUp();
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        moveFocusToMainFragment(false);
                        manageSpaceAtTheBottom(false);

                        bottomSheetBehavior.setPeekHeight(peekHeight);
                        // Disable click because overlay buttons located on top of buttons
                        // from the player
                        setOverlayElementsClickable(false);
                        hideSystemUiIfNeeded();
                        // Conditions when the player should be expanded to fullscreen
                        if (isLandscape()
                                && player != null
                                && player.isPlaying()
                                && !player.isFullscreen()
                                && !DeviceUtils.isTablet(activity)
                                && player.videoPlayerSelected()) {
                            player.toggleFullscreen();
                        }
                        setOverlayLook(binding.appBarLayout, behavior, 1);
                        break;
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        moveFocusToMainFragment(true);
                        manageSpaceAtTheBottom(false);

                        bottomSheetBehavior.setPeekHeight(peekHeight);

                        // Re-enable clicks
                        setOverlayElementsClickable(true);
                        if (player != null) {
                            player.closeItemsList();
                        }
                        setOverlayLook(binding.appBarLayout, behavior, 0);
                        break;
                    case BottomSheetBehavior.STATE_DRAGGING:
                    case BottomSheetBehavior.STATE_SETTLING:
                        if (player != null && player.isFullscreen()) {
                            showSystemUi();
                        }
                        if (player != null && player.isControlsVisible()) {
                            player.hideControls(0, 0);
                        }
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull final View bottomSheet, final float slideOffset) {
                setOverlayLook(binding.appBarLayout, behavior, slideOffset);
            }
        });

        // User opened a new page and the player will hide itself
        activity.getSupportFragmentManager().addOnBackStackChangedListener(() -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });
    }

    private void updateOverlayData(@Nullable final String overlayTitle,
                                   @Nullable final String uploader,
                                   @Nullable final String thumbnailUrl) {
        binding.overlayTitleTextView.setText(isEmpty(overlayTitle) ? "" : overlayTitle);
        binding.overlayChannelTextView.setText(isEmpty(uploader) ? "" : uploader);
        binding.overlayThumbnail.setImageResource(R.drawable.dummy_thumbnail_dark);
        if (!isEmpty(thumbnailUrl)) {
            IMAGE_LOADER.displayImage(thumbnailUrl, binding.overlayThumbnail,
                    ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, null);
        }
    }

    private void setOverlayPlayPauseImage(final boolean playerIsPlaying) {
        final int drawable = playerIsPlaying
                ? R.drawable.ic_pause
                : R.drawable.ic_play_arrow;
        binding.overlayPlayPauseButton.setImageResource(drawable);
    }

    private void setOverlayLook(final AppBarLayout appBar,
                                final AppBarLayout.Behavior behavior,
                                final float slideOffset) {
        // SlideOffset < 0 when mini player is about to close via swipe.
        // Stop animation in this case
        if (behavior == null || slideOffset < 0) {
            return;
        }
        binding.overlayLayout.setAlpha(Math.min(MAX_OVERLAY_ALPHA, 1 - slideOffset));
        // These numbers are not special. They just do a cool transition
        behavior.setTopAndBottomOffset(
                (int) (-binding.detailThumbnailImageView.getHeight() * 2 * (1 - slideOffset) / 3));
        appBar.requestLayout();
    }

    private void setOverlayElementsClickable(final boolean enable) {
        binding.overlayThumbnail.setClickable(enable);
        binding.overlayThumbnail.setLongClickable(enable);
        binding.overlayMetadataLayout.setClickable(enable);
        binding.overlayMetadataLayout.setLongClickable(enable);
        binding.overlayButtonsLayout.setClickable(enable);
        binding.overlayPlayPauseButton.setClickable(enable);
        binding.overlayCloseButton.setClickable(enable);
    }
}
