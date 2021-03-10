package org.schabi.newpipe.player.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.io.Serializable;
import java.util.List;

public class MediaSourceTag implements Serializable {
    @NonNull
    private final StreamInfo metadata;

    @NonNull
    private final List<VideoStream> sortedAvailableVideoStreams;
    private final int selectedVideoStreamIndex;

    public MediaSourceTag(@NonNull final StreamInfo metadata,
                          @NonNull final List<VideoStream> sortedAvailableVideoStreams,
                          final int selectedVideoStreamIndex) {
        this.metadata = metadata;
        this.sortedAvailableVideoStreams = sortedAvailableVideoStreams;
        this.selectedVideoStreamIndex = selectedVideoStreamIndex;
    }

    public MediaSourceTag(@NonNull final StreamInfo metadata) {
        this(metadata, List.of(), /*indexNotAvailable=*/-1);
    }

    @NonNull
    public StreamInfo getMetadata() {
        return metadata;
    }

    @NonNull
    public List<VideoStream> getSortedAvailableVideoStreams() {
        return sortedAvailableVideoStreams;
    }

    public int getSelectedVideoStreamIndex() {
        return selectedVideoStreamIndex;
    }

    @Nullable
    public VideoStream getSelectedVideoStream() {
        return selectedVideoStreamIndex < 0
                || selectedVideoStreamIndex >= sortedAvailableVideoStreams.size()
                ? null : sortedAvailableVideoStreams.get(selectedVideoStreamIndex);
    }
}
