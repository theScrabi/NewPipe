package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemHandler;

public class ChannelGridInfoItemHolder extends ChannelMiniInfoItemHolder {
    public ChannelGridInfoItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(itemHandler, R.layout.list_channel_grid_item, parent);
    }
}
