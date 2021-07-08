package org.schabi.newpipe.player.resolver;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.source.MediaSource;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.util.ListHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AudioPlaybackResolver implements PlaybackResolver {
    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;

    public AudioPlaybackResolver(@NonNull final Context context,
                                 @NonNull final PlayerDataSource dataSource) {
        this.context = context;
        this.dataSource = dataSource;
    }

    @Override
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info) {
        final MediaSource hlsLiveSource = maybeBuildHlsLiveMediaSource(dataSource, info);
        if (hlsLiveSource != null) {
            return hlsLiveSource;
        }
        final List<AudioStream> audioStreams = new ArrayList<>(info.getAudioStreams());
        removeTorrentStreams(audioStreams);

        final int index = ListHelper.getDefaultAudioFormat(context, audioStreams);
        if (index < 0 || index >= info.getAudioStreams().size()) {
            return null;
        }

        final AudioStream audio = info.getAudioStreams().get(index);
        final MediaSourceTag tag = new MediaSourceTag(info);
        MediaSource mediaSource = null;
        final StreamType streamType = info.getStreamType();
        if (streamType != StreamType.LIVE_STREAM
                && streamType != StreamType.AUDIO_LIVE_STREAM) {
            try {
                mediaSource = buildMediaSource(dataSource, audio, PlayerHelper.cacheKeyOf(info,
                        audio), tag);
            } catch (final IOException ignored) {
            }
        } else {
            try {
                mediaSource = buildLiveMediaSource(dataSource, audio, tag);
            } catch (final IOException ignored) {
            }
        }
        return mediaSource;
    }
}
