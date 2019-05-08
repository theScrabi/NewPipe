package org.schabi.newpipe.database.stream.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.reactivex.rxjava3.core.Flowable
import org.schabi.newpipe.database.BasicDAO
import org.schabi.newpipe.database.stream.model.StreamEntity
import org.schabi.newpipe.database.stream.model.StreamEntity.Companion.STREAM_ID
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
import org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
import java.time.OffsetDateTime

@Dao
abstract class StreamDAO : BasicDAO<StreamEntity> {
    @Query("SELECT * FROM streams")
    abstract override fun getAll(): Flowable<List<StreamEntity>>

    @Query("DELETE FROM streams")
    abstract override fun deleteAll(): Int

    @Query("SELECT * FROM streams WHERE service_id = :serviceId")
    abstract override fun listByService(serviceId: Int): Flowable<List<StreamEntity>>

    @Query("SELECT * FROM streams WHERE url = :url AND service_id = :serviceId")
    abstract fun getStream(serviceId: Long, url: String): Flowable<List<StreamEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsertInternal(stream: StreamEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    internal abstract fun silentInsertAllInternal(streams: List<StreamEntity>): List<Long>

    @Query("SELECT COUNT(*) != 0 FROM streams WHERE url = :url AND service_id = :serviceId")
    internal abstract fun exists(serviceId: Long, url: String?): Boolean

    @Query(
        """
        SELECT uid, stream_type, textual_upload_date, upload_date, is_upload_date_approximation, duration 
        FROM streams WHERE url = :url AND service_id = :serviceId
        """
    )
    internal abstract fun getMinimalStreamForCompare(serviceId: Int, url: String): StreamCompareFeed?

    @Transaction
    open fun upsert(newerStream: StreamEntity): Long {
        val uid = silentInsertInternal(newerStream)

        if (uid != -1L) {
            newerStream.uid = uid
            return uid
        }

        compareAndUpdateStream(newerStream)

        update(newerStream)
        return newerStream.uid
    }

    @Transaction
    open fun upsertAll(streams: List<StreamEntity>): List<Long> {
        val insertUidList = silentInsertAllInternal(streams)

        val streamIds = ArrayList<Long>(streams.size)
        for ((index, uid) in insertUidList.withIndex()) {
            val newerStream = streams[index]
            if (uid != -1L) {
                streamIds.add(uid)
                newerStream.uid = uid
                continue
            }

            compareAndUpdateStream(newerStream)
            streamIds.add(newerStream.uid)
        }

        update(streams)
        return streamIds
    }

    private fun compareAndUpdateStream(newerStream: StreamEntity) {
        val existentMinimalStream = getMinimalStreamForCompare(newerStream.serviceId, newerStream.url)
            ?: throw IllegalStateException("Stream cannot be null just after insertion.")
        newerStream.uid = existentMinimalStream.uid

        val isNewerStreamLive = newerStream.streamType == AUDIO_LIVE_STREAM || newerStream.streamType == LIVE_STREAM
        if (!isNewerStreamLive) {

            // Use the existent upload date if the newer stream does not have a better precision
            // (i.e. is an approximation). This is done to prevent unnecessary changes.
            val hasBetterPrecision =
                newerStream.uploadDate != null && newerStream.isUploadDateApproximation != true
            if (existentMinimalStream.uploadDate != null && !hasBetterPrecision) {
                newerStream.uploadDate = existentMinimalStream.uploadDate
                newerStream.textualUploadDate = existentMinimalStream.textualUploadDate
                newerStream.isUploadDateApproximation = existentMinimalStream.isUploadDateApproximation
            }

            if (existentMinimalStream.duration > 0 && newerStream.duration < 0) {
                newerStream.duration = existentMinimalStream.duration
            }
        }
    }

    @Query(
        """
        DELETE FROM streams WHERE

        NOT EXISTS (SELECT 1 FROM stream_history sh
        WHERE sh.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM playlist_stream_join ps
        WHERE ps.stream_id = streams.uid)

        AND NOT EXISTS (SELECT 1 FROM feed f
        WHERE f.stream_id = streams.uid)
        """
    )
    abstract fun deleteOrphans(): Int

    /**
     * Minimal entry class used when comparing/updating an existent stream.
     */
    internal data class StreamCompareFeed(
        @ColumnInfo(name = STREAM_ID)
        var uid: Long = 0,

        @ColumnInfo(name = StreamEntity.STREAM_TYPE)
        var streamType: StreamType,

        @ColumnInfo(name = StreamEntity.STREAM_TEXTUAL_UPLOAD_DATE)
        var textualUploadDate: String? = null,

        @ColumnInfo(name = StreamEntity.STREAM_UPLOAD_DATE)
        var uploadDate: OffsetDateTime? = null,

        @ColumnInfo(name = StreamEntity.STREAM_IS_UPLOAD_DATE_APPROXIMATION)
        var isUploadDateApproximation: Boolean? = null,

        @ColumnInfo(name = StreamEntity.STREAM_DURATION)
        var duration: Long
    )
}
