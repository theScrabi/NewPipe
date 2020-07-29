package org.schabi.newpipe.local.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.ItemHandler;

public class LocalStatisticStreamGridItemHolder extends LocalStatisticStreamItemHolder {
    public LocalStatisticStreamGridItemHolder(final ItemHandler itemHandler,
                                              final ViewGroup parent) {
        super(itemHandler, R.layout.list_stream_grid_item, parent);
    }
}
