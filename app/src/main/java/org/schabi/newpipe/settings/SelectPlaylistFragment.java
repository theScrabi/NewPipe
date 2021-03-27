package org.schabi.newpipe.settings;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.util.PicassoHelper;

import java.util.List;
import java.util.Vector;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.disposables.Disposable;

public class SelectPlaylistFragment extends DialogFragment {

    private OnSelectedListener onSelectedListener = null;

    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;
    private Disposable disposable = null;

    private List<PlaylistLocalItem> playlists = new Vector<>();

    public void setOnSelectedListener(final OnSelectedListener listener) {
        onSelectedListener = listener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.select_playlist_fragment, container, false);
        progressBar = v.findViewById(R.id.progressBar);
        recyclerView = v.findViewById(R.id.items_list);
        emptyView = v.findViewById(R.id.empty_state_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        final SelectPlaylistAdapter playlistAdapter = new SelectPlaylistAdapter();
        recyclerView.setAdapter(playlistAdapter);

        loadPlaylists();
        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and display playlists
    //////////////////////////////////////////////////////////////////////////*/

    private void loadPlaylists() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        final AppDatabase database = NewPipeDatabase.getInstance(requireContext());
        final LocalPlaylistManager localPlaylistManager = new LocalPlaylistManager(database);
        final RemotePlaylistManager remotePlaylistManager = new RemotePlaylistManager(database);

        disposable = Flowable.combineLatest(localPlaylistManager.getPlaylists(),
                remotePlaylistManager.getPlaylists(), PlaylistLocalItem::merge)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::displayPlaylists, this::onError);
    }

    private void displayPlaylists(final List<PlaylistLocalItem> newPlaylists) {
        playlists = newPlaylists;
        progressBar.setVisibility(View.GONE);
        emptyView.setVisibility(newPlaylists.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(newPlaylists.isEmpty() ? View.GONE : View.VISIBLE);
    }

    protected void onError(final Throwable e) {
        final Activity activity = requireActivity();
        ErrorActivity.reportErrorInSnackbar(activity, new ErrorInfo(e,
                UserAction.UI_ERROR, "Loading playlists"));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    private void clickedItem(final int position) {
        if (onSelectedListener != null) {
            final LocalItem selectedItem = playlists.get(position);

            if (selectedItem instanceof PlaylistMetadataEntry) {
                final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                onSelectedListener.onLocalPlaylistSelected(entry.uid, entry.name);

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                onSelectedListener.onRemotePlaylistSelected(
                        entry.getServiceId(), entry.getUrl(), entry.getName());
            }
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedListener {
        void onLocalPlaylistSelected(long id, String name);
        void onRemotePlaylistSelected(int serviceId, String url, String name);
    }

    private class SelectPlaylistAdapter
            extends RecyclerView.Adapter<SelectPlaylistAdapter.SelectPlaylistItemHolder> {
        @NonNull
        @Override
        public SelectPlaylistItemHolder onCreateViewHolder(final ViewGroup parent,
                                                           final int viewType) {
            final View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_playlist_mini_item, parent, false);
            return new SelectPlaylistItemHolder(item);
        }

        @Override
        public void onBindViewHolder(@NonNull final SelectPlaylistItemHolder holder,
                                     final int position) {
            final PlaylistLocalItem selectedItem = playlists.get(position);

            if (selectedItem instanceof PlaylistMetadataEntry) {
                final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);

                holder.titleView.setText(entry.name);
                holder.view.setOnClickListener(view -> clickedItem(position));
                PicassoHelper.loadPlaylistThumbnail(entry.thumbnailUrl).into(holder.thumbnailView);

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);

                holder.titleView.setText(entry.getName());
                holder.view.setOnClickListener(view -> clickedItem(position));
                PicassoHelper.loadPlaylistThumbnail(entry.getThumbnailUrl())
                        .into(holder.thumbnailView);
            }
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

        public class SelectPlaylistItemHolder extends RecyclerView.ViewHolder {
            public final View view;
            final ImageView thumbnailView;
            final TextView titleView;

            SelectPlaylistItemHolder(final View v) {
                super(v);
                this.view = v;
                thumbnailView = v.findViewById(R.id.itemThumbnailView);
                titleView = v.findViewById(R.id.itemTitleView);
            }
        }
    }
}
