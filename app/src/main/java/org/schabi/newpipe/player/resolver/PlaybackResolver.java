package org.schabi.newpipe.player.resolver;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import java.util.Iterator;
import java.util.List;

public interface PlaybackResolver extends Resolver<StreamInfo, MediaSource> {

    @Nullable
    default MediaSource maybeBuildHlsLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                                     @NonNull final StreamInfo info) {
        final StreamType streamType = info.getStreamType();
        if (!(streamType == StreamType.AUDIO_LIVE_STREAM
                || streamType == StreamType.LIVE_STREAM)) {
            return null;
        }

        final String hlsUrl = info.getHlsUrl();
        if (!hlsUrl.isEmpty() && info.getDashMpdUrl().isEmpty()) {
            final MediaSourceTag tag = new MediaSourceTag(info);
            return dataSource.getLiveHlsMediaSourceFactory().setTag(tag)
                    .createMediaSource(MediaItem.fromUri(hlsUrl));
        }

        return null;
    }

    @NonNull
    default MediaSource buildLiveMediaSource(@NonNull final PlayerDataSource dataSource,
                                             @NonNull final Stream stream,
                                             @NonNull final MediaSourceTag metadata)
            throws IOException {
        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        if (deliveryMethod.equals(DeliveryMethod.DASH)) {
            if (stream.isUrl()) {
                return dataSource.getLiveDashMediaSourceFactory().setTag(metadata)
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
                return dataSource.getLiveDashMediaSourceFactory().setTag(metadata)
                        .createMediaSource(dashManifest);
            }
        } else if (deliveryMethod.equals(DeliveryMethod.HLS)) {
            if (stream.isUrl()) {
                return dataSource.getLiveHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(stream.getContent())));
            } else {
                throw new IllegalArgumentException(
                        "HLS streams which are not URLs are not supported");
            }

        } else {
            throw new IllegalArgumentException(
                    "Only DASH and HLS streams are supported to create live media sources");
        }
    }

    @NonNull
    default MediaSource buildMediaSource(@NonNull final PlayerDataSource dataSource,
                                         @NonNull final Stream stream,
                                         @NonNull final String cacheKey,
                                         @NonNull final MediaSourceTag metadata)
            throws IOException {
        final DeliveryMethod deliveryMethod = stream.getDeliveryMethod();
        if (deliveryMethod.equals(DeliveryMethod.PROGRESSIVE_HTTP)) {
            final String url = stream.getContent();
            return dataSource.getExtractorMediaSourceFactory(cacheKey).setTag(metadata)
                    .createMediaSource(MediaItem.fromUri((url)));
        } else if (deliveryMethod.equals(DeliveryMethod.HLS)) {
            if (stream.isUrl()) {
                return dataSource.getHlsMediaSourceFactory().setTag(metadata)
                        .createMediaSource(MediaItem.fromUri(Uri.parse(stream.getContent())));
            } else {
                throw new IllegalArgumentException(
                        "HLS streams which are not URLs are not supported");
            }
        } else if (deliveryMethod.equals(DeliveryMethod.DASH)) {
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
            throw new IllegalArgumentException("Unsupported delivery type" + deliveryMethod);
        }
    }

    default void removeTorrentStreams(@NonNull final List<? extends Stream> streamList) {
        final Iterator<? extends Stream> streamIterator = streamList.iterator();
        while (streamIterator.hasNext()) {
            final Stream stream = streamIterator.next();
            if (stream.getDeliveryMethod() == DeliveryMethod.TORRENT) {
                streamIterator.remove();
            }
        }
    }
}
