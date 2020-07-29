package org.schabi.newpipe.local.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemHandler;

public class RemotePlaylistGridItemHolder extends RemotePlaylistItemHolder {
    public RemotePlaylistGridItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(itemHandler, R.layout.list_playlist_grid_item, parent);
    }
}
