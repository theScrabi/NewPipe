package org.schabi.newpipe.fragments.list.playlist;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;

public final class AppendPlaylistDialog extends PlaylistDialog
        implements PlaylistDialog.OnSelectedListener {
    public static final String TAG = AppendPlaylistDialog.class.getSimpleName();

    public AppendPlaylistDialog(@Nullable final List<StreamEntity> streamEntities) {
        super(streamEntities, false);
        setOnSelectedListener(this);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {

        final View view = super.onCreateView(inflater, container, savedInstanceState);

        final View newPlaylistButton = Objects.requireNonNull(view).findViewById(R.id.newPlaylist);
        newPlaylistButton.setVisibility(View.VISIBLE);
        newPlaylistButton.setOnClickListener(v -> openCreatePlaylistDialog());

        view.findViewById(R.id.titleTextView).setVisibility(View.GONE);

        return view;
    }

    public static AppendPlaylistDialog fromStreamInfo(final StreamInfo streamInfo) {
        return new AppendPlaylistDialog(Collections.singletonList(new StreamEntity(streamInfo)));
    }

    public static AppendPlaylistDialog fromStreamInfoItems(
            final List<StreamInfoItem> streamInfoItems) {

        final List<StreamEntity> streamEntities = new ArrayList<>(streamInfoItems.size());
        for (final StreamInfoItem streamInfoItem : streamInfoItems) {
            streamEntities.add(new StreamEntity(streamInfoItem));
        }
        return new AppendPlaylistDialog(streamEntities);
    }

    public static AppendPlaylistDialog fromPlayQueueItems(
            final List<PlayQueueItem> playQueueItems) {

        final List<StreamEntity> streamEntities = new ArrayList<>(playQueueItems.size());
        for (final PlayQueueItem playQueueItem : playQueueItems) {
            streamEntities.add(new StreamEntity(playQueueItem));
        }
        return new AppendPlaylistDialog(streamEntities);
    }


    @Override
    public void onLocalPlaylistSelected(final PlaylistMetadataEntry localPlaylist) {
        if (getStoredStreamEntities() != null) {
            final Toast successToast = Toast.makeText(getContext(),
                    R.string.playlist_add_stream_success, Toast.LENGTH_SHORT);

            if (localPlaylist.thumbnailUrl
                    .equals("drawable://" + R.drawable.dummy_thumbnail_playlist)) {
                dialogDisposables.add(getLocalPlaylistManager()
                        .changePlaylistThumbnail(localPlaylist.uid,
                                getStoredStreamEntities().get(0).getThumbnailUrl())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(ignored -> successToast.show()));
            }

            dialogDisposables.add(getLocalPlaylistManager()
                    .appendToPlaylist(localPlaylist.uid, getStoredStreamEntities())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ignored -> successToast.show()));

            requireDialog().dismiss();
        }
    }

    @Override
    public void onRemotePlaylistSelected(final PlaylistRemoteEntity remotePlaylist) {
        // unreachable code
    }

    public void openCreatePlaylistDialog() {
        if (getStoredStreamEntities() == null) {
            return;
        }

        new CreatePlaylistDialog(getStoredStreamEntities())
                .show(getParentFragmentManager(), TAG);
        requireDialog().dismiss();
    }
}
