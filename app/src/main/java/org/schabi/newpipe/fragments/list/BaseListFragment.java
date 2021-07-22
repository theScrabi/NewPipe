package org.schabi.newpipe.fragments.list;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PignateFooterBinding;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.fragments.BaseStateFragment;
import org.schabi.newpipe.fragments.OnScrollBelowItemsListener;
import org.schabi.newpipe.info_list.InfoItemDialog;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.player.helper.PlayerHolder;
import org.schabi.newpipe.util.external_communication.KoreUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.StreamDialogEntry;
import org.schabi.newpipe.views.SuperScrollLayoutManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;
import static org.schabi.newpipe.ktx.ViewUtils.animate;
import static org.schabi.newpipe.ktx.ViewUtils.animateHideRecyclerViewAllowingScrolling;

public abstract class BaseListFragment<I, N> extends BaseStateFragment<I>
        implements ListViewContract<I, N>, StateSaver.WriteRead,
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final int LIST_MODE_UPDATE_FLAG = 0x32;
    protected org.schabi.newpipe.util.SavedState savedState;

    private boolean useDefaultStateSaving = true;
    private int updateFlags = 0;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    protected InfoListAdapter infoListAdapter;
    protected RecyclerView itemsList;
    private int focusedPosition = -1;

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);

        if (infoListAdapter == null) {
            infoListAdapter = new InfoListAdapter(activity);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        PreferenceManager.getDefaultSharedPreferences(activity)
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (useDefaultStateSaving) {
            StateSaver.onDestroy(savedState);
        }
        PreferenceManager.getDefaultSharedPreferences(activity)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (updateFlags != 0) {
            if ((updateFlags & LIST_MODE_UPDATE_FLAG) != 0) {
                final boolean useGrid = isGridLayout();
                itemsList.setLayoutManager(useGrid
                        ? getGridLayoutManager() : getListLayoutManager());
                infoListAdapter.setUseGridVariant(useGrid);
                infoListAdapter.notifyDataSetChanged();
            }
            updateFlags = 0;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * If the default implementation of {@link StateSaver.WriteRead} should be used.
     *
     * @param useDefaultStateSaving Whether the default implementation should be used
     * @see StateSaver
     */
    public void setUseDefaultStateSaving(final boolean useDefaultStateSaving) {
        this.useDefaultStateSaving = useDefaultStateSaving;
    }

    @Override
    public String generateSuffix() {
        // Naive solution, but it's good for now (the items don't change)
        return "." + infoListAdapter.getItemsList().size() + ".list";
    }

    private int getFocusedPosition() {
        try {
            final View focusedItem = itemsList.getFocusedChild();
            final RecyclerView.ViewHolder itemHolder =
                    itemsList.findContainingViewHolder(focusedItem);
            return itemHolder.getAdapterPosition();
        } catch (final NullPointerException e) {
            return -1;
        }
    }

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        if (!useDefaultStateSaving) {
            return;
        }

        objectsToSave.add(infoListAdapter.getItemsList());
        objectsToSave.add(getFocusedPosition());
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        if (!useDefaultStateSaving) {
            return;
        }

        infoListAdapter.getItemsList().clear();
        infoListAdapter.getItemsList().addAll((List<InfoItem>) savedObjects.poll());
        restoreFocus((Integer) savedObjects.poll());
    }

    private void restoreFocus(final Integer position) {
        if (position == null || position < 0) {
            return;
        }

        itemsList.post(() -> {
            final RecyclerView.ViewHolder focusedHolder =
                    itemsList.findViewHolderForAdapterPosition(position);

            if (focusedHolder != null) {
                focusedHolder.itemView.requestFocus();
            }
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle bundle) {
        super.onSaveInstanceState(bundle);
        if (useDefaultStateSaving) {
            savedState = StateSaver
                    .tryToSave(activity.isChangingConfigurations(), savedState, bundle, this);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle bundle) {
        super.onRestoreInstanceState(bundle);
        if (useDefaultStateSaving) {
            savedState = StateSaver.tryToRestore(bundle, this);
        }
    }

    @Override
    public void onStop() {
        focusedPosition = getFocusedPosition();
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        restoreFocus(focusedPosition);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Nullable
    protected ViewBinding getListHeader() {
        return null;
    }

    protected ViewBinding getListFooter() {
        return PignateFooterBinding.inflate(activity.getLayoutInflater(), itemsList, false);
    }

    protected RecyclerView.LayoutManager getListLayoutManager() {
        return new SuperScrollLayoutManager(activity);
    }

    protected RecyclerView.LayoutManager getGridLayoutManager() {
        final Resources resources = activity.getResources();
        int width = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width);
        width += (24 * resources.getDisplayMetrics().density);
        final int spanCount = (int) Math.floor(resources.getDisplayMetrics().widthPixels
                / (double) width);
        final GridLayoutManager lm = new GridLayoutManager(activity, spanCount);
        lm.setSpanSizeLookup(infoListAdapter.getSpanSizeLookup(spanCount));
        return lm;
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        final boolean useGrid = isGridLayout();
        itemsList = rootView.findViewById(R.id.items_list);
        itemsList.setLayoutManager(useGrid ? getGridLayoutManager() : getListLayoutManager());

        infoListAdapter.setUseGridVariant(useGrid);
        infoListAdapter.setFooter(getListFooter().getRoot());

        final ViewBinding listHeader = getListHeader();
        if (listHeader != null) {
            infoListAdapter.setHeader(listHeader.getRoot());
        }

        itemsList.setAdapter(infoListAdapter);
    }

    protected void onItemSelected(final InfoItem selectedItem) {
        if (DEBUG) {
            Log.d(TAG, "onItemSelected() called with: selectedItem = [" + selectedItem + "]");
        }
    }

    @Override
    protected void initListeners() {
        super.initListeners();
        infoListAdapter.setOnStreamSelectedListener(new OnClickGesture<StreamInfoItem>() {
            @Override
            public void selected(final StreamInfoItem selectedItem) {
                onStreamSelected(selectedItem);
            }

            @Override
            public void held(final StreamInfoItem selectedItem) {
                showStreamDialog(selectedItem);
            }
        });

        infoListAdapter.setOnChannelSelectedListener(new OnClickGesture<ChannelInfoItem>() {
            @Override
            public void selected(final ChannelInfoItem selectedItem) {
                try {
                    onItemSelected(selectedItem);
                    NavigationHelper.openChannelFragment(getFM(),
                            selectedItem.getServiceId(),
                            selectedItem.getUrl(),
                            selectedItem.getName());
                } catch (final Exception e) {
                    ErrorActivity.reportUiErrorInSnackbar(
                            BaseListFragment.this, "Opening channel fragment", e);
                }
            }
        });

        infoListAdapter.setOnPlaylistSelectedListener(new OnClickGesture<PlaylistInfoItem>() {
            @Override
            public void selected(final PlaylistInfoItem selectedItem) {
                try {
                    onItemSelected(selectedItem);
                    NavigationHelper.openPlaylistFragment(getFM(),
                            selectedItem.getServiceId(),
                            selectedItem.getUrl(),
                            selectedItem.getName());
                } catch (final Exception e) {
                    ErrorActivity.reportUiErrorInSnackbar(BaseListFragment.this,
                            "Opening playlist fragment", e);
                }
            }
        });

        infoListAdapter.setOnCommentsSelectedListener(new OnClickGesture<CommentsInfoItem>() {
            @Override
            public void selected(final CommentsInfoItem selectedItem) {
                onItemSelected(selectedItem);
            }
        });

        itemsList.clearOnScrollListeners();
        itemsList.addOnScrollListener(new OnScrollBelowItemsListener() {
            @Override
            public void onScrolledDown(final RecyclerView recyclerView) {
                onScrollToBottom();
            }
        });
    }

    private void onStreamSelected(final StreamInfoItem selectedItem) {
        onItemSelected(selectedItem);
        NavigationHelper.openVideoDetailFragment(requireContext(), getFM(),
                selectedItem.getServiceId(), selectedItem.getUrl(), selectedItem.getName(),
                null, false);
    }

    protected void onScrollToBottom() {
        if (hasMoreItems() && !isLoading.get()) {
            loadMoreItems();
        }
    }

    protected void showStreamDialog(final StreamInfoItem item) {
        final Context context = getContext();
        final Activity activity = getActivity();
        if (context == null || context.getResources() == null || activity == null) {
            return;
        }

        final List<StreamDialogEntry> entries = new ArrayList<>();

        if (PlayerHolder.getInstance().getType() != null) {
            entries.add(StreamDialogEntry.enqueue);
        }
        if (item.getStreamType() == StreamType.AUDIO_STREAM) {
            entries.addAll(Arrays.asList(
                    StreamDialogEntry.start_here_on_background,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share
            ));
        } else {
            entries.addAll(Arrays.asList(
                    StreamDialogEntry.start_here_on_background,
                    StreamDialogEntry.start_here_on_popup,
                    StreamDialogEntry.append_playlist,
                    StreamDialogEntry.share
            ));
        }
        entries.add(StreamDialogEntry.open_in_browser);
        entries.add(StreamDialogEntry.download);
        if (KoreUtils.shouldShowPlayWithKodi(context, item.getServiceId())) {
            entries.add(StreamDialogEntry.play_with_kodi);
        }
        if (!isNullOrEmpty(item.getUploaderUrl())) {
            entries.add(StreamDialogEntry.show_channel_details);
        }

        StreamDialogEntry.setEnabledEntries(entries);

        new InfoItemDialog(activity, item, StreamDialogEntry.getCommands(context),
                (dialog, which) -> StreamDialogEntry.clickOn(which, this, item)).show();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        super.onCreateOptionsMenu(menu, inflater);
        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true);
            supportActionBar.setDisplayHomeAsUpEnabled(!useAsFrontPage);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    protected abstract void loadMoreItems();

    protected abstract boolean hasMoreItems();

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();
        animateHideRecyclerViewAllowingScrolling(itemsList);
    }

    @Override
    public void hideLoading() {
        super.hideLoading();
        animate(itemsList, true, 300);
    }

    @Override
    public void showEmptyState() {
        super.showEmptyState();
        showListFooter(false);
        animateHideRecyclerViewAllowingScrolling(itemsList);
    }

    @Override
    public void showListFooter(final boolean show) {
        itemsList.post(() -> {
            if (infoListAdapter != null && itemsList != null) {
                infoListAdapter.showFooter(show);
            }
        });
    }

    @Override
    public void handleNextItems(final N result) {
        isLoading.set(false);
    }

    @Override
    public void handleError() {
        super.handleError();
        showListFooter(false);
        animateHideRecyclerViewAllowingScrolling(itemsList);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String key) {
        if (key.equals(getString(R.string.list_view_mode_key))) {
            updateFlags |= LIST_MODE_UPDATE_FLAG;
        }
    }

    protected boolean isGridLayout() {
        final String listMode = PreferenceManager.getDefaultSharedPreferences(activity)
                .getString(getString(R.string.list_view_mode_key),
                        getString(R.string.list_view_mode_value));
        if ("auto".equals(listMode)) {
            final Configuration configuration = getResources().getConfiguration();
            return configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                    && configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE);
        } else {
            return "grid".equals(listMode);
        }
    }
}
