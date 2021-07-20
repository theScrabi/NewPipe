/*
 * Copyright 2019 Mauricio Colli <mauriciocolli@outlook.com>
 * FeedLoadService.kt is part of NewPipe
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

package org.schabi.newpipe.local.feed.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Function
import org.schabi.newpipe.App
import org.schabi.newpipe.MainActivity.DEBUG
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.service.FeedEventManager.Event.ErrorResultEvent
import org.schabi.newpipe.local.feed.service.FeedEventManager.postEvent
import java.util.concurrent.TimeUnit

class FeedLoadService : Service() {
    companion object {
        private val TAG = FeedLoadService::class.java.simpleName
        private const val NOTIFICATION_ID = 7293450
        private const val ACTION_CANCEL = App.PACKAGE_NAME + ".local.feed.service.FeedLoadService.CANCEL"

        /**
         * How often the notification will be updated.
         */
        private const val NOTIFICATION_SAMPLING_PERIOD = 1500

        const val EXTRA_GROUP_ID: String = "FeedLoadService.EXTRA_GROUP_ID"
    }

    private var loadingDisposable: Disposable? = null
    private var notificationDisposable: Disposable? = null

    private lateinit var feedLoadManager: FeedLoadManager

    // /////////////////////////////////////////////////////////////////////////
    // Lifecycle
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreate() {
        super.onCreate()
        feedLoadManager = FeedLoadManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) {
            Log.d(
                TAG,
                "onStartCommand() called with: intent = [" + intent + "]," +
                    " flags = [" + flags + "], startId = [" + startId + "]"
            )
        }

        if (intent == null || loadingDisposable != null) {
            return START_NOT_STICKY
        }

        setupNotification()
        setupBroadcastReceiver()

        val groupId = intent.getLongExtra(EXTRA_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
        loadingDisposable = feedLoadManager.startLoading(groupId)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnSubscribe {
                startForeground(NOTIFICATION_ID, notificationBuilder.build())
            }
            .subscribe { _, error ->
                if (error != null) {
                    Log.e(TAG, "Error while storing result", error)
                    handleError(error)
                    return@subscribe
                }
                stopService()
            }
        return START_NOT_STICKY
    }

    private fun disposeAll() {
        unregisterReceiver(broadcastReceiver)
        loadingDisposable?.dispose()
        notificationDisposable?.dispose()
    }

    private fun stopService() {
        disposeAll()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    // /////////////////////////////////////////////////////////////////////////
    // Loading & Handling
    // /////////////////////////////////////////////////////////////////////////

    class RequestException(val subscriptionId: Long, message: String, cause: Throwable) : Exception(message, cause) {
        companion object {
            fun wrapList(subscriptionId: Long, info: ListInfo<StreamInfoItem>): List<Throwable> {
                val toReturn = ArrayList<Throwable>(info.errors.size)
                info.errors.mapTo(toReturn) {
                    RequestException(subscriptionId, info.serviceId.toString() + ":" + info.url, it)
                }
                return toReturn
            }
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Notification
    // /////////////////////////////////////////////////////////////////////////

    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var notificationBuilder: NotificationCompat.Builder

    private fun createNotification(): NotificationCompat.Builder {
        val cancelActionIntent = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID, Intent(ACTION_CANCEL), 0
        )

        return NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
            .setOngoing(true)
            .setProgress(-1, -1, true)
            .setSmallIcon(R.drawable.ic_newpipe_triangle_white)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.cancel), cancelActionIntent)
            .setContentTitle(getString(R.string.feed_notification_loading))
    }

    private fun setupNotification() {
        notificationManager = NotificationManagerCompat.from(this)
        notificationBuilder = createNotification()

        val throttleAfterFirstEmission = Function { flow: Flowable<FeedLoadState> ->
            flow.take(1).concatWith(flow.skip(1).throttleLatest(NOTIFICATION_SAMPLING_PERIOD.toLong(), TimeUnit.MILLISECONDS))
        }

        notificationDisposable = feedLoadManager.notification
            .publish(throttleAfterFirstEmission)
            .observeOn(AndroidSchedulers.mainThread())
            .doOnTerminate { notificationManager.cancel(NOTIFICATION_ID) }
            .subscribe(this::updateNotificationProgress)
    }

    private fun updateNotificationProgress(state: FeedLoadState) {
        notificationBuilder.setProgress(state.maxProgress, state.currentProgress, state.maxProgress == -1)

        if (state.maxProgress == -1) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) notificationBuilder.setContentInfo(null)
            if (state.updateDescription.isNotEmpty()) notificationBuilder.setContentText(state.updateDescription)
            notificationBuilder.setContentText(state.updateDescription)
        } else {
            val progressText = state.currentProgress.toString() + "/" + state.maxProgress

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (state.updateDescription.isNotEmpty()) {
                    notificationBuilder.setContentText("${state.updateDescription}  ($progressText)")
                }
            } else {
                notificationBuilder.setContentInfo(progressText)
                if (state.updateDescription.isNotEmpty()) {
                    notificationBuilder.setContentText(state.updateDescription)
                }
            }
        }

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
    }

    // /////////////////////////////////////////////////////////////////////////
    // Notification Actions
    // /////////////////////////////////////////////////////////////////////////

    private lateinit var broadcastReceiver: BroadcastReceiver

    private fun setupBroadcastReceiver() {
        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ACTION_CANCEL) {
                    feedLoadManager.cancel()
                }
            }
        }
        registerReceiver(broadcastReceiver, IntentFilter(ACTION_CANCEL))
    }

    // /////////////////////////////////////////////////////////////////////////
    // Error handling
    // /////////////////////////////////////////////////////////////////////////

    private fun handleError(error: Throwable) {
        postEvent(ErrorResultEvent(error))
        stopService()
    }
}
