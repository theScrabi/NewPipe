package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemHandler;

public class PlaylistInfoItemHolder extends PlaylistMiniInfoItemHolder {
    public PlaylistInfoItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(itemHandler, R.layout.list_playlist_item, parent);
    }
}
