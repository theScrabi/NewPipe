package org.schabi.newpipe.notifications

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.RxWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Single
import org.schabi.newpipe.R
import java.util.concurrent.TimeUnit

class NotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : RxWorker(appContext, workerParams) {

    private val notificationHelper by lazy {
        NotificationHelper(appContext)
    }

    override fun createWork() = if (isEnabled(applicationContext)) {
        Flowable.create(
            SubscriptionUpdates(applicationContext),
            BackpressureStrategy.BUFFER
        ).doOnNext { notificationHelper.notify(it) }
            .toList()
            .map { Result.success() }
            .onErrorReturnItem(Result.failure())
    } else Single.just(Result.success())

    companion object {

        private const val TAG = "notifications"

        private fun isEnabled(context: Context): Boolean {
            return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(
                    context.getString(R.string.enable_streams_notifications),
                    false
                ) && NotificationHelper.isNotificationsEnabledNative(context)
        }

        fun schedule(context: Context, options: ScheduleOptions, force: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (options.isRequireNonMeteredNetwork) {
                        NetworkType.UNMETERED
                    } else {
                        NetworkType.CONNECTED
                    }
                ).build()
            val request = PeriodicWorkRequest.Builder(
                NotificationWorker::class.java,
                options.interval,
                TimeUnit.MILLISECONDS
            ).setConstraints(constraints)
                .addTag(TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    TAG,
                    if (force) {
                        ExistingPeriodicWorkPolicy.REPLACE
                    } else {
                        ExistingPeriodicWorkPolicy.KEEP
                    },
                    request
                )
        }

        @JvmStatic
        fun schedule(context: Context) = schedule(context, ScheduleOptions.from(context))
    }
}
