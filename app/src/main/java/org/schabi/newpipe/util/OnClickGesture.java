package org.schabi.newpipe.util;

import androidx.recyclerview.widget.RecyclerView;

public abstract class OnClickGesture<T> {

    public void selected(T selectedItem) {
        // Optional gesture
    }

    public void held(final T selectedItem) {
        // Optional gesture
    }

    public void drag(final T selectedItem, final RecyclerView.ViewHolder viewHolder) {
        // Optional gesture
    }
}
