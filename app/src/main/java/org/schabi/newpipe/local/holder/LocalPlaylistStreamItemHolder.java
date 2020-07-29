package org.schabi.newpipe.local.holder;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.info_list.ItemHolderWithToolbar;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class LocalPlaylistStreamItemHolder extends ItemHolderWithToolbar<PlaylistStreamEntry> {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    private final TextView itemAdditionalDetailsView;
    public final TextView itemDurationView;
    private final View itemHandleView;
    private final AnimatedProgressBar itemProgressView;

    LocalPlaylistStreamItemHolder(final ItemHandler itemHandler, final int layoutId,
                                  final ViewGroup parent) {
        super(PlaylistStreamEntry.class, itemHandler, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemAdditionalDetailsView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemHandleView = itemView.findViewById(R.id.itemHandle);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public LocalPlaylistStreamItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        this(itemHandler, R.layout.list_stream_playlist_item, parent);
    }

    @Override
    public void updateFromItem(final PlaylistStreamEntry item,
                               final HistoryRecordManager historyRecordManager) {
        itemVideoTitleView.setText(item.getStreamEntity().getTitle());
        itemAdditionalDetailsView.setText(Localization
                .concatenateStrings(item.getStreamEntity().getUploader(),
                        NewPipe.getNameOfService(item.getStreamEntity().getServiceId())));

        if (item.getStreamEntity().getDuration() > 0) {
            itemDurationView.setText(Localization
                    .getDurationString(item.getStreamEntity().getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemHandler.getActivity(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            StreamStateEntity state = historyRecordManager
                    .loadLocalStreamStateBatch(new ArrayList<LocalItem>() {{
                add(item);
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
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        itemHandler.displayImage(item.getStreamEntity().getThumbnailUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemThumbnailView.setOnTouchListener(getOnTouchListener(item));
        itemHandleView.setOnTouchListener(getOnTouchListener(item));
    }

    @Override
    public void updateStateFromItem(final PlaylistStreamEntry item,
                                    final HistoryRecordManager historyRecordManager) {
        StreamStateEntity state = historyRecordManager
                .loadLocalStreamStateBatch(new ArrayList<LocalItem>() {{
            add(item);
        }}).blockingGet().get(0);
        if (state != null && item.getStreamEntity().getDuration() > 0) {
            itemProgressView.setMax((int) item.getStreamEntity().getDuration());
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

    private View.OnTouchListener getOnTouchListener(final PlaylistStreamEntry item) {
        return (view, motionEvent) -> {
            view.performClick();
            if (itemHandler != null && itemHandler.getOnItemSelectedListener() != null
                    && motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemHandler.getOnItemSelectedListener().drag(item,
                        LocalPlaylistStreamItemHolder.this);
            }
            return false;
        };
    }
}
