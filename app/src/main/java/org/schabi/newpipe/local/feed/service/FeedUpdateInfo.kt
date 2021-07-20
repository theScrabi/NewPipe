package org.schabi.newpipe.local.feed.service

import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.extractor.ListInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class FeedUpdateInfo(
    val uid: Long,
    @NotificationMode
    val notificationMode: Int,
    val name: String,
    val avatarUrl: String,
    val listInfo: ListInfo<StreamInfoItem>
) {
    constructor(subscription: SubscriptionEntity, listInfo: ListInfo<StreamInfoItem>) : this(
        uid = subscription.uid,
        notificationMode = subscription.notificationMode,
        name = subscription.name,
        avatarUrl = subscription.avatarUrl,
        listInfo = listInfo
    )

    val pseudoId: Int
        get() = listInfo.url.hashCode()

    var newStreamsCount: Int = 0

    val newStreams: List<StreamInfoItem>
        get() = listInfo.relatedItems.take(newStreamsCount)
}
