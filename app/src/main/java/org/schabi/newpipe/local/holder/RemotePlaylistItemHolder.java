package org.schabi.newpipe.local.holder;

import android.text.TextUtils;
import android.view.ViewGroup;

import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;

public class RemotePlaylistItemHolder extends PlaylistItemHolder<PlaylistRemoteEntity> {
    public RemotePlaylistItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(PlaylistRemoteEntity.class, itemHandler, parent);
    }

    RemotePlaylistItemHolder(final ItemHandler itemHandler,
                             final int layoutId,
                             final ViewGroup parent) {
        super(PlaylistRemoteEntity.class, itemHandler, layoutId, parent);
    }

    @Override
    public void updateFromItem(final PlaylistRemoteEntity item,
                               final HistoryRecordManager historyRecordManager) {
        itemTitleView.setText(item.getName());
        itemStreamCountView.setText(Localization.localizeStreamCountMini(
                itemStreamCountView.getContext(), item.getStreamCount()));

        // Here is where the uploader name is set in the bookmarked playlists library
        if (!TextUtils.isEmpty(item.getUploader())) {
            itemUploaderView.setText(Localization.concatenateStrings(item.getUploader(),
                    NewPipe.getNameOfService(item.getServiceId())));
        } else {
            itemUploaderView.setText(NewPipe.getNameOfService(item.getServiceId()));
        }

        itemHandler.displayImage(item.getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_PLAYLIST_OPTIONS);
    }
}
