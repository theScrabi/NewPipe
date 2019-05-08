package org.schabi.newpipe.notifications

import android.content.Context
import android.content.Intent
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.util.NavigationHelper

data class ChannelUpdates(
    val serviceId: Int,
    val url: String,
    val avatarUrl: String,
    val name: String,
    val streams: List<StreamInfoItem>
) {

    val id = url.hashCode()

    val isNotEmpty: Boolean
        get() = streams.isNotEmpty()

    val size = streams.size

    fun getText(context: Context): String {
        val separator = context.resources.getString(R.string.enumeration_comma) + " "
        return streams.joinToString(separator) { it.name }
    }

    fun createOpenChannelIntent(context: Context?): Intent {
        return NavigationHelper.getChannelIntent(context, serviceId, url)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    companion object {
        fun from(channel: ChannelInfo, streams: List<StreamInfoItem>): ChannelUpdates {
            return ChannelUpdates(
                channel.serviceId,
                channel.url,
                channel.avatarUrl,
                channel.name,
                streams
            )
        }
    }
}
