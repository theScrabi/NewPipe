package org.schabi.newpipe.player.playqueue;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SinglePlayQueue extends PlayQueue {
    public SinglePlayQueue(final StreamInfoItem item) {
        super(0, Collections.singletonList(new PlayQueueItem(item)));
    }

    public SinglePlayQueue(final StreamInfo info) {
        super(0, Collections.singletonList(new PlayQueueItem(info)));
    }

    public SinglePlayQueue(final StreamInfo info, final long startPosition) {
        super(0, Collections.singletonList(new PlayQueueItem(info)));
        getItem().setRecoveryPosition(startPosition);
    }

    public SinglePlayQueue(final List<StreamInfoItem> items, final int index) {
        super(index, playQueueItemsOf(items));
    }

    private static List<PlayQueueItem> playQueueItemsOf(final List<StreamInfoItem> items) {
        List<PlayQueueItem> playQueueItems = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            playQueueItems.add(new PlayQueueItem(item));
        }
        return playQueueItems;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void fetch(@Nullable final Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }
}
