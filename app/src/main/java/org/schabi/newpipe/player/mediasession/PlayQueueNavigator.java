package org.schabi.newpipe.player.mediasession;

import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.List;

import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM;

public class PlayQueueNavigator implements MediaSessionConnector.QueueNavigator {
    public static final int DEFAULT_MAX_QUEUE_SIZE = 10;

    private final MediaSessionCompat mediaSession;
    private final MediaSessionCallback callback;
    private final int maxQueueSize;

    private long activeQueueItemId;

    public PlayQueueNavigator(@NonNull final MediaSessionCompat mediaSession,
                              @NonNull final MediaSessionCallback callback) {
        this.mediaSession = mediaSession;
        this.callback = callback;
        this.maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;

        this.activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
    }

    @Override
    public long getSupportedQueueNavigatorActions(@Nullable final Player player) {
        return ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS | ACTION_SKIP_TO_QUEUE_ITEM;
    }

    @Override
    public void onTimelineChanged(final Player player) {
        publishFloatingQueueWindow();
    }

    @Override
    public void onCurrentWindowIndexChanged(final Player player) {
        if (activeQueueItemId == MediaSessionCompat.QueueItem.UNKNOWN_ID
                || player.getCurrentTimeline().getWindowCount() > maxQueueSize) {
            publishFloatingQueueWindow();
        } else if (!player.getCurrentTimeline().isEmpty()) {
            activeQueueItemId = player.getCurrentWindowIndex();
        }
    }

    @Override
    public long getActiveQueueItemId(@Nullable final Player player) {
        return callback.getCurrentPlayingIndex();
    }

    @Override
    public void onSkipToPrevious(final Player player, final ControlDispatcher controlDispatcher) {
        callback.playPrevious();
    }

    @Override
    public void onSkipToQueueItem(final Player player, final ControlDispatcher controlDispatcher,
                                  final long id) {
        callback.playItemAtIndex((int) id);
    }

    @Override
    public void onSkipToNext(final Player player, final ControlDispatcher controlDispatcher) {
        callback.playNext();
    }

    private void publishFloatingQueueWindow() {
        if (callback.getQueueSize() == 0) {
            mediaSession.setQueue(List.of());
            activeQueueItemId = MediaSessionCompat.QueueItem.UNKNOWN_ID;
            return;
        }

        // Yes this is almost a copypasta, got a problem with that? =\
        final int windowCount = callback.getQueueSize();
        final int currentWindowIndex = callback.getCurrentPlayingIndex();
        final int queueSize = Math.min(maxQueueSize, windowCount);
        final int startIndex = Util.constrainValue(currentWindowIndex - ((queueSize - 1) / 2), 0,
                windowCount - queueSize);

        final List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        for (int i = startIndex; i < startIndex + queueSize; i++) {
            queue.add(new MediaSessionCompat.QueueItem(callback.getQueueMetadata(i), i));
        }
        mediaSession.setQueue(queue);
        activeQueueItemId = currentWindowIndex;
    }

    @Override
    public boolean onCommand(final Player player, final ControlDispatcher controlDispatcher,
                             final String command, final Bundle extras, final ResultReceiver cb) {
        return false;
    }
}
