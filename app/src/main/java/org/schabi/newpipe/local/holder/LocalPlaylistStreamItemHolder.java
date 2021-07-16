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
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class LocalPlaylistStreamItemHolder extends LocalItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    private final TextView itemAdditionalDetailsView;
    public final TextView itemDurationView;
    private final View itemHandleView;
    private final AnimatedProgressBar itemProgressView;

    LocalPlaylistStreamItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                                  final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemAdditionalDetailsView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemHandleView = itemView.findViewById(R.id.itemHandle);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public LocalPlaylistStreamItemHolder(final LocalItemBuilder infoItemBuilder,
                                         final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_playlist_item, parent);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        if (!(localItem instanceof PlaylistStreamEntry)) {
            return;
        }
        final PlaylistStreamEntry item = (PlaylistStreamEntry) localItem;

        itemVideoTitleView.setText(item.getStreamEntity().getTitle());
        itemAdditionalDetailsView.setText(Localization
                .concatenateStrings(item.getStreamEntity().getUploader(),
                        NewPipe.getNameOfService(item.getStreamEntity().getServiceId())));

        if (item.getStreamEntity().getDuration() > 0) {
            itemDurationView.setText(Localization
                    .getDurationString(item.getStreamEntity().getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            if (item.getProgressMillis() > 0) {
                itemProgressView.setVisibility(View.VISIBLE);
                itemProgressView.setMax((int) item.getStreamEntity().getDuration());
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(item.getProgressMillis()));
            } else {
                itemProgressView.setVisibility(View.GONE);
            }
        } else {
            itemDurationView.setVisibility(View.GONE);
        }

        // Default thumbnail is shown on error, while loading and if the url is empty
        PicassoHelper.loadThumbnail(item.getStreamEntity().getThumbnailUrl())
                .into(itemThumbnailView);

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

        itemHandleView.setOnTouchListener(getOnTouchListener(item));
    }

    @Override
    public void updateState(final LocalItem localItem,
                            final HistoryRecordManager historyRecordManager) {
        if (!(localItem instanceof PlaylistStreamEntry)) {
            return;
        }
        final PlaylistStreamEntry item = (PlaylistStreamEntry) localItem;

        if (item.getProgressMillis() > 0 && item.getStreamEntity().getDuration() > 0) {
            itemProgressView.setMax((int) item.getStreamEntity().getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(item.getProgressMillis()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(item.getProgressMillis()));
                ViewUtils.animate(itemProgressView, true, 500);
            }
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            ViewUtils.animate(itemProgressView, false, 500);
        }
    }

    private View.OnTouchListener getOnTouchListener(final PlaylistStreamEntry item) {
        return (view, motionEvent) -> {
            view.performClick();
            if (itemBuilder != null && itemBuilder.getOnItemSelectedListener() != null
                    && motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemBuilder.getOnItemSelectedListener().drag(item,
                        LocalPlaylistStreamItemHolder.this);
            }
            return false;
        };
    }
}
