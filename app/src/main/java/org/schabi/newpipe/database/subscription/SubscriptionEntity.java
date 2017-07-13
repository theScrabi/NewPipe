package org.schabi.newpipe.database.subscription;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;

import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_SERVICE_ID;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_TABLE;
import static org.schabi.newpipe.database.subscription.SubscriptionEntity.SUBSCRIPTION_URL;

@Entity(tableName = SUBSCRIPTION_TABLE,
        indices = {@Index(value = {SUBSCRIPTION_SERVICE_ID, SUBSCRIPTION_URL}, unique = true)})
public class SubscriptionEntity {

    final static String SUBSCRIPTION_TABLE          = "subscriptions";
    final static String SUBSCRIPTION_SERVICE_ID     = "service_id";
    final static String SUBSCRIPTION_URL            = "url";
    final static String SUBSCRIPTION_TITLE          = "title";
    final static String SUBSCRIPTION_THUMBNAIL_URL  = "thumbnail_url";

    @PrimaryKey(autoGenerate = true)
    private long uid = 0;

    @ColumnInfo(name = SUBSCRIPTION_SERVICE_ID)
    private int serviceId = -1;

    /* Do not keep extraneous information on entities as they are dynamic */
    @ColumnInfo(name = SUBSCRIPTION_URL)
    private String url;

    @ColumnInfo(name = SUBSCRIPTION_TITLE)
    private String title;

    @ColumnInfo(name = SUBSCRIPTION_THUMBNAIL_URL)
    private String thumbnailUrl;

    public long getUid() {
        return uid;
    }

    /* Keep this package-private since UID should always be auto generated by Room impl */
    void setUid(long uid) {
        this.uid = uid;
    }

    public int getServiceId() {
        return serviceId;
    }

    public void setServiceId(int serviceId) {
        this.serviceId = serviceId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
}
