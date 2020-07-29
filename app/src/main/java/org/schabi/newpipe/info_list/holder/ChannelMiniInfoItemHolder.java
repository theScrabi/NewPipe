package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.info_list.ItemHolderWithToolbar;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChannelMiniInfoItemHolder extends ItemHolderWithToolbar<ChannelInfoItem> {
    public final CircleImageView itemThumbnailView;
    public final TextView itemTitleView;
    private final TextView itemAdditionalDetailView;

    ChannelMiniInfoItemHolder(final ItemHandler itemHandler, final int layoutId,
                              final ViewGroup parent) {
        super(ChannelInfoItem.class, itemHandler, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
    }

    public ChannelMiniInfoItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        this(itemHandler, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final ChannelInfoItem item,
                               final HistoryRecordManager historyRecordManager) {
        itemTitleView.setText(item.getName());
        itemAdditionalDetailView.setText(getDetailLine(item));

        itemHandler.displayImage(item.getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);
    }

    protected String getDetailLine(final ChannelInfoItem item) {
        String details = "";
        if (item.getSubscriberCount() >= 0) {
            details += Localization.shortSubscriberCount(itemHandler.getActivity(),
                    item.getSubscriberCount());
        }
        return details;
    }
}
