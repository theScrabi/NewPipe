package org.schabi.newpipe.util;

import android.content.Context;
import android.text.Layout;
import android.text.Selection;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class CommentTextOnTouchListener implements View.OnTouchListener {
    public static final CommentTextOnTouchListener INSTANCE = new CommentTextOnTouchListener();

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("(.*)#timestamp=(\\d+)");

    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        if (!(v instanceof TextView)) {
            return false;
        }
        TextView widget = (TextView) v;
        Object text = widget.getText();
        if (text instanceof Spanned) {
            Spannable buffer = (Spannable) text;

            int action = event.getAction();

            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_DOWN) {
                int x = (int) event.getX();
                int y = (int) event.getY();

                x -= widget.getTotalPaddingStart();
                y -= widget.getTotalPaddingTop();

                x += widget.getScrollX();
                y += widget.getScrollY();

                Layout layout = widget.getLayout();
                int line = layout.getLineForVertical(y);
                int off = layout.getOffsetForHorizontal(line, x);

                ClickableSpan[] link = buffer.getSpans(off, off,
                        ClickableSpan.class);

                if (link.length != 0) {
                    if (action == MotionEvent.ACTION_UP) {
                        boolean handled = false;
                        if (link[0] instanceof URLSpan) {
                            handled = handleUrl(v.getContext(), (URLSpan) link[0]);
                        }
                        if (!handled) {
                            link[0].onClick(widget);
                        }
                    } else if (action == MotionEvent.ACTION_DOWN) {
                        Selection.setSelection(buffer,
                                buffer.getSpanStart(link[0]),
                                buffer.getSpanEnd(link[0]));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleUrl(final Context context, final URLSpan urlSpan) {
        String url = urlSpan.getURL();
        int seconds = -1;
        Matcher matcher = TIMESTAMP_PATTERN.matcher(url);
        if (matcher.matches()) {
            url = matcher.group(1);
            seconds = Integer.parseInt(matcher.group(2));
        }
        StreamingService service;
        StreamingService.LinkType linkType;
        try {
            service = NewPipe.getServiceByUrl(url);
            linkType = service.getLinkTypeByUrl(url);
        } catch (ExtractionException e) {
            return false;
        }
        if (linkType == StreamingService.LinkType.NONE) {
            return false;
        }
        if (linkType == StreamingService.LinkType.STREAM && seconds != -1) {
            return playOnPopup(context, url, service, seconds);
        } else {
            NavigationHelper.openRouterActivity(context, url);
            return true;
        }
    }

    private boolean playOnPopup(final Context context, final String url,
                                final StreamingService service, final int seconds) {
        LinkHandlerFactory factory = service.getStreamLHFactory();
        String cleanUrl = null;
        try {
            cleanUrl = factory.getUrl(factory.getId(url));
        } catch (ParsingException e) {
            return false;
        }
        Single single = ExtractorHelper.getStreamInfo(service.getServiceId(), cleanUrl, false);
        single.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    PlayQueue playQueue = new SinglePlayQueue((StreamInfo) info, seconds * 1000);
                    NavigationHelper.playOnPopupPlayer(context, playQueue, false);
                });
        return true;
    }
}
