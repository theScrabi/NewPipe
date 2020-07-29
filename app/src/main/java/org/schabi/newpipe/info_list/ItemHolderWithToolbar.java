package org.schabi.newpipe.info_list;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.database.stream.StreamStatisticsEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.playlist.AppendPlaylistDialog;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue;
import org.schabi.newpipe.player.playqueue.LocalPlaylistPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.player.playqueue.PlaylistPlayQueue;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ShareUtils;

public abstract class ItemHolderWithToolbar<ItemType> extends ItemHolder {
    private static final String TAG = ItemHolderWithToolbar.class.getSimpleName();
    private static final int TOGGLE_ITEM_TOOLBAR_ANIMATION_DURATION = 70; // ms

    private static class ToolbarButtonsVisibility {
        private final boolean delete;
        private final boolean setAsPlaylistThumbnail;

        ToolbarButtonsVisibility(final boolean delete,
                                 final boolean setAsPlaylistThumbnail) {
            this.delete = delete;
            this.setAsPlaylistThumbnail = setAsPlaylistThumbnail;
        }
    }


    private final Class<ItemType> itemClass;
    @NonNull
    private LinearLayout itemToolbarView;
    private final ImageButton share;
    private final ImageButton addToPlaylist;
    private final ImageButton delete;
    private final ImageButton setAsPlaylistThumbnail;
    private final ImageButton download;
    private final ImageButton playBackground;
    private final ImageButton playPopup;
    private final ImageButton playMain;

    public ItemHolderWithToolbar(final Class<ItemType> itemClass,
                                 final ItemHandler itemHandler,
                                 final int layoutId,
                                 final ViewGroup parent) {

        super(itemHandler, layoutId, parent);
        this.itemClass = itemClass;

        itemToolbarView = itemView.findViewById(R.id.toolbarBelowItem);
        if (itemToolbarView == null) {
            itemToolbarView = itemView.findViewById(R.id.toolbarOverlayItem);
            if (itemToolbarView == null) {
                throw new IllegalArgumentException(
                        "getToolbarViewFromItem(): no item toolbars found for itemView");
            }
        }

        share = itemToolbarView.findViewById(R.id.shareToolbarButton);
        addToPlaylist = itemToolbarView.findViewById(R.id.addToPlaylistToolbarButton);
        delete = itemToolbarView.findViewById(R.id.deleteToolbarButton);
        setAsPlaylistThumbnail =
                itemToolbarView.findViewById(R.id.setAsPlaylistThumbnailToolbarButton);
        download = itemToolbarView.findViewById(R.id.downloadToolbarButton);
        playBackground = itemToolbarView.findViewById(R.id.playBackgroundToolbarButton);
        playPopup = itemToolbarView.findViewById(R.id.playPopupToolbarButton);
        playMain = itemToolbarView.findViewById(R.id.playMainToolbarButton);
    }


    public abstract void updateFromItem(ItemType item,
                                        HistoryRecordManager historyRecordManager);

    public void updateStateFromItem(final ItemType item,
                                    final HistoryRecordManager historyRecordManager) {
    }

    @Override
    public void updateFromObject(final Object itemObject,
                                 final HistoryRecordManager historyRecordManager) {
        resetItemToolbar();
        hideInvalidToolbarButtons(itemObject);

        itemView.setOnClickListener(view -> {
            if (itemHandler.getOnItemSelectedListener() != null) {
                itemHandler.getOnItemSelectedListener().selected(itemObject);
            }

            if (itemToolbarView.getVisibility() == View.VISIBLE) {
                onShowInfo(itemObject);
            } else {
                showItemToolbar(itemObject);
            }
        });

        itemView.setOnLongClickListener(view -> {
            if (itemHandler.getOnItemSelectedListener() != null) {
                itemHandler.getOnItemSelectedListener().held(itemObject);
            }
            return true;
        });

        if (itemClass.isAssignableFrom(itemObject.getClass())) {
            updateFromItem(itemClass.cast(itemObject), historyRecordManager);
        }
    }

    @Override
    public void updateStateFromObject(final Object itemObject,
                                      final HistoryRecordManager historyRecordManager) {
        if (itemClass.isAssignableFrom(itemObject.getClass())) {
            updateStateFromItem(itemClass.cast(itemObject), historyRecordManager);
        }
    }

    void hideInvalidToolbarButtons(final Object itemObject) {
        final ToolbarButtonsVisibility v;
        if (itemObject instanceof PlaylistInfoItem) {
            v = new ToolbarButtonsVisibility(false, false);
        } else if (itemObject instanceof ChannelInfoItem) {
            v = new ToolbarButtonsVisibility(false, false);
        } else if (itemObject instanceof PlaylistRemoteEntity) {
            v = new ToolbarButtonsVisibility(false, false);
        } else if (itemObject instanceof PlaylistMetadataEntry) {
            v = new ToolbarButtonsVisibility(false, false);
        } else {
            // convertible to stream info item
            v = new ToolbarButtonsVisibility( false, false);
        }

        delete.setVisibility(v.delete ? View.VISIBLE : View.GONE);
        setAsPlaylistThumbnail.setVisibility(v.setAsPlaylistThumbnail ? View.VISIBLE : View.GONE);

        download.setVisibility(View.GONE); // TODO
    }


    private void resetItemToolbar() {
        switch (itemToolbarView.getId()) {
            case R.id.toolbarOverlayItem:
                ToolbarOverlayItemAnimation.resetToolbarOverlayItem(itemToolbarView);
                break;
            case R.id.toolbarBelowItem:
                ToolbarBelowItemAnimation.resetToolbarBelowItem(itemToolbarView);
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid itemToolbarView passed to resetItemToolbarView()");
        }
    }

    private void animateToggleItemToolbar() {
        final Animation animation;
        switch (itemToolbarView.getId()) {
            case R.id.toolbarOverlayItem:
                animation = new ToolbarOverlayItemAnimation(TOGGLE_ITEM_TOOLBAR_ANIMATION_DURATION,
                        itemToolbarView);
                break;
            case R.id.toolbarBelowItem:
                animation = new ToolbarBelowItemAnimation(TOGGLE_ITEM_TOOLBAR_ANIMATION_DURATION,
                        itemToolbarView);
                break;
            default:
                throw new IllegalArgumentException(
                        "Invalid itemToolbarView passed to toggleItemToolbar()");
        }
        itemToolbarView.startAnimation(animation);
    }

    private void showItemToolbar(final Object itemObject) {
        animateToggleItemToolbar();
        itemHandler.replaceItemHolderWithToolbarOpen(this);

        share.setOnClickListener(v -> onShare(itemObject));
        addToPlaylist.setOnClickListener(v -> onAddToPlaylist(itemObject));
        playMain.setOnClickListener(v -> onPlayMain(itemObject));
        playPopup.setOnClickListener(v -> onPlayPopup(itemObject));
        playPopup.setOnLongClickListener(v -> {
            onEnqueuePopup(itemObject);
            return true;
        });
        playBackground.setOnClickListener(v -> onPlayBackground(itemObject));
        playBackground.setOnLongClickListener(v -> {
            onEnqueueBackground(itemObject);
            return true;
        });
    }

    public void hideItemToolbar() {
        // this function is public and called by itemHandler when another item's toolbar is opened
        // so prevent the toolbar from opening in case it is called in an unwanted case
        if (itemToolbarView.getVisibility() == View.VISIBLE) {
            animateToggleItemToolbar();
        }
    }


    //////////////////////////////////////
    // ACTIONS
    //////////////////////////////////////

    private void onShowInfo(final Object itemObject) {
        if (itemObject instanceof PlaylistInfoItem) {
            PlaylistInfoItem item = (PlaylistInfoItem) itemObject;
            NavigationHelper.openPlaylistFragment(itemHandler.getFragmentManager(),
                    item.getServiceId(), item.getUrl(), item.getName());

        } else if (itemObject instanceof ChannelInfoItem) {
            ChannelInfoItem item = (ChannelInfoItem) itemObject;
            NavigationHelper.openChannelFragment(itemHandler.getFragmentManager(),
                    item.getServiceId(), item.getUrl(), item.getName());
            NavigationHelper.openVideoDetailFragment(itemHandler.getFragmentManager(),
                    item.getServiceId(), item.getUrl(), item.getName());

        } else if (itemObject instanceof PlaylistRemoteEntity) {
            PlaylistRemoteEntity item = (PlaylistRemoteEntity) itemObject;
            NavigationHelper.openPlaylistFragment(itemHandler.getFragmentManager(),
                    item.getServiceId(), item.getUrl(), item.getName());

        } else if (itemObject instanceof PlaylistMetadataEntry) {
            PlaylistMetadataEntry item = (PlaylistMetadataEntry) itemObject;
            NavigationHelper.openLocalPlaylistFragment(itemHandler.getFragmentManager(),
                    item.uid, item.name);

        } else {
            StreamInfoItem item = toStreamInfoItem(itemObject);
            NavigationHelper.openVideoDetailFragment(itemHandler.getFragmentManager(),
                    item.getServiceId(), item.getUrl(), item.getName());
        }
    }

    private void onShare(final Object itemObject) {
        if (itemObject instanceof PlaylistRemoteEntity) {
            PlaylistRemoteEntity item = (PlaylistRemoteEntity) itemObject;
            ShareUtils.shareUrl(itemHandler.getActivity(), item.getName(), item.getUrl());
        } else if (itemObject instanceof PlaylistMetadataEntry) {
            Log.e(TAG, "onShare: tried to share local playlist: " + itemObject.toString());
        } else {
            InfoItem item;
            if (itemObject instanceof InfoItem) {
                item = (InfoItem) itemObject;
            } else {
                item = toStreamInfoItem(itemObject);
            }

            ShareUtils.shareUrl(itemHandler.getActivity(), item.getName(), item.getUrl());
        }
    }

    private void onAddToPlaylist(final Object itemObject) {
        // TODO this breaks strangely with LocalPlaylistPlayQueue (see the class)
        // so currently adding local playlists to other playlists is disabled
        // TODO adding remote playlists to other playlists does not show "Playlisted" toast
        final PlayQueue playQueue = queueFromObject(itemObject);
        playQueue.fetch(() -> AppendPlaylistDialog.fromPlayQueueItems(playQueue.getStreams())
            .show(itemHandler.getActivity().getSupportFragmentManager(),
                "StreamDialogEntry@append_playlist"));
    }

    private void onPlayMain(final Object itemObject) {
        NavigationHelper.playOnMainPlayer(
                itemHandler.getActivity(), queueFromObject(itemObject), true);
    }

    private void onPlayPopup(final Object itemObject) {
        NavigationHelper.playOnPopupPlayer(
                itemHandler.getActivity(), queueFromObject(itemObject), true);
    }

    private void onEnqueuePopup(final Object itemObject) {
        NavigationHelper.enqueueOnPopupPlayer(
                itemHandler.getActivity(), queueFromObject(itemObject), false);
    }

    private void onPlayBackground(final Object itemObject) {
        NavigationHelper.playOnBackgroundPlayer(
                itemHandler.getActivity(), queueFromObject(itemObject), true);
    }

    private void onEnqueueBackground(final Object itemObject) {
        NavigationHelper.enqueueOnBackgroundPlayer(
                itemHandler.getActivity(), queueFromObject(itemObject), false);
    }


    //////////////////////////////////////
    // UTIL
    //////////////////////////////////////

    private static StreamInfoItem toStreamInfoItem(final Object itemObject) {
        if (itemObject instanceof StreamInfoItem) {
            return (StreamInfoItem) itemObject;
        } else if (itemObject instanceof StreamStatisticsEntry) {
            return ((StreamStatisticsEntry) itemObject).toStreamInfoItem();
        } else if (itemObject instanceof PlaylistStreamEntry) {
            return ((PlaylistStreamEntry) itemObject).toStreamInfoItem();
        }

        throw new IllegalArgumentException("toStreamInfoItem: invalid item object: " + itemObject);
    }

    private PlayQueue queueFromObject(final Object itemObject) {
        if (itemObject instanceof PlaylistInfoItem) {
            return new PlaylistPlayQueue((PlaylistInfoItem) itemObject);
        } else if (itemObject instanceof PlaylistRemoteEntity) {
            final PlaylistRemoteEntity item = (PlaylistRemoteEntity) itemObject;
            return new PlaylistPlayQueue(
                    new PlaylistInfoItem(item.getServiceId(), item.getUrl(), item.getName()));
        } else if (itemObject instanceof PlaylistMetadataEntry) {
            return new LocalPlaylistPlayQueue(
                    itemHandler.getActivity(), (PlaylistMetadataEntry) itemObject);
        } else if (itemObject instanceof ChannelInfoItem) {
            return new ChannelPlayQueue((ChannelInfoItem) itemObject);
        } else {
            return new SinglePlayQueue(toStreamInfoItem(itemObject));
        }
    }
}
