package org.schabi.newpipe.player.resolver;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;

import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.SubtitlesStream;
import org.schabi.newpipe.extractor.stream.VideoStream;
import org.schabi.newpipe.player.helper.PlayerDataSource;
import org.schabi.newpipe.player.helper.PlayerHelper;
import org.schabi.newpipe.util.ListHelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.google.android.exoplayer2.C.TIME_UNSET;

public class VideoPlaybackResolver implements PlaybackResolver {
    @NonNull
    private final Context context;
    @NonNull
    private final PlayerDataSource dataSource;
    @NonNull
    private final QualityResolver qualityResolver;

    @Nullable
    private String playbackQuality;

    public VideoPlaybackResolver(@NonNull final Context context,
                                 @NonNull final PlayerDataSource dataSource,
                                 @NonNull final QualityResolver qualityResolver) {
        this.context = context;
        this.dataSource = dataSource;
        this.qualityResolver = qualityResolver;
    }

    @Override
    @Nullable
    public MediaSource resolve(@NonNull final StreamInfo info) {
        final MediaSource liveSource = maybeBuildLiveMediaSource(dataSource, info);
        if (liveSource != null) {
            return liveSource;
        }

        final List<MediaSource> mediaSources = new ArrayList<>();
        final List<VideoStream> videoStreams = new ArrayList<>(info.getVideoStreams());
        final List<VideoStream> videoOnlyStreams = new ArrayList<>(info.getVideoOnlyStreams());

        removeTorrentStreams(videoStreams);
        removeTorrentStreams(videoOnlyStreams);

        // Create video stream source
        final List<VideoStream> videos = ListHelper.getSortedStreamVideosList(context,
                videoStreams, videoOnlyStreams, false);
        final int index;
        if (videos.isEmpty()) {
            index = -1;
        } else if (playbackQuality == null) {
            index = qualityResolver.getDefaultResolutionIndex(videos);
        } else {
            index = qualityResolver.getOverrideResolutionIndex(videos, getPlaybackQuality());
        }
        final MediaSourceTag tag = new MediaSourceTag(info, videos, index);
        @Nullable final VideoStream video = tag.getSelectedVideoStream();

        if (video != null) {
            try {
                final MediaSource streamSource = buildMediaSource(dataSource, video,
                        PlayerHelper.cacheKeyOf(info, video), tag);
                mediaSources.add(streamSource);
            } catch (final IOException e) {
                return null;
            }
        }

        // Create optional audio stream source
        final List<AudioStream> audioStreams = info.getAudioStreams();
        removeTorrentStreams(audioStreams);
        final AudioStream audio = audioStreams.isEmpty() ? null : audioStreams.get(
                ListHelper.getDefaultAudioFormat(context, audioStreams));

        // Use the audio stream if there is no video stream, or
        // merge with audio stream in case if video does not contain audio
        if (audio != null && (video == null || video.isVideoOnly())) {
            try {
                final MediaSource audioSource = buildMediaSource(dataSource, audio,
                        PlayerHelper.cacheKeyOf(info, audio), tag);
                mediaSources.add(audioSource);
            } catch (final IOException e) {
                return null;
            }
        }

        // If there is no audio or video sources, then this media source cannot be played back
        if (mediaSources.isEmpty()) {
            return null;
        }
        // Below are auxiliary media sources

        // Create subtitle sources
        final List<SubtitlesStream> subtitlesStreams = info.getSubtitles();
        if (subtitlesStreams != null) {
            // Torrent and non URL subtitles are not supported by ExoPlayer
            removeTorrentAndNonUrlStreams(subtitlesStreams);
            for (final SubtitlesStream subtitle : subtitlesStreams) {
                final String mimeType = PlayerHelper.subtitleMimeTypesOf(subtitle.getFormat());
                if (mimeType == null) {
                    continue;
                }
                final MediaSource textSource = dataSource.getSampleMediaSourceFactory()
                        .createMediaSource(
                                new MediaItem.Subtitle(Uri.parse(subtitle.getContent()),
                                        mimeType,
                                        PlayerHelper.captionLanguageOf(context, subtitle)),
                                TIME_UNSET);
                mediaSources.add(textSource);
            }
        }

        if (mediaSources.size() == 1) {
            return mediaSources.get(0);
        } else {
            return new MergingMediaSource(mediaSources.toArray(
                    new MediaSource[0]));
        }
    }

    @Nullable
    public String getPlaybackQuality() {
        return playbackQuality;
    }

    public void setPlaybackQuality(@Nullable final String playbackQuality) {
        this.playbackQuality = playbackQuality;
    }

    public interface QualityResolver {
        int getDefaultResolutionIndex(List<VideoStream> sortedVideos);

        int getOverrideResolutionIndex(List<VideoStream> sortedVideos, String playbackQuality);
    }
}
