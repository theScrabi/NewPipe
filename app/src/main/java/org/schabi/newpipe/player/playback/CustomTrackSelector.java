package org.schabi.newpipe.player.playback;

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.RendererCapabilities.Capabilities;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.util.Assertions;

/**
 * This class allows irregular text language labels for use when selecting text captions and
 * is mostly a copy-paste from {@link DefaultTrackSelector}.
 * <p>
 * This is a hack and should be removed once ExoPlayer fixes language normalization to accept
 * a broader set of languages.
 * </p>
 */
public class CustomTrackSelector extends DefaultTrackSelector {
    private String preferredTextLanguage;

    public CustomTrackSelector(final Context context,
                               final ExoTrackSelection.Factory adaptiveTrackSelectionFactory) {
        super(context, adaptiveTrackSelectionFactory);
    }

    private static boolean formatHasLanguage(final Format format, final String language) {
        return language != null && TextUtils.equals(language, format.language);
    }

    public String getPreferredTextLanguage() {
        return preferredTextLanguage;
    }

    public void setPreferredTextLanguage(@NonNull final String label) {
        Assertions.checkNotNull(label);
        if (!label.equals(preferredTextLanguage)) {
            preferredTextLanguage = label;
            invalidate();
        }
    }

    @Override
    @Nullable
    protected Pair<ExoTrackSelection.Definition, TextTrackScore> selectTextTrack(
            final TrackGroupArray groups,
            @NonNull final int[][] formatSupport,
            @NonNull final Parameters params,
            @Nullable final String selectedAudioLanguage) {
        TrackGroup selectedGroup = null;
        int selectedTrackIndex = C.INDEX_UNSET;
        TextTrackScore selectedTrackScore = null;

        for (int groupIndex = 0; groupIndex < groups.length; groupIndex++) {
            final TrackGroup trackGroup = groups.get(groupIndex);
            @Capabilities final int[] trackFormatSupport = formatSupport[groupIndex];

            for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                if (isSupported(trackFormatSupport[trackIndex],
                        params.exceedRendererCapabilitiesIfNecessary)) {
                    final Format format = trackGroup.getFormat(trackIndex);
                    final TextTrackScore trackScore = new TextTrackScore(format, params,
                            trackFormatSupport[trackIndex], selectedAudioLanguage);

                    if (formatHasLanguage(format, preferredTextLanguage)) {
                        selectedGroup = trackGroup;
                        selectedTrackIndex = trackIndex;
                        selectedTrackScore = trackScore;
                        break; // found user selected match (perfect!)

                    } else if (trackScore.isWithinConstraints && (selectedTrackScore == null
                            || trackScore.compareTo(selectedTrackScore) > 0)) {
                        selectedGroup = trackGroup;
                        selectedTrackIndex = trackIndex;
                        selectedTrackScore = trackScore;
                    }
                }
            }
        }
        return selectedGroup == null ? null
                : Pair.create(new ExoTrackSelection.Definition(selectedGroup, selectedTrackIndex),
                        Assertions.checkNotNull(selectedTrackScore));
    }
}
