package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.info_list.LivePreviewDispatcher;

public class StreamGridInfoItemHolder extends StreamInfoItemHolder {

    public StreamGridInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                    final ViewGroup parent,
                                    final LivePreviewDispatcher previewDispatcher) {
        super(infoItemBuilder, R.layout.list_stream_grid_item, parent, previewDispatcher);
    }
}
