package org.schabi.newpipe.notifications

import android.content.Context
import io.reactivex.FlowableEmitter
import io.reactivex.FlowableOnSubscribe
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.local.subscription.SubscriptionManager
import org.schabi.newpipe.util.ExtractorHelper

class SubscriptionUpdates(context: Context) : FlowableOnSubscribe<ChannelUpdates?> {

    private val subscriptionManager = SubscriptionManager(context)
    private val streamTable = NewPipeDatabase.getInstance(context).streamDAO()

    override fun subscribe(emitter: FlowableEmitter<ChannelUpdates?>) {
        try {
            val subscriptions = subscriptionManager.subscriptions().blockingFirst()
            for (subscription in subscriptions) {
                if (subscription.notificationMode != NotificationMode.DISABLED) {
                    val channel = ExtractorHelper.getChannelInfo(
                        subscription.serviceId,
                        subscription.url, true
                    ).blockingGet()
                    val updates = ChannelUpdates.from(channel, filterStreams(channel.relatedItems))
                    if (updates.isNotEmpty) {
                        emitter.onNext(updates)
                        // prevent duplicated notifications
                        streamTable.upsertAll(updates.streams.map { StreamEntity(it) })
                    }
                }
            }
            emitter.onComplete()
        } catch (e: Exception) {
            emitter.onError(e)
        }
    }

    private fun filterStreams(list: List<*>): List<StreamInfoItem> {
        val streams = ArrayList<StreamInfoItem>(list.size)
        for (o in list) {
            if (o is StreamInfoItem) {
                if (streamTable.exists(o.serviceId.toLong(), o.url)) {
                    break
                }
                streams.add(o)
            }
        }
        return streams
    }
}
