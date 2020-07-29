package org.schabi.newpipe.info_list;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.info_list.holder.ChannelGridInfoItemHolder;
import org.schabi.newpipe.info_list.holder.ChannelInfoItemHolder;
import org.schabi.newpipe.info_list.holder.ChannelMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.CommentsInfoItemHolder;
import org.schabi.newpipe.info_list.holder.CommentsMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistGridInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistInfoItemHolder;
import org.schabi.newpipe.info_list.holder.PlaylistMiniInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamGridInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamInfoItemHolder;
import org.schabi.newpipe.info_list.holder.StreamMiniInfoItemHolder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.local.holder.LocalPlaylistGridItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistStreamGridItemHolder;
import org.schabi.newpipe.local.holder.LocalPlaylistStreamItemHolder;
import org.schabi.newpipe.local.holder.LocalStatisticStreamGridItemHolder;
import org.schabi.newpipe.local.holder.LocalStatisticStreamItemHolder;
import org.schabi.newpipe.local.holder.RemotePlaylistGridItemHolder;
import org.schabi.newpipe.local.holder.RemotePlaylistItemHolder;
import org.schabi.newpipe.util.FallbackViewHolder;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.OnClickGesture;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

/*
 * Created by Christian Schabesberger on 01.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * InfoListAdapter.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class ItemListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final String TAG = ItemListAdapter.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int HEADER_TYPE = 0;
    private static final int FOOTER_TYPE = 1;

    private static final int STREAM_HOLDER_MINI_TYPE = 0x100;
    private static final int STREAM_HOLDER_TYPE = 0x101;
    private static final int STREAM_HOLDER_GRID_TYPE = 0x102;
    private static final int CHANNEL_HOLDER_MINI_TYPE = 0x200;
    private static final int CHANNEL_HOLDER_TYPE = 0x201;
    private static final int CHANNEL_HOLDER_GRID_TYPE = 0x202;
    private static final int PLAYLIST_HOLDER_MINI_TYPE = 0x300;
    private static final int PLAYLIST_HOLDER_TYPE = 0x301;
    private static final int PLAYLIST_HOLDER_GRID_TYPE = 0x302;
    private static final int COMMENT_HOLDER_MINI_TYPE = 0x400;
    private static final int COMMENT_HOLDER_TYPE = 0x401;

    private static final int STREAM_STATISTICS_HOLDER_TYPE = 0x1000;
    private static final int STREAM_STATISTICS_GRID_HOLDER_TYPE = 0x1002;
    private static final int STREAM_PLAYLIST_HOLDER_TYPE = 0x1001;
    private static final int STREAM_PLAYLIST_GRID_HOLDER_TYPE = 0x1004;
    private static final int LOCAL_PLAYLIST_HOLDER_TYPE = 0x2000;
    private static final int LOCAL_PLAYLIST_GRID_HOLDER_TYPE = 0x2002;
    private static final int REMOTE_PLAYLIST_HOLDER_TYPE = 0x2001;
    private static final int REMOTE_PLAYLIST_GRID_HOLDER_TYPE = 0x2004;

    private final ItemHandler itemHandler;
    private final ArrayList<Object> itemList;
    private final HistoryRecordManager recordManager;

    private boolean useMiniVariant = false;
    private boolean useGridVariant = false;
    private boolean showFooter = false;
    private View header = null;
    private View footer = null;

    public ItemListAdapter(final FragmentActivity activity) {
        this.recordManager = new HistoryRecordManager(activity);
        itemHandler = new ItemHandler(activity, DateFormat.getDateInstance(DateFormat.SHORT,
                Localization.getPreferredLocale(activity)));
        itemList = new ArrayList<>();
    }

    public void setOnItemSelectedListener(
            @Nullable final OnClickGesture<Object> listener) {
        itemHandler.setOnItemSelectedListener(listener);
    }

    public void setUseMiniVariant(final boolean useMiniVariant) {
        this.useMiniVariant = useMiniVariant;
    }

    public void setUseGridVariant(final boolean useGridVariant) {
        this.useGridVariant = useGridVariant;
    }

    public void addItems(@Nullable final List<?> data) {
        if (data == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "addInfoItemList() before > infoItemList.size() = "
                    + itemList.size() + ", data.size() = " + data.size());
        }

        int offsetStart = sizeConsideringHeaderOffset();
        itemList.addAll(data);

        if (DEBUG) {
            Log.d(TAG, "addInfoItemList() after > offsetStart = " + offsetStart + ", "
                    + "infoItemList.size() = " + itemList.size() + ", "
                    + "header = " + header + ", footer = " + footer + ", "
                    + "showFooter = " + showFooter);
        }
        notifyItemRangeInserted(offsetStart, data.size());

        if (footer != null && showFooter) {
            int footerNow = sizeConsideringHeaderOffset();
            notifyItemMoved(offsetStart, footerNow);

            if (DEBUG) {
                Log.d(TAG, "addInfoItemList() footer from " + offsetStart
                        + " to " + footerNow);
            }
        }
    }

    public void setItemList(final List<?> data) {
        itemList.clear();
        itemList.addAll(data);
        notifyDataSetChanged();
    }

    public void removeItem(final LocalItem data) {
        final int index = itemList.indexOf(data);
        itemList.remove(index);
        notifyItemRemoved(index + (header != null ? 1 : 0));
    }

    public boolean swapItems(final int fromAdapterPosition, final int toAdapterPosition) {
        final int actualFrom = adapterOffsetWithoutHeader(fromAdapterPosition);
        final int actualTo = adapterOffsetWithoutHeader(toAdapterPosition);

        if (actualFrom < 0 || actualTo < 0) {
            return false;
        }
        if (actualFrom >= itemList.size() || actualTo >= itemList.size()) {
            return false;
        }

        itemList.add(actualTo, itemList.remove(actualFrom));
        notifyItemMoved(fromAdapterPosition, toAdapterPosition);
        return true;
    }

    public void clearStreamItemList() {
        if (itemList.isEmpty()) {
            return;
        }
        itemList.clear();
        notifyDataSetChanged();
    }

    public void setHeader(final View header) {
        boolean changed = header != this.header;
        this.header = header;
        if (changed) {
            notifyDataSetChanged();
        }
    }

    public void setFooter(final View view) {
        this.footer = view;
    }

    public void showFooter(final boolean show) {
        if (DEBUG) {
            Log.d(TAG, "showFooter() called with: show = [" + show + "]");
        }
        if (show == showFooter) {
            return;
        }

        showFooter = show;
        if (show) {
            notifyItemInserted(sizeConsideringHeaderOffset());
        } else {
            notifyItemRemoved(sizeConsideringHeaderOffset());
        }
    }

    private int adapterOffsetWithoutHeader(final int offset) {
        return offset - (header != null ? 1 : 0);
    }

    private int sizeConsideringHeaderOffset() {
        int i = itemList.size() + (header != null ? 1 : 0);
        if (DEBUG) {
            Log.d(TAG, "sizeConsideringHeaderOffset() called → " + i);
        }
        return i;
    }

    public ArrayList<Object> getItemList() {
        return itemList;
    }

    @Override
    public int getItemCount() {
        int count = itemList.size();
        if (header != null) {
            count++;
        }
        if (footer != null && showFooter) {
            count++;
        }

        if (DEBUG) {
            Log.d(TAG, "getItemCount() called with: "
                    + "count = " + count + ", infoItemList.size() = " + itemList.size() + ", "
                    + "header = " + header + ", footer = " + footer + ", "
                    + "showFooter = " + showFooter);
        }
        return count;
    }

    @Override
    public int getItemViewType(int position) {
        if (DEBUG) {
            Log.d(TAG, "getItemViewType() called with: position = [" + position + "]");
        }

        if (header != null && position == 0) {
            return HEADER_TYPE;
        } else if (header != null) {
            position--;
        }
        if (footer != null && position == itemList.size() && showFooter) {
            return FOOTER_TYPE;
        }

        final Object object = itemList.get(position);
        if (object instanceof InfoItem) {
            return getRemoteItemViewType((InfoItem) object);
        } else if (object instanceof LocalItem) {
            return getLocalItemViewType((LocalItem) object);
        } else {
            Log.e(TAG, "No holder type has been considered for item of unknown type: ["
                    + object + "]");
            return -1;
        }
    }

    private int getRemoteItemViewType(final InfoItem item) {
        switch (item.getInfoType()) {
            case STREAM:
                return useGridVariant ? STREAM_HOLDER_GRID_TYPE : useMiniVariant
                        ? STREAM_HOLDER_MINI_TYPE : STREAM_HOLDER_TYPE;
            case CHANNEL:
                return useGridVariant ? CHANNEL_HOLDER_GRID_TYPE : useMiniVariant
                        ? CHANNEL_HOLDER_MINI_TYPE : CHANNEL_HOLDER_TYPE;
            case PLAYLIST:
                return useGridVariant ? PLAYLIST_HOLDER_GRID_TYPE : useMiniVariant
                        ? PLAYLIST_HOLDER_MINI_TYPE : PLAYLIST_HOLDER_TYPE;
            case COMMENT:
                return useMiniVariant ? COMMENT_HOLDER_MINI_TYPE : COMMENT_HOLDER_TYPE;
            default:
                Log.e(TAG, "No holder type has been considered for remote item: ["
                        + item.getInfoType() + "]");
                return -1;
        }
    }

    private int getLocalItemViewType(final LocalItem item) {
        switch (item.getLocalItemType()) {
            case PLAYLIST_LOCAL_ITEM:
                return useGridVariant
                        ? LOCAL_PLAYLIST_GRID_HOLDER_TYPE : LOCAL_PLAYLIST_HOLDER_TYPE;
            case PLAYLIST_REMOTE_ITEM:
                return useGridVariant
                        ? REMOTE_PLAYLIST_GRID_HOLDER_TYPE : REMOTE_PLAYLIST_HOLDER_TYPE;
            case PLAYLIST_STREAM_ITEM:
                return useGridVariant
                        ? STREAM_PLAYLIST_GRID_HOLDER_TYPE : STREAM_PLAYLIST_HOLDER_TYPE;
            case STATISTIC_STREAM_ITEM:
                return useGridVariant
                        ? STREAM_STATISTICS_GRID_HOLDER_TYPE : STREAM_STATISTICS_HOLDER_TYPE;
            default:
                Log.e(TAG, "No holder type has been considered for local item: ["
                        + item.getLocalItemType() + "]");
                return -1;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull final ViewGroup parent,
                                                      final int type) {
        if (DEBUG) {
            Log.d(TAG, "onCreateViewHolder() called with: "
                    + "parent = [" + parent + "], type = [" + type + "]");
        }
        switch (type) {
            case HEADER_TYPE:
                return new HFHolder(header);
            case FOOTER_TYPE:
                return new HFHolder(footer);
            case STREAM_HOLDER_MINI_TYPE:
                return new StreamMiniInfoItemHolder(itemHandler, parent);
            case STREAM_HOLDER_TYPE:
                return new StreamInfoItemHolder(itemHandler, parent);
            case STREAM_HOLDER_GRID_TYPE:
                return new StreamGridInfoItemHolder(itemHandler, parent);
            case CHANNEL_HOLDER_MINI_TYPE:
                return new ChannelMiniInfoItemHolder(itemHandler, parent);
            case CHANNEL_HOLDER_TYPE:
                return new ChannelInfoItemHolder(itemHandler, parent);
            case CHANNEL_HOLDER_GRID_TYPE:
                return new ChannelGridInfoItemHolder(itemHandler, parent);
            case PLAYLIST_HOLDER_MINI_TYPE:
                return new PlaylistMiniInfoItemHolder(itemHandler, parent);
            case PLAYLIST_HOLDER_TYPE:
                return new PlaylistInfoItemHolder(itemHandler, parent);
            case PLAYLIST_HOLDER_GRID_TYPE:
                return new PlaylistGridInfoItemHolder(itemHandler, parent);
            case COMMENT_HOLDER_MINI_TYPE:
                return new CommentsMiniInfoItemHolder(itemHandler, parent);
            case COMMENT_HOLDER_TYPE:
                return new CommentsInfoItemHolder(itemHandler, parent);
            case LOCAL_PLAYLIST_HOLDER_TYPE:
                return new LocalPlaylistItemHolder(itemHandler, parent);
            case LOCAL_PLAYLIST_GRID_HOLDER_TYPE:
                return new LocalPlaylistGridItemHolder(itemHandler, parent);
            case REMOTE_PLAYLIST_HOLDER_TYPE:
                return new RemotePlaylistItemHolder(itemHandler, parent);
            case REMOTE_PLAYLIST_GRID_HOLDER_TYPE:
                return new RemotePlaylistGridItemHolder(itemHandler, parent);
            case STREAM_PLAYLIST_HOLDER_TYPE:
                return new LocalPlaylistStreamItemHolder(itemHandler, parent);
            case STREAM_PLAYLIST_GRID_HOLDER_TYPE:
                return new LocalPlaylistStreamGridItemHolder(itemHandler, parent);
            case STREAM_STATISTICS_HOLDER_TYPE:
                return new LocalStatisticStreamItemHolder(itemHandler, parent);
            case STREAM_STATISTICS_GRID_HOLDER_TYPE:
                return new LocalStatisticStreamGridItemHolder(itemHandler, parent);
            default:
                Log.e(TAG, "Invalid view holder type: [" + type + "]");
                return new FallbackViewHolder(new View(parent.getContext()));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        if (DEBUG) {
            Log.d(TAG, "onBindViewHolder() called with: "
                    + "holder = [" + holder.getClass().getSimpleName() + "], "
                    + "position = [" + position + "]");
        }
        if (holder instanceof ItemHolder) {
            // If header isn't null, offset the items by -1
            if (header != null) {
                position--;
            }

            ((ItemHolder) holder).updateFromObject(itemList.get(position), recordManager);
        } else if (holder instanceof HFHolder && position == 0 && header != null) {
            ((HFHolder) holder).view = header;
        } else if (holder instanceof HFHolder && position == sizeConsideringHeaderOffset()
                && footer != null && showFooter) {
            ((HFHolder) holder).view = footer;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, final int position,
                                 @NonNull final List<Object> payloads) {
        if (!payloads.isEmpty() && holder instanceof ItemHolder) {
            for (Object payload : payloads) {
                if (payload instanceof StreamStateEntity) {
                    ((ItemHolder) holder).updateStateFromObject(itemList
                            .get(header == null ? position : position - 1), recordManager);
                } else if (payload instanceof Boolean) {
                    ((ItemHolder) holder).updateStateFromObject(itemList
                            .get(header == null ? position : position - 1), recordManager);
                }
            }
        } else {
            onBindViewHolder(holder, position);
        }
    }

    public GridLayoutManager.SpanSizeLookup getSpanSizeLookup(final int spanCount) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(final int position) {
                final int type = getItemViewType(position);
                return type == HEADER_TYPE || type == FOOTER_TYPE ? spanCount : 1;
            }
        };
    }

    public class HFHolder extends RecyclerView.ViewHolder {
        public View view;

        HFHolder(final View v) {
            super(v);
            view = v;
        }
    }
}
