package org.schabi.newpipe.fragments;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.navigation.NavigationView;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.settings.sections.Section;
import org.schabi.newpipe.settings.sections.SectionsManager;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PeertubeHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

public class DrawerFragment extends MainFragment {
    MainActivity mainActivity;
    private SectionsManager sectionsManager;
    private List<Section> sectionList = new ArrayList<>();
    private ActionBarDrawerToggle toggle;
    private ImageView serviceArrow;
    private ImageView headerServiceIcon;
    private TextView headerServiceView;
    private Button toggleServiceButton;
    private boolean servicesShown = false;
    private DrawerLayout drawer;
    private NavigationView drawerItems;

    //TODO: sync those constants with the constants in the Section class
    //must be same as in the Section class#
    //0, 1, 2, 3 are youtube, soundcloud, mediaccc, peertube
    static final int ITEM_ID_BLANK = -1;
    static final int ITEM_ID_SETTINGS = -2;
    static final int ITEM_ID_ABOUT = -3;
    static final int ITEM_ID_BOOKMARKS = -4;
    static final int ITEM_ID_FEED = -5;
    static final int ITEM_ID_SUBSCRIPTIONS = -6;
    static final int ITEM_ID_DOWNLOADS = -7;
    static final int ITEM_ID_HISTORY = -8;
    static final int ITEM_ID_DEFAULT_KIOSK = -9;
    static final int ITEM_ID_KIOSK = -10;
    static final int ITEM_ID_CHANNEL = -11;
    static final int ITEM_ID_PLAYLIST = -12;

    private static final int ORDER = 0;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        setHasOptionsMenu(true);

        //init drawer
        drawer = activity.findViewById(R.id.drawer_layout);
        drawerItems = activity.findViewById(R.id.navigation);

        final Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toggle = new ActionBarDrawerToggle(activity, drawer,
                toolbar, R.string.drawer_close,
                R.string.drawer_open);
        toggle.syncState();
        drawer.addDrawerListener(toggle);

        super.onCreate(savedInstanceState);

        mainActivity = (MainActivity) activity;
        mainActivity.setDrawer(this);

        sectionsManager = SectionsManager.getManager(activity);
        sectionsManager.setSavedSectionsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupSections();
            }
        });

        setupSections();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sectionsManager.unsetSavedSectionsListener();
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        try {
            final int selectedServiceId = ServiceHelper.getSelectedServiceId(activity);
            final String selectedServiceName = NewPipe.getService(selectedServiceId)
                    .getServiceInfo().getName();
            headerServiceView.setText(selectedServiceName);
            headerServiceIcon.setImageResource(ServiceHelper.getIcon(selectedServiceId));

            headerServiceView.post(() -> headerServiceView.setSelected(true));
            toggleServiceButton.setContentDescription(
                    getString(R.string.drawer_header_description) + selectedServiceName);
        } catch (final Exception e) {
            ErrorActivity.reportUiError(activity, e);
        }

        final SharedPreferences sharedPreferences = PreferenceManager.
                getDefaultSharedPreferences(activity);

        if (sharedPreferences.getBoolean(Constants.KEY_THEME_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "Theme has changed, recreating activity...");
            }
            sharedPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, false).apply();
            // https://stackoverflow.com/questions/10844112/
            // Briefly, let the activity resume
            // properly posting the recreate call to end of the message queue
            new Handler(Looper.getMainLooper()).post(activity::recreate);
        }

        if (sharedPreferences.getBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "main page has changed, recreating main fragment...");
            }
            sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false).apply();
            NavigationHelper.openMainActivity(activity);
        }

        setupSections();
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        //initialize variables
        setupDrawerHeader();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setupSections() {
        sectionList.clear();
        sectionList.addAll(sectionsManager.getSections());

        final Toolbar toolbar = activity.findViewById(R.id.toolbar);

        updateSectionsIconAndDescription();

        toggle = new ActionBarDrawerToggle(activity, drawer, toolbar, R.string.drawer_close,
                R.string.drawer_open);
        toggle.syncState();
        drawer.addDrawerListener(toggle);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            private int lastService;

            @Override
            public void onDrawerOpened(final View drawerView) {
                lastService = ServiceHelper.getSelectedServiceId(drawerView.getContext());
            }

            @Override
            public void onDrawerClosed(final View drawerView) {
                if (servicesShown) {
                    toggleServices();
                }
                final MainActivity activity = (MainActivity) drawerView.getContext();
                if (lastService != ServiceHelper.getSelectedServiceId(activity)) {
                    new Handler(Looper.myLooper()).post(activity::recreate);
                }
            }
        });

        final boolean isHistoryEnabled = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(getString(R.string.enable_watch_history_key), true);
        if (drawerItems.getMenu().findItem(ITEM_ID_HISTORY) != null) {
            drawerItems.getMenu().findItem(ITEM_ID_HISTORY).setVisible(isHistoryEnabled);
        }

        drawerItems.setNavigationItemSelectedListener(this::drawerItemSelected);
        setupDrawerHeader();
    }

    private void setupDrawerHeader() {
        final NavigationView navigationView = activity.findViewById(R.id.navigation);
        final View hView = navigationView.getHeaderView(0);

        serviceArrow = hView.findViewById(R.id.drawer_arrow);
        headerServiceIcon = hView.findViewById(R.id.drawer_header_service_icon);
        headerServiceView = hView.findViewById(R.id.drawer_header_service_view);
        toggleServiceButton = hView.findViewById(R.id.drawer_header_action_button);
        toggleServiceButton.setOnClickListener(view -> toggleServices());

        // If the current app name is bigger than the default "NewPipe" (7 chars),
        // let the text view grow a little more as well.
        if (activity.getString(R.string.app_name).length() > "NewPipe".length()) {
            final TextView headerTitle = hView.findViewById(R.id.drawer_header_newpipe_title);
            final ViewGroup.LayoutParams layoutParams = headerTitle.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            headerTitle.setLayoutParams(layoutParams);
            headerTitle.setMaxLines(2);
            headerTitle.setMinWidth(activity.getResources()
                    .getDimensionPixelSize(R.dimen.drawer_header_newpipe_title_default_width));
            headerTitle.setMaxWidth(activity.getResources()
                    .getDimensionPixelSize(R.dimen.drawer_header_newpipe_title_max_width));
        }
    }

    private void toggleServices() {
        servicesShown = !servicesShown;

        drawerItems.getMenu().removeGroup(R.id.menu_services_group);
        drawerItems.getMenu().removeGroup(R.id.menu_tabs_group);
        drawerItems.getMenu().removeGroup(R.id.menu_options_about_group);

        if (servicesShown) {
            showServices();
        } else {
            try {
                showSections();
            } catch (final Exception e) {
                ErrorActivity.reportUiError(activity, e);
            }
        }
    }

    private void showServices() {
        serviceArrow.setImageResource(R.drawable.ic_arrow_drop_up_white_24dp);

        for (final StreamingService s : NewPipe.getServices()) {
            final String title = s.getServiceInfo().getName()
                    + (ServiceHelper.isBeta(s) ? " (beta)" : "");

            final MenuItem menuItem = drawerItems.getMenu()
                    .add(R.id.menu_services_group, s.getServiceId(), ORDER, title)
                    .setIcon(ServiceHelper.getIcon(s.getServiceId()));

            // peertube specifics
            if (s.getServiceId() == 3) {
                enhancePeertubeMenu(s, menuItem);
            }
        }
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(activity))
                .setChecked(true);
    }

    private void enhancePeertubeMenu(final StreamingService s, final MenuItem menuItem) {
        final PeertubeInstance currentInstace = PeertubeHelper.getCurrentInstance();
        menuItem.setTitle(currentInstace.getName() + (ServiceHelper.isBeta(s) ? " (beta)" : ""));
        final Spinner spinner = (Spinner) LayoutInflater.from(activity)
                .inflate(R.layout.instance_spinner_layout, null);
        final List<PeertubeInstance> instances = PeertubeHelper.getInstanceList(activity);
        final List<String> items = new ArrayList<>();
        int defaultSelect = 0;
        for (final PeertubeInstance instance : instances) {
            items.add(instance.getName());
            if (instance.getUrl().equals(currentInstace.getUrl())) {
                defaultSelect = items.size() - 1;
            }
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(activity,
                R.layout.instance_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(defaultSelect, false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(final AdapterView<?> parent, final View view,
                                       final int position, final long id) {
                final PeertubeInstance newInstance = instances.get(position);
                if (newInstance.getUrl().equals(PeertubeHelper.getCurrentInstance().getUrl())) {
                    return;
                }
                PeertubeHelper.selectInstance(newInstance, activity);
                changeService(menuItem);
                drawer.closeDrawers();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    activity.getSupportFragmentManager().popBackStack(null,
                            FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    activity.recreate();
                }, 300);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {

            }
        });
        menuItem.setActionView(spinner);
    }

    private void changeService(final MenuItem item) {
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(activity))
                .setChecked(false);
        ServiceHelper.setSelectedServiceId(activity, item.getItemId());
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(activity))
                .setChecked(true);
    }

    private boolean drawerItemSelected(final MenuItem item) {
        switch (item.getGroupId()) {
            case R.id.menu_services_group:
                changeService(item);
                break;
            case R.id.menu_tabs_group:
                try {
                    sectionSelected(item);
                } catch (final Exception e) {
                    ErrorActivity.reportUiError(activity, e);
                }
                break;
            case R.id.menu_options_about_group:
                optionsAboutSelected(item);
                break;
            default:
                return false;
        }

        drawer.closeDrawers();
        return true;
    }

    private void sectionSelected(final MenuItem item) throws ExtractionException {
        final int serviceId = ServiceHelper.getSelectedServiceId(activity);
        switch (item.getItemId()) {
            case ITEM_ID_BLANK:
                break;
            case ITEM_ID_SUBSCRIPTIONS:
                NavigationHelper.openSubscriptionFragment(activity.getSupportFragmentManager());
                break;
            case ITEM_ID_FEED:
                NavigationHelper.openFeedFragment(activity.getSupportFragmentManager());
                break;
            case ITEM_ID_BOOKMARKS:
                NavigationHelper.openBookmarksFragment(activity.getSupportFragmentManager());
                break;
            case ITEM_ID_DOWNLOADS:
                NavigationHelper.openDownloads(activity);
                break;
            case ITEM_ID_HISTORY:
                NavigationHelper.openStatisticFragment(activity.getSupportFragmentManager());
                break;
            case ITEM_ID_CHANNEL:
                Section.ChannelSection channelSection = null;

                //find correct Section
                for (int i = 0; i < sectionList.size(); i++) {
                    if (sectionList.get(i) instanceof Section.ChannelSection) {
                        final Section.ChannelSection sec =
                                (Section.ChannelSection) sectionList.get(i);
                        //channels never get Translated
                        if (sec.getChannelName() == item.getTitle()) {
                            channelSection = (Section.ChannelSection) sectionList.get(i);
                            break;
                        }
                    }
                }

                NavigationHelper.openChannelFragment(
                        activity.getSupportFragmentManager(),
                        channelSection.getChannelServiceId(),
                        channelSection.getChannelUrl(),
                        channelSection.getChannelName());
                break;
            case ITEM_ID_PLAYLIST:
                Section.PlaylistSection playlistSection = null;

                //find correct Section
                for (int i = 0; i < sectionList.size(); i++) {
                    if (sectionList.get(i) instanceof Section.PlaylistSection) {
                        final Section.PlaylistSection sec =
                                (Section.PlaylistSection) sectionList.get(i);
                        if (sec.getPlaylistName() == item.getTitle()) {
                            playlistSection = (Section.PlaylistSection) sectionList.get(i);
                            break;
                        }
                    }
                }

                NavigationHelper.openPlaylistFragment(
                        activity.getSupportFragmentManager(),
                        playlistSection.getPlaylistServiceId(),
                        playlistSection.getPlaylistUrl(),
                        playlistSection.getPlaylistName()
                );
                break;
            default:
                final List<String> kioskIdList = getKioskIdsAsList();

                for (int i = 0; i < kioskIdList.size(); i++) {
                    final String kioskId = kioskIdList.get(i);
                    final String translation =
                            KioskTranslator.getTranslatedKioskName(kioskId, activity);
                    if (translation == item.getTitle()) {
                        NavigationHelper.openKioskFragment(
                                activity.getSupportFragmentManager(),
                                serviceId,
                                kioskIdList.get(i));
                        break;
                    }
                }
        }
    }

    private List<String> getKioskIdsAsList() {
        final int serviceId = ServiceHelper.getSelectedServiceId(activity);
        final StreamingService service;
        final List<String> kioskList = new ArrayList<>();
        try {
            service = NewPipe.getService(serviceId);
            kioskList.addAll(service.getKioskList().getAvailableKiosks());
        } catch (final ExtractionException e) {
            e.printStackTrace();
        }
        return kioskList;
    }

    private void optionsAboutSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case ITEM_ID_SETTINGS:
                NavigationHelper.openSettings(activity);
                break;
            case ITEM_ID_ABOUT:
                NavigationHelper.openAbout(activity);
                break;
        }
    }

    private void showSections() {
        serviceArrow.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp);

        updateSectionsIconAndDescription();
    }

    private void updateSectionsIconAndDescription() {
        drawerItems.getMenu().clear();

        int kioskCounter = 0;
        final List<String> kioskList = getKioskIdsAsList();
        final int serviceId = ServiceHelper.getSelectedServiceId(activity);

        for (int i = 0; i < sectionList.size(); i++) {
            final Section section = sectionList.get(i);

            switch (section.getSectionId()) {
                case ITEM_ID_BLANK:
                    //don't add blank sections
                    break;
                case ITEM_ID_DEFAULT_KIOSK:
                case ITEM_ID_KIOSK:
                    //limits number of Kiosks to numbers of actual Kiosks
                    if (kioskCounter < kioskList.size()) {
                        final String kioskId = kioskList.get(kioskCounter);
                        final String sectionName =
                                KioskTranslator.getTranslatedKioskName(kioskId, activity);
                        final int iconID = KioskTranslator.getKioskIcon(kioskId, activity);
                        drawerItems.getMenu().add(R.id.menu_tabs_group,
                                serviceId, ORDER,
                                sectionName)
                                .setIcon(iconID);
                        kioskCounter++;
                    }
                    break;
                default:
                    drawerItems.getMenu()
                            .add(R.id.menu_tabs_group, section.getSectionId(), ORDER,
                                    section.getSectionName(activity))
                            .setIcon(section.getSectionIconRes(activity));
                }
        }

        //Settings and About
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.ic_settings));
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(activity, R.attr.ic_info_outline));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // getters and setters
    //////////////////////////////////////////////////////////////////////////*/

    public DrawerLayout getDrawer() {
        return this.drawer;
    }

    public ActionBarDrawerToggle getToggle() {
        return this.toggle;
    }
}
