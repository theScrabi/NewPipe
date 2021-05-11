package org.schabi.newpipe.local.history;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.snackbar.Snackbar;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.databinding.PlaylistControlBinding;
import org.schabi.newpipe.databinding.StatisticPlaylistControlBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.settings.HistorySettingsFragment;
import org.schabi.newpipe.util.KoreUtil;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.StreamDialogEntry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;

public class StatisticsPlaylistFragment
        extends BaseLocalListFragment<List<StreamStatisticsEntry>, Void> {
    private final CompositeDisposable disposables = new CompositeDisposable();
    @State
    Parcelable itemsListState;
    private StatisticSortMode sortMode = StatisticSortMode.LAST_PLAYED;

    private StatisticPlaylistControlBinding headerBinding;
    private PlaylistControlBinding playlistControlBinding;

    /* Used for independent events */
    private Subscription databaseSubscription;
    private HistoryRecordManager recordManager;

    private List<StreamStatisticsEntry> processResult(final List<StreamStatisticsEntry> results) {
        final Comparator<StreamStatisticsEntry> comparator;
        switch (sortMode) {
            case LAST_PLAYED:
                comparator = Comparator.comparing(StreamStatisticsEntry::getLatestAccessDate);
                break;
            case MOST_PLAYED:
                comparator = Comparator.comparingLong(StreamStatisticsEntry::getWatchCount);
                break;
            default:
                return null;
        }
        Collections.sort(results, comparator.reversed());
        return results;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        recordManager = new HistoryRecordManager(getContext());
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playlist, container, false);
    }

    @Override
    public void setUserVisibleHint(final boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (activity != null && isVisibleToUser) {
            setTitle(activity.getString(R.string.title_activity_history));
        }
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_history, menu);
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        if (!useAsFrontPage) {
            setTitle(getString(R.string.title_last_played));
        }
    }

    @Override
    protected ViewBinding getListHeader() {
        headerBinding = StatisticPlaylistControlBinding.inflate(activity.getLayoutInflater(),
                itemsList, false);
        playlistControlBinding = headerBinding.playlistControl;

        return headerBinding;
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        itemListAdapter.setSelectedListener(new OnClickGesture<LocalItem>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                if (selectedItem instanceof StreamStatisticsEntry) {
                    final StreamEntity item =
                            ((StreamStatisticsEntry) selectedItem).getStreamEntity();
                    NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                            item.getServiceId(), item.getUrl(), item.getTitle(), null, false);
                }
            }

            @Override
            public void held(final LocalItem selectedItem) {
                if (selectedItem instanceof StreamStatisticsEntry) {
                    showStreamDialog((StreamStatisticsEntry) selectedItem);
                }
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_history_clear) {
            HistorySettingsFragment
                    .openDeleteWatchHistoryDialog(requireContext(), recordManager, disposables);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);
        recordManager.getStreamStatistics()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getHistoryObserver());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = Objects.requireNonNull(itemsList.getLayoutManager()).onSaveInstanceState();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (itemListAdapter != null) {
            itemListAdapter.unsetSelectedListener();
        }
        if (playlistControlBinding != null) {
            playlistControlBinding.playlistCtrlPlayBgButton.setOnClickListener(null);
            playlistControlBinding.playlistCtrlPlayAllButton.setOnClickListener(null);
            playlistControlBinding.playlistCtrlPlayPopupButton.setOnClickListener(null);

            headerBinding = null;
            playlistControlBinding = null;
        }

        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }
        databaseSubscription = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        recordManager = null;
        itemsListState = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Statistics Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<StreamStatisticsEntry>> getHistoryObserver() {
        return new Subscriber<List<StreamStatisticsEntry>>() {
            @Override
            public void onSubscribe(final Subscription s) {
                showLoading();

                if (databaseSubscription != null) {
                    databaseSubscription.cancel();
                }
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(final List<StreamStatisticsEntry> streams) {
                handleResult(streams);
                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                showError(
                        new ErrorInfo(exception, UserAction.SOMETHING_ELSE, "History Statistics"));
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull final List<StreamStatisticsEntry> result) {
        super.handleResult(result);
        if (itemListAdapter == null) {
            return;
        }

        playlistControlBinding.getRoot().setVisibility(View.VISIBLE);

        itemListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(processResult(result));
        if (itemsListState != null && itemsList.getLayoutManager() != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }

        playlistControlBinding.playlistCtrlPlayAllButton.setOnClickListener(view ->
                NavigationHelper.playOnMainPlayer(activity, getPlayQueue()));
        playlistControlBinding.playlistCtrlPlayPopupButton.setOnClickListener(view ->
                NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(), false));
        playlistControlBinding.playlistCtrlPlayBgButton.setOnClickListener(view ->
                NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue(), false));
        headerBinding.sortButton.setOnClickListener(view -> toggleSortMode());

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

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void toggleSortMode() {
        if (sortMode == StatisticSortMode.LAST_PLAYED) {
            sortMode = StatisticSortMode.MOST_PLAYED;
            setTitle(getString(R.string.title_most_played));
            headerBinding.sortButtonIcon.setImageResource(R.drawable.ic_history);
            headerBinding.sortButtonText.setText(R.string.title_last_played);
        } else {
            sortMode = StatisticSortMode.LAST_PLAYED;
            setTitle(getString(R.string.title_last_played));
            headerBinding.sortButtonIcon.setImageResource(
                R.drawable.ic_filter_list);
            headerBinding.sortButtonText.setText(R.string.title_most_played);
        }
        startLoading(true);
    }

    private PlayQueue getPlayQueueStartingAt(final StreamStatisticsEntry infoItem) {
        return getPlayQueue(Math.max(itemListAdapter.getItemsList().indexOf(infoItem), 0));
    }

    private void showStreamDialog(final StreamStatisticsEntry item) {
        final Context context = getContext();
        final Activity activity = getActivity();
        if (context == null || context.getResources() == null || activity == null) {
            return;
        }
        final StreamInfoItem infoItem = item.toStreamInfoItem();

        final ArrayList<StreamDialogEntry> entries = new ArrayList<>();

        if (PlayerHolder.getType() != null) {
            entries.add(StreamDialogEntry.enqueue);
        }
        if (infoItem.getStreamType() == StreamType.AUDIO_STREAM) {
            entries.addAll(Arrays.asList(
                    StreamDialogEntry.start_here_on_background,
                    StreamDialogEntry.delete,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share
            ));
        } else  {
            entries.addAll(Arrays.asList(
                    StreamDialogEntry.start_here_on_background,
                    StreamDialogEntry.start_here_on_popup,
                    StreamDialogEntry.delete,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share
            ));
        }
        if (KoreUtil.shouldShowPlayWithKodi(context, infoItem.getServiceId())) {
            entries.add(StreamDialogEntry.play_with_kodi);
        }
        StreamDialogEntry.setEnabledEntries(entries);

        StreamDialogEntry.start_here_on_background.setCustomAction((fragment, infoItemDuplicate) ->
                NavigationHelper
                        .playOnBackgroundPlayer(context, getPlayQueueStartingAt(item), true));
        StreamDialogEntry.delete.setCustomAction((fragment, infoItemDuplicate) ->
                deleteEntry(Math.max(itemListAdapter.getItemsList().indexOf(item), 0)));

        new InfoItemDialog(activity, infoItem, StreamDialogEntry.getCommands(context),
                (dialog, which) -> StreamDialogEntry.clickOn(which, this, infoItem)).show();
    }

    private void deleteEntry(final int index) {
        final LocalItem infoItem = itemListAdapter.getItemsList().get(index);
        if (infoItem instanceof StreamStatisticsEntry) {
            final StreamStatisticsEntry entry = (StreamStatisticsEntry) infoItem;
            final Disposable onDelete = recordManager
                    .deleteStreamHistoryAndState(entry.getStreamId())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            () -> {
                                if (getView() != null) {
                                    Snackbar.make(getView(), R.string.one_item_deleted,
                                            Snackbar.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getContext(),
                                            R.string.one_item_deleted,
                                            Toast.LENGTH_SHORT).show();
                                }
                            },
                            throwable -> showSnackBarError(new ErrorInfo(throwable,
                                    UserAction.DELETE_FROM_HISTORY, "Deleting item")));

            disposables.add(onDelete);
        }
    }

    private PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        if (itemListAdapter == null) {
            return new SinglePlayQueue(Collections.emptyList(), 0);
        }

        final List<LocalItem> infoItems = itemListAdapter.getItemsList();
        final List<StreamInfoItem> streamInfoItems = new ArrayList<>(infoItems.size());
        for (final LocalItem item : infoItems) {
            if (item instanceof StreamStatisticsEntry) {
                streamInfoItems.add(((StreamStatisticsEntry) item).toStreamInfoItem());
            }
        }
        return new SinglePlayQueue(streamInfoItems, index);
    }

    private enum StatisticSortMode {
        LAST_PLAYED,
        MOST_PLAYED,
    }
}

