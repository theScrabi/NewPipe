package org.schabi.newpipe.player;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Build;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;

import java.util.List;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;
import static com.google.android.exoplayer2.Player.REPEAT_MODE_ONE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_CLOSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_FORWARD;
import static org.schabi.newpipe.player.MainPlayer.ACTION_FAST_REWIND;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_NEXT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PAUSE;
import static org.schabi.newpipe.player.MainPlayer.ACTION_PLAY_PREVIOUS;
import static org.schabi.newpipe.player.MainPlayer.ACTION_REPEAT;
import static org.schabi.newpipe.player.MainPlayer.ACTION_SHUFFLE;

/**
 * This is a utility class for player notifications.
 *
 * @author cool-student
 */
public final class NotificationUtil {
    private static final String TAG = NotificationUtil.class.getSimpleName();
    private static final boolean DEBUG = Player.DEBUG;
    private static final int NOTIFICATION_ID = 123789;

    @Nullable private static NotificationUtil instance = null;

    @NotificationConstants.Action
    private final int[] notificationSlots = NotificationConstants.SLOT_DEFAULTS.clone();

    private NotificationManagerCompat notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    private NotificationUtil() {
    }

    public static NotificationUtil getInstance() {
        if (instance == null) {
            instance = new NotificationUtil();
        }
        return instance;
    }


    /////////////////////////////////////////////////////
    // NOTIFICATION
    /////////////////////////////////////////////////////

    /**
     * Creates the notification if it does not exist already and recreates it if forceRecreate is
     * true. Updates the notification with the data in the player.
     * @param player the player currently open, to take data from
     * @param forceRecreate whether to force the recreation of the notification even if it already
     *                      exists
     */
    synchronized void createNotificationIfNeededAndUpdate(final Player player,
                                                          final boolean forceRecreate) {
        if (forceRecreate || notificationBuilder == null) {
            notificationBuilder = createNotification(player);
        }
        updateNotification(player);
        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
    }

    private synchronized NotificationCompat.Builder createNotification(final Player player) {
        if (DEBUG) {
            Log.d(TAG, "createNotification()");
        }
        notificationManager = NotificationManagerCompat.from(player.getContext());
        final NotificationCompat.Builder builder =
                new NotificationCompat.Builder(player.getContext(),
                player.getContext().getString(R.string.notification_channel_id));

        initializeNotificationSlots(player);

        // count the number of real slots, to make sure compact slots indices are not out of bound
        int nonNothingSlotCount = 5;
        if (notificationSlots[3] == NotificationConstants.NOTHING) {
            --nonNothingSlotCount;
        }
        if (notificationSlots[4] == NotificationConstants.NOTHING) {
            --nonNothingSlotCount;
        }

        // build the compact slot indices array (need code to convert from Integer... because Java)
        final List<Integer> compactSlotList = NotificationConstants.getCompactSlotsFromPreferences(
                player.getContext(), player.getPrefs(), nonNothingSlotCount);
        final int[] compactSlots = new int[compactSlotList.size()];
        for (int i = 0; i < compactSlotList.size(); i++) {
            compactSlots[i] = compactSlotList.get(i);
        }

        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(player.getMediaSessionManager().getSessionToken())
                    .setShowActionsInCompactView(compactSlots))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setShowWhen(false)
                .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
                .setColor(ContextCompat.getColor(player.getContext(),
                        R.color.dark_background_color))
                .setColorized(player.getPrefs().getBoolean(
                        player.getContext().getString(R.string.notification_colorize_key), true))
                .setDeleteIntent(PendingIntent.getBroadcast(player.getContext(), NOTIFICATION_ID,
                        new Intent(ACTION_CLOSE), FLAG_UPDATE_CURRENT));

        return builder;
    }

    /**
     * Updates the notification builder and the button icons depending on the playback state.
     * @param player the player currently open, to take data from
     */
    private synchronized void updateNotification(final Player player) {
        if (DEBUG) {
            Log.d(TAG, "updateNotification()");
        }

        // also update content intent, in case the user switched players
        notificationBuilder.setContentIntent(PendingIntent.getActivity(player.getContext(),
                NOTIFICATION_ID, getIntentForNotification(player), FLAG_UPDATE_CURRENT));
        notificationBuilder.setContentTitle(player.getVideoTitle());
        notificationBuilder.setContentText(player.getUploaderName());
        notificationBuilder.setTicker(player.getVideoTitle());
        updateActions(notificationBuilder, player);
        final boolean showThumbnail = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.show_thumbnail_key), true);
        if (showThumbnail) {
            setLargeIcon(notificationBuilder, player);
        }
    }


    @SuppressLint("RestrictedApi")
    boolean shouldUpdateBufferingSlot() {
        if (notificationBuilder == null) {
            // if there is no notification active, there is no point in updating it
            return false;
        } else if (notificationBuilder.mActions.size() < 3) {
            // this should never happen, but let's make sure notification actions are populated
            return true;
        }

        // only second and third slot could contain PLAY_PAUSE_BUFFERING, update them only if they
        // are not already in the buffering state (the only one with a null action intent)
        return (notificationSlots[1] == NotificationConstants.PLAY_PAUSE_BUFFERING
                && notificationBuilder.mActions.get(1).actionIntent != null)
                || (notificationSlots[2] == NotificationConstants.PLAY_PAUSE_BUFFERING
                && notificationBuilder.mActions.get(2).actionIntent != null);
    }


    void createNotificationAndStartForeground(final Player player, final Service service) {
        if (notificationBuilder == null) {
            notificationBuilder = createNotification(player);
        }
        updateNotification(player);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(NOTIFICATION_ID, notificationBuilder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            service.startForeground(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    void cancelNotificationAndStopForeground(final Service service) {
        ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE);

        if (notificationManager != null) {
            notificationManager.cancel(NOTIFICATION_ID);
        }
        notificationManager = null;
        notificationBuilder = null;
    }


    /////////////////////////////////////////////////////
    // ACTIONS
    /////////////////////////////////////////////////////

    private void initializeNotificationSlots(final Player player) {
        for (int i = 0; i < 5; ++i) {
            notificationSlots[i] = player.getPrefs().getInt(
                    player.getContext().getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                    NotificationConstants.SLOT_DEFAULTS[i]);
        }
    }

    @SuppressLint("RestrictedApi")
    private void updateActions(final NotificationCompat.Builder builder, final Player player) {
        builder.mActions.clear();
        for (int i = 0; i < 5; ++i) {
            addAction(builder, player, notificationSlots[i]);
        }
    }

    private void addAction(final NotificationCompat.Builder builder,
                           final Player player,
                           @NotificationConstants.Action final int slot) {
        final NotificationCompat.Action action = getAction(player, slot);
        if (action != null) {
            builder.addAction(action);
        }
    }

    @Nullable
    private NotificationCompat.Action getAction(
            final Player player,
            @NotificationConstants.Action final int selectedAction) {
        final int baseActionIcon = NotificationConstants.ACTION_ICONS[selectedAction];
        switch (selectedAction) {
            case NotificationConstants.PREVIOUS:
                return getAction(player, baseActionIcon,
                        R.string.exo_controls_previous_description, ACTION_PLAY_PREVIOUS);

            case NotificationConstants.NEXT:
                return getAction(player, baseActionIcon,
                        R.string.exo_controls_next_description, ACTION_PLAY_NEXT);

            case NotificationConstants.REWIND:
                return getAction(player, baseActionIcon,
                        R.string.exo_controls_rewind_description, ACTION_FAST_REWIND);

            case NotificationConstants.FORWARD:
                return getAction(player, baseActionIcon,
                        R.string.exo_controls_fastforward_description, ACTION_FAST_FORWARD);

            case NotificationConstants.SMART_REWIND_PREVIOUS:
                if (player.getPlayQueue() != null && player.getPlayQueue().size() > 1) {
                    return getAction(player, R.drawable.exo_notification_previous,
                            R.string.exo_controls_previous_description, ACTION_PLAY_PREVIOUS);
                } else {
                    return getAction(player, R.drawable.exo_controls_rewind,
                            R.string.exo_controls_rewind_description, ACTION_FAST_REWIND);
                }

            case NotificationConstants.SMART_FORWARD_NEXT:
                if (player.getPlayQueue() != null && player.getPlayQueue().size() > 1) {
                    return getAction(player, R.drawable.exo_notification_next,
                            R.string.exo_controls_next_description, ACTION_PLAY_NEXT);
                } else {
                    return getAction(player, R.drawable.exo_controls_fastforward,
                            R.string.exo_controls_fastforward_description, ACTION_FAST_FORWARD);
                }

            case NotificationConstants.PLAY_PAUSE_BUFFERING:
                if (player.getCurrentState() == Player.STATE_PREFLIGHT
                        || player.getCurrentState() == Player.STATE_BLOCKED
                        || player.getCurrentState() == Player.STATE_BUFFERING) {
                    // null intent -> show hourglass icon that does nothing when clicked
                    return new NotificationCompat.Action(R.drawable.ic_hourglass_top,
                            player.getContext().getString(R.string.notification_action_buffering),
                            null);
                }

            case NotificationConstants.PLAY_PAUSE:
                if (player.getCurrentState() == Player.STATE_COMPLETED) {
                    return getAction(player, R.drawable.ic_replay,
                            R.string.exo_controls_pause_description, ACTION_PLAY_PAUSE);
                } else if (player.isPlaying()
                        || player.getCurrentState() == Player.STATE_PREFLIGHT
                        || player.getCurrentState() == Player.STATE_BLOCKED
                        || player.getCurrentState() == Player.STATE_BUFFERING) {
                    return getAction(player, R.drawable.exo_notification_pause,
                            R.string.exo_controls_pause_description, ACTION_PLAY_PAUSE);
                } else {
                    return getAction(player, R.drawable.exo_notification_play,
                            R.string.exo_controls_play_description, ACTION_PLAY_PAUSE);
                }

            case NotificationConstants.REPEAT:
                if (player.getRepeatMode() == REPEAT_MODE_ALL) {
                    return getAction(player, R.drawable.exo_media_action_repeat_all,
                            R.string.exo_controls_repeat_all_description, ACTION_REPEAT);
                } else if (player.getRepeatMode() == REPEAT_MODE_ONE) {
                    return getAction(player, R.drawable.exo_media_action_repeat_one,
                            R.string.exo_controls_repeat_one_description, ACTION_REPEAT);
                } else /* player.getRepeatMode() == REPEAT_MODE_OFF */ {
                    return getAction(player, R.drawable.exo_media_action_repeat_off,
                            R.string.exo_controls_repeat_off_description, ACTION_REPEAT);
                }

            case NotificationConstants.SHUFFLE:
                if (player.getPlayQueue() != null && player.getPlayQueue().isShuffled()) {
                    return getAction(player, R.drawable.exo_controls_shuffle_on,
                            R.string.exo_controls_shuffle_on_description, ACTION_SHUFFLE);
                } else {
                    return getAction(player, R.drawable.exo_controls_shuffle_off,
                            R.string.exo_controls_shuffle_off_description, ACTION_SHUFFLE);
                }

            case NotificationConstants.CLOSE:
                return getAction(player, R.drawable.ic_close,
                        R.string.close, ACTION_CLOSE);

            case NotificationConstants.NOTHING:
            default:
                // do nothing
                return null;
        }
    }

    private NotificationCompat.Action getAction(final Player player,
                                                @DrawableRes final int drawable,
                                                @StringRes final int title,
                                                final String intentAction) {
        return new NotificationCompat.Action(drawable, player.getContext().getString(title),
                PendingIntent.getBroadcast(player.getContext(), NOTIFICATION_ID,
                        new Intent(intentAction), FLAG_UPDATE_CURRENT));
    }

    private Intent getIntentForNotification(final Player player) {
        if (player.audioPlayerSelected() || player.popupPlayerSelected()) {
            // Means we play in popup or audio only. Let's show the play queue
            return NavigationHelper.getPlayQueueActivityIntent(player.getContext());
        } else {
            // We are playing in fragment. Don't open another activity just show fragment. That's it
            final Intent intent = NavigationHelper.getPlayerIntent(
                    player.getContext(), MainActivity.class, null, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            return intent;
        }
    }


    /////////////////////////////////////////////////////
    // BITMAP
    /////////////////////////////////////////////////////

    private void setLargeIcon(final NotificationCompat.Builder builder, final Player player) {
        final boolean scaleImageToSquareAspectRatio = player.getPrefs().getBoolean(
                player.getContext().getString(R.string.scale_to_square_image_in_notifications_key),
                false);
        if (scaleImageToSquareAspectRatio) {
            builder.setLargeIcon(getBitmapWithSquareAspectRatio(player.getThumbnail()));
        } else {
            builder.setLargeIcon(player.getThumbnail());
        }
    }

    private Bitmap getBitmapWithSquareAspectRatio(final Bitmap bitmap) {
        return getResizedBitmap(bitmap, bitmap.getWidth(), bitmap.getWidth());
    }

    private Bitmap getResizedBitmap(final Bitmap bitmap, final int newWidth, final int newHeight) {
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final float scaleWidth = ((float) newWidth) / width;
        final float scaleHeight = ((float) newHeight) / height;
        final Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, false);
    }
}
