package org.schabi.newpipe.util;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RelatedItemInfo extends ListInfo<InfoItem> {
    public RelatedItemInfo(final int serviceId, final ListLinkHandler listUrlIdHandler,
                           final String name) {
        super(serviceId, listUrlIdHandler, name);
    }

    public static RelatedItemInfo getInfo(final StreamInfo info) {
        final ListLinkHandler handler = new ListLinkHandler(
                info.getOriginalUrl(), info.getUrl(), info.getId(), Collections.emptyList(), null);
        final RelatedItemInfo relatedItemInfo = new RelatedItemInfo(
                info.getServiceId(), handler, info.getName());
        final List<InfoItem> relatedItems = new ArrayList<>(info.getRelatedItems());
        relatedItemInfo.setRelatedItems(relatedItems);
        return relatedItemInfo;
    }
}
