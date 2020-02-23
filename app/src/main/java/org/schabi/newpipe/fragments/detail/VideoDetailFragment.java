package org.schabi.newpipe.fragments.detail;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.Description;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.EmptyFragment;
import org.schabi.newpipe.fragments.list.comments.CommentsFragment;
import org.schabi.newpipe.fragments.list.videos.RelatedVideosFragment;
import org.schabi.newpipe.local.dialog.PlaylistAppendDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.MainVideoPlayer;
import org.schabi.newpipe.player.PopupVideoPlayer;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.InfoCache;
import org.schabi.newpipe.util.KoreUtil;
import org.schabi.newpipe.util.ListHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.ShareUtils;
import org.schabi.newpipe.util.StreamItemAdapter;
import org.schabi.newpipe.util.StreamItemAdapter.StreamSizeWrapper;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.noties.markwon.Markwon;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability.COMMENTS;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class VideoDetailFragment
        extends BaseStateFragment<StreamInfo>
        implements BackPressable,
        SharedPreferences.OnSharedPreferenceChangeListener,
        View.OnClickListener,
        View.OnLongClickListener {
    public static final String AUTO_PLAY = "auto_play";

    private int updateFlags = 0;
    private static final int RELATED_STREAMS_UPDATE_FLAG = 0x1;
    private static final int RESOLUTIONS_MENU_UPDATE_FLAG = 0x2;
    private static final int TOOLBAR_ITEMS_UPDATE_FLAG = 0x4;
    private static final int COMMENTS_UPDATE_FLAG = 0x8;

    private boolean autoPlayEnabled;
    private boolean showRelatedStreams;
    private boolean showComments;
    private String selectedTabTag;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private StreamInfo currentInfo;
    private Disposable currentWorker;
    @NonNull
    private CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private Disposable positionSubscriber = null;

    private List<VideoStream> sortedVideoStreams;
    private int selectedVideoStreamIndex = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private Menu menu;

    private Spinner spinnerToolbar;

    private LinearLayout contentRootLayoutHiding;

    private View thumbnailBackgroundButton;
    private ImageView thumbnailImageView;
    private ImageView thumbnailPlayButton;
    private AnimatedProgressBar positionView;

    private View videoTitleRoot;
    private TextView videoTitleTextView;
    private ImageView videoTitleToggleArrow;
    private TextView videoCountView;

    private TextView detailControlsBackground;
    private TextView detailControlsPopup;
    private TextView detailControlsAddToPlaylist;
    private TextView detailControlsDownload;
    private TextView appendControlsDetail;
    private TextView detailDurationView;
    private TextView detailPositionView;

    private LinearLayout videoDescriptionRootLayout;
    private TextView videoUploadDateView;
    private TextView videoDescriptionView;

    private View uploaderRootLayout;
    private TextView uploaderTextView;
    private ImageView uploaderThumb;

    private TextView thumbsUpTextView;
    private ImageView thumbsUpImageView;
    private TextView thumbsDownTextView;
    private ImageView thumbsDownImageView;
    private TextView thumbsDisabledTextView;

    private static final String COMMENTS_TAB_TAG = "COMMENTS";
    private static final String RELATED_TAB_TAG = "NEXT VIDEO";
    private static final String EMPTY_TAB_TAG = "EMPTY TAB";

    private AppBarLayout appBarLayout;
    private  ViewPager viewPager;
    private TabAdaptor pageAdapter;
    private TabLayout tabLayout;
    private FrameLayout relatedStreamsLayout;


    /*////////////////////////////////////////////////////////////////////////*/

    public static VideoDetailFragment getInstance(int serviceId, String videoUrl, String name) {
        VideoDetailFragment instance = new VideoDetailFragment();
        instance.setInitialData(serviceId, videoUrl, name);
        return instance;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void
    onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        showRelatedStreams = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_next_video_key), true);

        showComments = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.show_comments_key), true);

        selectedTabTag = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(getString(R.string.stream_info_selected_tab_key), COMMENTS_TAB_TAG);

        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_detail, container, false);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentWorker != null) currentWorker.dispose();
        PreferenceManager.getDefaultSharedPreferences(getContext())
                .edit()
                .putString(getString(R.string.stream_info_selected_tab_key), pageAdapter.getItemTitle(viewPager.getCurrentItem()))
                .apply();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (updateFlags != 0) {
            if (!isLoading.get() && currentInfo != null) {
                if ((updateFlags & RELATED_STREAMS_UPDATE_FLAG) != 0) startLoading(false);
                if ((updateFlags & RESOLUTIONS_MENU_UPDATE_FLAG) != 0) setupActionBar(currentInfo);
                if ((updateFlags & COMMENTS_UPDATE_FLAG) != 0) startLoading(false);
            }

            if ((updateFlags & TOOLBAR_ITEMS_UPDATE_FLAG) != 0
                    && menu != null) {
                updateMenuItemVisibility();
            }

            updateFlags = 0;
        }

        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            selectAndLoadVideo(serviceId, url, name);
        } else if (currentInfo != null) {
            updateProgressInfo(currentInfo);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this);

        if (positionSubscriber != null) positionSubscriber.dispose();
        if (currentWorker != null) currentWorker.dispose();
        if (disposables != null) disposables.clear();
        positionSubscriber = null;
        currentWorker = null;
        disposables = null;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) Log.d(TAG, "onDestroyView() called");
        spinnerToolbar.setOnItemSelectedListener(null);
        spinnerToolbar.setAdapter(null);
        super.onDestroyView();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK) {
                    NavigationHelper.openVideoDetailFragment(getFragmentManager(), serviceId, url, name);
                } else Log.e(TAG, "ReCaptcha failed");
                break;
            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getString(R.string.show_next_video_key))) {
            showRelatedStreams = sharedPreferences.getBoolean(key, true);
            updateFlags |= RELATED_STREAMS_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.default_video_format_key))
                || key.equals(getString(R.string.default_resolution_key))
                || key.equals(getString(R.string.show_higher_resolutions_key))
                || key.equals(getString(R.string.use_external_video_player_key))) {
            updateFlags |= RESOLUTIONS_MENU_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.show_play_with_kodi_key))) {
            updateFlags |= TOOLBAR_ITEMS_UPDATE_FLAG;
        } else if (key.equals(getString(R.string.show_comments_key))) {
            showComments = sharedPreferences.getBoolean(key, true);
            updateFlags |= COMMENTS_UPDATE_FLAG;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    private static final String INFO_KEY = "info_key";
    private static final String STACK_KEY = "stack_key";

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        // Check if the next video label and video is visible,
        // if it is, include the two elements in the next check
        int nextCount = currentInfo != null && currentInfo.getNextVideo() != null ? 2 : 0;

        if (!isLoading.get() && currentInfo != null && isVisible()) {
            outState.putSerializable(INFO_KEY, currentInfo);
        }

        outState.putSerializable(STACK_KEY, stack);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedState) {
        super.onRestoreInstanceState(savedState);

        Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof StreamInfo) {
            //noinspection unchecked
            currentInfo = (StreamInfo) serializable;
            InfoCache.getInstance().putInfo(serviceId, url, currentInfo, InfoItem.InfoType.STREAM);
        }

        serializable = savedState.getSerializable(STACK_KEY);
        if (serializable instanceof Collection) {
            //noinspection unchecked
            stack.addAll((Collection<? extends StackItem>) serializable);
        }

    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(View v) {
        if (isLoading.get() || currentInfo == null) return;

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(false);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(false);
                break;
            case R.id.detail_controls_playlist_append:
                if (getFragmentManager() != null && currentInfo != null) {
                    PlaylistAppendDialog.fromStreamInfo(currentInfo)
                            .show(getFragmentManager(), TAG);
                }
                break;
            case R.id.detail_controls_download:
                if (PermissionHelper.checkStoragePermissions(activity,
                        PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE)) {
                    this.openDownloadDialog();
                }
                break;
            case R.id.detail_uploader_root_layout:
                if (TextUtils.isEmpty(currentInfo.getUploaderUrl())) {
                    Log.w(TAG, "Can't open channel because we got no channel URL");
                } else {
                    try {
                    NavigationHelper.openChannelFragment(
                            getFragmentManager(),
                            currentInfo.getServiceId(),
                            currentInfo.getUploaderUrl(),
                            currentInfo.getUploaderName());
                    } catch (Exception e) {
                        ErrorActivity.reportUiError((AppCompatActivity) getActivity(), e);
                }
                }
                break;
            case R.id.detail_thumbnail_root_layout:
                if (currentInfo.getVideoStreams().isEmpty()
                        && currentInfo.getVideoOnlyStreams().isEmpty()) {
                    openBackgroundPlayer(false);
                } else {
                    openVideoPlayer();
                }
                break;
            case R.id.detail_title_root_layout:
                toggleTitleAndDescription();
                break;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        if (isLoading.get() || currentInfo == null) return false;

        switch (v.getId()) {
            case R.id.detail_controls_background:
                openBackgroundPlayer(true);
                break;
            case R.id.detail_controls_popup:
                openPopupPlayer(true);
                break;
            case R.id.detail_controls_download:
                NavigationHelper.openDownloads(getActivity());
                break;
        }

        return true;
    }

    private void toggleTitleAndDescription() {
        if (videoDescriptionRootLayout.getVisibility() == View.VISIBLE) {
            videoTitleTextView.setMaxLines(1);
            videoDescriptionRootLayout.setVisibility(View.GONE);
            videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        } else {
            videoTitleTextView.setMaxLines(10);
            videoDescriptionRootLayout.setVisibility(View.VISIBLE);
            videoTitleToggleArrow.setImageResource(R.drawable.arrow_up);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(View rootView, Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        spinnerToolbar = activity.findViewById(R.id.toolbar).findViewById(R.id.toolbar_spinner);

        thumbnailBackgroundButton = rootView.findViewById(R.id.detail_thumbnail_root_layout);
        thumbnailImageView = rootView.findViewById(R.id.detail_thumbnail_image_view);
        thumbnailPlayButton = rootView.findViewById(R.id.detail_thumbnail_play_button);

        contentRootLayoutHiding = rootView.findViewById(R.id.detail_content_root_hiding);

        videoTitleRoot = rootView.findViewById(R.id.detail_title_root_layout);
        videoTitleTextView = rootView.findViewById(R.id.detail_video_title_view);
        videoTitleToggleArrow = rootView.findViewById(R.id.detail_toggle_description_view);
        videoCountView = rootView.findViewById(R.id.detail_view_count_view);
        positionView = rootView.findViewById(R.id.position_view);

        detailControlsBackground = rootView.findViewById(R.id.detail_controls_background);
        detailControlsPopup = rootView.findViewById(R.id.detail_controls_popup);
        detailControlsAddToPlaylist = rootView.findViewById(R.id.detail_controls_playlist_append);
        detailControlsDownload = rootView.findViewById(R.id.detail_controls_download);
        appendControlsDetail = rootView.findViewById(R.id.touch_append_detail);
        detailDurationView = rootView.findViewById(R.id.detail_duration_view);
        detailPositionView = rootView.findViewById(R.id.detail_position_view);

        videoDescriptionRootLayout = rootView.findViewById(R.id.detail_description_root_layout);
        videoUploadDateView = rootView.findViewById(R.id.detail_upload_date_view);
        videoDescriptionView = rootView.findViewById(R.id.detail_description_view);
        videoDescriptionView.setMovementMethod(LinkMovementMethod.getInstance());

        thumbsUpTextView = rootView.findViewById(R.id.detail_thumbs_up_count_view);
        thumbsUpImageView = rootView.findViewById(R.id.detail_thumbs_up_img_view);
        thumbsDownTextView = rootView.findViewById(R.id.detail_thumbs_down_count_view);
        thumbsDownImageView = rootView.findViewById(R.id.detail_thumbs_down_img_view);
        thumbsDisabledTextView = rootView.findViewById(R.id.detail_thumbs_disabled_view);

        uploaderRootLayout = rootView.findViewById(R.id.detail_uploader_root_layout);
        uploaderTextView = rootView.findViewById(R.id.detail_uploader_text_view);
        uploaderThumb = rootView.findViewById(R.id.detail_uploader_thumbnail_view);

        appBarLayout = rootView.findViewById(R.id.appbarlayout);
        viewPager = rootView.findViewById(R.id.viewpager);
        pageAdapter = new TabAdaptor(getChildFragmentManager());
        viewPager.setAdapter(pageAdapter);
        tabLayout = rootView.findViewById(R.id.tablayout);
        tabLayout.setupWithViewPager(viewPager);

        relatedStreamsLayout = rootView.findViewById(R.id.relatedStreamsLayout);

        setHeightThumbnail();


    }

    @Override
    protected void initListeners() {
        super.initListeners();

        videoTitleRoot.setOnClickListener(this);
        uploaderRootLayout.setOnClickListener(this);
        thumbnailBackgroundButton.setOnClickListener(this);
        detailControlsBackground.setOnClickListener(this);
        detailControlsPopup.setOnClickListener(this);
        detailControlsAddToPlaylist.setOnClickListener(this);
        detailControlsDownload.setOnClickListener(this);
        detailControlsDownload.setOnLongClickListener(this);

        detailControlsBackground.setLongClickable(true);
        detailControlsPopup.setLongClickable(true);
        detailControlsBackground.setOnLongClickListener(this);
        detailControlsPopup.setOnLongClickListener(this);
        detailControlsBackground.setOnTouchListener(getOnControlsTouchListener());
        detailControlsPopup.setOnTouchListener(getOnControlsTouchListener());
    }

    private View.OnTouchListener getOnControlsTouchListener() {
        return (View view, MotionEvent motionEvent) -> {
            if (!PreferenceManager.getDefaultSharedPreferences(activity)
                    .getBoolean(getString(R.string.show_hold_to_append_key), true)) {
                return false;
            }

            if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                animateView(appendControlsDetail, true, 250, 0, () ->
                        animateView(appendControlsDetail, false, 1500, 1000));
            }
            return false;
        };
    }

    private void initThumbnailViews(@NonNull StreamInfo info) {
        thumbnailImageView.setImageResource(R.drawable.dummy_thumbnail_dark);
        if (!TextUtils.isEmpty(info.getThumbnailUrl())) {
            final String infoServiceName = NewPipe.getNameOfService(info.getServiceId());
            final ImageLoadingListener onFailListener = new SimpleImageLoadingListener() {
                @Override
                public void onLoadingFailed(String imageUri, View view, FailReason failReason) {
                    showSnackBarError(failReason.getCause(), UserAction.LOAD_IMAGE,
                            infoServiceName, imageUri, R.string.could_not_load_thumbnails);
                }
            };

            imageLoader.displayImage(info.getThumbnailUrl(), thumbnailImageView,
                    ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS, onFailListener);
        }

        if (!TextUtils.isEmpty(info.getUploaderAvatarUrl())) {
            imageLoader.displayImage(info.getUploaderAvatarUrl(), uploaderThumb,
                    ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        this.menu = menu;

        // CAUTION set item properties programmatically otherwise it would not be accepted by
        // appcompat itemsinflater.inflate(R.menu.videoitem_detail, menu);

        inflater.inflate(R.menu.video_detail_menu, menu);

        updateMenuItemVisibility();

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(true);
            supportActionBar.setDisplayShowTitleEnabled(false);
        }
    }

    private void updateMenuItemVisibility() {

        // show kodi if set in settings
        menu.findItem(R.id.action_play_with_kodi).setVisible(
                PreferenceManager.getDefaultSharedPreferences(activity).getBoolean(
                        activity.getString(R.string.show_play_with_kodi_key), false));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            NavigationHelper.openSettings(requireContext());
            return true;
        }

        if (isLoading.get()) {
            // if still loading, block menu buttons related to video info
            return true;
        }

        switch (id) {
            case R.id.menu_item_share: {
                if (currentInfo != null) {
                    ShareUtils.shareUrl(requireContext(), currentInfo.getName(), currentInfo.getOriginalUrl());
                }
                return true;
            }
            case R.id.menu_item_openInBrowser: {
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(requireContext(), currentInfo.getOriginalUrl());
                }
                return true;
            }
            case R.id.action_play_with_kodi:
                try {
                    NavigationHelper.playWithKore(activity, Uri.parse(
                            url.replace("https", "http")));
                } catch (Exception e) {
                    if (DEBUG) Log.i(TAG, "Failed to start kore", e);
                    KoreUtil.showInstallKoreDialog(activity);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setupActionBarOnError(final String url) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandlerOnError() called with: url = [" + url + "]");
        Log.e("-----", "missing code");
    }

    private void setupActionBar(final StreamInfo info) {
        if (DEBUG) Log.d(TAG, "setupActionBarHandler() called with: info = [" + info + "]");
        boolean isExternalPlayerEnabled = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_video_player_key), false);

        sortedVideoStreams = ListHelper.getSortedStreamVideosList(
                activity,
                info.getVideoStreams(),
                info.getVideoOnlyStreams(),
                false);
        selectedVideoStreamIndex = ListHelper.getDefaultResolutionIndex(activity, sortedVideoStreams);

        final StreamItemAdapter<VideoStream, Stream> streamsAdapter =
                new StreamItemAdapter<>(activity,
                        new StreamSizeWrapper<>(sortedVideoStreams, activity), isExternalPlayerEnabled);
        spinnerToolbar.setAdapter(streamsAdapter);
        spinnerToolbar.setSelection(selectedVideoStreamIndex);
        spinnerToolbar.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedVideoStreamIndex = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OwnStack
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Stack that contains the "navigation history".<br>
     * The peek is the current video.
     */
    protected final LinkedList<StackItem> stack = new LinkedList<>();

    public void pushToStack(int serviceId, String videoUrl, String name) {
        if (DEBUG) {
            Log.d(TAG, "pushToStack() called with: serviceId = ["
                    + serviceId + "], videoUrl = [" + videoUrl + "], name = [" + name + "]");
        }

        if (stack.size() > 0
                && stack.peek().getServiceId() == serviceId
                && stack.peek().getUrl().equals(videoUrl)) {
            Log.d(TAG, "pushToStack() called with: serviceId == peek.serviceId = ["
                    + serviceId + "], videoUrl == peek.getUrl = [" + videoUrl + "]");
            return;
        } else {
            Log.d(TAG, "pushToStack() wasn't equal");
        }

        stack.push(new StackItem(serviceId, videoUrl, name));
    }

    public void setTitleToUrl(int serviceId, String videoUrl, String name) {
        if (name != null && !name.isEmpty()) {
            for (StackItem stackItem : stack) {
                if (stack.peek().getServiceId() == serviceId
                        && stackItem.getUrl().equals(videoUrl)) {
                    stackItem.setTitle(name);
                }
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        if (DEBUG) Log.d(TAG, "onBackPressed() called");
        // That means that we are on the start of the stack,
        // return false to let the MainActivity handle the onBack
        if (stack.size() <= 1) return false;
        // Remove top
        stack.pop();
        // Get stack item from the new top
        StackItem peek = stack.peek();

        selectAndLoadVideo(peek.getServiceId(),
                peek.getUrl(),
                !TextUtils.isEmpty(peek.getTitle())
                        ? peek.getTitle()
                        : "");
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Info loading and handling
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void doInitialLoadLogic() {
        if (currentInfo == null) prepareAndLoadInfo();
        else prepareAndHandleInfo(currentInfo, false);
    }

    public void selectAndLoadVideo(int serviceId, String videoUrl, String name) {
        setInitialData(serviceId, videoUrl, name);
        prepareAndLoadInfo();
    }

    public void prepareAndHandleInfo(final StreamInfo info, boolean scrollToTop) {
        if (DEBUG) Log.d(TAG, "prepareAndHandleInfo() called with: info = ["
                + info + "], scrollToTop = [" + scrollToTop + "]");

        setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        pushToStack(serviceId, url, name);
        showLoading();
        initTabs();

        if (scrollToTop) appBarLayout.setExpanded(true, true);
        handleResult(info);
        showContent();

    }

    protected void prepareAndLoadInfo() {
        appBarLayout.setExpanded(true, true);
        pushToStack(serviceId, url, name);
        startLoading(false);
    }

    @Override
    public void startLoading(boolean forceLoad) {
        super.startLoading(forceLoad);

        initTabs();
        currentInfo = null;
        if (currentWorker != null) currentWorker.dispose();

        currentWorker = ExtractorHelper.getStreamInfo(serviceId, url, forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@NonNull StreamInfo result) -> {
                    isLoading.set(false);
                    currentInfo = result;
                    handleResult(result);
                    showContent();
                }, (@NonNull Throwable throwable) -> {
                    isLoading.set(false);
                    onError(throwable);
                });

    }

    private void initTabs() {
        if (pageAdapter.getCount() != 0) {
            selectedTabTag = pageAdapter.getItemTitle(viewPager.getCurrentItem());
        }
        pageAdapter.clearAllItems();

        if(shouldShowComments()){
            pageAdapter.addFragment(CommentsFragment.getInstance(serviceId, url, name), COMMENTS_TAB_TAG);
        }

        if(showRelatedStreams && null == relatedStreamsLayout){
            //temp empty fragment. will be updated in handleResult
            pageAdapter.addFragment(new Fragment(), RELATED_TAB_TAG);
        }

        if(pageAdapter.getCount() == 0){
            pageAdapter.addFragment(new EmptyFragment(), EMPTY_TAB_TAG);
        }

        pageAdapter.notifyDataSetUpdate();

        if(pageAdapter.getCount() < 2){
            tabLayout.setVisibility(View.GONE);
        }else{
            int position = pageAdapter.getItemPositionByTitle(selectedTabTag);
            if(position != -1) viewPager.setCurrentItem(position);
            tabLayout.setVisibility(View.VISIBLE);
        }
    }

    private boolean shouldShowComments() {
        try {
            return showComments && NewPipe.getService(serviceId)
                    .getServiceInfo()
                    .getMediaCapabilities()
                    .contains(COMMENTS);
        } catch (ExtractionException e) {
            return false;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Play Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void openBackgroundPlayer(final boolean append) {
        AudioStream audioStream = currentInfo.getAudioStreams()
                .get(ListHelper.getDefaultAudioFormat(activity, currentInfo.getAudioStreams()));

        boolean useExternalAudioPlayer = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(activity.getString(R.string.use_external_audio_player_key), false);

        if (!useExternalAudioPlayer && android.os.Build.VERSION.SDK_INT >= 16) {
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

        final PlayQueue itemQueue = new SinglePlayQueue(currentInfo);
        if (append) {
            NavigationHelper.enqueueOnPopupPlayer(activity, itemQueue, false);
        } else {
            Toast.makeText(activity, R.string.popup_playing_toast, Toast.LENGTH_SHORT).show();
            final Intent intent = NavigationHelper.getPlayerIntent(
                    activity, PopupVideoPlayer.class, itemQueue, getSelectedVideoStream().resolution, true
            );
            activity.startService(intent);
        }
    }

    private void openVideoPlayer() {
        VideoStream selectedVideoStream = getSelectedVideoStream();

        if (PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(this.getString(R.string.use_external_video_player_key), false)) {
            startOnExternalPlayer(activity, currentInfo, selectedVideoStream);
        } else {
            openNormalPlayer();
        }
    }

    private void openNormalBackgroundPlayer(final boolean append) {
        final PlayQueue itemQueue = new SinglePlayQueue(currentInfo);
        if (append) {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, itemQueue, false);
        } else {
            NavigationHelper.playOnBackgroundPlayer(activity, itemQueue, true);
        }
    }

    private void openNormalPlayer() {
        Intent mIntent;
        final PlayQueue playQueue = new SinglePlayQueue(currentInfo);
        mIntent = NavigationHelper.getPlayerIntent(activity,
                MainVideoPlayer.class,
                playQueue,
                getSelectedVideoStream().getResolution(), true);
        startActivity(mIntent);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setAutoplay(boolean autoplay) {
        this.autoPlayEnabled = autoplay;
    }

    private void startOnExternalPlayer(@NonNull final Context context,
                                       @NonNull final StreamInfo info,
                                       @NonNull final Stream selectedStream) {
        NavigationHelper.playOnExternalPlayer(context, currentInfo.getName(),
                currentInfo.getUploaderName(), selectedStream);

        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        disposables.add(recordManager.onViewed(info).onErrorComplete()
                .subscribe(
                        ignored -> {/* successful */},
                        error -> Log.e(TAG, "Register view failure: ", error)
                ));
    }

    @Nullable
    private VideoStream getSelectedVideoStream() {
        return sortedVideoStreams != null ? sortedVideoStreams.get(selectedVideoStreamIndex) : null;
    }

    private void prepareDescription(Description description) {
        if (TextUtils.isEmpty(description.getContent()) || description == Description.emptyDescription) {
            return;
        }

        if (description.getType() == Description.HTML) {
            disposables.add(Single.just(description.getContent())
                    .map((@io.reactivex.annotations.NonNull String descriptionText) -> {
                        Spanned parsedDescription;
                        if (Build.VERSION.SDK_INT >= 24) {
                            parsedDescription = Html.fromHtml(descriptionText, 0);
                        } else {
                            //noinspection deprecation
                            parsedDescription = Html.fromHtml(descriptionText);
                        }
                        return parsedDescription;
                    })
                    .subscribeOn(Schedulers.computation())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((@io.reactivex.annotations.NonNull Spanned spanned) -> {
                        videoDescriptionView.setText(spanned);
                        videoDescriptionView.setVisibility(View.VISIBLE);
                    }));
        } else if (description.getType() == Description.MARKDOWN) {
            final Markwon markwon = Markwon.builder(getContext())
                    .usePlugin(LinkifyPlugin.create())
                    .build();
            markwon.setMarkdown(videoDescriptionView, description.getContent());
            videoDescriptionView.setVisibility(View.VISIBLE);
        } else {
            //== Description.PLAIN_TEXT
            videoDescriptionView.setAutoLinkMask(Linkify.WEB_URLS);
            videoDescriptionView.setText(description.getContent(), TextView.BufferType.SPANNABLE);
            videoDescriptionView.setVisibility(View.VISIBLE);
        }
    }

    private void setHeightThumbnail() {
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        boolean isPortrait = metrics.heightPixels > metrics.widthPixels;
        int height = isPortrait
                ? (int) (metrics.widthPixels / (16.0f / 9.0f))
                : (int) (metrics.heightPixels / 2f);
        thumbnailImageView.setLayoutParams(
                new FrameLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height));
        thumbnailImageView.setMinimumHeight(height);
    }

    private void showContent() {
        contentRootLayoutHiding.setVisibility(View.VISIBLE);
    }

    protected void setInitialData(int serviceId, String url, String name) {
        this.serviceId = serviceId;
        this.url = url;
        this.name = !TextUtils.isEmpty(name) ? name : "";
    }

    private void setErrorImage(final int imageResource) {
        if (thumbnailImageView == null || activity == null) return;

        thumbnailImageView.setImageDrawable(ContextCompat.getDrawable(activity, imageResource));
        animateView(thumbnailImageView, false, 0, 0,
                () -> animateView(thumbnailImageView, true, 500));
    }

    @Override
    public void showError(String message, boolean showRetryButton) {
        showError(message, showRetryButton, R.drawable.not_available_monkey);
    }

    protected void showError(String message, boolean showRetryButton, @DrawableRes int imageError) {
        super.showError(message, showRetryButton);
        setErrorImage(imageError);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {

        super.showLoading();

        //if data is already cached, transition from VISIBLE -> INVISIBLE -> VISIBLE is not required
        if(!ExtractorHelper.isCached(serviceId, url, InfoItem.InfoType.STREAM)){
            contentRootLayoutHiding.setVisibility(View.INVISIBLE);
        }

        animateView(spinnerToolbar, false, 200);
        animateView(thumbnailPlayButton, false, 50);
        animateView(detailDurationView, false, 100);
        animateView(detailPositionView, false, 100);
        animateView(positionView, false, 50);

        videoTitleTextView.setText(name != null ? name : "");
        videoTitleTextView.setMaxLines(1);
        animateView(videoTitleTextView, true, 0);

        videoDescriptionRootLayout.setVisibility(View.GONE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoTitleToggleArrow.setVisibility(View.GONE);
        videoTitleRoot.setClickable(false);

        if(relatedStreamsLayout != null){
            if(showRelatedStreams){
                relatedStreamsLayout.setVisibility(View.INVISIBLE);
            }else{
                relatedStreamsLayout.setVisibility(View.GONE);
            }
        }

        imageLoader.cancelDisplayTask(thumbnailImageView);
        imageLoader.cancelDisplayTask(uploaderThumb);
        thumbnailImageView.setImageBitmap(null);
        uploaderThumb.setImageBitmap(null);
    }

    @Override
    public void handleResult(@NonNull StreamInfo info) {
        super.handleResult(info);

        setInitialData(info.getServiceId(), info.getOriginalUrl(), info.getName());

        if(showRelatedStreams){
            if(null == relatedStreamsLayout){ //phone
                pageAdapter.updateItem(RELATED_TAB_TAG, RelatedVideosFragment.getInstance(currentInfo));
                pageAdapter.notifyDataSetUpdate();
            }else{ //tablet
                getChildFragmentManager().beginTransaction()
                        .replace(R.id.relatedStreamsLayout, RelatedVideosFragment.getInstance(currentInfo))
                        .commitNow();
                relatedStreamsLayout.setVisibility(View.VISIBLE);
            }
        }

        //pushToStack(serviceId, url, name);

        animateView(thumbnailPlayButton, true, 200);
        videoTitleTextView.setText(name);

        if (!TextUtils.isEmpty(info.getUploaderName())) {
            uploaderTextView.setText(info.getUploaderName());
            uploaderTextView.setVisibility(View.VISIBLE);
            uploaderTextView.setSelected(true);
        } else {
            uploaderTextView.setVisibility(View.GONE);
        }
        uploaderThumb.setImageDrawable(ContextCompat.getDrawable(activity, R.drawable.buddy));

        if (info.getViewCount() >= 0) {
            if (info.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                videoCountView.setText(Localization.listeningCount(activity, info.getViewCount()));
            } else if (info.getStreamType().equals(StreamType.LIVE_STREAM)) {
                videoCountView.setText(Localization.localizeWatchingCount(activity, info.getViewCount()));
            } else {
                videoCountView.setText(Localization.localizeViewCount(activity, info.getViewCount()));
            }
            videoCountView.setVisibility(View.VISIBLE);
        } else {
            videoCountView.setVisibility(View.GONE);
        }

        if (info.getDislikeCount() == -1 && info.getLikeCount() == -1) {
            thumbsDownImageView.setVisibility(View.VISIBLE);
            thumbsUpImageView.setVisibility(View.VISIBLE);
            thumbsUpTextView.setVisibility(View.GONE);
            thumbsDownTextView.setVisibility(View.GONE);

            thumbsDisabledTextView.setVisibility(View.VISIBLE);
        } else {
            if (info.getDislikeCount() >= 0) {
                thumbsDownTextView.setText(Localization.shortCount(activity, info.getDislikeCount()));
                thumbsDownTextView.setVisibility(View.VISIBLE);
                thumbsDownImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsDownTextView.setVisibility(View.GONE);
                thumbsDownImageView.setVisibility(View.GONE);
            }

            if (info.getLikeCount() >= 0) {
                thumbsUpTextView.setText(Localization.shortCount(activity, info.getLikeCount()));
                thumbsUpTextView.setVisibility(View.VISIBLE);
                thumbsUpImageView.setVisibility(View.VISIBLE);
            } else {
                thumbsUpTextView.setVisibility(View.GONE);
                thumbsUpImageView.setVisibility(View.GONE);
            }
            thumbsDisabledTextView.setVisibility(View.GONE);
        }

        if (info.getDuration() > 0) {
            detailDurationView.setText(Localization.getDurationString(info.getDuration()));
            detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.duration_background_color));
            animateView(detailDurationView, true, 100);
        } else if (info.getStreamType() == StreamType.LIVE_STREAM) {
            detailDurationView.setText(R.string.duration_live);
            detailDurationView.setBackgroundColor(
                    ContextCompat.getColor(activity, R.color.live_duration_background_color));
            animateView(detailDurationView, true, 100);
        } else {
            detailDurationView.setVisibility(View.GONE);
        }

        videoDescriptionView.setVisibility(View.GONE);
        videoTitleRoot.setClickable(true);
        videoTitleToggleArrow.setVisibility(View.VISIBLE);
        videoTitleToggleArrow.setImageResource(R.drawable.arrow_down);
        videoDescriptionRootLayout.setVisibility(View.GONE);

        if (info.getUploadDate() != null) {
            videoUploadDateView.setText(Localization.localizeUploadDate(activity, info.getUploadDate().date().getTime()));
            videoUploadDateView.setVisibility(View.VISIBLE);
        } else {
            videoUploadDateView.setText(null);
            videoUploadDateView.setVisibility(View.GONE);
        }

        prepareDescription(info.getDescription());
        updateProgressInfo(info);

        animateView(spinnerToolbar, true, 500);
        setupActionBar(info);
        initThumbnailViews(info);

        setTitleToUrl(info.getServiceId(), info.getUrl(), info.getName());
        setTitleToUrl(info.getServiceId(), info.getOriginalUrl(), info.getName());

        if (!info.getErrors().isEmpty()) {
            showSnackBarError(info.getErrors(),
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(info.getServiceId()),
                    info.getUrl(),
                    0);
        }

        switch (info.getStreamType()) {
            case LIVE_STREAM:
            case AUDIO_LIVE_STREAM:
                detailControlsDownload.setVisibility(View.GONE);
                spinnerToolbar.setVisibility(View.GONE);
                break;
            default:
                if(info.getAudioStreams().isEmpty()) detailControlsBackground.setVisibility(View.GONE);
                if (!info.getVideoStreams().isEmpty()
                        || !info.getVideoOnlyStreams().isEmpty()) break;

                detailControlsPopup.setVisibility(View.GONE);
                spinnerToolbar.setVisibility(View.GONE);
                thumbnailPlayButton.setImageResource(R.drawable.ic_headset_white_24dp);
                break;
        }

        if (autoPlayEnabled) {
            openVideoPlayer();
            // Only auto play in the first open
            autoPlayEnabled = false;
        }
    }


    public void openDownloadDialog() {
            try {
                DownloadDialog downloadDialog = DownloadDialog.newInstance(currentInfo);
                downloadDialog.setVideoStreams(sortedVideoStreams);
                downloadDialog.setAudioStreams(currentInfo.getAudioStreams());
                downloadDialog.setSelectedVideoStream(selectedVideoStreamIndex);
                downloadDialog.setSubtitleStreams(currentInfo.getSubtitles());

                downloadDialog.show(getActivity().getSupportFragmentManager(), "downloadDialog");
            } catch (Exception e) {
                ErrorActivity.ErrorInfo info = ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                        ServiceList.all()
                                .get(currentInfo
                                        .getServiceId())
                                .getServiceInfo()
                                .getName(), "",
                        R.string.could_not_setup_download_menu);

                ErrorActivity.reportError(getActivity(),
                        e,
                        getActivity().getClass(),
                        getActivity().findViewById(android.R.id.content), info);
            }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Stream Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(Throwable exception) {
        if (super.onError(exception)) return true;

        else if (exception instanceof ContentNotAvailableException) {
            showError(getString(R.string.content_not_available), false);
        } else {
            int errorId = exception instanceof YoutubeStreamExtractor.DecryptException
                    ? R.string.youtube_signature_decryption_error
                    : exception instanceof ParsingException
                    ? R.string.parsing_error
                    : R.string.general_error;
            onUnrecoverableError(exception,
                    UserAction.REQUESTED_STREAM,
                    NewPipe.getNameOfService(serviceId),
                    url,
                    errorId);
        }

        return true;
    }

    private void updateProgressInfo(@NonNull final StreamInfo info) {
        if (positionSubscriber != null) {
            positionSubscriber.dispose();
        }
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        final boolean playbackResumeEnabled =
                prefs.getBoolean(activity.getString(R.string.enable_watch_history_key), true)
                        && prefs.getBoolean(activity.getString(R.string.enable_playback_resume_key), true);
        if (!playbackResumeEnabled || info.getDuration() <= 0) {
            positionView.setVisibility(View.INVISIBLE);
            detailPositionView.setVisibility(View.GONE);
            return;
        }
        final HistoryRecordManager recordManager = new HistoryRecordManager(requireContext());
        positionSubscriber = recordManager.loadStreamState(info)
                .subscribeOn(Schedulers.io())
                .onErrorComplete()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(state -> {
                    final int seconds = (int) TimeUnit.MILLISECONDS.toSeconds(state.getProgressTime());
                    positionView.setMax((int) info.getDuration());
                    positionView.setProgressAnimated(seconds);
                    detailPositionView.setText(Localization.getDurationString(seconds));
                    animateView(positionView, true, 500);
                    animateView(detailPositionView, true, 500);
                }, e -> {
                    if (DEBUG) e.printStackTrace();
                }, () -> {
                    animateView(positionView, false, 500);
                    animateView(detailPositionView, false, 500);
                });
    }
}
