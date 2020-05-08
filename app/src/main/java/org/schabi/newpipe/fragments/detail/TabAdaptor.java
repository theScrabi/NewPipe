package org.schabi.newpipe.fragments.detail;

import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import org.schabi.newpipe.util.LocalizeLayoutUtils;

import java.util.ArrayList;
import java.util.List;

public class TabAdaptor extends FragmentPagerAdapter {
    private final List<Fragment> mFragmentList = new ArrayList<>();
    private final List<String> mFragmentTitleList = new ArrayList<>();
    private final FragmentManager fragmentManager;
    private boolean isRTL;

    public TabAdaptor(final FragmentManager fm, final boolean isRTL) {
        super(fm);
        this.fragmentManager = fm;
        this.isRTL = isRTL;
    }

    @Override
    public Fragment getItem(final int position) {
        return mFragmentList.get(getLayoutPosition(position));
    }

    @Override
    public int getCount() {
        return mFragmentList.size();
    }

    public void addFragment(final Fragment fragment, final String title) {
        mFragmentList.add(fragment);
        mFragmentTitleList.add(title);
    }

    public void clearAllItems() {
        mFragmentList.clear();
        mFragmentTitleList.clear();
    }

    public void removeItem(final int position) {
        int newPosition = getLayoutPosition(position);

        mFragmentList.remove(newPosition == 0 ? 0 : newPosition - 1);
        mFragmentTitleList.remove(newPosition == 0 ? 0 : newPosition - 1);
    }

    public void updateItem(final int position, final Fragment fragment) {
        mFragmentList.set(getLayoutPosition(position), fragment);
    }

    public void updateItem(final String title, final Fragment fragment) {
        int index = getLayoutPosition(mFragmentTitleList.indexOf(title));
        if (index != -1) {
            updateItem(index, fragment);
        }
    }

    @Override
    public int getItemPosition(final Object object) {
        if (mFragmentList.contains(object)) {
            return getLayoutPosition(mFragmentList.indexOf(object));
        } else {
            return POSITION_NONE;
        }
    }

    public int getItemPositionByTitle(final String title) {
        return getLayoutPosition(mFragmentTitleList.indexOf(title));
    }

    @Nullable
    public String getItemTitle(final int position) {
        if (position < 0 || position >= mFragmentTitleList.size()) {
            return null;
        }

        return mFragmentTitleList.get(getLayoutPosition(position));
    }

    public void notifyDataSetUpdate() {
        notifyDataSetChanged();
    }

    @Override
    public void destroyItem(final ViewGroup container, final int position, final Object object) {
        fragmentManager.beginTransaction().remove((Fragment) object).commitNowAllowingStateLoss();
    }

    private int getLayoutPosition(final int position) {
        return LocalizeLayoutUtils.getLayoutPosition(this.isRTL, mFragmentList.size(), position);
    }
}
