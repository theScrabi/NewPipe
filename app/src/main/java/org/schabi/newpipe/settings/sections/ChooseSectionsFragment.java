package org.schabi.newpipe.settings.sections;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.SelectChannelFragment;
import org.schabi.newpipe.settings.SelectKioskFragment;
import org.schabi.newpipe.settings.SelectPlaylistFragment;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChooseSectionsFragment extends Fragment {
    private static final int MENU_ITEM_RESTORE_ID = 123456;

    private SectionsManager sectionsManager;

    private final List<Section> sectionList = new ArrayList<>();
    private SelectedSectionsAdapter selectedSectionsAdapter;

    /*//////////////////////////////////////////////////////////////////////////
    // Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sectionsManager = SectionsManager.getManager(requireContext());
        updateSectionList();

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_choose_tabs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View rootView,
                              @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);

        initButton(rootView);

        final RecyclerView listSelectedSections = rootView.findViewById(R.id.selectedTabs);
        listSelectedSections.setLayoutManager(new LinearLayoutManager(requireContext()));

        final ItemTouchHelper itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(listSelectedSections);

        selectedSectionsAdapter = new SelectedSectionsAdapter(requireContext(), itemTouchHelper);
        listSelectedSections.setAdapter(selectedSectionsAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateTitle();
    }

    @Override
    public void onPause() {
        super.onPause();
        saveChanges();
    }
    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        final MenuItem restoreItem = menu.add(Menu.NONE, MENU_ITEM_RESTORE_ID, Menu.NONE,
                R.string.restore_defaults);
        restoreItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        final int restoreIcon = ThemeHelper.resolveResourceIdFromAttr(requireContext(),
                R.attr.ic_restore_defaults);
        restoreItem.setIcon(AppCompatResources.getDrawable(requireContext(), restoreIcon));
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == MENU_ITEM_RESTORE_ID) {
            restoreDefaults();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void updateSectionList() {
        sectionList.clear();
        sectionList.addAll(sectionsManager.getSections());
    }

    private void initButton(final View rootView) {
        final FloatingActionButton fab = rootView.findViewById(R.id.addTabsButton);
        fab.setOnClickListener(v -> {
            final AddSectionDialog.ChooseSectionListItem[] availableSections =
                    getAvailableSections(requireContext());

            if (availableSections.length == 0) {
                return;
            }

            final Dialog.OnClickListener actionListener = ((dialog, which) -> {
               final AddSectionDialog.ChooseSectionListItem selected = availableSections[which];
               addSection(selected.sectionId);
            });

            new AddSectionDialog(requireContext(), availableSections, actionListener).show();
        });
    }

    private void updateTitle() {
        if (getActivity() instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setTitle(R.string.panel_content);
            }
        }
    }

    private void restoreDefaults() {
        new AlertDialog.Builder(requireContext(), ThemeHelper.getDialogTheme(requireContext()))
                .setTitle(R.string.restore_defaults)
                .setMessage(R.string.restore_defaults_confirmation)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    sectionsManager.resetSections();
                    updateSectionList();
                    selectedSectionsAdapter.notifyDataSetChanged();
                })
                .show();
    }

    private void addSection(final Section section) {
        if (sectionList.get(0).getSectionId() == Section.ITEM_ID_BLANK) {
            sectionList.remove(0);
        }
        sectionList.add(section);
        selectedSectionsAdapter.notifyDataSetChanged();
    }

    private void addSection(final int sectionId) {
        final Section.Type type = Section.typeFrom(sectionId);

        if (type == null) {
            ErrorActivity.reportError(requireContext(),
                    new IllegalStateException("Section id not found: " + sectionId), null, null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                            "Choosing sections on settings", 0));
            return;
        }

        switch (type) {
            case KIOSK:
                final SelectKioskFragment selectKioskFragment = new SelectKioskFragment();
                selectKioskFragment.setOnSelectedListener((serviceId, kioskId, kioskName) ->
                        addSection(new Section.KioskSection(serviceId, kioskId)));
                selectKioskFragment.show(getParentFragmentManager(), "select_kiosk");
                return;
            case CHANNEL:
                final SelectChannelFragment selectChannelFragment = new SelectChannelFragment();
                selectChannelFragment.setOnSelectedListener((serviceId, url, name) ->
                        addSection(new Section.ChannelSection(serviceId, url, name)));
                selectChannelFragment.show(getParentFragmentManager(), "select_channel");
                return;
            case PLAYLIST:
                final SelectPlaylistFragment selectPlaylistFragment = new SelectPlaylistFragment();
                selectPlaylistFragment.setOnSelectedListener(
                        new SelectPlaylistFragment.OnSelectedListener() {
                            @Override
                            public void onLocalPlaylistSelected(final long id, final String name) {
                                addSection(new Section.PlaylistSection(id, name));
                            }

                            @Override
                            public void onRemotePlaylistSelected(
                                    final int serviceId, final String url, final String name) {
                                addSection(new Section.PlaylistSection(serviceId, url, name));
                            }
                        });
                selectPlaylistFragment.show(getParentFragmentManager(), "select_playlist");
                return;
            default:
                addSection(type.getSection());
                break;
        }
    }

    private void saveChanges() {
        sectionsManager.saveSections(sectionList);
    }

    private AddSectionDialog.ChooseSectionListItem[] getAvailableSections(final Context context) {
        final ArrayList<AddSectionDialog.ChooseSectionListItem> returnList = new ArrayList<>();

        for (final Section.Type type : Section.Type.values()) {
            final Section section = type.getSection();
            switch (type) {
                case BLANK:
                    //dont show blank pages
                    break;
                case DOWNLOADS:
                    returnList.add(new AddSectionDialog.ChooseSectionListItem(
                            section.getSectionId(),
                            getString(R.string.download),
                            section.getSectionIconRes(context)));
                    break;
                case KIOSK:
                    returnList.add(new AddSectionDialog.ChooseSectionListItem(
                            section.getSectionId(),
                            getString(R.string.kiosk_page_summary),
                            ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_kiosk_hot)));
                    break;
                case CHANNEL:
                    returnList.add(new AddSectionDialog.ChooseSectionListItem(
                            section.getSectionId(),
                            getString(R.string.channel_page_summary),
                            section.getSectionIconRes(context)));
                    break;
                case DEFAULT_KIOSK:
                    returnList.add(new AddSectionDialog.ChooseSectionListItem(
                            section.getSectionId(),
                            getString(R.string.default_kiosk_page_summary),
                            ThemeHelper.resolveResourceIdFromAttr(context,
                                    R.attr.ic_kiosk_hot)));
                    break;
                case PLAYLIST:
                    returnList.add(new AddSectionDialog.ChooseSectionListItem(
                            section.getSectionId(),
                            getString(R.string.playlist_page_summary),
                            section.getSectionIconRes(context)));
                    break;
                default:
                    if (!sectionList.contains(section)) {
                        returnList.add(
                                new AddSectionDialog.ChooseSectionListItem(context, section));
                    }
                    break;
            }
        }

        return returnList.toArray(new AddSectionDialog.ChooseSectionListItem[0]);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // List Handling
    //////////////////////////////////////////////////////////////////////////*/

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        return new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN,
                ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public int interpolateOutOfBoundsScroll(final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView, viewSize,
                        viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(12,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(final RecyclerView recyclerView,
                                  final RecyclerView.ViewHolder source,
                                  final RecyclerView.ViewHolder target) {
                if (source.getItemViewType() != target.getItemViewType()
                        || selectedSectionsAdapter == null) {
                    return false;
                }

                final int sourceIndex = source.getAdapterPosition();
                final int targetIndex = target.getAdapterPosition();
                selectedSectionsAdapter.swapItems(sourceIndex, targetIndex);
                return true;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return true;
            }

            @Override
            public void onSwiped(final RecyclerView.ViewHolder viewHolder, final int swipeDir) {
                final int position = viewHolder.getAdapterPosition();
                sectionList.remove(position);
                selectedSectionsAdapter.notifyItemRemoved(position);

                if (sectionList.isEmpty()) {
                    sectionList.add(Section.Type.BLANK.getSection());
                    selectedSectionsAdapter.notifyItemInserted(0);
                }
            }
        };
    }

    private class SelectedSectionsAdapter
            extends RecyclerView.Adapter<SelectedSectionsAdapter.TabViewHolder> {
        private final LayoutInflater inflater;
        private ItemTouchHelper itemTouchHelper;

        SelectedSectionsAdapter(final Context context, final ItemTouchHelper itemTouchHelper) {
            this.itemTouchHelper = itemTouchHelper;
            this.inflater = LayoutInflater.from(context);
        }

        public void swapItems(final int fromPosition, final int toPosition) {
            Collections.swap(sectionList, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        @NonNull
        @Override
        public SelectedSectionsAdapter.TabViewHolder onCreateViewHolder(
                @NonNull final ViewGroup parent, final int viewType) {
            final View view = inflater.inflate(R.layout.list_choose_tabs, parent, false);
            return new SelectedSectionsAdapter.TabViewHolder(view);
        }

        @Override
        public void onBindViewHolder(
                @NonNull final SelectedSectionsAdapter.TabViewHolder holder,
                final int position) {
            holder.bind(position, holder);
        }

        @Override
        public int getItemCount() {
            return sectionList.size();
        }

        class TabViewHolder extends RecyclerView.ViewHolder {
            private AppCompatImageView sectionIconView;
            private TextView sectionNameView;
            private ImageView handle;

            TabViewHolder(final View itemView) {
                super(itemView);

                sectionNameView = itemView.findViewById(R.id.tabName);
                sectionIconView = itemView.findViewById(R.id.tabIcon);
                handle = itemView.findViewById(R.id.handle);
            }

            @SuppressLint("ClickableViewAccessibility")
            void bind(final int position, final TabViewHolder holder) {
                handle.setOnTouchListener(getOnTouchListener(holder));

                final Section section = sectionList.get(position);
                final Section.Type type = Section.typeFrom(section.getSectionId());

                if (type == null) {
                    return;
                }

                final String sectionName;
                switch (type) {
                    case BLANK:
                        sectionName = getString(R.string.blank_page_summary);
                        break;
                    case DEFAULT_KIOSK:
                        sectionName = getString(R.string.default_kiosk_page_summary);
                        break;
                    case KIOSK:
                        sectionName = NewPipe.getNameOfService(((Section.KioskSection) section)
                                .getKioskServiceId()) + "/"
                                + section.getSectionName(requireContext());
                        break;
                    case CHANNEL:
                        sectionName = NewPipe.getNameOfService(((Section.ChannelSection) section)
                                .getChannelServiceId()) + "/"
                                + section.getSectionName(requireContext());
                        break;
                    case PLAYLIST:
                        final int serviceId = ((Section.PlaylistSection) section)
                                .getPlaylistServiceId();
                        final String serviceName = serviceId == -1
                                ? getString(R.string.local)
                                : NewPipe.getNameOfService(serviceId);
                        sectionName = serviceName + "/" + section.getSectionName(requireContext());
                        break;
                    default:
                        sectionName = section.getSectionName(requireContext());
                        break;
                }

                sectionNameView.setText(sectionName);
                sectionIconView.setImageResource(section.getSectionIconRes(requireContext()));
            }

            @SuppressLint("ClickableViewAccessibility")
            private View.OnTouchListener getOnTouchListener(final RecyclerView.ViewHolder item) {
                return (view, motionEvent) -> {
                    if (motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        if (itemTouchHelper != null && getItemCount() > 1) {
                            itemTouchHelper.startDrag(item);
                            return true;
                        }
                    }
                    return false;
                };
            }
        }
    }
}
