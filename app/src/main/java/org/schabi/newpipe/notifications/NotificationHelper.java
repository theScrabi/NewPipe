package org.schabi.newpipe.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class NotificationHelper {

    private final Context context;
    private final NotificationManager manager;
    private final CompositeDisposable disposable;

    public NotificationHelper(final Context context) {
        this.context = context;
        this.disposable = new CompositeDisposable();
        this.manager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE
        );
    }

    public Context getContext() {
        return context;
    }

    /**
     * Check whether notifications are not disabled by user via system settings.
     *
     * @param context Context
     * @return true if notifications are allowed, false otherwise
     */
    public static boolean isNotificationsEnabledNative(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelId = context.getString(R.string.streams_notification_channel_id);
            final NotificationManager manager = (NotificationManager) context
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                final NotificationChannel channel = manager.getNotificationChannel(channelId);
                return channel != null
                        && channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
            } else {
                return false;
            }
        } else {
            return NotificationManagerCompat.from(context).areNotificationsEnabled();
        }
    }

    public static boolean isNewStreamsNotificationsEnabled(@NonNull final Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.enable_streams_notifications), false)
                && isNotificationsEnabledNative(context);
    }

    public static void openNativeSettingsScreen(final Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final String channelId = context.getString(R.string.streams_notification_channel_id);
            final Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
            context.startActivity(intent);
        } else {
            final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        }
    }

    public void notify(final ChannelUpdates data) {
        final String summary = context.getResources().getQuantityString(
                R.plurals.new_streams, data.getSize(), data.getSize()
        );
        final NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                context.getString(R.string.streams_notification_channel_id))
                .setContentTitle(
                        context.getString(R.string.notification_title_pattern,
                                data.getName(),
                                summary)
                )
                .setContentText(data.getText(context))
                .setNumber(data.getSize())
                .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_stat_newpipe)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(),
                        R.drawable.ic_newpipe_triangle_white))
                .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
                .setColorized(true)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL);
        final NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();
        for (final StreamInfoItem stream : data.getStreams()) {
            style.addLine(stream.getName());
        }
        style.setSummaryText(summary);
        style.setBigContentTitle(data.getName());
        builder.setStyle(style);
        builder.setContentIntent(PendingIntent.getActivity(
                context,
                data.getId(),
                data.createOpenChannelIntent(context),
                0
        ));

        disposable.add(
                Single.create(new NotificationIcon(context, data.getAvatarUrl()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .doAfterTerminate(() -> manager.notify(data.getId(), builder.build()))
                        .subscribe(builder::setLargeIcon, throwable -> {
                            if (BuildConfig.DEBUG) {
                                throwable.printStackTrace();
                            }
                        })
        );
    }
}
