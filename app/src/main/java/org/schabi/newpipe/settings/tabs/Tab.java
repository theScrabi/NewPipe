package org.schabi.newpipe.settings.tabs;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonSink;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem.LocalItemType;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.fragments.BlankFragment;
import org.schabi.newpipe.fragments.list.channel.ChannelFragment;
import org.schabi.newpipe.fragments.list.kiosk.DefaultKioskFragment;
import org.schabi.newpipe.fragments.list.kiosk.KioskFragment;
import org.schabi.newpipe.fragments.list.playlist.PlaylistFragment;
import org.schabi.newpipe.local.bookmark.BookmarkFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.local.history.StatisticsPlaylistFragment;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.local.subscription.SubscriptionFragment;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.Objects;

public abstract class Tab {
    private static final String JSON_TAB_ID_KEY = "tab_id";

    Tab() {
    }

    Tab(@NonNull final JsonObject jsonObject) {
        readDataFromJson(jsonObject);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tab Handling
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public static Tab from(@NonNull final JsonObject jsonObject) {
        final int tabId = jsonObject.getInt(Tab.JSON_TAB_ID_KEY, -1);

        if (tabId == -1) {
            return null;
        }

        return from(tabId, jsonObject);
    }

    @Nullable
    public static Tab from(final int tabId) {
        return from(tabId, null);
    }

    @Nullable
    public static Type typeFrom(final int tabId) {
        for (final Type available : Type.values()) {
            if (available.getTabId() == tabId) {
                return available;
            }
        }
        return null;
    }

    @Nullable
    private static Tab from(final int tabId, @Nullable final JsonObject jsonObject) {
        final Type type = typeFrom(tabId);

        if (type == null) {
            return null;
        }

        if (jsonObject != null) {
            switch (type) {
                case KIOSK:
                    return new KioskTab(jsonObject);
                case CHANNEL:
                    return new ChannelTab(jsonObject);
                case PLAYLIST:
                    return new PlaylistTab(jsonObject);
            }
        }

        return type.getTab();
    }

    public abstract int getTabId();

    public abstract String getTabName(Context context);

    @DrawableRes
    public abstract int getTabIconRes(Context context);

    /**
     * Return a instance of the fragment that this tab represent.
     *
     * @param context Android app context
     * @return the fragment this tab represents
     */
    public abstract Fragment getFragment(Context context) throws ExtractionException;

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        return obj instanceof Tab && obj.getClass() == this.getClass()
                && ((Tab) obj).getTabId() == this.getTabId();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // JSON Handling
    //////////////////////////////////////////////////////////////////////////*/

    public void writeJsonOn(final JsonSink jsonSink) {
        jsonSink.object();

        jsonSink.value(JSON_TAB_ID_KEY, getTabId());
        writeDataToJson(jsonSink);

        jsonSink.end();
    }

    protected void writeDataToJson(final JsonSink writerSink) {
        // No-op
    }

    protected void readDataFromJson(final JsonObject jsonObject) {
        // No-op
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Implementations
    //////////////////////////////////////////////////////////////////////////*/

    public enum Type {
        BLANK(new BlankTab()),
        DEFAULT_KIOSK(new DefaultKioskTab()),
        SUBSCRIPTIONS(new SubscriptionsTab()),
        FEED(new FeedTab()),
        BOOKMARKS(new BookmarksTab()),
        HISTORY(new HistoryTab()),
        KIOSK(new KioskTab()),
        CHANNEL(new ChannelTab()),
        PLAYLIST(new PlaylistTab());

        private final Tab tab;

        Type(final Tab tab) {
            this.tab = tab;
        }

        public int getTabId() {
            return tab.getTabId();
        }

        public Tab getTab() {
            return tab;
        }
    }

    public static class BlankTab extends Tab {
        public static final int ID = 0;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return "NewPipe"; //context.getString(R.string.blank_page_summary);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_crop_portrait;
        }

        @Override
        public BlankFragment getFragment(final Context context) {
            return new BlankFragment();
        }
    }

    public static class SubscriptionsTab extends Tab {
        public static final int ID = 1;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.tab_subscriptions);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_tv;
        }

        @Override
        public SubscriptionFragment getFragment(final Context context) {
            return new SubscriptionFragment();
        }

    }

    public static class FeedTab extends Tab {
        public static final int ID = 2;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.fragment_feed_title);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_rss_feed;
        }

        @Override
        public FeedFragment getFragment(final Context context) {
            return new FeedFragment();
        }
    }

    public static class BookmarksTab extends Tab {
        public static final int ID = 3;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.tab_bookmarks);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_bookmark;
        }

        @Override
        public BookmarkFragment getFragment(final Context context) {
            return new BookmarkFragment();
        }
    }

    public static class HistoryTab extends Tab {
        public static final int ID = 4;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return context.getString(R.string.title_activity_history);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_history;
        }

        @Override
        public StatisticsPlaylistFragment getFragment(final Context context) {
            return new StatisticsPlaylistFragment();
        }
    }

    public static class KioskTab extends Tab {
        public static final int ID = 5;
        private static final String JSON_KIOSK_SERVICE_ID_KEY = "service_id";
        private static final String JSON_KIOSK_ID_KEY = "kiosk_id";
        private int kioskServiceId;
        private String kioskId;

        private KioskTab() {
            this(-1, "<no-id>");
        }

        public KioskTab(final int kioskServiceId, final String kioskId) {
            this.kioskServiceId = kioskServiceId;
            this.kioskId = kioskId;
        }

        public KioskTab(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(kioskId, context);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            final int kioskIcon = KioskTranslator.getKioskIcon(kioskId, context);

            if (kioskIcon <= 0) {
                throw new IllegalStateException("Kiosk ID is not valid: \"" + kioskId + "\"");
            }

            return kioskIcon;
        }

        @Override
        public KioskFragment getFragment(final Context context) throws ExtractionException {
            return KioskFragment.getInstance(kioskServiceId, kioskId);
        }

        @Override
        protected void writeDataToJson(final JsonSink writerSink) {
            writerSink.value(JSON_KIOSK_SERVICE_ID_KEY, kioskServiceId)
                    .value(JSON_KIOSK_ID_KEY, kioskId);
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            kioskServiceId = jsonObject.getInt(JSON_KIOSK_SERVICE_ID_KEY, -1);
            kioskId = jsonObject.getString(JSON_KIOSK_ID_KEY, "<no-id>");
        }

        @Override
        public boolean equals(final Object obj) {
            return super.equals(obj) && kioskServiceId == ((KioskTab) obj).kioskServiceId
                    && Objects.equals(kioskId, ((KioskTab) obj).kioskId);
        }

        public int getKioskServiceId() {
            return kioskServiceId;
        }

        public String getKioskId() {
            return kioskId;
        }
    }

    public static class ChannelTab extends Tab {
        public static final int ID = 6;
        private static final String JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id";
        private static final String JSON_CHANNEL_URL_KEY = "channel_url";
        private static final String JSON_CHANNEL_NAME_KEY = "channel_name";
        private int channelServiceId;
        private String channelUrl;
        private String channelName;

        private ChannelTab() {
            this(-1, "<no-url>", "<no-name>");
        }

        public ChannelTab(final int channelServiceId, final String channelUrl,
                          final String channelName) {
            this.channelServiceId = channelServiceId;
            this.channelUrl = channelUrl;
            this.channelName = channelName;
        }

        public ChannelTab(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return channelName;
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_tv;
        }

        @Override
        public ChannelFragment getFragment(final Context context) {
            return ChannelFragment.getInstance(channelServiceId, channelUrl, channelName);
        }

        @Override
        protected void writeDataToJson(final JsonSink writerSink) {
            writerSink.value(JSON_CHANNEL_SERVICE_ID_KEY, channelServiceId)
                    .value(JSON_CHANNEL_URL_KEY, channelUrl)
                    .value(JSON_CHANNEL_NAME_KEY, channelName);
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            channelServiceId = jsonObject.getInt(JSON_CHANNEL_SERVICE_ID_KEY, -1);
            channelUrl = jsonObject.getString(JSON_CHANNEL_URL_KEY, "<no-url>");
            channelName = jsonObject.getString(JSON_CHANNEL_NAME_KEY, "<no-name>");
        }

        @Override
        public boolean equals(final Object obj) {
            return super.equals(obj) && channelServiceId == ((ChannelTab) obj).channelServiceId
                    && Objects.equals(channelUrl, ((ChannelTab) obj).channelUrl)
                    && Objects.equals(channelName, ((ChannelTab) obj).channelName);
        }

        public int getChannelServiceId() {
            return channelServiceId;
        }

        public String getChannelUrl() {
            return channelUrl;
        }

        public String getChannelName() {
            return channelName;
        }
    }

    public static class DefaultKioskTab extends Tab {
        public static final int ID = 7;

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(getDefaultKioskId(context), context);
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return KioskTranslator.getKioskIcon(getDefaultKioskId(context), context);
        }

        @Override
        public DefaultKioskFragment getFragment(final Context context) {
            return new DefaultKioskFragment();
        }

        private String getDefaultKioskId(final Context context) {
            final int kioskServiceId = ServiceHelper.getSelectedServiceId(context);

            String kioskId = "";
            try {
                final StreamingService service = NewPipe.getService(kioskServiceId);
                kioskId = service.getKioskList().getDefaultKioskId();
            } catch (final ExtractionException e) {
                ErrorActivity.reportErrorInSnackbar(context, new ErrorInfo(e,
                        UserAction.REQUESTED_KIOSK, "Loading default kiosk for selected service"));
            }
            return kioskId;
        }
    }

    public static class PlaylistTab extends Tab {
        public static final int ID = 8;
        private static final String JSON_PLAYLIST_SERVICE_ID_KEY = "playlist_service_id";
        private static final String JSON_PLAYLIST_URL_KEY = "playlist_url";
        private static final String JSON_PLAYLIST_NAME_KEY = "playlist_name";
        private static final String JSON_PLAYLIST_ID_KEY = "playlist_id";
        private static final String JSON_PLAYLIST_TYPE_KEY = "playlist_type";
        private int playlistServiceId;
        private String playlistUrl;
        private String playlistName;
        private long playlistId;
        private LocalItemType playlistType;

        private PlaylistTab() {
            this(-1, "<no-name>");
        }

        public PlaylistTab(final long playlistId, final String playlistName) {
            this.playlistName = playlistName;
            this.playlistId = playlistId;
            this.playlistType = LocalItemType.PLAYLIST_LOCAL_ITEM;
            this.playlistServiceId = -1;
            this.playlistUrl = "<no-url>";
        }

        public PlaylistTab(final int playlistServiceId, final String playlistUrl,
                           final String playlistName) {
            this.playlistServiceId = playlistServiceId;
            this.playlistUrl = playlistUrl;
            this.playlistName = playlistName;
            this.playlistType = LocalItemType.PLAYLIST_REMOTE_ITEM;
            this.playlistId = -1;
        }

        public PlaylistTab(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getTabId() {
            return ID;
        }

        @Override
        public String getTabName(final Context context) {
            return playlistName;
        }

        @DrawableRes
        @Override
        public int getTabIconRes(final Context context) {
            return R.drawable.ic_bookmark;
        }

        @Override
        public Fragment getFragment(final Context context) {
            if (playlistType == LocalItemType.PLAYLIST_LOCAL_ITEM) {
                return LocalPlaylistFragment.getInstance(playlistId, playlistName);

            } else { // playlistType == LocalItemType.PLAYLIST_REMOTE_ITEM
                return PlaylistFragment.getInstance(playlistServiceId, playlistUrl, playlistName);
            }
        }

        @Override
        protected void writeDataToJson(final JsonSink writerSink) {
            writerSink.value(JSON_PLAYLIST_SERVICE_ID_KEY, playlistServiceId)
                    .value(JSON_PLAYLIST_URL_KEY, playlistUrl)
                    .value(JSON_PLAYLIST_NAME_KEY, playlistName)
                    .value(JSON_PLAYLIST_ID_KEY, playlistId)
                    .value(JSON_PLAYLIST_TYPE_KEY, playlistType.toString());
        }

        @Override
        protected void readDataFromJson(final JsonObject jsonObject) {
            playlistServiceId = jsonObject.getInt(JSON_PLAYLIST_SERVICE_ID_KEY, -1);
            playlistUrl = jsonObject.getString(JSON_PLAYLIST_URL_KEY, "<no-url>");
            playlistName = jsonObject.getString(JSON_PLAYLIST_NAME_KEY, "<no-name>");
            playlistId = jsonObject.getInt(JSON_PLAYLIST_ID_KEY, -1);
            playlistType = LocalItemType.valueOf(
                    jsonObject.getString(JSON_PLAYLIST_TYPE_KEY,
                            LocalItemType.PLAYLIST_LOCAL_ITEM.toString())
            );
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(super.equals(obj)
                    && Objects.equals(playlistType, ((PlaylistTab) obj).playlistType)
                    && Objects.equals(playlistName, ((PlaylistTab) obj).playlistName))) {
                return false; // base objects are different
            }

            return (playlistId == ((PlaylistTab) obj).playlistId)                     // local
                    || (playlistServiceId == ((PlaylistTab) obj).playlistServiceId    // remote
                    && Objects.equals(playlistUrl, ((PlaylistTab) obj).playlistUrl));
        }

        public int getPlaylistServiceId() {
            return playlistServiceId;
        }

        public String getPlaylistUrl() {
            return playlistUrl;
        }

        public String getPlaylistName() {
            return playlistName;
        }

        public long getPlaylistId() {
            return playlistId;
        }

        public LocalItemType getPlaylistType() {
            return playlistType;
        }
    }
}
