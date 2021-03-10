package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.List;

public class RelatedStreamInfo extends ListInfo<InfoItem> {
    public RelatedStreamInfo(final int serviceId, final ListLinkHandler listUrlIdHandler,
                             final String name) {
        super(serviceId, listUrlIdHandler, name);
    }

    public static RelatedStreamInfo getInfo(final StreamInfo info) {
        final ListLinkHandler handler = new ListLinkHandler(
                info.getOriginalUrl(), info.getUrl(), info.getId(), List.of(), null);
        final RelatedStreamInfo relatedStreamInfo = new RelatedStreamInfo(
                info.getServiceId(), handler, info.getName());
        final List<InfoItem> streams = new ArrayList<>(info.getRelatedStreams());
        relatedStreamInfo.setRelatedItems(streams);
        return relatedStreamInfo;
    }
}
