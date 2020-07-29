package org.schabi.newpipe.info_list;

import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.util.OnClickGesture;

import java.text.DateFormat;

/*
 * Created by Christian Schabesberger on 26.09.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * InfoItemBuilder.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class ItemHandler {
    private final FragmentActivity activity;
    private final ImageLoader imageLoader = ImageLoader.getInstance();
    private final DateFormat dateFormat;

    @Nullable private OnClickGesture<Object> onItemSelectedListener;
    @Nullable private ItemHolderWithToolbar<?> itemHolderWithToolbarOpen = null;

    public ItemHandler(final FragmentActivity activity, final DateFormat dateFormat) {
        this.activity = activity;
        this.dateFormat = dateFormat;
    }


    public FragmentActivity getActivity() {
        return activity;
    }

    public FragmentManager getFragmentManager() {
        return activity.getSupportFragmentManager();
    }

    public void displayImage(final String url, final ImageView view,
                             final DisplayImageOptions options) {
        imageLoader.displayImage(url, view, options);
    }

    public DateFormat getDateFormat() {
        return dateFormat;
    }


    public void setOnItemSelectedListener(
            @Nullable final OnClickGesture<Object> onItemSelectedListener) {
        this.onItemSelectedListener = onItemSelectedListener;
    }

    @Nullable
    public OnClickGesture<Object> getOnItemSelectedListener() {
        return onItemSelectedListener;
    }

    public void replaceItemHolderWithToolbarOpen(
            @Nullable ItemHolderWithToolbar<?> newItemHolderWithToolbar) {
        if (itemHolderWithToolbarOpen != null) {
            itemHolderWithToolbarOpen.hideItemToolbar();
        }
        itemHolderWithToolbarOpen = newItemHolderWithToolbar;
    }
}
