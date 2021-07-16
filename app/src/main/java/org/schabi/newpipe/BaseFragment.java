package org.schabi.newpipe;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import icepick.Icepick;
import icepick.State;
import leakcanary.AppWatcher;

public abstract class BaseFragment extends Fragment {
    protected final String TAG = getClass().getSimpleName() + "@" + Integer.toHexString(hashCode());
    protected final boolean DEBUG = MainActivity.DEBUG;
    protected AppCompatActivity activity;
    //These values are used for controlling fragments when they are part of the frontpage
    @State
    protected boolean useAsFrontPage = false;
    private boolean mIsVisibleToUser = false;

    public void useAsFrontPage(final boolean value) {
        useAsFrontPage = value;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's Lifecycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
        activity = (AppCompatActivity) context;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activity = null;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        if (DEBUG) {
            Log.d(TAG, "onCreate() called with: "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }
        super.onCreate(savedInstanceState);
        Icepick.restoreInstanceState(this, savedInstanceState);
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }
    }


    @Override
    public void onViewCreated(@NonNull final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        if (DEBUG) {
            Log.d(TAG, "onViewCreated() called with: "
                    + "rootView = [" + rootView + "], "
                    + "savedInstanceState = [" + savedInstanceState + "]");
        }
        initViews(rootView, savedInstanceState);
        initListeners();
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }

    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        AppWatcher.INSTANCE.getObjectWatcher().watch(this);
    }

    @Override
    public void setUserVisibleHint(final boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        mIsVisibleToUser = isVisibleToUser;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    protected void initViews(final View rootView, final Bundle savedInstanceState) {
    }

    protected void initListeners() {
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    public void setTitle(final String title) {
        if (DEBUG) {
            Log.d(TAG, "setTitle() called with: title = [" + title + "]");
        }
        if ((!useAsFrontPage || mIsVisibleToUser)
                && (activity != null && activity.getSupportActionBar() != null)) {
            activity.getSupportActionBar().setDisplayShowTitleEnabled(true);
            activity.getSupportActionBar().setTitle(title);
        }
    }

    protected FragmentManager getFM() {
        return getParentFragment() == null
                ? getFragmentManager()
                : getParentFragment().getFragmentManager();
    }
}
