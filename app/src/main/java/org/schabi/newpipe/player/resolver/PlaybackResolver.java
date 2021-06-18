package org.schabi.newpipe.player.resolver;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.manifest.DashManifest;
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser;

import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.player.helper.PlayerDataSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
                                         @NonNull final Stream stream,
                                         @NonNull final String cacheKey,
                                         @NonNull final MediaSourceTag metadata)
            throws IOException {
        final DeliveryMethod deliveryFormat = stream.getDeliveryMethod();
        if (deliveryFormat.equals(DeliveryMethod.PROGRESSIVE_HTTP)
                || deliveryFormat.equals(DeliveryMethod.TORRENT)) {
            final String url = stream.getContent();
            return dataSource.getExtractorMediaSourceFactory(cacheKey).setTag(metadata)
                    .createMediaSource(MediaItem.fromUri((url)));
        } else if (deliveryFormat.equals(DeliveryMethod.HLS)) {
            if (stream.isUrl()) {
                return dataSource.getHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(stream.getContent())));
            } else {
                throw new IllegalArgumentException(
                        "HLS streams which are not URLs are not supported");
            }
            // Other contents are not supported right now
        } else if (deliveryFormat.equals(DeliveryMethod.DASH)) {
            if (stream.isUrl()) {
                return dataSource.getDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(stream.getContent())));
            } else {
                final DashManifest dashManifest;
                try {
                    final ByteArrayInputStream dashManifestInput = new ByteArrayInputStream(
                            stream.getContent().getBytes(StandardCharsets.UTF_8));
                    dashManifest = new DashManifestParser().parse(Uri.parse(stream.getBaseUrl()),
                            dashManifestInput);
                } catch (final IOException e) {
                    throw new IOException("Error when parsing manual DASH manifest", e);
                }
                return dataSource.getDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(dashManifest);
            }
        } else {
            throw new IllegalArgumentException("Unsupported delivery type" + deliveryFormat);
        }
    }
}
