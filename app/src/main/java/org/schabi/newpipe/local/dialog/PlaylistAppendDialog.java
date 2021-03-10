package org.schabi.newpipe.local.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.local.LocalItemListAdapter;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.player.playqueue.PlayQueueItem;
import org.schabi.newpipe.util.OnClickGesture;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public final class PlaylistAppendDialog extends PlaylistDialog {
    private static final String TAG = PlaylistAppendDialog.class.getCanonicalName();

    private RecyclerView playlistRecyclerView;
    private LocalItemListAdapter playlistAdapter;

    private final CompositeDisposable playlistDisposables = new CompositeDisposable();

    public static Disposable onPlaylistFound(
            final Context context, final Runnable onSuccess, final Runnable onFailed
    ) {
        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(context));

        return playlistManager.hasPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(hasPlaylists -> {
                    if (hasPlaylists) {
                        onSuccess.run();
                    } else {
                        onFailed.run();
                    }
                });
    }

    public static PlaylistAppendDialog fromStreamInfo(final StreamInfo info) {
        final PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        dialog.setInfo(List.of(new StreamEntity(info)));
        return dialog;
    }

    public static PlaylistAppendDialog fromStreamInfoItems(final List<StreamInfoItem> items) {
        final PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        final List<StreamEntity> entities = new ArrayList<>(items.size());
        for (final StreamInfoItem item : items) {
            entities.add(new StreamEntity(item));
        }
        dialog.setInfo(entities);
        return dialog;
    }

    public static PlaylistAppendDialog fromPlayQueueItems(final List<PlayQueueItem> items) {
        final PlaylistAppendDialog dialog = new PlaylistAppendDialog();
        final List<StreamEntity> entities = new ArrayList<>(items.size());
        for (final PlayQueueItem item : items) {
            entities.add(new StreamEntity(item));
        }
        dialog.setInfo(entities);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Creation
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_playlists, container);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final LocalPlaylistManager playlistManager =
                new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));

        playlistAdapter = new LocalItemListAdapter(getActivity());
        playlistAdapter.setSelectedListener(new OnClickGesture<LocalItem>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                if (!(selectedItem instanceof PlaylistMetadataEntry) || getStreams() == null) {
                    return;
                }
                onPlaylistSelected(playlistManager, (PlaylistMetadataEntry) selectedItem,
                        getStreams());
            }
        });

        playlistRecyclerView = view.findViewById(R.id.playlist_list);
        playlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        playlistRecyclerView.setAdapter(playlistAdapter);

        final View newPlaylistButton = view.findViewById(R.id.newPlaylist);
        newPlaylistButton.setOnClickListener(ignored -> openCreatePlaylistDialog());

        playlistDisposables.add(playlistManager.getPlaylists()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::onPlaylistsReceived));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle - Destruction
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        playlistDisposables.dispose();
        if (playlistAdapter != null) {
            playlistAdapter.unsetSelectedListener();
        }

        playlistDisposables.clear();
        playlistRecyclerView = null;
        playlistAdapter = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Helper
    //////////////////////////////////////////////////////////////////////////*/

    public void openCreatePlaylistDialog() {
        if (getStreams() == null || !isAdded()) {
            return;
        }

        PlaylistCreationDialog.newInstance(getStreams()).show(getParentFragmentManager(), TAG);
        requireDialog().dismiss();
    }

    private void onPlaylistsReceived(@NonNull final List<PlaylistMetadataEntry> playlists) {
        if (playlistAdapter != null && playlistRecyclerView != null) {
            playlistAdapter.clearStreamItemList();
            playlistAdapter.addItems(playlists);
            playlistRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void onPlaylistSelected(@NonNull final LocalPlaylistManager manager,
                                    @NonNull final PlaylistMetadataEntry playlist,
                                    @NonNull final List<StreamEntity> streams) {
        if (getStreams() == null) {
            return;
        }

        final Toast successToast = Toast.makeText(getContext(),
                R.string.playlist_add_stream_success, Toast.LENGTH_SHORT);

        if (playlist.thumbnailUrl.equals("drawable://" + R.drawable.dummy_thumbnail_playlist)) {
            playlistDisposables.add(manager
                    .changePlaylistThumbnail(playlist.uid, streams.get(0).getThumbnailUrl())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(ignored -> successToast.show()));
        }

        playlistDisposables.add(manager.appendToPlaylist(playlist.uid, streams)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> successToast.show()));

        requireDialog().dismiss();
    }
}
