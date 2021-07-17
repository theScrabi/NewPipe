package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.upstream.ResolvingDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.services.youtube.YoutubeThrottlingDecoder;
import org.schabi.newpipe.extractor.utils.Parser;

public class PlayerDataSource {
    private static final int MANIFEST_MINIMUM_RETRY = 5;
    private static final int EXTRACTOR_MINIMUM_RETRY = Integer.MAX_VALUE;
    private static final int LIVE_STREAM_EDGE_GAP_MILLIS = 10000;

    private final DataSource.Factory cacheDataSourceFactory;
    private final DataSource.Factory ytThrottlingDataSourceFactory;
    private final DataSource.Factory cachelessDataSourceFactory;

    public PlayerDataSource(@NonNull final Context context, @NonNull final String userAgent,
                            @NonNull final TransferListener transferListener) {
        cacheDataSourceFactory = new CacheFactory(context, userAgent, transferListener);
        cachelessDataSourceFactory
                = new DefaultDataSourceFactory(context, userAgent, transferListener);

        try {
            YoutubeThrottlingDecoder youtubeThrottlingDecoder = new YoutubeThrottlingDecoder("dbevJM-2lc", Localization.DEFAULT);
            ytThrottlingDataSourceFactory = new ResolvingDataSource.Factory(
                    cacheDataSourceFactory,
                    dataSpec -> {
                        Log.d("aaaa", "dataspec called");
                        String url = dataSpec.uri.toString();

                        if (url.contains("ratebypass=yes")) {
                            Log.d("aaaa", "ratebypass=yes");
                        }
                        try {
                            String oldNParam = youtubeThrottlingDecoder.parseNParam(url);
                            String newNParam = youtubeThrottlingDecoder.decodeNParam(oldNParam);
                            String newUrl = youtubeThrottlingDecoder.replaceNParam(url, oldNParam, newNParam);

                            Log.d("aaaa", oldNParam + " - " + newNParam);
                            String ipV4Pattern = "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";
                            Log.d("aaaa", newUrl.replaceAll(ipV4Pattern, "127.0.0.1"));

                            return dataSpec.withUri(Uri.parse(newUrl));

                        } catch (Parser.RegexException e) {
                            Log.d("aaaa", "regex exception ignored");
                            return dataSpec;
                        }
                    }
            );
        } catch (ParsingException e) {
            Log.d("aaaa", "parsing exception", e);
            throw new RuntimeException(e);
        }
    }

    public SsMediaSource.Factory getLiveSsMediaSourceFactory() {
        Log.d("aaaa", "getLiveSs");
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(
                cachelessDataSourceFactory), cachelessDataSourceFactory)
                .setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY))
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS);
    }

    public HlsMediaSource.Factory getLiveHlsMediaSourceFactory() {
        Log.d("aaaa", "getLiveHls");
        return new HlsMediaSource.Factory(cachelessDataSourceFactory)
                .setAllowChunklessPreparation(true)
                .setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY));
    }

    public DashMediaSource.Factory getLiveDashMediaSourceFactory() {
        Log.d("aaaa", "getLiveDash");
        return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                cachelessDataSourceFactory), cachelessDataSourceFactory)
                .setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(MANIFEST_MINIMUM_RETRY))
                .setLivePresentationDelayMs(LIVE_STREAM_EDGE_GAP_MILLIS, true);
    }

    public SsMediaSource.Factory getSsMediaSourceFactory() {
        Log.d("aaaa", "getSs");
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(
                ytThrottlingDataSourceFactory), ytThrottlingDataSourceFactory);
    }

    public HlsMediaSource.Factory getHlsMediaSourceFactory() {
        Log.d("aaaa", "getHls");
        return new HlsMediaSource.Factory(ytThrottlingDataSourceFactory);
    }

    public DashMediaSource.Factory getDashMediaSourceFactory() {
        Log.d("aaaa", "getDash");
        return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(
                ytThrottlingDataSourceFactory), ytThrottlingDataSourceFactory);
    }

    public ProgressiveMediaSource.Factory getExtractorMediaSourceFactory() {
        Log.d("aaaa", "getExtractor");
        return new ProgressiveMediaSource.Factory(ytThrottlingDataSourceFactory)
                .setLoadErrorHandlingPolicy(
                        new DefaultLoadErrorHandlingPolicy(EXTRACTOR_MINIMUM_RETRY));
    }

    public ProgressiveMediaSource.Factory getExtractorMediaSourceFactory(
            @NonNull final String key) {
        Log.d("aaaa", "getExtractor with key");
        return getExtractorMediaSourceFactory().setCustomCacheKey(key);
    }

    public SingleSampleMediaSource.Factory getSampleMediaSourceFactory() {
        return new SingleSampleMediaSource.Factory(ytThrottlingDataSourceFactory);
    }
}
