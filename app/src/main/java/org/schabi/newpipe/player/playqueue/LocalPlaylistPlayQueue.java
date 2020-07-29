package org.schabi.newpipe.player.playqueue;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class LocalPlaylistPlayQueue extends PlayQueue {
    // TODO not sure if context can be made transient, because then it would be lost on instance
    //  saving, but there were no errors
    private final transient Context context;
    private final long playlistId;

    private boolean isComplete = false;
    private transient Disposable disposable;

    public LocalPlaylistPlayQueue(final Context context,
                                  final PlaylistMetadataEntry playlistMetadataEntry) {
        super(0, Collections.emptyList());
        this.context = context;
        playlistId = playlistMetadataEntry.uid;
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public void fetch(@Nullable final Runnable runnable) {
        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(context));

        disposable = playlistManager.getPlaylistStreams(playlistId)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        streams -> {
                            List<PlayQueueItem> playQueueItems = new ArrayList<>(streams.size());
                            for (final PlaylistStreamEntry stream : streams) {
                                playQueueItems.add(new PlayQueueItem(stream.toStreamInfoItem()));
                            }

                            isComplete = true;
                            append(playQueueItems);
                            // TODO calling runnable here creates strange loops
                        },
                        t -> ErrorActivity.reportError(context, t, MainActivity.class, null,
                                ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE,
                                        "none", "Local Playlist", R.string.general_error))
                );
    }

    @Override
    public void dispose() {
        super.dispose();
        if (disposable != null) {
            disposable.dispose();
        }
    }
}
