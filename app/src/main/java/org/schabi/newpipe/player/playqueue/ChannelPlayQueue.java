package org.schabi.newpipe.player.playqueue;


import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.util.ExtractorHelper;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public final class ChannelPlayQueue extends AbstractInfoPlayQueue<ChannelInfo, ChannelInfoItem> {
    public ChannelPlayQueue(final ChannelInfoItem item) {
        super(item);
    }

    public ChannelPlayQueue(final ChannelInfo info) {
        this(info.getServiceId(), info.getUrl(), info.getNextPage(), info.getRelatedItems(), 0);
    }

    public ChannelPlayQueue(final int serviceId,
                            final String url,
                            final Page nextPage,
                            final List<StreamInfoItem> streams,
                            final int index) {
        super(serviceId, url, nextPage, streams, index);
    }

    @Override
    protected String getTag() {
        return "ChannelPlayQueue@" + Integer.toHexString(hashCode());
    }

    @Override
    public void fetch(@Nullable final Runnable runnable) {
        if (this.isInitial) {
            ExtractorHelper.getChannelInfo(this.serviceId, this.baseUrl, false)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getHeadListObserver(runnable));
        } else {
            ExtractorHelper.getMoreChannelItems(this.serviceId, this.baseUrl, this.nextPage)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(getNextPageObserver(runnable));
        }
    }
}
