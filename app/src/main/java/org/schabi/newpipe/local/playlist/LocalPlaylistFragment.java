package org.schabi.newpipe.local.playlist;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.history.model.StreamHistoryEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.stream.model.StreamStateEntity;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.StreamDialogEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import icepick.State;
import io.reactivex.Flowable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.disposables.Disposables;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class LocalPlaylistFragment extends BaseLocalListFragment<List<PlaylistStreamEntry>, Void> {
    // Save the list 10 seconds after the last change occurred
    private static final long SAVE_DEBOUNCE_MILLIS = 10000;
    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 12;

    @State
    protected Long playlistId;
    @State
    protected String name;
    @State
    Parcelable itemsListState;

    private View headerRootLayout;
    private TextView headerTitleView;
    private TextView headerStreamCount;
    private View playlistControl;
    private View headerPlayAllButton;
    private View headerPopupButton;
    private View headerBackgroundButton;

    private ItemTouchHelper itemTouchHelper;

    private LocalPlaylistManager playlistManager;
    private Subscription databaseSubscription;

    private PublishSubject<Long> debouncedSaveSignal;
    private CompositeDisposable disposables;

    /* Has the playlist been fully loaded from db */
    private AtomicBoolean isLoadingComplete;
    /* Has the playlist been modified (e.g. items reordered or deleted) */
    private AtomicBoolean isModified;
    /* Is the playlist currently being processed to remove watched videos */
    private boolean isRemovingWatched = false;

    public static LocalPlaylistFragment getInstance(final long playlistId, final String name) {
        LocalPlaylistFragment instance = new LocalPlaylistFragment();
        instance.setInitialData(playlistId, name);
        return instance;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        playlistManager = new LocalPlaylistManager(NewPipeDatabase.getInstance(requireContext()));
        debouncedSaveSignal = PublishSubject.create();

        disposables = new CompositeDisposable();

        isLoadingComplete = new AtomicBoolean();
        isModified = new AtomicBoolean();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);

        if (headerTitleView != null) {
            headerTitleView.setText(title);
        }
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        setTitle(name);
    }

    @Override
    protected View getListHeader() {
        headerRootLayout = activity.getLayoutInflater()
                .inflate(R.layout.local_playlist_header, itemsList, false);

        headerTitleView = headerRootLayout.findViewById(R.id.playlist_title_view);
        headerTitleView.setSelected(true);

        headerStreamCount = headerRootLayout.findViewById(R.id.playlist_stream_count);

        playlistControl = headerRootLayout.findViewById(R.id.playlist_control);
        headerPlayAllButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_all_button);
        headerPopupButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_popup_button);
        headerBackgroundButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_bg_button);

        return headerRootLayout;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        headerTitleView.setOnClickListener(view -> createRenameDialog());

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        itemListAdapter.setOnItemSelectedListener(new OnClickGesture<Object>() {

            @Override
            public void drag(final Object selectedItem,
                             final RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void showLoading() {
        super.showLoading();
        if (headerRootLayout != null) {
            animateView(headerRootLayout, false, 200);
        }
        if (playlistControl != null) {
            animateView(playlistControl, false, 200);
        }
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        if (headerRootLayout != null) {
            animateView(headerRootLayout, true, 200);
        }
        if (playlistControl != null) {
            animateView(playlistControl, true, 200);
        }
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        if (disposables == null) {
            disposables = new CompositeDisposable();
        } else {
            disposables.clear();
        }
        disposables.add(getDebouncedSaver());

        isLoadingComplete.set(false);
        isModified.set(false);

        playlistManager.getPlaylistStreams(playlistId)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistObserver());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Lifecycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();

        // Save on exit
        saveImmediate();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_local_playlist, menu);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (itemListAdapter != null) {
            itemListAdapter.setOnItemSelectedListener(null);
        }
        if (headerBackgroundButton != null) {
            headerBackgroundButton.setOnClickListener(null);
        }
        if (headerPlayAllButton != null) {
            headerPlayAllButton.setOnClickListener(null);
        }
        if (headerPopupButton != null) {
            headerPopupButton.setOnClickListener(null);
        }

        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }
        if (disposables != null) {
            disposables.clear();
        }

        databaseSubscription = null;
        itemTouchHelper = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (debouncedSaveSignal != null) {
            debouncedSaveSignal.onComplete();
        }
        if (disposables != null) {
            disposables.dispose();
        }

        debouncedSaveSignal = null;
        playlistManager = null;
        disposables = null;

        isLoadingComplete = null;
        isModified = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Playlist Stream Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistStreamEntry>> getPlaylistObserver() {
        return new Subscriber<List<PlaylistStreamEntry>>() {
            @Override
            public void onSubscribe(final Subscription s) {
                showLoading();
                isLoadingComplete.set(false);

                if (databaseSubscription != null) {
                    databaseSubscription.cancel();
                }
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(final List<PlaylistStreamEntry> streams) {
                // Skip handling the result after it has been modified
                if (isModified == null || !isModified.get()) {
                    handleResult(streams);
                    isLoadingComplete.set(true);
                }

                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                LocalPlaylistFragment.this.onError(exception);
            }

            @Override
            public void onComplete() { }
        };
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_remove_watched:
                if (!isRemovingWatched) {
                    new AlertDialog.Builder(requireContext())
                            .setMessage(R.string.remove_watched_popup_warning)
                            .setTitle(R.string.remove_watched_popup_title)
                            .setPositiveButton(R.string.yes,
                                    (DialogInterface d, int id) -> removeWatchedStreams(false))
                            .setNeutralButton(
                                    R.string.remove_watched_popup_yes_and_partially_watched_videos,
                                    (DialogInterface d, int id) -> removeWatchedStreams(true))
                            .setNegativeButton(R.string.cancel,
                                    (DialogInterface d, int id) -> d.cancel())
                            .create()
                            .show();
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    public void removeWatchedStreams(final boolean removePartiallyWatched) {
        if (isRemovingWatched) {
            return;
        }
        isRemovingWatched = true;
        showLoading();

        disposables.add(playlistManager.getPlaylistStreams(playlistId)
                .subscribeOn(Schedulers.io())
                .map((List<PlaylistStreamEntry> playlist) -> {
                    // Playlist data
                    final Iterator<PlaylistStreamEntry> playlistIter = playlist.iterator();

                    // History data
                    final HistoryRecordManager recordManager
                            = new HistoryRecordManager(getContext());
                    final Iterator<StreamHistoryEntry> historyIter = recordManager
                            .getStreamHistorySortedById().blockingFirst().iterator();

                    // Remove Watched, Functionality data
                    final List<PlaylistStreamEntry> notWatchedItems = new ArrayList<>();
                    boolean thumbnailVideoRemoved = false;

                    // already sorted by ^ getStreamHistorySortedById(), binary search can be used
                    final ArrayList<Long> historyStreamIds = new ArrayList<>();
                    while (historyIter.hasNext()) {
                        historyStreamIds.add(historyIter.next().getStreamId());
                    }

                    if (removePartiallyWatched) {
                        while (playlistIter.hasNext()) {
                            final PlaylistStreamEntry playlistItem = playlistIter.next();
                            int indexInHistory = Collections.binarySearch(historyStreamIds,
                                    playlistItem.getStreamId());

                            if (indexInHistory < 0) {
                                notWatchedItems.add(playlistItem);
                            } else if (!thumbnailVideoRemoved
                                    && playlistManager.getPlaylistThumbnail(playlistId)
                                    .equals(playlistItem.getStreamEntity().getThumbnailUrl())) {
                                thumbnailVideoRemoved = true;
                            }
                        }
                    } else {
                        final Iterator<StreamStateEntity> streamStatesIter = recordManager
                                .loadLocalStreamStateBatch(playlist).blockingGet().iterator();

                        while (playlistIter.hasNext()) {
                            PlaylistStreamEntry playlistItem = playlistIter.next();
                            final int indexInHistory = Collections.binarySearch(historyStreamIds,
                                    playlistItem.getStreamId());

                            final boolean hasState = streamStatesIter.next() != null;
                            if (indexInHistory < 0 ||  hasState) {
                                notWatchedItems.add(playlistItem);
                            } else if (!thumbnailVideoRemoved
                                    && playlistManager.getPlaylistThumbnail(playlistId)
                                    .equals(playlistItem.getStreamEntity().getThumbnailUrl())) {
                                thumbnailVideoRemoved = true;
                            }
                        }
                    }

                    return Flowable.just(notWatchedItems, thumbnailVideoRemoved);
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(flow -> {
                    final List<PlaylistStreamEntry> notWatchedItems =
                            (List<PlaylistStreamEntry>) flow.blockingFirst();
                    final boolean thumbnailVideoRemoved = (Boolean) flow.blockingLast();

                    itemListAdapter.clearStreamItemList();
                    itemListAdapter.addItems(notWatchedItems);
                    saveChanges();


                    if (thumbnailVideoRemoved) {
                        updateThumbnailUrl();
                    }

                    final long videoCount = itemListAdapter.getItemList().size();
                    setVideoCount(videoCount);
                    if (videoCount == 0) {
                        showEmptyState();
                    }

                    hideLoading();
                    isRemovingWatched = false;
                }, this::onError));
    }

    @Override
    public void handleResult(@NonNull final List<PlaylistStreamEntry> result) {
        super.handleResult(result);
        if (itemListAdapter == null) {
            return;
        }

        itemListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(result);
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        setVideoCount(itemListAdapter.getItemList().size());

        headerPlayAllButton.setOnClickListener(view ->
                NavigationHelper.playOnMainPlayer(activity, getPlayQueue(), false));
        headerPopupButton.setOnClickListener(view ->
                NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(), false));
        headerBackgroundButton.setOnClickListener(view ->
                NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue(), false));

        headerPopupButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPopupPlayer(activity, getPlayQueue(), true);
            return true;
        });

        headerBackgroundButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, getPlayQueue(), true);
            return true;
        });

        hideLoading();
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }
    }

    @Override
    protected boolean onError(final Throwable exception) {
        if (super.onError(exception)) {
            return true;
        }

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE,
                "none", "Local Playlist", R.string.general_error);
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata/Streams Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    private void createRenameDialog() {
        if (playlistId == null || name == null || getContext() == null) {
            return;
        }

        final View dialogView = View.inflate(getContext(), R.layout.dialog_playlist_name, null);
        EditText nameEdit = dialogView.findViewById(R.id.playlist_name);
        nameEdit.setText(name);
        nameEdit.setSelection(nameEdit.getText().length());

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.rename_playlist)
                .setView(dialogView)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.rename, (dialogInterface, i) -> {
                    changePlaylistName(nameEdit.getText().toString());
                });

        dialogBuilder.show();
    }

    private void changePlaylistName(final String title) {
        if (playlistManager == null) {
            return;
        }

        this.name = title;
        setTitle(title);

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with new title=[" + title + "] items");
        }

        final Disposable disposable = playlistManager.renamePlaylist(playlistId, title)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> { /*Do nothing on success*/ }, this::onError);
        disposables.add(disposable);
    }

    private void changeThumbnailUrl(final String thumbnailUrl) {
        if (playlistManager == null) {
            return;
        }

        final Toast successToast = Toast.makeText(getActivity(),
                R.string.playlist_thumbnail_change_success,
                Toast.LENGTH_SHORT);

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with new thumbnail url=[" + thumbnailUrl + "]");
        }

        final Disposable disposable = playlistManager
                .changePlaylistThumbnail(playlistId, thumbnailUrl)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignore -> successToast.show(), this::onError);
        disposables.add(disposable);
    }

    private void updateThumbnailUrl() {
        String newThumbnailUrl;

        if (!itemListAdapter.getItemList().isEmpty()) {
            newThumbnailUrl = ((PlaylistStreamEntry) itemListAdapter.getItemList().get(0))
                    .getStreamEntity().getThumbnailUrl();
        } else {
            newThumbnailUrl = "drawable://" + R.drawable.dummy_thumbnail_playlist;
        }

        changeThumbnailUrl(newThumbnailUrl);
    }

    private void deleteItem(final PlaylistStreamEntry item) {
        if (itemListAdapter == null) {
            return;
        }

        itemListAdapter.removeItem(item);
        if (playlistManager.getPlaylistThumbnail(playlistId)
                .equals(item.getStreamEntity().getThumbnailUrl())) {
            updateThumbnailUrl();
        }

        setVideoCount(itemListAdapter.getItemList().size());
        saveChanges();
    }

    private void saveChanges() {
        if (isModified == null || debouncedSaveSignal == null) {
            return;
        }

        isModified.set(true);
        debouncedSaveSignal.onNext(System.currentTimeMillis());
    }

    private Disposable getDebouncedSaver() {
        if (debouncedSaveSignal == null) {
            return Disposables.empty();
        }

        return debouncedSaveSignal
                .debounce(SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(ignored -> saveImmediate(), this::onError);
    }

    private void saveImmediate() {
        if (playlistManager == null || itemListAdapter == null) {
            return;
        }

        // List must be loaded and modified in order to save
        if (isLoadingComplete == null || isModified == null
                || !isLoadingComplete.get() || !isModified.get()) {
            Log.w(TAG, "Attempting to save playlist when local playlist "
                    + "is not loaded or not modified: playlist id=[" + playlistId + "]");
            return;
        }

        final List<Object> items = itemListAdapter.getItemList();
        List<Long> streamIds = new ArrayList<>(items.size());
        for (final Object item : items) {
            if (item instanceof PlaylistStreamEntry) {
                streamIds.add(((PlaylistStreamEntry) item).getStreamId());
            }
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + playlistId + "] "
                    + "with [" + streamIds.size() + "] items");
        }

        final Disposable disposable = playlistManager.updateJoin(playlistId, streamIds)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        () -> {
                            if (isModified != null) {
                                isModified.set(false);
                            }
                        },
                        this::onError
                );
        disposables.add(disposable);
    }


    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        int directions = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        if (isGridLayout()) {
            directions |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        }
        return new ItemTouchHelper.SimpleCallback(directions,
                ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public int interpolateOutOfBoundsScroll(final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView,
                        viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(MINIMUM_INITIAL_DRAG_VELOCITY,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(final RecyclerView recyclerView,
                                  final RecyclerView.ViewHolder source,
                                  final RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()
                        || itemListAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                final boolean isSwapped = itemListAdapter.swapItems(sourceIndex, targetIndex);
                if (isSwapped) {
                    saveChanges();
                }
                return isSwapped;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int swipeDir) { }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private PlayQueue getPlayQueueStartingAt(final PlaylistStreamEntry infoItem) {
        return getPlayQueue(Math.max(itemListAdapter.getItemList().indexOf(infoItem), 0));
    }

    protected void showStreamItemDialog(final PlaylistStreamEntry item) {
        final Context context = getContext();
        final Activity activity = getActivity();
        if (context == null || context.getResources() == null || activity == null) {
            return;
        }
        final StreamInfoItem infoItem = item.toStreamInfoItem();

        if (infoItem.getStreamType() == StreamType.AUDIO_STREAM) {
            StreamDialogEntry.setEnabledEntries(
                    StreamDialogEntry.enqueue_on_background,
                    StreamDialogEntry.start_here_on_background,
                    StreamDialogEntry.set_as_playlist_thumbnail,
                    StreamDialogEntry.delete,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share);
        } else {
            StreamDialogEntry.setEnabledEntries(
                    StreamDialogEntry.enqueue_on_background,
                    StreamDialogEntry.enqueue_on_popup,
                    StreamDialogEntry.start_here_on_background,
                    StreamDialogEntry.start_here_on_popup,
                    StreamDialogEntry.set_as_playlist_thumbnail,
                    StreamDialogEntry.delete,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share);

            StreamDialogEntry.start_here_on_popup.setCustomAction(
                    (fragment, infoItemDuplicate) -> NavigationHelper.
                            playOnPopupPlayer(context, getPlayQueueStartingAt(item), true));
        }

        StreamDialogEntry.start_here_on_background.setCustomAction((fragment, infoItemDuplicate) ->
                NavigationHelper.playOnBackgroundPlayer(context,
                        getPlayQueueStartingAt(item), true));
        StreamDialogEntry.set_as_playlist_thumbnail.setCustomAction(
                (fragment, infoItemDuplicate) ->
                        changeThumbnailUrl(item.getStreamEntity().getThumbnailUrl()));
        StreamDialogEntry.delete.setCustomAction((fragment, infoItemDuplicate) ->
                deleteItem(item));

        new InfoItemDialog(activity, infoItem, StreamDialogEntry.getCommands(context),
                (dialog, which) -> StreamDialogEntry.clickOn(which, this, infoItem)).show();
    }

    private void setInitialData(final long pid, final String title) {
        this.playlistId = pid;
        this.name = !TextUtils.isEmpty(title) ? title : "";
    }

    private void setVideoCount(final long count) {
        if (activity != null && headerStreamCount != null) {
            headerStreamCount.setText(Localization.localizeStreamCount(activity, count));
        }
    }

    private PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        if (itemListAdapter == null) {
            return new SinglePlayQueue(Collections.emptyList(), 0);
        }

        final List<Object> items = itemListAdapter.getItemList();
        List<StreamInfoItem> streamInfoItems = new ArrayList<>(items.size());
        for (final Object item : items) {
            if (item instanceof PlaylistStreamEntry) {
                streamInfoItems.add(((PlaylistStreamEntry) item).toStreamInfoItem());
            }
        }
        return new SinglePlayQueue(streamInfoItems, index);
    }
}

