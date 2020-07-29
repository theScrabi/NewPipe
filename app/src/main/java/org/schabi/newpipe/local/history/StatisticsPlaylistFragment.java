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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.StreamDialogEntry;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import icepick.State;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class StatisticsPlaylistFragment
        extends BaseLocalListFragment<List<StreamStatisticsEntry>, Void> {
    private final CompositeDisposable disposables = new CompositeDisposable();
    @State
    Parcelable itemsListState;
    private StatisticSortMode sortMode = StatisticSortMode.LAST_PLAYED;
    private View headerPlayAllButton;
    private View headerPopupButton;
    private View headerBackgroundButton;
    private View playlistCtrl;
    private View sortButton;
    private ImageView sortButtonIcon;
    private TextView sortButtonText;
    /* Used for independent events */
    private Subscription databaseSubscription;
    private HistoryRecordManager recordManager;

    private List<StreamStatisticsEntry> processResult(final List<StreamStatisticsEntry> results) {
        switch (sortMode) {
            case LAST_PLAYED:
                Collections.sort(results, (left, right) ->
                        right.getLatestAccessDate().compareTo(left.getLatestAccessDate()));
                return results;
            case MOST_PLAYED:
                Collections.sort(results, (left, right) ->
                        Long.compare(right.getWatchCount(), left.getWatchCount()));
                return results;
            default:
                return null;
        }
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
    protected View getListHeader() {
        final View headerRootLayout = activity.getLayoutInflater()
                .inflate(R.layout.statistic_playlist_control, itemsList, false);
        playlistCtrl = headerRootLayout.findViewById(R.id.playlist_control);
        headerPlayAllButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_all_button);
        headerPopupButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_popup_button);
        headerBackgroundButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_bg_button);
        sortButton = headerRootLayout.findViewById(R.id.sortButton);
        sortButtonIcon = headerRootLayout.findViewById(R.id.sortButtonIcon);
        sortButtonText = headerRootLayout.findViewById(R.id.sortButtonText);
        return headerRootLayout;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_history_clear:
                new AlertDialog.Builder(activity)
                        .setTitle(R.string.delete_view_history_alert)
                        .setNegativeButton(R.string.cancel, ((dialog, which) -> dialog.dismiss()))
                        .setPositiveButton(R.string.delete, ((dialog, which) -> {
                            final Disposable onDelete = recordManager.deleteWholeStreamHistory()
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            howManyDeleted -> Toast.makeText(getContext(),
                                                    R.string.watch_history_deleted,
                                                    Toast.LENGTH_SHORT).show(),
                                            throwable -> ErrorActivity.reportError(getContext(),
                                                    throwable,
                                                    SettingsActivity.class, null,
                                                    ErrorActivity.ErrorInfo.make(
                                                            UserAction.DELETE_FROM_HISTORY,
                                                            "none",
                                                            "Delete view history",
                                                            R.string.general_error)));

                            final Disposable onClearOrphans = recordManager.removeOrphanedRecords()
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(
                                            howManyDeleted -> {
                                            },
                                            throwable -> ErrorActivity.reportError(getContext(),
                                                    throwable,
                                                    SettingsActivity.class, null,
                                                    ErrorActivity.ErrorInfo.make(
                                                            UserAction.DELETE_FROM_HISTORY,
                                                            "none",
                                                            "Delete search history",
                                                            R.string.general_error)));
                            disposables.add(onClearOrphans);
                            disposables.add(onDelete);
                        }))
                        .create()
                        .show();
                break;
            default:
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
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();
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
                StatisticsPlaylistFragment.this.onError(exception);
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

        playlistCtrl.setVisibility(View.VISIBLE);

        itemListAdapter.clearStreamItemList();

        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(processResult(result));
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }

        headerPlayAllButton.setOnClickListener(view ->
                NavigationHelper.playOnMainPlayer(activity, getPlayQueue(), false));
        headerPopupButton.setOnClickListener(view ->
                NavigationHelper.playOnPopupPlayer(activity, getPlayQueue(), false));
        headerBackgroundButton.setOnClickListener(view ->
                NavigationHelper.playOnBackgroundPlayer(activity, getPlayQueue(), false));
        sortButton.setOnClickListener(view -> toggleSortMode());

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
                "none", "History Statistics", R.string.general_error);
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void toggleSortMode() {
        if (sortMode == StatisticSortMode.LAST_PLAYED) {
            sortMode = StatisticSortMode.MOST_PLAYED;
            setTitle(getString(R.string.title_most_played));
            sortButtonIcon.setImageResource(
                ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_history));
            sortButtonText.setText(R.string.title_last_played);
        } else {
            sortMode = StatisticSortMode.LAST_PLAYED;
            setTitle(getString(R.string.title_last_played));
            sortButtonIcon.setImageResource(
                ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_filter_list));
            sortButtonText.setText(R.string.title_most_played);
        }
        startLoading(true);
    }

    private PlayQueue getPlayQueueStartingAt(final StreamStatisticsEntry infoItem) {
        return getPlayQueue(Math.max(itemListAdapter.getItemList().indexOf(infoItem), 0));
    }

    private void showStreamDialog(final StreamStatisticsEntry item) {
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
                    StreamDialogEntry.delete,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share);
        } else {
            StreamDialogEntry.setEnabledEntries(
                    StreamDialogEntry.enqueue_on_background,
                    StreamDialogEntry.enqueue_on_popup,
                    StreamDialogEntry.start_here_on_background,
                    StreamDialogEntry.start_here_on_popup,
                    StreamDialogEntry.delete,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share);

            StreamDialogEntry.start_here_on_popup.setCustomAction((fragment, infoItemDuplicate) ->
                    NavigationHelper
                            .playOnPopupPlayer(context, getPlayQueueStartingAt(item), true));
        }

        StreamDialogEntry.start_here_on_background.setCustomAction((fragment, infoItemDuplicate) ->
                NavigationHelper
                        .playOnBackgroundPlayer(context, getPlayQueueStartingAt(item), true));
        StreamDialogEntry.delete.setCustomAction((fragment, infoItemDuplicate) ->
                deleteEntry(Math.max(itemListAdapter.getItemList().indexOf(item), 0)));

        new InfoItemDialog(activity, infoItem, StreamDialogEntry.getCommands(context),
                (dialog, which) -> StreamDialogEntry.clickOn(which, this, infoItem)).show();
    }

    private void deleteEntry(final int index) {
        final Object item = itemListAdapter.getItemList().get(index);
        if (item instanceof StreamStatisticsEntry) {
            final StreamStatisticsEntry entry = (StreamStatisticsEntry) item;
            final Disposable onDelete = recordManager.deleteStreamHistory(entry.getStreamId())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            howManyDeleted -> {
                                if (getView() != null) {
                                    Snackbar.make(getView(), R.string.one_item_deleted,
                                            Snackbar.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(getContext(),
                                            R.string.one_item_deleted,
                                            Toast.LENGTH_SHORT).show();
                                }
                            },
                            throwable -> showSnackBarError(throwable,
                                    UserAction.DELETE_FROM_HISTORY, "none",
                                    "Deleting item failed", R.string.general_error));

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

        final List<Object> items = itemListAdapter.getItemList();
        List<StreamInfoItem> streamInfoItems = new ArrayList<>(items.size());
        for (final Object item : items) {
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

