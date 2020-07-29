package org.schabi.newpipe.info_list.holder;

import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.info_list.ItemHolder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.AndroidTvUtils;
import org.schabi.newpipe.util.CommentTextOnTouchListener;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ShareUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.hdodenhof.circleimageview.CircleImageView;

public class CommentsMiniInfoItemHolder extends ItemHolder {
    private static final int COMMENT_DEFAULT_LINES = 2;
    private static final int COMMENT_EXPANDED_LINES = 1000;
    private static final Pattern PATTERN = Pattern.compile("(\\d+:)?(\\d+)?:(\\d+)");

    public final CircleImageView itemThumbnailView;
    private final TextView itemContentView;
    private final TextView itemLikesCountView;
    private final TextView itemDislikesCountView;
    private final TextView itemPublishedTime;

    private String commentText;
    private String streamUrl;

    private final Linkify.TransformFilter timestampLink = new Linkify.TransformFilter() {
        @Override
        public String transformUrl(final Matcher match, final String url) {
            int timestamp = 0;
            String hours = match.group(1);
            String minutes = match.group(2);
            String seconds = match.group(3);
            if (hours != null) {
                timestamp += (Integer.parseInt(hours.replace(":", "")) * 3600);
            }
            if (minutes != null) {
                timestamp += (Integer.parseInt(minutes.replace(":", "")) * 60);
            }
            if (seconds != null) {
                timestamp += (Integer.parseInt(seconds));
            }
            return streamUrl + url.replace(match.group(0), "#timestamp=" + timestamp);
        }
    };

    CommentsMiniInfoItemHolder(final ItemHandler itemHandler, final int layoutId,
                               final ViewGroup parent) {
        super(itemHandler, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemLikesCountView = itemView.findViewById(R.id.detail_thumbs_up_count_view);
        itemDislikesCountView = itemView.findViewById(R.id.detail_thumbs_down_count_view);
        itemPublishedTime = itemView.findViewById(R.id.itemPublishedTime);
        itemContentView = itemView.findViewById(R.id.itemCommentContentView);
    }

    public CommentsMiniInfoItemHolder(final ItemHandler itemHandler,
                                      final ViewGroup parent) {
        this(itemHandler, R.layout.list_comments_mini_item, parent);
    }

    @Override
    public void updateFromObject(final Object object,
                                 final HistoryRecordManager historyRecordManager) {
        if (!(object instanceof CommentsInfoItem)) {
            return;
        }
        final CommentsInfoItem item = (CommentsInfoItem) object;

        itemHandler.displayImage(item.getUploaderAvatarUrl(), itemThumbnailView,
                ImageDisplayConstants.DISPLAY_THUMBNAIL_OPTIONS);

        itemThumbnailView.setOnClickListener(view -> openCommentAuthor(item));

        streamUrl = item.getUrl();

        itemContentView.setLines(COMMENT_DEFAULT_LINES);
        commentText = item.getCommentText();
        itemContentView.setText(commentText);
        itemContentView.setOnTouchListener(CommentTextOnTouchListener.INSTANCE);

        if (itemContentView.getLineCount() == 0) {
            itemContentView.post(this::ellipsize);
        } else {
            ellipsize();
        }

        if (item.getLikeCount() >= 0) {
            itemLikesCountView.setText(String.valueOf(item.getLikeCount()));
        } else {
            itemLikesCountView.setText("-");
        }

        if (item.getUploadDate() != null) {
            itemPublishedTime.setText(Localization.relativeTime(item.getUploadDate().date()));
        } else {
            itemPublishedTime.setText(item.getTextualUploadDate());
        }

        itemView.setOnClickListener(view -> {
            toggleEllipsize();
            if (itemHandler.getOnItemSelectedListener() != null) {
                itemHandler.getOnItemSelectedListener().selected(item);
            }
        });


        itemView.setOnLongClickListener(view -> {
            if (AndroidTvUtils.isTv(itemHandler.getActivity())) {
                openCommentAuthor(item);
            } else {
                ShareUtils.copyToClipboard(itemHandler.getActivity(), commentText);
            }
            return true;
        });
    }

    private void openCommentAuthor(final CommentsInfoItem item) {
        if (TextUtils.isEmpty(item.getUploaderUrl())) {
            return;
        }
        try {
            final AppCompatActivity activity = (AppCompatActivity) itemHandler.getActivity();
            NavigationHelper.openChannelFragment(
                    activity.getSupportFragmentManager(),
                    item.getServiceId(),
                    item.getUploaderUrl(),
                    item.getUploaderName());
        } catch (Exception e) {
            ErrorActivity.reportUiError((AppCompatActivity) itemHandler.getActivity(), e);
        }
    }

    private void allowLinkFocus() {
        itemContentView.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void denyLinkFocus() {
        itemContentView.setMovementMethod(null);
    }

    private boolean shouldFocusLinks() {
        if (itemView.isInTouchMode()) {
            return false;
        }

        URLSpan[] urls = itemContentView.getUrls();

        return urls != null && urls.length != 0;
    }

    private void determineLinkFocus() {
        if (shouldFocusLinks()) {
            allowLinkFocus();
        } else {
            denyLinkFocus();
        }
    }

    private void ellipsize() {
        boolean hasEllipsis = false;

        if (itemContentView.getLineCount() > COMMENT_DEFAULT_LINES) {
            int endOfLastLine = itemContentView.getLayout().getLineEnd(COMMENT_DEFAULT_LINES - 1);
            int end = itemContentView.getText().toString().lastIndexOf(' ', endOfLastLine - 2);
            if (end == -1) {
                end = Math.max(endOfLastLine - 2, 0);
            }
            String newVal = itemContentView.getText().subSequence(0, end) + " …";
            itemContentView.setText(newVal);
            hasEllipsis = true;
        }

        linkify();

        if (hasEllipsis) {
            denyLinkFocus();
        } else {
            determineLinkFocus();
        }
    }

    private void toggleEllipsize() {
        if (itemContentView.getText().toString().equals(commentText)) {
            if (itemContentView.getLineCount() > COMMENT_DEFAULT_LINES) {
                ellipsize();
            }
        } else {
            expand();
        }
    }

    private void expand() {
        itemContentView.setMaxLines(COMMENT_EXPANDED_LINES);
        itemContentView.setText(commentText);
        linkify();
        determineLinkFocus();
    }

    private void linkify() {
        Linkify.addLinks(itemContentView, Linkify.WEB_URLS);
        Linkify.addLinks(itemContentView, PATTERN, null, null, timestampLink);
    }
}
