package org.schabi.newpipe.info_list.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.info_list.ItemHolderWithToolbar;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.concurrent.TimeUnit;

public class StreamMiniInfoItemHolder extends ItemHolderWithToolbar<StreamInfoItem> {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    public final TextView itemUploaderView;
    public final TextView itemDurationView;
    private final AnimatedProgressBar itemProgressView;

    StreamMiniInfoItemHolder(final ItemHandler itemHandler, final int layoutId,
                             final ViewGroup parent) {
        super(StreamInfoItem.class, itemHandler, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public StreamMiniInfoItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        this(itemHandler, R.layout.list_stream_mini_item, parent);
    }

    @Override
    public void updateFromItem(final StreamInfoItem item,
                               final HistoryRecordManager historyRecordManager) {
        itemVideoTitleView.setText(item.getName());
        itemUploaderView.setText(item.getUploaderName());

        if (item.getDuration() > 0) {
            itemDurationView.setText(Localization.getDurationString(item.getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemHandler.getActivity(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            StreamStateEntity state2 = historyRecordManager.loadStreamState(item)
                    .blockingGet()[0];
            if (state2 != null) {
                itemProgressView.setVisibility(View.VISIBLE);
                itemProgressView.setMax((int) item.getDuration());
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state2.getProgressTime()));
            } else {
                itemProgressView.setVisibility(View.GONE);
            }
        } else if (item.getStreamType() == StreamType.LIVE_STREAM) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemHandler.getActivity(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemHandler.displayImage(item.getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);
    }

    @Override
    public void updateStateFromItem(final StreamInfoItem item,
                                    final HistoryRecordManager historyRecordManager) {
        StreamStateEntity state = historyRecordManager.loadStreamState(item).blockingGet()[0];
        if (state != null && item.getDuration() > 0
                && item.getStreamType() != StreamType.LIVE_STREAM) {
            itemProgressView.setMax((int) item.getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressTime()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(state.getProgressTime()));
                AnimationUtils.animateView(itemProgressView, true, 500);
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            AnimationUtils.animateView(itemProgressView, false, 500);
        }
    }
}
