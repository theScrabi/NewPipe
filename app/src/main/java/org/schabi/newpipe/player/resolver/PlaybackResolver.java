package org.schabi.newpipe.player.resolver;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.util.Util;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerDataSource;

public interface PlaybackResolver extends Resolver<StreamInfo, MediaSource> {

    @Nullable
    default MediaSource maybeBuildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                                  @NonNull final StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!(streamType == StreamType.AUDIO_LIVE_STREAM || streamType == StreamType.LIVE_STREAM)) {
            return null;
        }

        final MediaSourceTag tag = new MediaSourceTag(info);
        if (!info.getHlsUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getHlsUrl(), C.TYPE_HLS, tag);
        } else if (!info.getDashMpdUrl().isEmpty()) {
            return buildLiveMediaSource(dataSource, info.getDashMpdUrl(), C.TYPE_DASH, tag);
        }

        return null;
    }

    @NonNull
    default MediaSource buildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                             @NonNull final String sourceUrl,
                                             @C.ContentType final int type,
                                             @NonNull final MediaSourceTag metadata) {
        final Uri uri = Uri.parse(sourceUrl);
        switch (type) {
            case C.TYPE_SS:
                return dataSource.getLiveSsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_DASH:
                return dataSource.getLiveDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_HLS:
                return dataSource.getLiveHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(uri));
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }

    @NonNull
    default MediaSource buildMediaSource(@NonNull final PlayerDataSource dataSource,
                                         @NonNull final String sourceUrl,
                                         @NonNull final String cacheKey,
                                         @NonNull final String overrideExtension,
                                         @NonNull final MediaSourceTag metadata) {
        final Uri uri = Uri.parse(sourceUrl);
        @C.ContentType final int type = TextUtils.isEmpty(overrideExtension)
                ? Util.inferContentType(uri) : Util.inferContentType("." + overrideExtension);

        switch (type) {
            case C.TYPE_SS:
                return dataSource.getLiveSsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_DASH:
                return dataSource.getDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_HLS:
                return dataSource.getHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(uri));
            case C.TYPE_OTHER:
                return dataSource.getExtractorMediaSourceFactory(cacheKey).setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(uri));
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }
    }
}
