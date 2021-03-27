package org.schabi.newpipe.local.subscription.item

import android.content.Context
import android.widget.ImageView
import android.widget.TextView
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.Item
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.util.Localization
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.PicassoHelper

class ChannelItem(
    private val infoItem: ChannelInfoItem,
    private val subscriptionId: Long = -1L,
    var itemVersion: ItemVersion = ItemVersion.NORMAL,
    var gesturesListener: OnClickGesture<ChannelInfoItem>? = null
) : Item<GroupieViewHolder>() {
    override fun getId(): Long = if (subscriptionId == -1L) super.getId() else subscriptionId

    enum class ItemVersion { NORMAL, MINI, GRID }

    override fun getLayout(): Int = when (itemVersion) {
        ItemVersion.NORMAL -> R.layout.list_channel_item
        ItemVersion.MINI -> R.layout.list_channel_mini_item
        ItemVersion.GRID -> R.layout.list_channel_grid_item
    }

    override fun bind(viewHolder: GroupieViewHolder, position: Int) {
        val itemTitleView = viewHolder.root.findViewById<TextView>(R.id.itemTitleView)
        val itemAdditionalDetails = viewHolder.root.findViewById<TextView>(R.id.itemAdditionalDetails)
        val itemChannelDescriptionView = viewHolder.root.findViewById<TextView>(R.id.itemChannelDescriptionView)
        val itemThumbnailView = viewHolder.root.findViewById<ImageView>(R.id.itemThumbnailView)

        itemTitleView.text = infoItem.name
        itemAdditionalDetails.text = getDetailLine(viewHolder.root.context)
        if (itemVersion == ItemVersion.NORMAL) {
            itemChannelDescriptionView.text = infoItem.description
        }

        PicassoHelper.loadThumbnail(infoItem.thumbnailUrl).into(itemThumbnailView)

        gesturesListener?.run {
            viewHolder.root.setOnClickListener { selected(infoItem) }
            viewHolder.root.setOnLongClickListener { held(infoItem); true }
        }
    }

    private fun getDetailLine(context: Context): String {
        var details = if (infoItem.subscriberCount >= 0) {
            Localization.shortSubscriberCount(context, infoItem.subscriberCount)
        } else {
            context.getString(R.string.subscribers_count_not_available)
        }

        if (itemVersion == ItemVersion.NORMAL) {
            if (infoItem.streamCount >= 0) {
                val formattedVideoAmount = Localization.localizeStreamCount(context, infoItem.streamCount)
                details = Localization.concatenateStrings(details, formattedVideoAmount)
            }
        }
        return details
    }

    override fun getSpanSize(spanCount: Int, position: Int): Int {
        return if (itemVersion == ItemVersion.GRID) 1 else spanCount
    }
}
