package org.schabi.newpipe.database.feed.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.schabi.newpipe.database.feed.model.FeedGroupEntity.Companion.FEED_GROUP_TABLE
import org.schabi.newpipe.local.subscription.FeedGroupIcon

@Entity(tableName = FEED_GROUP_TABLE)
data class FeedGroupEntity(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = ID)
        val uid: Long,

        @ColumnInfo(name = NAME)
        var name: String,

        @ColumnInfo(name = ICON)
        var icon: FeedGroupIcon
) {
    companion object {
        const val FEED_GROUP_TABLE = "feed_group"

        const val ID = "uid"
        const val NAME = "name"
        const val ICON = "icon_id"

        const val GROUP_ALL_ID = -1L
    }
}