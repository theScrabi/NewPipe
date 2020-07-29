package org.schabi.newpipe.player.playqueue;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public final class PlaylistPlayQueue extends AbstractInfoPlayQueue<PlaylistInfo, PlaylistInfoItem> {
    public PlaylistPlayQueue(final PlaylistInfoItem item) {
        super(item);
    }

    public PlaylistPlayQueue(final PlaylistInfo info) {
        this(info.getServiceId(), info.getUrl(), info.getNextPage(), info.getRelatedItems(), 0);
    }

    public PlaylistPlayQueue(final int serviceId,
                             final String url,
                             final Page nextPage,
                             final List<StreamInfoItem> streams,
                             final int index) {
        super(serviceId, url, nextPage, streams, index);
    }

    @Override
    protected String getTag() {
        return "PlaylistPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch(@Nullable final Runnable runnable) {
        if (this.isInitial) {
            ExtractorHelper.getPlaylistInfo(this.serviceId, this.baseUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getHeadListObserver(runnable));
        } else {
            ExtractorHelper.getMorePlaylistItems(this.serviceId, this.baseUrl, this.nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver(runnable));
        }
    }
}
