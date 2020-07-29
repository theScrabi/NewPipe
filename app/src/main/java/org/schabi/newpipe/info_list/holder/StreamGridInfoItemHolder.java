package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemHandler;

public class StreamGridInfoItemHolder extends StreamInfoItemHolder {
    public StreamGridInfoItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(itemHandler, R.layout.list_stream_grid_item, parent);
    }
}
