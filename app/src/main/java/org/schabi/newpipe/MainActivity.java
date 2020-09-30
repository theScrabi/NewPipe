/*
 * Created by Christian Schabesberger on 02.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * DownloadActivity.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.util.Log;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigation.NavigationView;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.fragments.detail.VideoDetailFragment;
import org.schabi.newpipe.fragments.list.search.SearchFragment;
import org.schabi.newpipe.player.VideoPlayer;
import org.schabi.newpipe.player.event.OnKeyDownListener;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.KioskTranslator;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.PeertubeHelper;
import org.schabi.newpipe.util.PermissionHelper;
import org.schabi.newpipe.util.SerializedCache;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StateSaver;
import org.schabi.newpipe.util.TLSSocketFactoryCompat;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.FocusOverlayView;

import java.util.ArrayList;
import java.util.List;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    public static final boolean DEBUG = !BuildConfig.BUILD_TYPE.equals("release");

    private ActionBarDrawerToggle toggle;
    private DrawerLayout drawer;
    private NavigationView drawerItems;
    private ImageView headerServiceIcon;
    private TextView headerServiceView;
    private Button toggleServiceButton;

    private boolean servicesShown = false;
    private ImageView serviceArrow;

    private static final int ITEM_ID_SUBSCRIPTIONS = -1;
    private static final int ITEM_ID_FEED = -2;
    private static final int ITEM_ID_BOOKMARKS = -3;
    private static final int ITEM_ID_DOWNLOADS = -4;
    private static final int ITEM_ID_HISTORY = -5;
    private static final int ITEM_ID_SETTINGS = 0;
    private static final int ITEM_ID_ABOUT = 1;

    private static final int ORDER = 0;

    /*//////////////////////////////////////////////////////////////////////////
    // Activity's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }

        // enable TLS1.1/1.2 for kitkat devices, to fix download and play for mediaCCC sources
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            TLSSocketFactoryCompat.setAsDefault();
        }
        ThemeHelper.setTheme(this, ServiceHelper.getSelectedServiceId(this));

        assureCorrectAppLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getSupportFragmentManager() != null
                && getSupportFragmentManager().getBackStackEntryCount() == 0) {
            initFragments();
        }

        setSupportActionBar(findViewById(R.id.toolbar));
        try {
            setupDrawer();
        } catch (final Exception e) {
            ErrorActivity.reportUiError(this, e);
        }

        if (DeviceUtils.isTv(this)) {
            FocusOverlayView.setupFocusObserver(this);
        }
    }

    private void setupDrawer() throws Exception {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        drawer = findViewById(R.id.drawer_layout);
        drawerItems = findViewById(R.id.navigation);

        //Tabs
        final int currentServiceId = ServiceHelper.getSelectedServiceId(this);
        final StreamingService service = NewPipe.getService(currentServiceId);

        int kioskId = 0;

        for (final String ks : service.getKioskList().getAvailableKiosks()) {
            drawerItems.getMenu()
                    .add(R.id.menu_tabs_group, kioskId, 0, KioskTranslator
                            .getTranslatedKioskName(ks, this))
                    .setIcon(KioskTranslator.getKioskIcon(ks, this));
            kioskId++;
        }

        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_SUBSCRIPTIONS, ORDER,
                        R.string.tab_subscriptions)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_channel));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_FEED, ORDER, R.string.fragment_feed_title)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_rss));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_BOOKMARKS, ORDER, R.string.tab_bookmarks)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_bookmark));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_DOWNLOADS, ORDER, R.string.downloads)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_file_download));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_HISTORY, ORDER, R.string.action_history)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_history));

        //Settings and About
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_settings));
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_info_outline));

        toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.drawer_open,
                R.string.drawer_close);
        toggle.syncState();
        drawer.addDrawerListener(toggle);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            private int lastService;

            @Override
            public void onDrawerOpened(final View drawerView) {
                lastService = ServiceHelper.getSelectedServiceId(MainActivity.this);
            }

            @Override
            public void onDrawerClosed(final View drawerView) {
                if (servicesShown) {
                    toggleServices();
                }
                if (lastService != ServiceHelper.getSelectedServiceId(MainActivity.this)) {
                    new Handler(Looper.getMainLooper()).post(MainActivity.this::recreate);
                }
            }
        });

        drawerItems.setNavigationItemSelectedListener(this::drawerItemSelected);
        setupDrawerHeader();
    }

    private boolean drawerItemSelected(final MenuItem item) {
        switch (item.getGroupId()) {
            case R.id.menu_services_group:
                changeService(item);
                break;
            case R.id.menu_tabs_group:
                try {
                    tabSelected(item);
                } catch (final Exception e) {
                    ErrorActivity.reportUiError(this, e);
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

    private void changeService(final MenuItem item) {
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(this))
                .setChecked(false);
        ServiceHelper.setSelectedServiceId(this, item.getItemId());
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(this))
                .setChecked(true);
    }

    private void tabSelected(final MenuItem item) throws ExtractionException {
        switch (item.getItemId()) {
            case ITEM_ID_SUBSCRIPTIONS:
                NavigationHelper.openSubscriptionFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_FEED:
                NavigationHelper.openFeedFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_BOOKMARKS:
                NavigationHelper.openBookmarksFragment(getSupportFragmentManager());
                break;
            case ITEM_ID_DOWNLOADS:
                NavigationHelper.openDownloads(this);
                break;
            case ITEM_ID_HISTORY:
                NavigationHelper.openStatisticFragment(getSupportFragmentManager());
                break;
            default:
                final int currentServiceId = ServiceHelper.getSelectedServiceId(this);
                final StreamingService service = NewPipe.getService(currentServiceId);
                String serviceName = "";

                int kioskId = 0;
                for (final String ks : service.getKioskList().getAvailableKiosks()) {
                    if (kioskId == item.getItemId()) {
                        serviceName = ks;
                    }
                    kioskId++;
                }

                NavigationHelper.openKioskFragment(getSupportFragmentManager(), currentServiceId,
                        serviceName);
                break;
        }
    }

    private void optionsAboutSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case ITEM_ID_SETTINGS:
                NavigationHelper.openSettings(this);
                break;
            case ITEM_ID_ABOUT:
                NavigationHelper.openAbout(this);
                break;
        }
    }

    private void setupDrawerHeader() {
        final NavigationView navigationView = findViewById(R.id.navigation);
        final View hView = navigationView.getHeaderView(0);

        serviceArrow = hView.findViewById(R.id.drawer_arrow);
        headerServiceIcon = hView.findViewById(R.id.drawer_header_service_icon);
        headerServiceView = hView.findViewById(R.id.drawer_header_service_view);
        toggleServiceButton = hView.findViewById(R.id.drawer_header_action_button);
        toggleServiceButton.setOnClickListener(view -> toggleServices());

        // If the current app name is bigger than the default "NewPipe" (7 chars),
        // let the text view grow a little more as well.
        if (getString(R.string.app_name).length() > "NewPipe".length()) {
            final TextView headerTitle = hView.findViewById(R.id.drawer_header_newpipe_title);
            final ViewGroup.LayoutParams layoutParams = headerTitle.getLayoutParams();
            layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            headerTitle.setLayoutParams(layoutParams);
            headerTitle.setMaxLines(2);
            headerTitle.setMinWidth(getResources()
                    .getDimensionPixelSize(R.dimen.drawer_header_newpipe_title_default_width));
            headerTitle.setMaxWidth(getResources()
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
                showTabs();
            } catch (final Exception e) {
                ErrorActivity.reportUiError(this, e);
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
        drawerItems.getMenu().getItem(ServiceHelper.getSelectedServiceId(this))
                .setChecked(true);
    }

    private void enhancePeertubeMenu(final StreamingService s, final MenuItem menuItem) {
        final PeertubeInstance currentInstace = PeertubeHelper.getCurrentInstance();
        menuItem.setTitle(currentInstace.getName() + (ServiceHelper.isBeta(s) ? " (beta)" : ""));
        final Spinner spinner = (Spinner) LayoutInflater.from(this)
                .inflate(R.layout.instance_spinner_layout, null);
        final List<PeertubeInstance> instances = PeertubeHelper.getInstanceList(this);
        final List<String> items = new ArrayList<>();
        int defaultSelect = 0;
        for (final PeertubeInstance instance : instances) {
            items.add(instance.getName());
            if (instance.getUrl().equals(currentInstace.getUrl())) {
                defaultSelect = items.size() - 1;
            }
        }
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
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
                PeertubeHelper.selectInstance(newInstance, getApplicationContext());
                changeService(menuItem);
                drawer.closeDrawers();
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    getSupportFragmentManager().popBackStack(null,
                            FragmentManager.POP_BACK_STACK_INCLUSIVE);
                    recreate();
                }, 300);
            }

            @Override
            public void onNothingSelected(final AdapterView<?> parent) {

            }
        });
        menuItem.setActionView(spinner);
    }

    private void showTabs() throws ExtractionException {
        serviceArrow.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp);

        //Tabs
        final int currentServiceId = ServiceHelper.getSelectedServiceId(this);
        final StreamingService service = NewPipe.getService(currentServiceId);

        int kioskId = 0;

        for (final String ks : service.getKioskList().getAvailableKiosks()) {
            drawerItems.getMenu()
                    .add(R.id.menu_tabs_group, kioskId, ORDER,
                            KioskTranslator.getTranslatedKioskName(ks, this))
                    .setIcon(KioskTranslator.getKioskIcon(ks, this));
            kioskId++;
        }

        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_SUBSCRIPTIONS, ORDER, R.string.tab_subscriptions)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_channel));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_FEED, ORDER, R.string.fragment_feed_title)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_rss));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_BOOKMARKS, ORDER, R.string.tab_bookmarks)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_bookmark));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_DOWNLOADS, ORDER, R.string.downloads)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_file_download));
        drawerItems.getMenu()
                .add(R.id.menu_tabs_group, ITEM_ID_HISTORY, ORDER, R.string.action_history)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_history));

        //Settings and About
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_SETTINGS, ORDER, R.string.settings)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_settings));
        drawerItems.getMenu()
                .add(R.id.menu_options_about_group, ITEM_ID_ABOUT, ORDER, R.string.tab_about)
                .setIcon(ThemeHelper.resolveResourceIdFromAttr(this, R.attr.ic_info_outline));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations()) {
            StateSaver.clearStateFiles();
        }

        SharedPreferences sharedPreferences =
                this.getSharedPreferences("play_mode_state", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("shuffle_mode", false);
        editor.apply();
    }

    @Override
    protected void onResume() {
        assureCorrectAppLanguage(this);
        // Change the date format to match the selected language on resume
        Localization.init(getApplicationContext());
        super.onResume();

        // Close drawer on return, and don't show animation,
        // so it looks like the drawer isn't open when the user returns to MainActivity
        drawer.closeDrawer(GravityCompat.START, false);
        try {
            final int selectedServiceId = ServiceHelper.getSelectedServiceId(this);
            final String selectedServiceName = NewPipe.getService(selectedServiceId)
                    .getServiceInfo().getName();
            headerServiceView.setText(selectedServiceName);
            headerServiceIcon.setImageResource(ServiceHelper.getIcon(selectedServiceId));

            headerServiceView.post(() -> headerServiceView.setSelected(true));
            toggleServiceButton.setContentDescription(
                    getString(R.string.drawer_header_description) + selectedServiceName);
        } catch (final Exception e) {
            ErrorActivity.reportUiError(this, e);
        }

        final SharedPreferences sharedPreferences
                = PreferenceManager.getDefaultSharedPreferences(this);
        if (sharedPreferences.getBoolean(Constants.KEY_THEME_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "Theme has changed, recreating activity...");
            }
            sharedPreferences.edit().putBoolean(Constants.KEY_THEME_CHANGE, false).apply();
            // https://stackoverflow.com/questions/10844112/
            // Briefly, let the activity resume
            // properly posting the recreate call to end of the message queue
            new Handler(Looper.getMainLooper()).post(MainActivity.this::recreate);
        }

        if (sharedPreferences.getBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false)) {
            if (DEBUG) {
                Log.d(TAG, "main page has changed, recreating main fragment...");
            }
            sharedPreferences.edit().putBoolean(Constants.KEY_MAIN_PAGE_CHANGE, false).apply();
            NavigationHelper.openMainActivity(this);
        }

        final boolean isHistoryEnabled = sharedPreferences.getBoolean(
                getString(R.string.enable_watch_history_key), true);
        drawerItems.getMenu().findItem(ITEM_ID_HISTORY).setVisible(isHistoryEnabled);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        if (DEBUG) {
            Log.d(TAG, "onNewIntent() called with: intent = [" + intent + "]");
        }
        if (intent != null) {
            // Return if launched from a launcher (e.g. Nova Launcher, Pixel Launcher ...)
            // to not destroy the already created backstack
            final String action = intent.getAction();
            if ((action != null && action.equals(Intent.ACTION_MAIN))
                    && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                return;
            }
        }

        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event) {
        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_player_holder);
        if (fragment instanceof OnKeyDownListener
                && !bottomSheetHiddenOrCollapsed()) {
            // Provide keyDown event to fragment which then sends this event
            // to the main player service
            return ((OnKeyDownListener) fragment).onKeyDown(keyCode)
                    || super.onKeyDown(keyCode, event);
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (DEBUG) {
            Log.d(TAG, "onBackPressed() called");
        }

        if (DeviceUtils.isTv(this)) {
            final View drawerPanel = findViewById(R.id.navigation);
            if (drawer.isDrawerOpen(drawerPanel)) {
                drawer.closeDrawers();
                return;
            }
        }

        // In case bottomSheet is not visible on the screen or collapsed we can assume that the user
        // interacts with a fragment inside fragment_holder so all back presses should be
        // handled by it
        if (bottomSheetHiddenOrCollapsed()) {
            final Fragment fragment = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_holder);
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragment instanceof BackPressable) {
                if (((BackPressable) fragment).onBackPressed()) {
                    return;
                }
            }

        } else {
            final Fragment fragmentPlayer = getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_player_holder);
            // If current fragment implements BackPressable (i.e. can/wanna handle back press)
            // delegate the back press to it
            if (fragmentPlayer instanceof BackPressable) {
                if (!((BackPressable) fragmentPlayer).onBackPressed()) {
                    final FrameLayout bottomSheetLayout =
                            findViewById(R.id.fragment_player_holder);
                    BottomSheetBehavior.from(bottomSheetLayout)
                            .setState(BottomSheetBehavior.STATE_COLLAPSED);
                }
                return;
            }
        }

        if (getSupportFragmentManager().getBackStackEntryCount() == 1) {
            finish();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode,
                                           @NonNull final String[] permissions,
                                           @NonNull final int[] grantResults) {
        for (final int i : grantResults) {
            if (i == PackageManager.PERMISSION_DENIED) {
                return;
            }
        }
        switch (requestCode) {
            case PermissionHelper.DOWNLOADS_REQUEST_CODE:
                NavigationHelper.openDownloads(this);
                break;
            case PermissionHelper.DOWNLOAD_DIALOG_REQUEST_CODE:
                final Fragment fragment = getSupportFragmentManager()
                        .findFragmentById(R.id.fragment_player_holder);
                if (fragment instanceof VideoDetailFragment) {
                    ((VideoDetailFragment) fragment).openDownloadDialog();
                }
                break;
        }
    }

    /**
     * Implement the following diagram behavior for the up button:
     * <pre>
     *              +---------------+
     *              |  Main Screen  +----+
     *              +-------+-------+    |
     *                      |            |
     *                      ▲ Up         | Search Button
     *                      |            |
     *                 +----+-----+      |
     *    +------------+  Search  |◄-----+
     *    |            +----+-----+
     *    |   Open          |
     *    |  something      ▲ Up
     *    |                 |
     *    |    +------------+-------------+
     *    |    |                          |
     *    |    |  Video    <->  Channel   |
     *    +---►|  Channel  <->  Playlist  |
     *         |  Video    <->  ....      |
     *         |                          |
     *         +--------------------------+
     * </pre>
     */
    private void onHomeButtonPressed() {
        // If search fragment wasn't found in the backstack...
        if (!NavigationHelper.tryGotoSearchFragment(getSupportFragmentManager())) {
            // ...go to the main fragment
            NavigationHelper.gotoMainFragment(getSupportFragmentManager());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: menu = [" + menu + "]");
        }
        super.onCreateOptionsMenu(menu);

        final Fragment fragment
                = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (!(fragment instanceof SearchFragment)) {
            findViewById(R.id.toolbar).findViewById(R.id.toolbar_search_container)
                    .setVisibility(View.GONE);
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(false);
        }

        updateDrawerNavigation();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (DEBUG) {
            Log.d(TAG, "onOptionsItemSelected() called with: item = [" + item + "]");
        }
        final int id = item.getItemId();

        switch (id) {
            case android.R.id.home:
                onHomeButtonPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    private void initFragments() {
        if (DEBUG) {
            Log.d(TAG, "initFragments() called");
        }
        StateSaver.clearStateFiles();
        if (getIntent() != null && getIntent().hasExtra(Constants.KEY_LINK_TYPE)) {
            // When user watch a video inside popup and then tries to open the video in main player
            // while the app is closed he will see a blank fragment on place of kiosk.
            // Let's open it first
            if (getSupportFragmentManager().getBackStackEntryCount() == 0) {
                NavigationHelper.openMainFragment(getSupportFragmentManager());
            }

            handleIntent(getIntent());
        } else {
            NavigationHelper.gotoMainFragment(getSupportFragmentManager());
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void updateDrawerNavigation() {
        if (getSupportActionBar() == null) {
            return;
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);

        final Fragment fragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_holder);
        if (fragment instanceof MainFragment) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            if (toggle != null) {
                toggle.syncState();
                toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNDEFINED);
            }
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onHomeButtonPressed());
        }
    }

    private void handleIntent(final Intent intent) {
        try {
            if (DEBUG) {
                Log.d(TAG, "handleIntent() called with: intent = [" + intent + "]");
            }

            if (intent.hasExtra(Constants.KEY_LINK_TYPE)) {
                final String url = intent.getStringExtra(Constants.KEY_URL);
                final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                final String title = intent.getStringExtra(Constants.KEY_TITLE);
                switch (((StreamingService.LinkType) intent
                        .getSerializableExtra(Constants.KEY_LINK_TYPE))) {
                    case STREAM:
                        final boolean autoPlay = intent
                                .getBooleanExtra(VideoDetailFragment.AUTO_PLAY, false);
                        final String intentCacheKey = intent
                                .getStringExtra(VideoPlayer.PLAY_QUEUE_KEY);
                        final PlayQueue playQueue = intentCacheKey != null
                                ? SerializedCache.getInstance()
                                .take(intentCacheKey, PlayQueue.class)
                                : null;
                        NavigationHelper.openVideoDetailFragment(getSupportFragmentManager(),
                                serviceId, url, title, autoPlay, playQueue);
                        break;
                    case CHANNEL:
                        NavigationHelper.openChannelFragment(getSupportFragmentManager(),
                                serviceId,
                                url,
                                title);
                        break;
                    case PLAYLIST:
                        NavigationHelper.openPlaylistFragment(getSupportFragmentManager(),
                                serviceId,
                                url,
                                title);
                        break;
                }
            } else if (intent.hasExtra(Constants.KEY_OPEN_SEARCH)) {
                String searchString = intent.getStringExtra(Constants.KEY_SEARCH_STRING);
                if (searchString == null) {
                    searchString = "";
                }
                final int serviceId = intent.getIntExtra(Constants.KEY_SERVICE_ID, 0);
                NavigationHelper.openSearchFragment(
                        getSupportFragmentManager(),
                        serviceId,
                        searchString);

            } else {
                NavigationHelper.gotoMainFragment(getSupportFragmentManager());
            }
        } catch (final Exception e) {
            ErrorActivity.reportUiError(this, e);
        }
    }
    /*
     * Utils
     * */

    private boolean bottomSheetHiddenOrCollapsed() {
        final FrameLayout bottomSheetLayout = findViewById(R.id.fragment_player_holder);
        final BottomSheetBehavior<FrameLayout> bottomSheetBehavior =
                BottomSheetBehavior.from(bottomSheetLayout);

        final int sheetState = bottomSheetBehavior.getState();
        return sheetState == BottomSheetBehavior.STATE_HIDDEN
                || sheetState == BottomSheetBehavior.STATE_COLLAPSED;
    }
}
