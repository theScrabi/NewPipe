package org.schabi.newpipe.player.helper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.CaptioningManager;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player.RepeatMode;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.text.CaptionStyleCompat;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout.ResizeMode;
import com.google.android.exoplayer2.util.MimeTypes;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.extractor.utils.Utils;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.Player;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ListHelper;

import java.lang.annotation.Retention;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_OFF;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import static org.schabi.newpipe.player.Player.IDLE_WINDOW_FLAGS;
import static org.schabi.newpipe.player.Player.PLAYER_TYPE;
import static org.schabi.newpipe.player.helper.PlayerHelper.AutoplayType.AUTOPLAY_TYPE_ALWAYS;
import static org.schabi.newpipe.player.helper.PlayerHelper.AutoplayType.AUTOPLAY_TYPE_NEVER;
import static org.schabi.newpipe.player.helper.PlayerHelper.AutoplayType.AUTOPLAY_TYPE_WIFI;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_BACKGROUND;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_NONE;
import static org.schabi.newpipe.player.helper.PlayerHelper.MinimizeMode.MINIMIZE_ON_EXIT_MODE_POPUP;

public final class PlayerHelper {
    private static final StringBuilder STRING_BUILDER = new StringBuilder();
    private static final Formatter STRING_FORMATTER
            = new Formatter(STRING_BUILDER, Locale.getDefault());
    private static final NumberFormat SPEED_FORMATTER = new DecimalFormat("0.##x");
    private static final NumberFormat PITCH_FORMATTER = new DecimalFormat("##%");

    @Retention(SOURCE)
    @IntDef({AUTOPLAY_TYPE_ALWAYS, AUTOPLAY_TYPE_WIFI,
            AUTOPLAY_TYPE_NEVER})
    public @interface AutoplayType {
        int AUTOPLAY_TYPE_ALWAYS = 0;
        int AUTOPLAY_TYPE_WIFI = 1;
        int AUTOPLAY_TYPE_NEVER = 2;
    }

    @Retention(SOURCE)
    @IntDef({MINIMIZE_ON_EXIT_MODE_NONE, MINIMIZE_ON_EXIT_MODE_BACKGROUND,
            MINIMIZE_ON_EXIT_MODE_POPUP})
    public @interface MinimizeMode {
        int MINIMIZE_ON_EXIT_MODE_NONE = 0;
        int MINIMIZE_ON_EXIT_MODE_BACKGROUND = 1;
        int MINIMIZE_ON_EXIT_MODE_POPUP = 2;
    }

    private PlayerHelper() { }

    ////////////////////////////////////////////////////////////////////////////
    // Exposed helpers
    ////////////////////////////////////////////////////////////////////////////

    public static String getTimeString(final int milliSeconds) {
        final int seconds = (milliSeconds % 60000) / 1000;
        final int minutes = (milliSeconds % 3600000) / 60000;
        final int hours = (milliSeconds % 86400000) / 3600000;
        final int days = (milliSeconds % (86400000 * 7)) / 86400000;

        STRING_BUILDER.setLength(0);
        return (days > 0
                ? STRING_FORMATTER.format("%d:%02d:%02d:%02d", days, hours, minutes, seconds)
                : hours > 0
                ? STRING_FORMATTER.format("%d:%02d:%02d", hours, minutes, seconds)
                : STRING_FORMATTER.format("%02d:%02d", minutes, seconds)
        ).toString();
    }

    public static String formatSpeed(final double speed) {
        return SPEED_FORMATTER.format(speed);
    }

    public static String formatPitch(final double pitch) {
        return PITCH_FORMATTER.format(pitch);
    }

    public static String subtitleMimeTypesOf(final MediaFormat format) {
        switch (format) {
            case VTT:
                return MimeTypes.TEXT_VTT;
            case TTML:
                return MimeTypes.APPLICATION_TTML;
            default:
                throw new IllegalArgumentException("Unrecognized mime type: " + format.name());
        }
    }

    @NonNull
    public static String captionLanguageOf(@NonNull final Context context,
                                           @NonNull final SubtitlesStream subtitles) {
        final String displayName = subtitles.getDisplayLanguageName();
        return displayName + (subtitles.isAutoGenerated()
                ? " (" + context.getString(R.string.caption_auto_generated) + ")" : "");
    }

    @NonNull
    public static String resizeTypeOf(@NonNull final Context context,
                                      @ResizeMode final int resizeMode) {
        switch (resizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                return context.getResources().getString(R.string.resize_fit);
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                return context.getResources().getString(R.string.resize_fill);
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
                return context.getResources().getString(R.string.resize_zoom);
            case AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT:
            case AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH:
            default:
                throw new IllegalArgumentException("Unrecognized resize mode: " + resizeMode);
        }
    }

    @NonNull
    public static String cacheKeyOf(@NonNull final StreamInfo info,
                                    @NonNull final VideoStream video) {
        return info.getUrl() + video.getResolution() + video.getFormat().getName();
    }

    @NonNull
    public static String cacheKeyOf(@NonNull final StreamInfo info,
                                    @NonNull final AudioStream audio) {
        return info.getUrl() + audio.getAverageBitrate() + audio.getFormat().getName();
    }

    /**
     * Given a {@link StreamInfo} and the existing queue items,
     * provide the {@link SinglePlayQueue} consisting of the next video for auto queueing.
     * <p>
     * This method detects and prevents cycles by naively checking
     * if a candidate next video's url already exists in the existing items.
     * </p>
     * <p>
     * The first item in {@link StreamInfo#getRelatedItems()} is checked first.
     * If it is non-null and is not part of the existing items, it will be used as the next stream.
     * Otherwise, a random stream with non-repeating url will be selected
     * from the {@link StreamInfo#getRelatedItems()}. Non-stream items are ignored.
     * </p>
     *
     * @param info          currently playing stream
     * @param existingItems existing items in the queue
     * @return {@link SinglePlayQueue} with the next stream to queue
     */
    @Nullable
    public static PlayQueue autoQueueOf(@NonNull final StreamInfo info,
                                        @NonNull final List<PlayQueueItem> existingItems) {
        final Set<String> urls = new HashSet<>(existingItems.size());
        for (final PlayQueueItem item : existingItems) {
            urls.add(item.getUrl());
        }

        final List<InfoItem> relatedItems = info.getRelatedItems();
        if (Utils.isNullOrEmpty(relatedItems)) {
            return null;
        }

        if (relatedItems.get(0) != null && relatedItems.get(0) instanceof StreamInfoItem
                && !urls.contains(relatedItems.get(0).getUrl())) {
            return getAutoQueuedSinglePlayQueue((StreamInfoItem) relatedItems.get(0));
        }

        final List<StreamInfoItem> autoQueueItems = new ArrayList<>();
        for (final InfoItem item : relatedItems) {
            if (item instanceof StreamInfoItem && !urls.contains(item.getUrl())) {
                autoQueueItems.add((StreamInfoItem) item);
            }
        }

        Collections.shuffle(autoQueueItems);
        return autoQueueItems.isEmpty()
                ? null : getAutoQueuedSinglePlayQueue(autoQueueItems.get(0));
    }

    ////////////////////////////////////////////////////////////////////////////
    // Settings Resolution
    ////////////////////////////////////////////////////////////////////////////

    public static boolean isResumeAfterAudioFocusGain(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.resume_on_audio_focus_gain_key), false);
    }

    public static boolean isVolumeGestureEnabled(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.volume_gesture_control_key), true);
    }

    public static boolean isBrightnessGestureEnabled(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.brightness_gesture_control_key), true);
    }

    public static boolean isAutoQueueEnabled(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.auto_queue_key), false);
    }

    public static boolean isClearingQueueConfirmationRequired(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.clear_queue_confirmation_key), false);
    }

    @MinimizeMode
    public static int getMinimizeOnExitAction(@NonNull final Context context) {
        final String action = getPreferences(context)
                .getString(context.getString(R.string.minimize_on_exit_key), "");
        if (action.equals(context.getString(R.string.minimize_on_exit_popup_key))) {
            return MINIMIZE_ON_EXIT_MODE_POPUP;
        } else if (action.equals(context.getString(R.string.minimize_on_exit_none_key))) {
            return MINIMIZE_ON_EXIT_MODE_NONE;
        } else {
            return MINIMIZE_ON_EXIT_MODE_BACKGROUND; // default
        }
    }

    @AutoplayType
    public static int getAutoplayType(@NonNull final Context context) {
        final String type = getPreferences(context).getString(
                context.getString(R.string.autoplay_key), "");
        if (type.equals(context.getString(R.string.autoplay_always_key))) {
            return AUTOPLAY_TYPE_ALWAYS;
        } else if (type.equals(context.getString(R.string.autoplay_never_key))) {
            return AUTOPLAY_TYPE_NEVER;
        } else {
            return AUTOPLAY_TYPE_WIFI; // default
        }
    }

    public static boolean isAutoplayAllowedByUser(@NonNull final Context context) {
        switch (PlayerHelper.getAutoplayType(context)) {
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_NEVER:
                return false;
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_WIFI:
                return !ListHelper.isMeteredNetwork(context);
            case PlayerHelper.AutoplayType.AUTOPLAY_TYPE_ALWAYS:
            default:
                return true;
        }
    }

    @NonNull
    public static SeekParameters getSeekParameters(@NonNull final Context context) {
        return isUsingInexactSeek(context) ? SeekParameters.CLOSEST_SYNC : SeekParameters.EXACT;
    }

    public static long getPreferredCacheSize() {
        return 64 * 1024 * 1024L;
    }

    public static long getPreferredFileSize() {
        return 2 * 1024 * 1024L; // ExoPlayer CacheDataSink.MIN_RECOMMENDED_FRAGMENT_SIZE
    }

    /**
     * @return the number of milliseconds the player buffers for before starting playback
     */
    public static int getPlaybackStartBufferMs() {
        return 500;
    }

    public static TrackSelection.Factory getQualitySelector() {
        return new AdaptiveTrackSelection.Factory(
                1000,
                AdaptiveTrackSelection.DEFAULT_MAX_DURATION_FOR_QUALITY_DECREASE_MS,
                AdaptiveTrackSelection.DEFAULT_MIN_DURATION_TO_RETAIN_AFTER_DISCARD_MS,
                AdaptiveTrackSelection.DEFAULT_BANDWIDTH_FRACTION);
    }

    public static boolean isUsingDSP() {
        return true;
    }

    public static int getTossFlingVelocity() {
        return 2500;
    }

    @NonNull
    public static CaptionStyleCompat getCaptionStyle(@NonNull final Context context) {
        final CaptioningManager captioningManager = ContextCompat.getSystemService(context,
                CaptioningManager.class);
        if (captioningManager == null || !captioningManager.isEnabled()) {
            return CaptionStyleCompat.DEFAULT;
        }

        return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
    }

    /**
     * Get scaling for captions based on system font scaling.
     * <p>Options:</p>
     * <ul>
     *     <li>Very small: 0.25f</li>
     *     <li>Small: 0.5f</li>
     *     <li>Normal: 1.0f</li>
     *     <li>Large: 1.5f</li>
     *     <li>Very large: 2.0f</li>
     * </ul>
     *
     * @param context Android app context
     * @return caption scaling
     */
    public static float getCaptionScale(@NonNull final Context context) {
        final CaptioningManager captioningManager = ContextCompat.getSystemService(context,
                CaptioningManager.class);
        if (captioningManager == null || !captioningManager.isEnabled()) {
            return 1.0f;
        }

        return captioningManager.getFontScale();
    }

    /**
     * @param context the Android context
     * @return the screen brightness to use. A value less than 0 (the default) means to use the
     *         preferred screen brightness
     */
    public static float getScreenBrightness(@NonNull final Context context) {
        final SharedPreferences sp = getPreferences(context);
        final long timestamp =
                sp.getLong(context.getString(R.string.screen_brightness_timestamp_key), 0);
        // Hypothesis: 4h covers a viewing block, e.g. evening.
        // External lightning conditions will change in the next
        // viewing block so we fall back to the default brightness
        if ((System.currentTimeMillis() - timestamp) > TimeUnit.HOURS.toMillis(4)) {
            return -1;
        } else {
            return sp.getFloat(context.getString(R.string.screen_brightness_key), -1);
        }
    }

    public static void setScreenBrightness(@NonNull final Context context,
                                           final float screenBrightness) {
        getPreferences(context).edit()
                .putFloat(context.getString(R.string.screen_brightness_key), screenBrightness)
                .putLong(context.getString(R.string.screen_brightness_timestamp_key),
                        System.currentTimeMillis())
                .apply();
    }

    public static boolean globalScreenOrientationLocked(final Context context) {
        // 1: Screen orientation changes using accelerometer
        // 0: Screen orientation is locked
        return android.provider.Settings.System.getInt(
                context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 0;
    }

    ////////////////////////////////////////////////////////////////////////////
    // Private helpers
    ////////////////////////////////////////////////////////////////////////////

    @NonNull
    private static SharedPreferences getPreferences(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }

    private static boolean isUsingInexactSeek(@NonNull final Context context) {
        return getPreferences(context)
                .getBoolean(context.getString(R.string.use_inexact_seek_key), false);
    }

    private static SinglePlayQueue getAutoQueuedSinglePlayQueue(
            final StreamInfoItem streamInfoItem) {
        final SinglePlayQueue singlePlayQueue = new SinglePlayQueue(streamInfoItem);
        Objects.requireNonNull(singlePlayQueue.getItem()).setAutoQueued(true);
        return singlePlayQueue;
    }


    ////////////////////////////////////////////////////////////////////////////
    // Utils used by player
    ////////////////////////////////////////////////////////////////////////////

    public static MainPlayer.PlayerType retrievePlayerTypeFromIntent(final Intent intent) {
        // If you want to open popup from the app just include Constants.POPUP_ONLY into an extra
        return MainPlayer.PlayerType.values()[
                intent.getIntExtra(PLAYER_TYPE, MainPlayer.PlayerType.VIDEO.ordinal())];
    }

    public static boolean isPlaybackResumeEnabled(final Player player) {
        return player.getPrefs().getBoolean(
                player.getContext().getString(R.string.enable_watch_history_key), true)
                && player.getPrefs().getBoolean(
                player.getContext().getString(R.string.enable_playback_resume_key), true);
    }

    @RepeatMode
    public static int nextRepeatMode(@RepeatMode final int repeatMode) {
        switch (repeatMode) {
            case REPEAT_MODE_OFF:
                return REPEAT_MODE_ONE;
            case REPEAT_MODE_ONE:
                return REPEAT_MODE_ALL;
            case REPEAT_MODE_ALL: default:
                return REPEAT_MODE_OFF;
        }
    }

    @ResizeMode
    public static int retrieveResizeModeFromPrefs(final Player player) {
        return player.getPrefs().getInt(player.getContext().getString(R.string.last_resize_mode),
                AspectRatioFrameLayout.RESIZE_MODE_FIT);
    }

    @SuppressLint("SwitchIntDef") // only fit, fill and zoom are supported by NewPipe
    @ResizeMode
    public static int nextResizeModeAndSaveToPrefs(final Player player,
                                                   @ResizeMode final int resizeMode) {
        final int newResizeMode;
        switch (resizeMode) {
            case AspectRatioFrameLayout.RESIZE_MODE_FIT:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_FILL:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM;
                break;
            case AspectRatioFrameLayout.RESIZE_MODE_ZOOM:
            default:
                newResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
                break;
        }

        // save the new resize mode so it can be restored in a future session
        player.getPrefs().edit().putInt(
                player.getContext().getString(R.string.last_resize_mode), newResizeMode).apply();
        return newResizeMode;
    }

    public static PlaybackParameters retrievePlaybackParametersFromPrefs(final Player player) {
        final float speed = player.getPrefs().getFloat(player.getContext().getString(
                R.string.playback_speed_key), player.getPlaybackSpeed());
        final float pitch = player.getPrefs().getFloat(player.getContext().getString(
                R.string.playback_pitch_key), player.getPlaybackPitch());
        return new PlaybackParameters(speed, pitch);
    }

    public static void savePlaybackParametersToPrefs(final Player player,
                                                     final float speed,
                                                     final float pitch,
                                                     final boolean skipSilence) {
        player.getPrefs().edit()
                .putFloat(player.getContext().getString(R.string.playback_speed_key), speed)
                .putFloat(player.getContext().getString(R.string.playback_pitch_key), pitch)
                .putBoolean(player.getContext().getString(R.string.playback_skip_silence_key),
                        skipSilence)
                .apply();
    }

    /**
     * @param player {@code screenWidth} and {@code screenHeight} must have been initialized
     * @return the popup starting layout params
     */
    @SuppressLint("RtlHardcoded")
    public static WindowManager.LayoutParams retrievePopupLayoutParamsFromPrefs(
            final Player player) {
        final boolean popupRememberSizeAndPos = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.popup_remember_size_pos_key), true);
        final float defaultSize =
                player.getContext().getResources().getDimension(R.dimen.popup_default_width);
        final float popupWidth = popupRememberSizeAndPos
                ? player.getPrefs().getFloat(player.getContext().getString(
                        R.string.popup_saved_width_key), defaultSize)
                : defaultSize;
        final float popupHeight = getMinimumVideoHeight(popupWidth);

        final WindowManager.LayoutParams popupLayoutParams = new WindowManager.LayoutParams(
                (int) popupWidth, (int) popupHeight,
                popupLayoutParamType(),
                IDLE_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT);
        popupLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        popupLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

        final int centerX = (int) (player.getScreenWidth() / 2f - popupWidth / 2f);
        final int centerY = (int) (player.getScreenHeight() / 2f - popupHeight / 2f);
        popupLayoutParams.x = popupRememberSizeAndPos
                ? player.getPrefs().getInt(player.getContext().getString(
                        R.string.popup_saved_x_key), centerX) : centerX;
        popupLayoutParams.y = popupRememberSizeAndPos
                ? player.getPrefs().getInt(player.getContext().getString(
                        R.string.popup_saved_y_key), centerY) : centerY;

        return popupLayoutParams;
    }

    public static void savePopupPositionAndSizeToPrefs(final Player player) {
        if (player.getPopupLayoutParams() != null) {
            player.getPrefs().edit()
                    .putFloat(player.getContext().getString(R.string.popup_saved_width_key),
                            player.getPopupLayoutParams().width)
                    .putInt(player.getContext().getString(R.string.popup_saved_x_key),
                            player.getPopupLayoutParams().x)
                    .putInt(player.getContext().getString(R.string.popup_saved_y_key),
                            player.getPopupLayoutParams().y)
                    .apply();
        }
    }

    public static float getMinimumVideoHeight(final float width) {
        return width / (16.0f / 9.0f); // Respect the 16:9 ratio that most videos have
    }

    @SuppressLint("RtlHardcoded")
    public static WindowManager.LayoutParams buildCloseOverlayLayoutParams() {
        final int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

        final WindowManager.LayoutParams closeOverlayLayoutParams = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                popupLayoutParamType(),
                flags,
                PixelFormat.TRANSLUCENT);

        closeOverlayLayoutParams.gravity = Gravity.LEFT | Gravity.TOP;
        closeOverlayLayoutParams.softInputMode =
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        return closeOverlayLayoutParams;
    }

    public static int popupLayoutParamType() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_PHONE
                : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
    }

    public static int retrieveSeekDurationFromPreferences(final Player player) {
        return Integer.parseInt(Objects.requireNonNull(player.getPrefs().getString(
                player.getContext().getString(R.string.seek_duration_key),
                player.getContext().getString(R.string.seek_duration_default_value))));
    }
}
