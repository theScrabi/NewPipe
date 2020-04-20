package org.schabi.newpipe.local.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.schabi.newpipe.util.AnimationUtils.DEFAULT_SHORT_ANIM_DURATION;
import static org.schabi.newpipe.util.AnimationUtils.animateView;
/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class LocalStatisticStreamItemHolder extends LocalItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    @Nullable
    public final TextView itemAdditionalDetails;
    private final AnimatedProgressBar itemProgressView;

    public LocalStatisticStreamItemHolder(final LocalItemBuilder itemBuilder,
                                          final ViewGroup parent) {
        this(itemBuilder, R.layout.list_stream_item, parent);
    }

    LocalStatisticStreamItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                                   final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    private String getStreamInfoDetailLine(final StreamStatisticsEntry entry,
                                           final DateFormat dateFormat) {
        final String watchCount = Localization
                .shortViewCount(itemBuilder.getContext(), entry.getWatchCount());
        final String uploadDate = dateFormat.format(entry.getLatestAccessDate());
        final String serviceName = NewPipe.getNameOfService(entry.getStreamEntity().getServiceId());
        return Localization.concatenateStrings(watchCount, uploadDate, serviceName);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateFormat dateFormat) {
        if (!(localItem instanceof StreamStatisticsEntry)) {
            return;
        }
        final StreamStatisticsEntry item = (StreamStatisticsEntry) localItem;

        itemVideoTitleView.setText(item.getStreamEntity().getTitle());
        itemUploaderView.setText(item.getStreamEntity().getUploader());

        if (item.getStreamEntity().getDuration() > 0) {
            itemDurationView.
                    setText(Localization.getDurationString(item.getStreamEntity().getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            StreamStateEntity state = historyRecordManager
                    .loadLocalStreamStateBatch(new ArrayList<LocalItem>() {{
                        add(localItem);
                    }}).blockingGet().get(0);
            if (state != null) {
                itemProgressView.setVisibility(View.VISIBLE);
                itemProgressView.setMax((int) item.getStreamEntity().getDuration());
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressTime()));
            } else {
                itemProgressView.setVisibility(View.GONE);
            }
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }

        if (itemAdditionalDetails != null) {
            itemAdditionalDetails.setText(getStreamInfoDetailLine(item, dateFormat));
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemBuilder.displayImage(item.getStreamEntity().getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().selected(item);
            }
        });

        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().held(item);
            }
            return true;
        });
    }

    @Override
    public void updateState(final LocalItem localItem,
                            final HistoryRecordManager historyRecordManager) {
        if (!(localItem instanceof StreamStatisticsEntry)) {
            return;
        }
        final StreamStatisticsEntry item = (StreamStatisticsEntry) localItem;

        StreamStateEntity state = historyRecordManager
                .loadLocalStreamStateBatch(new ArrayList<LocalItem>() {{
                    add(localItem);
                }}).blockingGet().get(0);
        if (state != null && item.getStreamEntity().getDuration() > 0) {
            itemProgressView.setMax((int) item.getStreamEntity().getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressTime()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressTime()));
                animateView(itemProgressView, true, DEFAULT_SHORT_ANIM_DURATION);
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            animateView(itemProgressView, false, DEFAULT_SHORT_ANIM_DURATION);
        }
    }
}
