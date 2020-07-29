package org.schabi.newpipe.info_list.holder;

import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.Localization;

import static org.schabi.newpipe.MainActivity.DEBUG;

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
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
 * </p?
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class StreamInfoItemHolder extends StreamMiniInfoItemHolder {
    public final TextView itemAdditionalDetails;

    public StreamInfoItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        this(itemHandler, R.layout.list_stream_item, parent);
    }

    public StreamInfoItemHolder(final ItemHandler itemHandler, final int layoutId,
                                final ViewGroup parent) {
        super(itemHandler, layoutId, parent);
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails);
    }

    @Override
    public void updateFromItem(final StreamInfoItem item,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(item, historyRecordManager);
        itemAdditionalDetails.setText(getStreamInfoDetailLine(item));
    }

    private String getStreamInfoDetailLine(final StreamInfoItem item) {
        String viewsAndDate = "";
        if (item.getViewCount() >= 0) {
            if (item.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
                viewsAndDate = Localization
                        .listeningCount(itemHandler.getActivity(), item.getViewCount());
            } else if (item.getStreamType().equals(StreamType.LIVE_STREAM)) {
                viewsAndDate = Localization
                        .shortWatchingCount(itemHandler.getActivity(), item.getViewCount());
            } else {
                viewsAndDate = Localization
                        .shortViewCount(itemHandler.getActivity(), item.getViewCount());
            }
        }

        final String uploadDate = getFormattedRelativeUploadDate(item);
        if (!TextUtils.isEmpty(uploadDate)) {
            if (viewsAndDate.isEmpty()) {
                return uploadDate;
            }

            return Localization.concatenateStrings(viewsAndDate, uploadDate);
        }

        return viewsAndDate;
    }

    private String getFormattedRelativeUploadDate(final StreamInfoItem item) {
        if (item.getUploadDate() != null) {
            String formattedRelativeTime = Localization
                    .relativeTime(item.getUploadDate().date());

            if (DEBUG && PreferenceManager.getDefaultSharedPreferences(itemHandler.getActivity())
                    .getBoolean(itemHandler.getActivity()
                            .getString(R.string.show_original_time_ago_key), false)) {
                formattedRelativeTime += " (" + item.getTextualUploadDate() + ")";
            }
            return formattedRelativeTime;
        } else {
            return item.getTextualUploadDate();
        }
    }
}
