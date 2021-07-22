package org.schabi.newpipe.local.feed.service

import android.content.Context
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Notification
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.processors.PublishProcessor
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.feed.FeedDatabaseManager
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ExtractorHelper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FeedLoadManager(private val context: Context) {

    private val subscriptionManager = SubscriptionManager(context)
    private val feedDatabaseManager = FeedDatabaseManager(context)

    private val notificationUpdater = PublishProcessor.create<String>()
    private val currentProgress = AtomicInteger(-1)
    private val maxProgress = AtomicInteger(-1)
    private val cancelSignal = AtomicBoolean()
    private val feedResultsHolder = FeedResultsHolder()

    val notification: Flowable<FeedLoadState> = notificationUpdater.map { description ->
        FeedLoadState(description, maxProgress.get(), currentProgress.get())
    }

    fun startLoading(
        groupId: Long = FeedGroupEntity.GROUP_ALL_ID
    ): Single<List<Notification<FeedUpdateInfo>>> {
        val defaultSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val useFeedExtractor = defaultSharedPreferences.getBoolean(
            context.getString(R.string.feed_use_dedicated_fetch_method_key),
            false
        )
        val thresholdOutdatedSeconds = defaultSharedPreferences.getString(
            context.getString(R.string.feed_update_threshold_key),
            context.getString(R.string.feed_update_threshold_default_value)
        )!!.toInt()

        val outdatedThreshold = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(thresholdOutdatedSeconds.toLong())

        val subscriptions = when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> feedDatabaseManager.outdatedSubscriptions(outdatedThreshold)
            else -> feedDatabaseManager.outdatedSubscriptionsForGroup(groupId, outdatedThreshold)
        }

        return subscriptions
            .take(1)

            .doOnNext {
                currentProgress.set(0)
                maxProgress.set(it.size)
            }
            .filter { it.isNotEmpty() }

            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                notificationUpdater.onNext("")
                broadcastProgress()
            }

            .observeOn(Schedulers.io())
            .flatMap { Flowable.fromIterable(it) }
            .takeWhile { !cancelSignal.get() }

            .parallel(PARALLEL_EXTRACTIONS, PARALLEL_EXTRACTIONS * 2)
            .runOn(Schedulers.io(), PARALLEL_EXTRACTIONS * 2)
            .filter { !cancelSignal.get() }

            .map { subscriptionEntity ->
                var error: Throwable? = null
                try {
                    val listInfo = if (useFeedExtractor) {
                        ExtractorHelper
                            .getFeedInfoFallbackToChannelInfo(subscriptionEntity.serviceId, subscriptionEntity.url)
                            .onErrorReturn {
                                error = it // store error, otherwise wrapped into RuntimeException
                                throw it
                            }
                            .blockingGet()
                    } else {
                        ExtractorHelper
                            .getChannelInfo(subscriptionEntity.serviceId, subscriptionEntity.url, true)
                            .onErrorReturn {
                                error = it // store error, otherwise wrapped into RuntimeException
                                throw it
                            }
                            .blockingGet()
                    } as ListInfo<StreamInfoItem>

                    return@map Notification.createOnNext(FeedUpdateInfo(subscriptionEntity, listInfo))
                } catch (e: Throwable) {
                    if (error == null) {
                        // do this to prevent blockingGet() from wrapping into RuntimeException
                        error = e
                    }

                    val request = "${subscriptionEntity.serviceId}:${subscriptionEntity.url}"
                    val wrapper = FeedLoadService.RequestException(subscriptionEntity.uid, request, error!!)
                    return@map Notification.createOnError<FeedUpdateInfo>(wrapper)
                }
            }
            .sequential()

            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext(NotificationConsumer())

            .observeOn(Schedulers.io())
            .buffer(BUFFER_COUNT_BEFORE_INSERT)
            .doOnNext(DatabaseConsumer())

            .subscribeOn(Schedulers.io())
            .toList()
            .flatMap { x -> postProcessFeed().toSingleDefault(x.flatten()) }
    }

    fun cancel() {
        cancelSignal.set(true)
    }

    private fun broadcastProgress() {
        FeedEventManager.postEvent(FeedEventManager.Event.ProgressEvent(currentProgress.get(), maxProgress.get()))
    }

    private fun postProcessFeed() = Completable.fromRunnable {
        FeedEventManager.postEvent(FeedEventManager.Event.ProgressEvent(R.string.feed_processing_message))
        feedDatabaseManager.removeOrphansOrOlderStreams()

        FeedEventManager.postEvent(FeedEventManager.Event.SuccessResultEvent(feedResultsHolder.itemsErrors))
    }.doOnSubscribe {
        currentProgress.set(-1)
        maxProgress.set(-1)

        notificationUpdater.onNext(context.getString(R.string.feed_processing_message))
        FeedEventManager.postEvent(FeedEventManager.Event.ProgressEvent(R.string.feed_processing_message))
    }.subscribeOn(Schedulers.io())

    private inner class NotificationConsumer : Consumer<Notification<FeedUpdateInfo>> {
        override fun accept(item: Notification<FeedUpdateInfo>) {
            currentProgress.incrementAndGet()
            notificationUpdater.onNext(item.value?.name.orEmpty())

            broadcastProgress()
        }
    }

    private inner class DatabaseConsumer : Consumer<List<Notification<FeedUpdateInfo>>> {

        override fun accept(list: List<Notification<FeedUpdateInfo>>) {
            feedDatabaseManager.database().runInTransaction {
                for (notification in list) {
                    when {
                        notification.isOnNext -> {
                            val subscriptionId = notification.value.uid
                            val info = notification.value.listInfo

                            notification.value.newStreamsCount = countNewStreams(info.relatedItems)
                            feedDatabaseManager.upsertAll(subscriptionId, info.relatedItems)
                            subscriptionManager.updateFromInfo(subscriptionId, info)

                            if (info.errors.isNotEmpty()) {
                                feedResultsHolder.addErrors(FeedLoadService.RequestException.wrapList(subscriptionId, info))
                                feedDatabaseManager.markAsOutdated(subscriptionId)
                            }
                        }
                        notification.isOnError -> {
                            val error = notification.error
                            feedResultsHolder.addError(error)

                            if (error is FeedLoadService.RequestException) {
                                feedDatabaseManager.markAsOutdated(error.subscriptionId)
                            }
                        }
                    }
                }
            }
        }

        private fun countNewStreams(list: List<StreamInfoItem>): Int {
            var count = 0
            for (item in list) {
                if (feedDatabaseManager.isStreamExist(item)) {
                    return count
                } else {
                    count++
                }
            }
            return 0
        }
    }

    private companion object {

        /**
         * How many extractions will be running in parallel.
         */
        const val PARALLEL_EXTRACTIONS = 6

        /**
         * Number of items to buffer to mass-insert in the database.
         */
        const val BUFFER_COUNT_BEFORE_INSERT = 20
    }
}
