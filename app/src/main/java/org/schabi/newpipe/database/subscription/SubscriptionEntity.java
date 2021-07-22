package org.schabi.newpipe.database.subscription;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.util.Constants;

import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_SERVICE_ID;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_TABLE;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_URL;

@Entity(tableName = SUBSCRIPTION_TABLE,
        indices = {@Index(value = {SUBSCRIPTION_SERVICE_ID, SUBSCRIPTION_URL}, unique = true)})
public class SubscriptionEntity {
    public static final String SUBSCRIPTION_UID = "uid";
    public static final String SUBSCRIPTION_TABLE = "subscriptions";
    public static final String SUBSCRIPTION_SERVICE_ID = "service_id";
    public static final String SUBSCRIPTION_URL = "url";
    public static final String SUBSCRIPTION_NAME = "name";
    public static final String SUBSCRIPTION_AVATAR_URL = "avatar_url";
    public static final String SUBSCRIPTION_SUBSCRIBER_COUNT = "subscriber_count";
    public static final String SUBSCRIPTION_DESCRIPTION = "description";
    public static final String SUBSCRIPTION_NOTIFICATION_MODE  = "notification_mode";

    @PrimaryKey(autoGenerate = true)
    private long uid = 0;

    @ColumnInfo(name = SUBSCRIPTION_SERVICE_ID)
    private int serviceId = Constants.NO_SERVICE_ID;

    @ColumnInfo(name = SUBSCRIPTION_URL)
    private String url;

    @ColumnInfo(name = SUBSCRIPTION_NAME)
    private String name;

    @ColumnInfo(name = SUBSCRIPTION_AVATAR_URL)
    private String avatarUrl;

    @ColumnInfo(name = SUBSCRIPTION_SUBSCRIBER_COUNT)
    private Long subscriberCount;

    @ColumnInfo(name = SUBSCRIPTION_DESCRIPTION)
    private String description;

    @ColumnInfo(name = SUBSCRIPTION_NOTIFICATION_MODE)
    private int notificationMode;

    @Ignore
    public static SubscriptionEntity from(@NonNull final ChannelInfo info) {
        final SubscriptionEntity result = new SubscriptionEntity();
        result.setServiceId(info.getServiceId());
        result.setUrl(info.getUrl());
        result.setData(info.getName(), info.getAvatarUrl(), info.getDescription(),
                info.getSubscriberCount());
        return result;
    }

    public long getUid() {
        return uid;
    }

    public void setUid(final long uid) {
        this.uid = uid;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(final int serviceId) {
        this.serviceId = serviceId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(final String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public Long getSubscriberCount() {
        return subscriberCount;
    }

    public void setSubscriberCount(final Long subscriberCount) {
        this.subscriberCount = subscriberCount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @NotificationMode
    public int getNotificationMode() {
        return notificationMode;
    }

    public void setNotificationMode(@NotificationMode final int notificationMode) {
        this.notificationMode = notificationMode;
    }

    @Ignore
    public void setData(final String n, final String au, final String d, final Long sc) {
        this.setName(n);
        this.setAvatarUrl(au);
        this.setDescription(d);
        this.setSubscriberCount(sc);
    }

    @Ignore
    public ChannelInfoItem toChannelInfoItem() {
        final ChannelInfoItem item = new ChannelInfoItem(getServiceId(), getUrl(), getName());
        item.setThumbnailUrl(getAvatarUrl());
        item.setSubscriberCount(getSubscriberCount());
        item.setDescription(getDescription());
        return item;
    }


    // TODO: Remove these generated methods by migrating this class to a data class from Kotlin.
    @Override
    @SuppressWarnings("EqualsReplaceableByObjectsCall")
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SubscriptionEntity that = (SubscriptionEntity) o;

        if (uid != that.uid) {
            return false;
        }
        if (serviceId != that.serviceId) {
            return false;
        }
        if (!url.equals(that.url)) {
            return false;
        }
        if (name != null ? !name.equals(that.name) : that.name != null) {
            return false;
        }
        if (avatarUrl != null ? !avatarUrl.equals(that.avatarUrl) : that.avatarUrl != null) {
            return false;
        }
        if (subscriberCount != null
                ? !subscriberCount.equals(that.subscriberCount)
                : that.subscriberCount != null) {
            return false;
        }
        return description != null
                ? description.equals(that.description)
                : that.description == null;
    }

    @Override
    public int hashCode() {
        int result = (int) (uid ^ (uid >>> 32));
        result = 31 * result + serviceId;
        result = 31 * result + url.hashCode();
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (avatarUrl != null ? avatarUrl.hashCode() : 0);
        result = 31 * result + (subscriberCount != null ? subscriberCount.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        return result;
    }
}
