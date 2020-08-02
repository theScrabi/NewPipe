package org.schabi.newpipe.fragments.list.playlist;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.StateSaver;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;

public class PlaylistDialog extends DialogFragment implements StateSaver.WriteRead {
    /**
     * This contains the base display options for images.
     */
    private static final DisplayImageOptions DISPLAY_IMAGE_OPTIONS
            = new DisplayImageOptions.Builder().cacheInMemory(true).build();


    public interface OnSelectedListener {
        void onLocalPlaylistSelected(PlaylistMetadataEntry localPlaylist);
        void onRemotePlaylistSelected(PlaylistRemoteEntity remotePlaylist);
    }


    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;

    protected CompositeDisposable dialogDisposables = new CompositeDisposable();
    private StateSaver.SavedState savedState;

    @Nullable private OnSelectedListener onSelectedListener = null;
    private LocalPlaylistManager localPlaylistManager;

    private List<PlaylistLocalItem> playlists = new ArrayList<>();
    @Nullable private List<StreamEntity> storedStreamEntities;
    private final boolean showRemotePlaylists;


    public PlaylistDialog(@Nullable final List<StreamEntity> storedStreamEntities,
                          final boolean showRemotePlaylists) {
        this.storedStreamEntities = storedStreamEntities;
        this.showRemotePlaylists = showRemotePlaylists;
    }

    @Nullable
    protected List<StreamEntity> getStoredStreamEntities() {
        return storedStreamEntities;
    }

    protected LocalPlaylistManager getLocalPlaylistManager() {
        return localPlaylistManager;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        savedState = StateSaver.tryToRestore(savedInstanceState, this);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View v =
                inflater.inflate(R.layout.dialog_playlists, container, false);
        recyclerView = v.findViewById(R.id.itemList);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        SelectPlaylistAdapter playlistAdapter = new SelectPlaylistAdapter();
        recyclerView.setAdapter(playlistAdapter);

        progressBar = v.findViewById(R.id.progressBar);
        emptyView = v.findViewById(R.id.emptyStateView);
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);

        final AppDatabase database = NewPipeDatabase.getInstance(requireContext());
        // save for usage in extending classes
        localPlaylistManager = new LocalPlaylistManager(database);

        if (showRemotePlaylists) {
            final RemotePlaylistManager remotePlaylistManager = new RemotePlaylistManager(database);
            dialogDisposables.add(Flowable.combineLatest(localPlaylistManager.getPlaylists(),
                    remotePlaylistManager.getPlaylists(), PlaylistLocalItem::merge)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::displayPlaylists, this::onError));
        } else {
            dialogDisposables.add(localPlaylistManager.getPlaylists()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((p) -> displayPlaylists(new ArrayList<>(p)), this::onError));
        }

        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        StateSaver.onDestroy(savedState);

        if (dialogDisposables != null) {
            dialogDisposables.dispose();
            dialogDisposables = null;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        final Dialog dialog = super.onCreateDialog(savedInstanceState);
        final Window window = dialog.getWindow();
        if (window != null) { // remove window title
            window.requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public String generateSuffix() {
        final int size = storedStreamEntities == null ? 0 : storedStreamEntities.size();
        return "." + size + ".list";
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        objectsToSave.add(playlists);
        objectsToSave.add(storedStreamEntities);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) {
        playlists = (List<PlaylistLocalItem>) savedObjects.poll();
        storedStreamEntities = (List<StreamEntity>) savedObjects.poll();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getActivity() != null) {
            savedState = StateSaver.tryToSave(getActivity().isChangingConfigurations(),
                    savedState, outState, this);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    public PlaylistDialog setOnSelectedListener(@Nullable final OnSelectedListener listener) {
        onSelectedListener = listener;
        return this;
    }

    private void clickedItem(final int position) {
        if (onSelectedListener != null) {
            final LocalItem selectedItem = playlists.get(position);

            if (selectedItem instanceof PlaylistMetadataEntry) {
                onSelectedListener.onLocalPlaylistSelected((PlaylistMetadataEntry) selectedItem);

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                onSelectedListener.onRemotePlaylistSelected((PlaylistRemoteEntity) selectedItem);
            }
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Item handling
    //////////////////////////////////////////////////////////////////////////*/

    private void displayPlaylists(final List<PlaylistLocalItem> newPlaylists) {
        this.playlists = newPlaylists;
        progressBar.setVisibility(View.GONE);
        if (newPlaylists.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        recyclerView.setVisibility(View.VISIBLE);

    }

    /*//////////////////////////////////////////////////////////////////////////
    // Error
    //////////////////////////////////////////////////////////////////////////*/

    protected void onError(final Throwable e) {
        final Activity activity = requireActivity();
        ErrorActivity.reportError(activity, e, activity.getClass(), null, ErrorActivity.ErrorInfo
                .make(UserAction.UI_ERROR, "none", "", R.string.app_ui_crash));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Adapter
    //////////////////////////////////////////////////////////////////////////*/

    private class SelectPlaylistAdapter
            extends RecyclerView.Adapter<SelectPlaylistItemHolder> {
        @Override
        @NonNull
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
                holder.updateItem(entry.name, "", entry.streamCount, entry.thumbnailUrl,
                        view -> clickedItem(position));

            } else if (selectedItem instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                holder.updateItem(entry.getName(), entry.getUploader(), entry.getStreamCount(),
                        entry.getThumbnailUrl(), view -> clickedItem(position));
            }
        }

        @Override
        public int getItemCount() {
            return playlists.size();
        }

    }

    public static class SelectPlaylistItemHolder extends RecyclerView.ViewHolder {
        private final View view;
        private final TextView titleView;
        private final TextView uploaderView;
        private final TextView streamCountView;
        private final ImageView thumbnailView;

        SelectPlaylistItemHolder(final View view) {
            super(view);
            this.view = view;
            titleView = view.findViewById(R.id.itemTitleView);
            uploaderView = view.findViewById(R.id.itemUploaderView);
            streamCountView = view.findViewById(R.id.itemStreamCountView);
            thumbnailView = view.findViewById(R.id.itemThumbnailView);
        }

        void updateItem(final String title,
                        final String uploader,
                        final long streamCount,
                        final String thumbnailUrl,
                        final View.OnClickListener onClickListener) {

            titleView.setText(title);
            uploaderView.setText(uploader);
            streamCountView.setText(Localization.localizeStreamCountMini(
                    streamCountView.getContext(), streamCount));
            ImageLoader.getInstance()
                    .displayImage(thumbnailUrl, thumbnailView, DISPLAY_IMAGE_OPTIONS);
            view.setOnClickListener(onClickListener);
        }
    }
}
