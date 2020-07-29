package org.schabi.newpipe.local.holder;

import android.view.View;
import android.view.ViewGroup;

import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class LocalPlaylistItemHolder extends PlaylistItemHolder<PlaylistMetadataEntry> {
    public LocalPlaylistItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(PlaylistMetadataEntry.class, itemHandler, parent);
    }

    LocalPlaylistItemHolder(final ItemHandler itemHandler, final int layoutId,
                            final ViewGroup parent) {
        super(PlaylistMetadataEntry.class, itemHandler, layoutId, parent);
    }

    @Override
    public void updateFromItem(final PlaylistMetadataEntry item,
                               final HistoryRecordManager historyRecordManager) {
        itemTitleView.setText(item.name);
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), item.streamCount));
        itemUploaderView.setVisibility(View.INVISIBLE);

        itemHandler.displayImage(item.thumbnailUrl, itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS);
    }
}
