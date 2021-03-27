package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.Localization;

import de.hdodenhof.circleimageview.CircleImageView;

public class ChannelMiniInfoItemHolder extends InfoItemHolder {
    public final CircleImageView itemThumbnailView;
    public final TextView itemTitleView;
    private final TextView itemAdditionalDetailView;

    ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemAdditionalDetailView = itemView.findViewById(R.id.itemAdditionalDetails);
    }

    public ChannelMiniInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                     final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_channel_mini_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        if (!(infoItem instanceof ChannelInfoItem)) {
            return;
        }
        final ChannelInfoItem item = (ChannelInfoItem) infoItem;

        itemTitleView.setText(item.getName());
        itemAdditionalDetailView.setText(getDetailLine(item));

        PicassoHelper.loadThumbnail(item.getThumbnailUrl()).into(itemThumbnailView);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().selected(item);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnChannelSelectedListener() != null) {
                itemBuilder.getOnChannelSelectedListener().held(item);
            }
            return true;
        });
    }

    protected String getDetailLine(final ChannelInfoItem item) {
        String details = "";
        if (item.getSubscriberCount() >= 0) {
            details += Localization.shortSubscriberCount(itemBuilder.getContext(),
                    item.getSubscriberCount());
        }
        return details;
    }
}
