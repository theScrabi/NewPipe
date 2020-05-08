package org.schabi.newpipe.fragments;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.LocalizeLayoutUtils;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {
    private ViewPager viewPager;
    private SelectedTabsPagerAdapter pagerAdapter;
    private ScrollableTabLayout tabLayout;

    private List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    private boolean previousYoutubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupTabs();
            } else {
                hasTabsChanged = true;
            }
        });

        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        previousYoutubeRestrictedModeEnabled =
                PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getBoolean(youtubeRestrictedModeEnabledKey, false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        tabLayout = rootView.findViewById(R.id.main_tab_layout);
        viewPager = rootView.findViewById(R.id.pager);

        tabLayout.setupWithViewPager(viewPager);
        tabLayout.addOnTabSelectedListener(this);

        setupTabs();
    }

    @Override
    public void onResume() {
        super.onResume();

        boolean youtubeRestrictedModeEnabled =
                PreferenceManager.getDefaultSharedPreferences(getContext())
                        .getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (previousYoutubeRestrictedModeEnabled != youtubeRestrictedModeEnabled) {
            previousYoutubeRestrictedModeEnabled = youtubeRestrictedModeEnabled;
            setupTabs();
        } else if (hasTabsChanged) {
            setupTabs();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (viewPager != null) {
            viewPager.setAdapter(null);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.main_fragment_menu, menu);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_search:
                try {
                    NavigationHelper.openSearchFragment(
                            getFragmentManager(),
                            ServiceHelper.getSelectedServiceId(activity),
                            "");
                } catch (Exception e) {
                    ErrorActivity.reportUiError((AppCompatActivity) getActivity(), e);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());

        boolean isRTL = LocalizeLayoutUtils.isRTL(this.getContext());

        if (pagerAdapter == null || !pagerAdapter.sameTabs(tabsList)) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList, isRTL);
        }

        viewPager.setAdapter(null);
        viewPager.setOffscreenPageLimit(tabsList.size());
        viewPager.setAdapter(pagerAdapter);

        if (isRTL) {
            tabLayout.getTabAt(pagerAdapter.getCount() - 1).select();
        }

        updateTabsIconAndDescription(isRTL);
        updateTitleForTab(viewPager.getCurrentItem());

        hasTabsChanged = false;
    }

    private void updateTabsIconAndDescription(final boolean isRTL) {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = tabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = isRTL
                        ? tabsList.get(tabsList.size() - 1 - i)
                        : tabsList.get(i);

                tabToSet.setIcon(tab.getTabIconRes(requireContext()));
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
            }
        }
    }

    private void updateTitleForTab(final int tabPosition) {
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    @Override
    public void onTabSelected(final TabLayout.Tab selectedTab) {
        if (DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        }
        updateTitleForTab(selectedTab.getPosition());
    }

    @Override
    public void onTabUnselected(final TabLayout.Tab tab) {
    }

    @Override
    public void onTabReselected(final TabLayout.Tab tab) {
        if (DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        }
        updateTitleForTab(tab.getPosition());
    }

    private static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;
        private boolean isRTL;

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList,
                                         final boolean isRTL) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
            this.isRTL = isRTL;
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(
                    LocalizeLayoutUtils.getLayoutPosition(
                            this.isRTL,
                            internalTabsList.size(),
                            position)
            );

            Throwable throwable = null;
            Fragment fragment = null;
            try {
                fragment = tab.getFragment(context);
            } catch (ExtractionException e) {
                throwable = e;
            }

            if (throwable != null) {
                ErrorActivity.reportError(context, throwable, null, null, ErrorActivity.ErrorInfo
                        .make(UserAction.UI_ERROR, "none", "", R.string.app_ui_crash));
                return new BlankFragment();
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            return fragment;
        }

        @Override
        public int getItemPosition(final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
