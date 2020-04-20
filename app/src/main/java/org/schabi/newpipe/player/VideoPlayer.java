/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * VideoPlayer.java is part of NewPipe
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
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.SubtitleView;
import com.google.android.exoplayer2.video.VideoListener;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.resolver.MediaSourceTag;
import org.schabi.newpipe.player.resolver.VideoPlaybackResolver;
import org.schabi.newpipe.util.AnimationUtils;

import java.util.ArrayList;
import java.util.List;

import static org.schabi.newpipe.player.helper.PlayerHelper.formatSpeed;
import static org.schabi.newpipe.player.helper.PlayerHelper.getTimeString;
import static org.schabi.newpipe.util.AnimationUtils.DEFAULT_SHORT_ANIM_DURATION;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

/**
 * Base for <b>video</b> players.
 *
 * @author mauriciocolli
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class VideoPlayer extends BasePlayer
        implements VideoListener,
        SeekBar.OnSeekBarChangeListener,
        View.OnClickListener,
        Player.EventListener,
        PopupMenu.OnMenuItemClickListener,
        PopupMenu.OnDismissListener {
    public static final boolean DEBUG = BasePlayer.DEBUG;
    public static final int DEFAULT_CONTROLS_HIDE_TIME = 2000;  // 2 Seconds

    /*//////////////////////////////////////////////////////////////////////////
    // Player
    //////////////////////////////////////////////////////////////////////////*/
    protected static final int RENDERER_UNAVAILABLE = -1;
    public final String TAG;
    @NonNull
    private final VideoPlaybackResolver resolver;
    private final Handler controlsVisibilityHandler = new Handler();
    private final int qualityPopupMenuGroupId = 69;
    private final int playbackSpeedPopupMenuGroupId = 79;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/
    private final int captionPopupMenuGroupId = 89;
    protected boolean wasPlaying = false;
    boolean isSomePopupMenuVisible = false;
    private List<VideoStream> availableStreams;
    private int selectedStreamIndex;
    private View rootView;
    private AspectRatioFrameLayout aspectRatioFrameLayout;
    private SurfaceView surfaceView;
    private View surfaceForeground;
    private View loadingPanel;
    private ImageView endScreen;
    private ImageView controlAnimationView;
    private View controlsRoot;
    private TextView currentDisplaySeek;
    private View bottomControlsRoot;
    private SeekBar playbackSeekBar;
    private TextView playbackCurrentTime;
    private TextView playbackEndTime;
    private TextView playbackLiveSync;
    private TextView playbackSpeedTextView;
    private View topControlsRoot;
    private TextView qualityTextView;
    private SubtitleView subtitleView;
    private TextView resizeView;
    private TextView captionTextView;
    private ValueAnimator controlViewAnimator;
    private PopupMenu qualityPopupMenu;
    private PopupMenu playbackSpeedPopupMenu;
    private PopupMenu captionPopupMenu;

    ///////////////////////////////////////////////////////////////////////////

    public VideoPlayer(final String debugTag, final Context context) {
        super(context);
        this.TAG = debugTag;
        this.resolver = new VideoPlaybackResolver(context, dataSource, getQualityResolver());
    }

    // workaround to match normalized captions like english to English or deutsch to Deutsch
    private static boolean containsCaseInsensitive(final List<String> list, final String toFind) {
        for (String i : list) {
            if (i.equalsIgnoreCase(toFind)) {
                return true;
            }
        }
        return false;
    }

    public void setup(final View view) {
        initViews(view);
        setup();
    }

    public void initViews(final View view) {
        this.rootView = view;
        this.aspectRatioFrameLayout = view.findViewById(R.id.aspectRatioLayout);
        this.surfaceView = view.findViewById(R.id.surfaceView);
        this.surfaceForeground = view.findViewById(R.id.surfaceForeground);
        this.loadingPanel = view.findViewById(R.id.loading_panel);
        this.endScreen = view.findViewById(R.id.endScreen);
        this.controlAnimationView = view.findViewById(R.id.controlAnimationView);
        this.controlsRoot = view.findViewById(R.id.playbackControlRoot);
        this.currentDisplaySeek = view.findViewById(R.id.currentDisplaySeek);
        this.playbackSeekBar = view.findViewById(R.id.playbackSeekBar);
        this.playbackCurrentTime = view.findViewById(R.id.playbackCurrentTime);
        this.playbackEndTime = view.findViewById(R.id.playbackEndTime);
        this.playbackLiveSync = view.findViewById(R.id.playbackLiveSync);
        this.playbackSpeedTextView = view.findViewById(R.id.playbackSpeed);
        this.bottomControlsRoot = view.findViewById(R.id.bottomControls);
        this.topControlsRoot = view.findViewById(R.id.topControls);
        this.qualityTextView = view.findViewById(R.id.qualityTextView);

        this.subtitleView = view.findViewById(R.id.subtitleView);

        final float captionScale = PlayerHelper.getCaptionScale(context);
        final CaptionStyleCompat captionStyle = PlayerHelper.getCaptionStyle(context);
        setupSubtitleView(subtitleView, captionScale, captionStyle);

        this.resizeView = view.findViewById(R.id.resizeTextView);
        resizeView.setText(PlayerHelper
                .resizeTypeOf(context, aspectRatioFrameLayout.getResizeMode()));

        this.captionTextView = view.findViewById(R.id.captionTextView);

        //this.aspectRatioFrameLayout.setAspectRatio(16.0f / 9.0f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            playbackSeekBar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        }
        this.playbackSeekBar.getProgressDrawable().
                setColorFilter(Color.RED, PorterDuff.Mode.MULTIPLY);

        this.qualityPopupMenu = new PopupMenu(context, qualityTextView);
        this.playbackSpeedPopupMenu = new PopupMenu(context, playbackSpeedTextView);
        this.captionPopupMenu = new PopupMenu(context, captionTextView);

        ((ProgressBar) this.loadingPanel.findViewById(R.id.progressBarLoadingPanel))
                .getIndeterminateDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
    }

    protected abstract void setupSubtitleView(@NonNull SubtitleView view, float captionScale,
                                              @NonNull CaptionStyleCompat captionStyle);

    @Override
    public void initListeners() {
        playbackSeekBar.setOnSeekBarChangeListener(this);
        playbackSpeedTextView.setOnClickListener(this);
        qualityTextView.setOnClickListener(this);
        captionTextView.setOnClickListener(this);
        resizeView.setOnClickListener(this);
        playbackLiveSync.setOnClickListener(this);
    }

    @Override
    public void initPlayer(final boolean playOnReady) {
        super.initPlayer(playOnReady);

        // Setup video view
        simpleExoPlayer.setVideoSurfaceView(surfaceView);
        simpleExoPlayer.addVideoListener(this);

        // Setup subtitle view
        simpleExoPlayer.addTextOutput(cues -> subtitleView.onCues(cues));

        // Setup audio session with onboard equalizer
        if (Build.VERSION.SDK_INT >= 21) {
            trackSelector.setParameters(trackSelector.buildUponParameters()
                    .setTunnelingAudioSessionId(C.generateAudioSessionIdV21(context)));
        }
    }

    @Override
    public void handleIntent(final Intent intent) {
        if (intent == null) {
            return;
        }

        if (intent.hasExtra(PLAYBACK_QUALITY)) {
            setPlaybackQuality(intent.getStringExtra(PLAYBACK_QUALITY));
        }

        super.handleIntent(intent);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // UI Builders
    //////////////////////////////////////////////////////////////////////////*/

    public void buildQualityMenu() {
        if (qualityPopupMenu == null) {
            return;
        }

        qualityPopupMenu.getMenu().removeGroup(qualityPopupMenuGroupId);
        for (int i = 0; i < availableStreams.size(); i++) {
            VideoStream videoStream = availableStreams.get(i);
            qualityPopupMenu.getMenu().add(qualityPopupMenuGroupId, i, Menu.NONE, MediaFormat
                    .getNameById(videoStream.getFormatId()) + " " + videoStream.resolution);
        }
        if (getSelectedVideoStream() != null) {
            qualityTextView.setText(getSelectedVideoStream().resolution);
        }
        qualityPopupMenu.setOnMenuItemClickListener(this);
        qualityPopupMenu.setOnDismissListener(this);
    }

    private void buildPlaybackSpeedMenu() {
        if (playbackSpeedPopupMenu == null) {
            return;
        }

        playbackSpeedPopupMenu.getMenu().removeGroup(playbackSpeedPopupMenuGroupId);
        for (int i = 0; i < PLAYBACK_SPEEDS.length; i++) {
            playbackSpeedPopupMenu.getMenu().add(playbackSpeedPopupMenuGroupId, i, Menu.NONE,
                    formatSpeed(PLAYBACK_SPEEDS[i]));
        }
        playbackSpeedTextView.setText(formatSpeed(getPlaybackSpeed()));
        playbackSpeedPopupMenu.setOnMenuItemClickListener(this);
        playbackSpeedPopupMenu.setOnDismissListener(this);
    }

    private void buildCaptionMenu(final List<String> availableLanguages) {
        if (captionPopupMenu == null) {
            return;
        }
        captionPopupMenu.getMenu().removeGroup(captionPopupMenuGroupId);

        String userPreferredLanguage = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.caption_user_set_key), null);
        /*
         * only search for autogenerated cc as fallback
         * if "(auto-generated)" was not already selected
         * we are only looking for "(" instead of "(auto-generated)" to hopefully get all
         * internationalized variants such as "(automatisch-erzeugt)" and so on
         */
        boolean searchForAutogenerated = userPreferredLanguage != null
                && !userPreferredLanguage.contains("(");

        // Add option for turning off caption
        MenuItem captionOffItem = captionPopupMenu.getMenu().add(captionPopupMenuGroupId,
                0, Menu.NONE, R.string.caption_none);
        captionOffItem.setOnMenuItemClickListener(menuItem -> {
            final int textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT);
            if (textRendererIndex != RENDERER_UNAVAILABLE) {
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setRendererDisabled(textRendererIndex, true));
            }
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().remove(context.getString(R.string.caption_user_set_key)).commit();
            return true;
        });

        // Add all available captions
        for (int i = 0; i < availableLanguages.size(); i++) {
            final String captionLanguage = availableLanguages.get(i);
            MenuItem captionItem = captionPopupMenu.getMenu().add(captionPopupMenuGroupId,
                    i + 1, Menu.NONE, captionLanguage);
            captionItem.setOnMenuItemClickListener(menuItem -> {
                final int textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT);
                if (textRendererIndex != RENDERER_UNAVAILABLE) {
                    trackSelector.setPreferredTextLanguage(captionLanguage);
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(textRendererIndex, false));
                    final SharedPreferences prefs = PreferenceManager
                            .getDefaultSharedPreferences(context);
                    prefs.edit().putString(context.getString(R.string.caption_user_set_key),
                            captionLanguage).commit();
                }
                return true;
            });
            // apply caption language from previous user preference
            if (userPreferredLanguage != null && (captionLanguage.equals(userPreferredLanguage)
                    || searchForAutogenerated && captionLanguage.startsWith(userPreferredLanguage)
                    || userPreferredLanguage.contains("(") && captionLanguage.startsWith(
                    userPreferredLanguage
                            .substring(0, userPreferredLanguage.indexOf('('))))) {
                final int textRendererIndex = getRendererIndex(C.TRACK_TYPE_TEXT);
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

    private void updateStreamRelatedViews() {
        if (getCurrentMetadata() == null) {
            return;
        }

        final MediaSourceTag tag = getCurrentMetadata();
        final StreamInfo metadata = tag.getMetadata();

        qualityTextView.setVisibility(View.GONE);
        playbackSpeedTextView.setVisibility(View.GONE);

        playbackEndTime.setVisibility(View.GONE);
        playbackLiveSync.setVisibility(View.GONE);

        switch (metadata.getStreamType()) {
            case AUDIO_STREAM:
                surfaceView.setVisibility(View.GONE);
                endScreen.setVisibility(View.VISIBLE);
                playbackEndTime.setVisibility(View.VISIBLE);
                break;

            case AUDIO_LIVE_STREAM:
                surfaceView.setVisibility(View.GONE);
                endScreen.setVisibility(View.VISIBLE);
                playbackLiveSync.setVisibility(View.VISIBLE);
                break;

            case LIVE_STREAM:
                surfaceView.setVisibility(View.VISIBLE);
                endScreen.setVisibility(View.GONE);
                playbackLiveSync.setVisibility(View.VISIBLE);
                break;

            case VIDEO_STREAM:
                if (metadata.getVideoStreams().size() + metadata.getVideoOnlyStreams().size()
                        == 0) {
                    break;
                }

                availableStreams = tag.getSortedAvailableVideoStreams();
                selectedStreamIndex = tag.getSelectedVideoStreamIndex();
                buildQualityMenu();

                qualityTextView.setVisibility(View.VISIBLE);
                surfaceView.setVisibility(View.VISIBLE);
            default:
                endScreen.setVisibility(View.GONE);
                playbackEndTime.setVisibility(View.VISIBLE);
                break;
        }

        buildPlaybackSpeedMenu();
        playbackSpeedTextView.setVisibility(View.VISIBLE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playback Listener
    //////////////////////////////////////////////////////////////////////////*/

    protected abstract VideoPlaybackResolver.QualityResolver getQualityResolver();

    protected void onMetadataChanged(@NonNull final MediaSourceTag tag) {
        super.onMetadataChanged(tag);
        updateStreamRelatedViews();
    }

    @Override
    @Nullable
    public MediaSource sourceOf(final PlayQueueItem item, final StreamInfo info) {
        return resolver.resolve(info);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // States Implementation
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onBlocked() {
        super.onBlocked();

        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        animateView(controlsRoot, false, DEFAULT_SHORT_ANIM_DURATION);

        playbackSeekBar.setEnabled(false);
        // Bug on lower api, disabling and enabling the seekBar resets the thumb color -.-,
        // so sets the color again
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            playbackSeekBar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        }

        loadingPanel.setBackgroundColor(Color.BLACK);
        animateView(loadingPanel, true, DEFAULT_SHORT_ANIM_DURATION);
        animateView(surfaceForeground, true, DEFAULT_SHORT_ANIM_DURATION);
    }

    @Override
    public void onPlaying() {
        super.onPlaying();

        updateStreamRelatedViews();

        showAndAnimateControl(-1, true);

        playbackSeekBar.setEnabled(true);
        // Bug on lower api, disabling and enabling the seekBar resets the thumb color -.-,
        // so sets the color again
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            playbackSeekBar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        }

        loadingPanel.setVisibility(View.GONE);

        animateView(currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, false,
                DEFAULT_SHORT_ANIM_DURATION);
    }

    @Override
    public void onBuffering() {
        if (DEBUG) {
            Log.d(TAG, "onBuffering() called");
        }
        loadingPanel.setBackgroundColor(Color.TRANSPARENT);
    }

    @Override
    public void onPaused() {
        if (DEBUG) {
            Log.d(TAG, "onPaused() called");
        }
        showControls(DEFAULT_SHORT_ANIM_DURATION);
        loadingPanel.setVisibility(View.GONE);
    }

    @Override
    public void onPausedSeek() {
        if (DEBUG) {
            Log.d(TAG, "onPausedSeek() called");
        }
        showAndAnimateControl(-1, true);
    }

    @Override
    public void onCompleted() {
        super.onCompleted();

        showControls(DEFAULT_SHORT_ANIM_DURATION);
        animateView(endScreen, true, 1000);
        animateView(currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, false,
                DEFAULT_SHORT_ANIM_DURATION);
        loadingPanel.setVisibility(View.GONE);

        animateView(surfaceForeground, true, DEFAULT_SHORT_ANIM_DURATION);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Video Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onTracksChanged(final TrackGroupArray trackGroups,
                                final TrackSelectionArray trackSelections) {
        super.onTracksChanged(trackGroups, trackSelections);
        onTextTrackUpdate();
    }

    @Override
    public void onPlaybackParametersChanged(final PlaybackParameters playbackParameters) {
        super.onPlaybackParametersChanged(playbackParameters);
        playbackSpeedTextView.setText(formatSpeed(playbackParameters.speed));
    }

    @Override
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
        aspectRatioFrameLayout.setAspectRatio(((float) width) / height);
    }

    @Override
    public void onRenderedFirstFrame() {
        animateView(surfaceForeground, false, DEFAULT_SHORT_ANIM_DURATION);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // ExoPlayer Track Updates
    //////////////////////////////////////////////////////////////////////////*/

    private void onTextTrackUpdate() {
        final int textRenderer = getRendererIndex(C.TRACK_TYPE_TEXT);

        if (captionTextView == null) {
            return;
        }
        if (trackSelector.getCurrentMappedTrackInfo() == null
                || textRenderer == RENDERER_UNAVAILABLE) {
            captionTextView.setVisibility(View.GONE);
            return;
        }

        final TrackGroupArray textTracks = trackSelector.getCurrentMappedTrackInfo()
                .getTrackGroups(textRenderer);

        // Extract all loaded languages
        List<String> availableLanguages = new ArrayList<>(textTracks.length);
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
            captionTextView.setText(R.string.caption_none);
        } else {
            captionTextView.setText(preferredLanguage);
        }
        captionTextView.setVisibility(availableLanguages.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // General Player
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onPrepared(final boolean playWhenReady) {
        if (DEBUG) {
            Log.d(TAG, "onPrepared() called with: playWhenReady = [" + playWhenReady + "]");
        }

        playbackSeekBar.setMax((int) simpleExoPlayer.getDuration());
        playbackEndTime.setText(getTimeString((int) simpleExoPlayer.getDuration()));
        playbackSpeedTextView.setText(formatSpeed(getPlaybackSpeed()));

        super.onPrepared(playWhenReady);

        if (simpleExoPlayer.getCurrentPosition() != 0 && !isControlsVisible()) {
            controlsVisibilityHandler.removeCallbacksAndMessages(null);
            controlsVisibilityHandler
                    .postDelayed(this::showControlsThenHide, DEFAULT_SHORT_ANIM_DURATION);
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        if (endScreen != null) {
            endScreen.setImageBitmap(null);
        }
    }

    @Override
    public void onUpdateProgress(final int currentProgress, final int duration,
                                 final int bufferPercent) {
        if (!isPrepared()) {
            return;
        }

        if (duration != playbackSeekBar.getMax()) {
            playbackEndTime.setText(getTimeString(duration));
            playbackSeekBar.setMax(duration);
        }
        if (currentState != STATE_PAUSED) {
            if (currentState != STATE_PAUSED_SEEK) {
                playbackSeekBar.setProgress(currentProgress);
            }
            playbackCurrentTime.setText(getTimeString(currentProgress));
        }
        if (simpleExoPlayer.isLoading() || bufferPercent > 90) {
            playbackSeekBar.setSecondaryProgress(
                    (int) (playbackSeekBar.getMax() * ((float) bufferPercent / 100)));
        }
        if (DEBUG && bufferPercent % 20 == 0) { //Limit log
            Log.d(TAG, "updateProgress() called with: "
                    + "isVisible = " + isControlsVisible() + ", "
                    + "currentProgress = [" + currentProgress + "], "
                    + "duration = [" + duration + "], bufferPercent = [" + bufferPercent + "]");
        }
        playbackLiveSync.setClickable(!isLiveEdge());
    }

    @Override
    public void onLoadingComplete(final String imageUri, final View view,
                                  final Bitmap loadedImage) {
        super.onLoadingComplete(imageUri, view, loadedImage);
        if (loadedImage != null) {
            endScreen.setImageBitmap(loadedImage);
        }
    }

    protected void onFullScreenButtonClicked() {
        changeState(STATE_BLOCKED);
    }

    @Override
    public void onFastRewind() {
        super.onFastRewind();
        showAndAnimateControl(R.drawable.ic_action_av_fast_rewind, true);
    }

    @Override
    public void onFastForward() {
        super.onFastForward();
        showAndAnimateControl(R.drawable.ic_action_av_fast_forward, true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnClick related
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onClick(final View v) {
        if (DEBUG) {
            Log.d(TAG, "onClick() called with: v = [" + v + "]");
        }
        if (v.getId() == qualityTextView.getId()) {
            onQualitySelectorClicked();
        } else if (v.getId() == playbackSpeedTextView.getId()) {
            onPlaybackSpeedClicked();
        } else if (v.getId() == resizeView.getId()) {
            onResizeClicked();
        } else if (v.getId() == captionTextView.getId()) {
            onCaptionClicked();
        } else if (v.getId() == playbackLiveSync.getId()) {
            seekToDefault();
        }
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

        if (qualityPopupMenuGroupId == menuItem.getGroupId()) {
            final int menuItemIndex = menuItem.getItemId();
            if (selectedStreamIndex == menuItemIndex || availableStreams == null
                    || availableStreams.size() <= menuItemIndex) {
                return true;
            }

            final String newResolution = availableStreams.get(menuItemIndex).resolution;
            setRecovery();
            setPlaybackQuality(newResolution);
            reload();

            qualityTextView.setText(menuItem.getTitle());
            return true;
        } else if (playbackSpeedPopupMenuGroupId == menuItem.getGroupId()) {
            int speedIndex = menuItem.getItemId();
            float speed = PLAYBACK_SPEEDS[speedIndex];

            setPlaybackSpeed(speed);
            playbackSpeedTextView.setText(formatSpeed(speed));
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
        isSomePopupMenuVisible = false;
        if (getSelectedVideoStream() != null) {
            qualityTextView.setText(getSelectedVideoStream().resolution);
        }
    }

    public void onQualitySelectorClicked() {
        if (DEBUG) {
            Log.d(TAG, "onQualitySelectorClicked() called");
        }
        qualityPopupMenu.show();
        isSomePopupMenuVisible = true;
        showControls(DEFAULT_SHORT_ANIM_DURATION);

        final VideoStream videoStream = getSelectedVideoStream();
        if (videoStream != null) {
            final String qualityText = MediaFormat.getNameById(videoStream.getFormatId()) + " "
                    + videoStream.resolution;
            qualityTextView.setText(qualityText);
        }

        wasPlaying = simpleExoPlayer.getPlayWhenReady();
    }

    public void onPlaybackSpeedClicked() {
        if (DEBUG) {
            Log.d(TAG, "onPlaybackSpeedClicked() called");
        }
        playbackSpeedPopupMenu.show();
        isSomePopupMenuVisible = true;
        showControls(DEFAULT_SHORT_ANIM_DURATION);
    }

    private void onCaptionClicked() {
        if (DEBUG) {
            Log.d(TAG, "onCaptionClicked() called");
        }
        captionPopupMenu.show();
        isSomePopupMenuVisible = true;
        showControls(DEFAULT_SHORT_ANIM_DURATION);
    }

    private void onResizeClicked() {
        if (getAspectRatioFrameLayout() != null) {
            final int currentResizeMode = getAspectRatioFrameLayout().getResizeMode();
            final int newResizeMode = nextResizeMode(currentResizeMode);
            setResizeMode(newResizeMode);
        }
    }

    protected void setResizeMode(@AspectRatioFrameLayout.ResizeMode final int resizeMode) {
        getAspectRatioFrameLayout().setResizeMode(resizeMode);
        getResizeView().setText(PlayerHelper.resizeTypeOf(context, resizeMode));
    }

    protected abstract int nextResizeMode(@AspectRatioFrameLayout.ResizeMode int resizeMode);

    /*//////////////////////////////////////////////////////////////////////////
    // SeekBar Listener
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onProgressChanged(final SeekBar seekBar, final int progress,
                                  final boolean fromUser) {
        if (DEBUG && fromUser) {
            Log.d(TAG, "onProgressChanged() called with: "
                    + "seekBar = [" + seekBar + "], progress = [" + progress + "]");
        }
        //if (fromUser) playbackCurrentTime.setText(getTimeString(progress));
        if (fromUser) {
            currentDisplaySeek.setText(getTimeString(progress));
        }
    }

    @Override
    public void onStartTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStartTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }
        if (getCurrentState() != STATE_PAUSED_SEEK) {
            changeState(STATE_PAUSED_SEEK);
        }

        wasPlaying = simpleExoPlayer.getPlayWhenReady();
        if (isPlaying()) {
            simpleExoPlayer.setPlayWhenReady(false);
        }

        showControls(DEFAULT_SHORT_ANIM_DURATION);
        animateView(currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, true,
                DEFAULT_SHORT_ANIM_DURATION);
    }

    @Override
    public void onStopTrackingTouch(final SeekBar seekBar) {
        if (DEBUG) {
            Log.d(TAG, "onStopTrackingTouch() called with: seekBar = [" + seekBar + "]");
        }

        seekTo(seekBar.getProgress());
        if (wasPlaying || simpleExoPlayer.getDuration() == seekBar.getProgress()) {
            simpleExoPlayer.setPlayWhenReady(true);
        }

        playbackCurrentTime.setText(getTimeString(seekBar.getProgress()));
        animateView(currentDisplaySeek, AnimationUtils.Type.SCALE_AND_ALPHA, false,
                DEFAULT_SHORT_ANIM_DURATION);

        if (getCurrentState() == STATE_PAUSED_SEEK) {
            changeState(STATE_BUFFERING);
        }
        if (!isProgressLoopRunning()) {
            startProgressLoop();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public int getRendererIndex(final int trackIndex) {
        if (simpleExoPlayer == null) {
            return RENDERER_UNAVAILABLE;
        }

        for (int t = 0; t < simpleExoPlayer.getRendererCount(); t++) {
            if (simpleExoPlayer.getRendererType(t) == trackIndex) {
                return t;
            }
        }

        return RENDERER_UNAVAILABLE;
    }

    public boolean isControlsVisible() {
        return controlsRoot != null && controlsRoot.getVisibility() == View.VISIBLE;
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
            if (controlAnimationView.getVisibility() == View.VISIBLE) {
                controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(controlAnimationView,
                        PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1.4f, 1f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.4f, 1f)
                ).setDuration(DEFAULT_SHORT_ANIM_DURATION);
                controlViewAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(final Animator animation) {
                        controlAnimationView.setVisibility(View.GONE);
                    }
                });
                controlViewAnimator.start();
            }
            return;
        }

        float scaleFrom = goneOnEnd ? 1f : 1f;
        float scaleTo = goneOnEnd ? 1.8f : 1.4f;
        float alphaFrom = goneOnEnd ? 1f : 0f;
        float alphaTo = goneOnEnd ? 0f : 1f;


        controlViewAnimator = ObjectAnimator.ofPropertyValuesHolder(controlAnimationView,
                PropertyValuesHolder.ofFloat(View.ALPHA, alphaFrom, alphaTo),
                PropertyValuesHolder.ofFloat(View.SCALE_X, scaleFrom, scaleTo),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, scaleFrom, scaleTo)
        );
        controlViewAnimator.setDuration(goneOnEnd ? 1000 : 500);
        controlViewAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animation) {
                if (goneOnEnd) {
                    controlAnimationView.setVisibility(View.GONE);
                } else {
                    controlAnimationView.setVisibility(View.VISIBLE);
                }
            }
        });


        controlAnimationView.setVisibility(View.VISIBLE);
        controlAnimationView.setImageDrawable(ContextCompat.getDrawable(context, drawableId));
        controlViewAnimator.start();
    }

    public boolean isSomePopupMenuVisible() {
        return isSomePopupMenuVisible;
    }

    public void showControlsThenHide() {
        if (DEBUG) {
            Log.d(TAG, "showControlsThenHide() called");
        }
        animateView(controlsRoot, true, DEFAULT_SHORT_ANIM_DURATION, 0, () ->
                hideControls(DEFAULT_SHORT_ANIM_DURATION, DEFAULT_CONTROLS_HIDE_TIME));
    }

    public void showControls(final long duration) {
        if (DEBUG) {
            Log.d(TAG, "showControls() called");
        }
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        animateView(controlsRoot, true, duration);
    }

    public void hideControls(final long duration, final long delay) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
        }
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler.postDelayed(() ->
                animateView(controlsRoot, false, duration), delay);
    }

    public void hideControlsAndButton(final long duration, final long delay, final View button) {
        if (DEBUG) {
            Log.d(TAG, "hideControls() called with: delay = [" + delay + "]");
        }
        controlsVisibilityHandler.removeCallbacksAndMessages(null);
        controlsVisibilityHandler
                .postDelayed(hideControlsAndButtonHandler(duration, button), delay);
    }

    private Runnable hideControlsAndButtonHandler(final long duration, final View videoPlayPause) {
        return () -> {
            videoPlayPause.setVisibility(View.INVISIBLE);
            animateView(controlsRoot, false, duration);
        };
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Getters and Setters
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public String getPlaybackQuality() {
        return resolver.getPlaybackQuality();
    }

    public void setPlaybackQuality(final String quality) {
        this.resolver.setPlaybackQuality(quality);
    }

    public AspectRatioFrameLayout getAspectRatioFrameLayout() {
        return aspectRatioFrameLayout;
    }

    public SurfaceView getSurfaceView() {
        return surfaceView;
    }

    public boolean wasPlaying() {
        return wasPlaying;
    }

    @Nullable
    public VideoStream getSelectedVideoStream() {
        return (selectedStreamIndex >= 0 && availableStreams != null
                && availableStreams.size() > selectedStreamIndex)
                ? availableStreams.get(selectedStreamIndex) : null;
    }

    public Handler getControlsVisibilityHandler() {
        return controlsVisibilityHandler;
    }

    public View getRootView() {
        return rootView;
    }

    public void setRootView(final View rootView) {
        this.rootView = rootView;
    }

    public View getLoadingPanel() {
        return loadingPanel;
    }

    public ImageView getEndScreen() {
        return endScreen;
    }

    public ImageView getControlAnimationView() {
        return controlAnimationView;
    }

    public View getControlsRoot() {
        return controlsRoot;
    }

    public View getBottomControlsRoot() {
        return bottomControlsRoot;
    }

    public SeekBar getPlaybackSeekBar() {
        return playbackSeekBar;
    }

    public TextView getPlaybackCurrentTime() {
        return playbackCurrentTime;
    }

    public TextView getPlaybackEndTime() {
        return playbackEndTime;
    }

    public View getTopControlsRoot() {
        return topControlsRoot;
    }

    public TextView getQualityTextView() {
        return qualityTextView;
    }

    public PopupMenu getQualityPopupMenu() {
        return qualityPopupMenu;
    }

    public PopupMenu getPlaybackSpeedPopupMenu() {
        return playbackSpeedPopupMenu;
    }

    public View getSurfaceForeground() {
        return surfaceForeground;
    }

    public TextView getCurrentDisplaySeek() {
        return currentDisplaySeek;
    }

    public SubtitleView getSubtitleView() {
        return subtitleView;
    }

    public TextView getResizeView() {
        return resizeView;
    }

    public TextView getCaptionTextView() {
        return captionTextView;
    }
}
