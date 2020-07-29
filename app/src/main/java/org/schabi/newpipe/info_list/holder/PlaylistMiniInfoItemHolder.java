package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.info_list.ItemHolderWithToolbar;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class PlaylistMiniInfoItemHolder extends ItemHolderWithToolbar<PlaylistInfoItem> {
    public final ImageView itemThumbnailView;
    private final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;

    public PlaylistMiniInfoItemHolder(final ItemHandler itemHandler, final int layoutId,
                                      final ViewGroup parent) {
        super(PlaylistInfoItem.class, itemHandler, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
    }

    public PlaylistMiniInfoItemHolder(final ItemHandler itemHandler,
                                      final ViewGroup parent) {
        this(itemHandler, R.layout.list_playlist_mini_item, parent);
    }

    @Override
    public void updateFromItem(final PlaylistInfoItem item,
                               final HistoryRecordManager historyRecordManager) {
        itemTitleView.setText(item.getName());
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), item.getStreamCount()));
        itemUploaderView.setText(item.getUploaderName());

        itemHandler.displayImage(item.getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);
    }
}
