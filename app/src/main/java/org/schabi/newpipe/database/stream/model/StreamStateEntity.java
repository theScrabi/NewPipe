package org.schabi.newpipe.database.stream.model;

import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import java.util.concurrent.TimeUnit;

import static androidx.room.ForeignKey.CASCADE;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.JOIN_STREAM_ID;
import static org.schabi.newpipe.database.stream.model.StreamStateEntity.STREAM_STATE_TABLE;

@Entity(tableName = STREAM_STATE_TABLE,
        primaryKeys = {JOIN_STREAM_ID},
        foreignKeys = {
                @ForeignKey(entity = StreamEntity.class,
                        parentColumns = StreamEntity.STREAM_ID,
                        childColumns = JOIN_STREAM_ID,
                        onDelete = CASCADE, onUpdate = CASCADE)
        })
public class StreamStateEntity {
    public static final String STREAM_STATE_TABLE = "stream_state";
    public static final String JOIN_STREAM_ID = "stream_id";
    // This additional field is required for the SQL query because 'stream_id' is used
    // for some other joins already
    public static final String JOIN_STREAM_ID_ALIAS = "stream_id_alias";
    public static final String STREAM_PROGRESS_TIME = "progress_time";

    /**
     * Playback state will not be saved, if playback time is less than this threshold.
     */
    private static final int PLAYBACK_SAVE_THRESHOLD_START_SECONDS = 5;
    /**
     * Playback state will not be saved, if time left is less than this threshold.
     */
    private static final int PLAYBACK_SAVE_THRESHOLD_END_SECONDS = 10;

    @ColumnInfo(name = JOIN_STREAM_ID)
    private long streamUid;

    @ColumnInfo(name = STREAM_PROGRESS_TIME)
    private long progressTime;

    public StreamStateEntity(final long streamUid, final long progressTime) {
        this.streamUid = streamUid;
        this.progressTime = progressTime;
    }

    public long getStreamUid() {
        return streamUid;
    }

    public void setStreamUid(final long streamUid) {
        this.streamUid = streamUid;
    }

    public long getProgressTime() {
        return progressTime;
    }

    public void setProgressTime(final long progressTime) {
        this.progressTime = progressTime;
    }

    public boolean isValid(final int durationInSeconds) {
        final int seconds = (int) TimeUnit.MILLISECONDS.toSeconds(progressTime);
        return (Math.abs(seconds - durationInSeconds) <= 1) || (seconds > PLAYBACK_SAVE_THRESHOLD_START_SECONDS
                && seconds < durationInSeconds - PLAYBACK_SAVE_THRESHOLD_END_SECONDS);
    }

    @Override
    public boolean equals(@Nullable final Object obj) {
        if (obj instanceof StreamStateEntity) {
            return ((StreamStateEntity) obj).streamUid == streamUid
                    && ((StreamStateEntity) obj).progressTime == progressTime;
        } else {
            return false;
        }
    }
}
