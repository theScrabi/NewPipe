package org.schabi.newpipe.settings.sections;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonSink;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
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
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.Objects;

public abstract class Section {

    //must be same as in the DrawerFragment class
    static final int ITEM_ID_BLANK = -1;
    static final int ITEM_ID_SETTINGS = -2;
    static final int ITEM_ID_ABOUT = -3;
    static final int ITEM_ID_BOOKMARKS = -4;
    static final int ITEM_ID_FEED = -5;
    static final int ITEM_ID_SUBSCRIPTIONS = -6;
    static final int ITEM_ID_DOWNLOADS = -7;
    static final int ITEM_ID_HISTORY = -8;
    static final int ITEM_ID_DEFAULT_KIOSK = -9;
    static final int ITEM_ID_KIOSK = -10;
    static final int ITEM_ID_CHANNEL = -11;
    static final int ITEM_ID_PLAYLIST = -12;

    private static final String JSON_SECTION_ID_KEY = "section_id";

    Section() {   }

    Section(@NonNull final JsonObject jsonObject) {
        readDataFromJson(jsonObject);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Section Handling
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    public static Section from(@NonNull final JsonObject jsonObject) {
        final int sectionId = jsonObject.getInt(Section.JSON_SECTION_ID_KEY, -1);

        return from(sectionId, jsonObject);
    }

    @Nullable
    public static Section from(final int sectionId) {
        return from(sectionId, null);
    }

    @Nullable
    public static Section.Type typeFrom(final int sectionId) {
        for (final Section.Type available : Section.Type.values()) {
            if (available.getSectionId() == sectionId) {
                return available;
            }
        }
        return null;
    }

    @Nullable
    private static Section from(final int sectionId, @Nullable final JsonObject jsonObject) {
        final Section.Type type = typeFrom(sectionId);

        if (type == null) {
            return null;
        }

        if (jsonObject != null) {
            switch (type) {
                case KIOSK:
                    return new Section.KioskSection(jsonObject);
                case CHANNEL:
                    return new Section.ChannelSection(jsonObject);
                case PLAYLIST:
                    return new Section.PlaylistSection(jsonObject);
            }
        }

        return type.getSection();
    }

    public abstract int getSectionId();

    public abstract String getSectionName(Context context);

    @DrawableRes
    public abstract int getSectionIconRes(Context context);

    /**
     * Return a instance of the fragment that this Section represent.
     *
     * @param context Android app context
     * @return the fragment this Section represents
     */
    public abstract Fragment getFragment(Context context) throws ExtractionException;

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }

        return obj instanceof Section && obj.getClass().equals(this.getClass())
                && ((Section) obj).getSectionId() == this.getSectionId();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // JSON Handling
    //////////////////////////////////////////////////////////////////////////*/

    public void writeJsonOn(final JsonSink jsonSink) {
        jsonSink.object();

        jsonSink.value(JSON_SECTION_ID_KEY, getSectionId());
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
        BLANK(new Section.BlankSection()),
        DOWNLOADS(new Section.DownloadSection()),
        DEFAULT_KIOSK(new Section.DefaultKioskSection()),
        SUBSCRIPTIONS(new Section.SubscriptionsSection()),
        FEED(new Section.FeedSection()),
        BOOKMARKS(new Section.BookmarksSection()),
        HISTORY(new Section.HistorySection()),
        KIOSK(new Section.KioskSection()),
        CHANNEL(new Section.ChannelSection()),
        PLAYLIST(new Section.PlaylistSection());

        private Section section;

        Type(final Section section) {
            this.section = section;
        }

        public int getSectionId() {
            return section.getSectionId();
        }

        public Section getSection() {
            return section;
        }
    }

    public static class BlankSection extends Section {
        public static final int ID = ITEM_ID_BLANK;

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return "NewPipe"; //context.getString(R.string.blank_page_summary);
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_blank_page);
        }

        @Override
        public BlankFragment getFragment(final Context context) {
            return new BlankFragment();
        }
    }

    public static class SubscriptionsSection extends Section {
        public static final int ID = ITEM_ID_SUBSCRIPTIONS;

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return context.getString(R.string.tab_subscriptions);
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_channel);
        }

        @Override
        public SubscriptionFragment getFragment(final Context context) {
            return new SubscriptionFragment();
        }

    }

    public static class DownloadSection extends Section {
        public static final int ID = ITEM_ID_DOWNLOADS;

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return context.getString(R.string.download);
        }

        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_file_download);
        }

        @Override
        public Fragment getFragment(final Context context) {
            return new FeedFragment();
        }
    }

    public static class FeedSection extends Section {
        public static final int ID = ITEM_ID_FEED;

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return context.getString(R.string.fragment_feed_title);
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_rss);
        }

        @Override
        public FeedFragment getFragment(final Context context) {
            return new FeedFragment();
        }
    }

    public static class BookmarksSection extends Section {
        public static final int ID = ITEM_ID_BOOKMARKS;

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return context.getString(R.string.tab_bookmarks);
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_bookmark);
        }

        @Override
        public BookmarkFragment getFragment(final Context context) {
            return new BookmarkFragment();
        }
    }

    public static class HistorySection extends Section {
        public static final int ID = ITEM_ID_HISTORY;

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return context.getString(R.string.title_activity_history);
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_history);
        }

        @Override
        public StatisticsPlaylistFragment getFragment(final Context context) {
            return new StatisticsPlaylistFragment();
        }
    }

    public static class KioskSection extends Section {
        public static final int ID = ITEM_ID_KIOSK;
        private static final String JSON_KIOSK_SERVICE_ID_KEY = "service_id";
        private static final String JSON_KIOSK_ID_KEY = "kiosk_id";
        private int kioskServiceId;
        private String kioskId;

        private KioskSection() {
            this(-1, "<no-id>");
        }

        public KioskSection(final int kioskServiceId, final String kioskId) {
            this.kioskServiceId = kioskServiceId;
            this.kioskId = kioskId;
        }

        public KioskSection(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(kioskId, context);
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
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
            return super.equals(obj) && kioskServiceId == ((KioskSection) obj).kioskServiceId
                    && Objects.equals(kioskId, ((KioskSection) obj).kioskId);
        }

        public int getKioskServiceId() {
            return kioskServiceId;
        }

        public String getKioskId() {
            return kioskId;
        }
    }

    public static class ChannelSection extends Section {
        public static final int ID = ITEM_ID_CHANNEL;
        private static final String JSON_CHANNEL_SERVICE_ID_KEY = "channel_service_id";
        private static final String JSON_CHANNEL_URL_KEY = "channel_url";
        private static final String JSON_CHANNEL_NAME_KEY = "channel_name";
        private int channelServiceId;
        private String channelUrl;
        private String channelName;

        private ChannelSection() {
            this(-1, "<no-url>", "<no-name>");
        }

        public ChannelSection(final int channelServiceId, final String channelUrl,
                          final String channelName) {
            this.channelServiceId = channelServiceId;
            this.channelUrl = channelUrl;
            this.channelName = channelName;
        }

        public ChannelSection(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return channelName;
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_channel);
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
            return super.equals(obj) && channelServiceId == ((ChannelSection) obj).channelServiceId
                    && Objects.equals(channelUrl, ((ChannelSection) obj).channelUrl)
                    && Objects.equals(channelName, ((ChannelSection) obj).channelName);
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

    public static class DefaultKioskSection extends Section {
        public static final int ID = ITEM_ID_DEFAULT_KIOSK;

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return KioskTranslator.getTranslatedKioskName(getDefaultKioskId(context), context);
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
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
                ErrorActivity.reportError(context, e, null, null,
                        ErrorActivity.ErrorInfo.make(UserAction.REQUESTED_KIOSK, "none",
                                "Loading default kiosk from selected service", 0));
            }
            return kioskId;
        }
    }

    public static class PlaylistSection extends Section {
        public static final int ID = ITEM_ID_PLAYLIST;
        private static final String JSON_PLAYLIST_SERVICE_ID_KEY = "playlist_service_id";
        private static final String JSON_PLAYLIST_URL_KEY = "playlist_url";
        private static final String JSON_PLAYLIST_NAME_KEY = "playlist_name";
        private static final String JSON_PLAYLIST_ID_KEY = "playlist_id";
        private static final String JSON_PLAYLIST_TYPE_KEY = "playlist_type";
        private int playlistServiceId;
        private String playlistUrl;
        private String playlistName;
        private long playlistId;
        private LocalItem.LocalItemType playlistType;

        private PlaylistSection() {
            this(-1, "<no-name>");
        }

        public PlaylistSection(final long playlistId, final String playlistName) {
            this.playlistName = playlistName;
            this.playlistId = playlistId;
            this.playlistType = LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM;
            this.playlistServiceId = -1;
            this.playlistUrl = "<no-url>";
        }

        public PlaylistSection(final int playlistServiceId, final String playlistUrl,
                           final String playlistName) {
            this.playlistServiceId = playlistServiceId;
            this.playlistUrl = playlistUrl;
            this.playlistName = playlistName;
            this.playlistType = LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM;
            this.playlistId = -1;
        }

        public PlaylistSection(final JsonObject jsonObject) {
            super(jsonObject);
        }

        @Override
        public int getSectionId() {
            return ID;
        }

        @Override
        public String getSectionName(final Context context) {
            return playlistName;
        }

        @DrawableRes
        @Override
        public int getSectionIconRes(final Context context) {
            return ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_bookmark);
        }

        @Override
        public Fragment getFragment(final Context context) {
            if (playlistType == LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM) {
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
            playlistType = LocalItem.LocalItemType.valueOf(
                    jsonObject.getString(JSON_PLAYLIST_TYPE_KEY,
                            LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM.toString())
            );
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(super.equals(obj)
                    && Objects.equals(playlistType, ((PlaylistSection) obj).playlistType)
                    && Objects.equals(playlistName, ((PlaylistSection) obj).playlistName))) {
                return false; // base objects are different
            }

            return (playlistId == ((PlaylistSection) obj).playlistId)                     // local
                    || (playlistServiceId == ((PlaylistSection) obj).playlistServiceId    // remote
                    && Objects.equals(playlistUrl, ((PlaylistSection) obj).playlistUrl));
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

        public LocalItem.LocalItemType getPlaylistType() {
            return playlistType;
        }
    }
}
