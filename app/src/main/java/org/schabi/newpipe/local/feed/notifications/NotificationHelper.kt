package org.schabi.newpipe.local.feed.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.service.FeedUpdateInfo
import org.schabi.newpipe.util.NavigationHelper

class NotificationHelper(val context: Context) {

    private val manager = context.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

    fun notify(data: FeedUpdateInfo): Completable {
        val newStreams: List<StreamInfoItem> = data.newStreams
        val summary = context.resources.getQuantityString(
            R.plurals.new_streams, newStreams.size, newStreams.size
        )
        val builder = NotificationCompat.Builder(
            context,
            context.getString(R.string.streams_notification_channel_id)
        )
            .setContentTitle(
                context.getString(
                    R.string.notification_title_pattern,
                    data.name,
                    summary
                )
            )
            .setContentText(
                data.listInfo.relatedItems.joinToString(
                    context.getString(R.string.enumeration_comma)
                ) { x -> x.name }
            )
            .setNumber(newStreams.size)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_LARGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setLargeIcon(
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_newpipe_triangle_white
                )
            )
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setColorized(true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
        val style = NotificationCompat.InboxStyle()
        for (stream in newStreams) {
            style.addLine(stream.name)
        }
        style.setSummaryText(summary)
        style.setBigContentTitle(data.name)
        builder.setStyle(style)
        builder.setContentIntent(
            PendingIntent.getActivity(
                context,
                data.pseudoId,
                NavigationHelper.getChannelIntent(context, data.listInfo.serviceId, data.listInfo.url)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                0
            )
        )
        return Single.create(NotificationIcon(context, data.avatarUrl))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSuccess { icon ->
                builder.setLargeIcon(icon)
            }
            .ignoreElement()
            .onErrorComplete()
            .doOnComplete { manager.notify(data.pseudoId, builder.build()) }
    }

    companion object {
        /**
         * Check whether notifications are not disabled by user via system settings.
         *
         * @param context Context
         * @return true if notifications are allowed, false otherwise
         */
        fun isNotificationsEnabledNative(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = context.getString(R.string.streams_notification_channel_id)
                val manager = context.getSystemService(
                    Context.NOTIFICATION_SERVICE
                ) as NotificationManager
                val channel = manager.getNotificationChannel(channelId)
                channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }

        @JvmStatic
        fun isNewStreamsNotificationsEnabled(context: Context): Boolean {
            return (
                PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean(context.getString(R.string.enable_streams_notifications), false) &&
                    isNotificationsEnabledNative(context)
                )
        }

        fun openNativeSettingsScreen(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = context.getString(R.string.streams_notification_channel_id)
                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                context.startActivity(intent)
            } else {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:" + context.packageName)
                context.startActivity(intent)
            }
        }
    }
}
