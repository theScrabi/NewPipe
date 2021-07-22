package org.schabi.newpipe.util;

import androidx.recyclerview.widget.RecyclerView;

public abstract class OnClickGesture<T> {

    public abstract void selected(T selectedItem);

    public void held(final T selectedItem) {
        // Optional gesture
    }

    public void drag(final T selectedItem, final RecyclerView.ViewHolder viewHolder) {
        // Optional gesture
    }

    public void swipe(final T selectedItem, final RecyclerView.ViewHolder viewHolder) {
        // Optional gesture
    }
}
