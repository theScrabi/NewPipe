package org.schabi.newpipe.fragments.list.videos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.viewbinding.ViewBinding;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.RelatedItemsHeaderBinding;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.util.RelatedItemInfo;

import java.io.Serializable;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

public class RelatedItemsFragment extends BaseListInfoFragment<RelatedItemInfo>
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String INFO_KEY = "related_info_key";
    private final CompositeDisposable disposables = new CompositeDisposable();
    private RelatedItemInfo relatedItemInfo;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private RelatedItemsHeaderBinding headerBinding;

    public static RelatedItemsFragment getInstance(final StreamInfo info) {
        final RelatedItemsFragment instance = new RelatedItemsFragment();
        instance.setInitialData(info);
        return instance;
    }

    public RelatedItemsFragment() {
        super(UserAction.REQUESTED_STREAM);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(@NonNull final Context context) {
        super.onAttach(context);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_related_items, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disposables.clear();
    }

    @Override
    public void onDestroyView() {
        headerBinding = null;
        super.onDestroyView();
    }

    @Override
    protected ViewBinding getListHeader() {
        if (relatedItemInfo != null && relatedItemInfo.getRelatedItems() != null) {
            headerBinding = RelatedItemsHeaderBinding
                    .inflate(activity.getLayoutInflater(), itemsList, false);

            final SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(requireContext());
            final boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
            headerBinding.autoplaySwitch.setChecked(autoplay);
            headerBinding.autoplaySwitch.setOnCheckedChangeListener((compoundButton, b) ->
                    PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                            .putBoolean(getString(R.string.auto_queue_key), b).apply());
            return headerBinding;
        } else {
            return null;
        }
    }

    @Override
    protected Single<ListExtractor.InfoItemsPage> loadMoreItemsLogic() {
        return Single.fromCallable(ListExtractor.InfoItemsPage::emptyPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<RelatedItemInfo> loadResult(final boolean forceLoad) {
        return Single.fromCallable(() -> relatedItemInfo);
    }

    @Override
    public void showLoading() {
        super.showLoading();
        if (headerBinding != null) {
            headerBinding.getRoot().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void handleResult(@NonNull final RelatedItemInfo result) {
        super.handleResult(result);

        if (headerBinding != null) {
            headerBinding.getRoot().setVisibility(View.VISIBLE);
        }
        ViewUtils.slideUp(requireView(), 120, 96, 0.06f);

        disposables.clear();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) {
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
    }

    private void setInitialData(final StreamInfo info) {
        super.setInitialData(info.getServiceId(), info.getUrl(), info.getName());
        if (this.relatedItemInfo == null) {
            this.relatedItemInfo = RelatedItemInfo.getInfo(info);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(INFO_KEY, relatedItemInfo);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        final Serializable serializable = savedState.getSerializable(INFO_KEY);
        if (serializable instanceof RelatedItemInfo) {
            this.relatedItemInfo = (RelatedItemInfo) serializable;
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences,
                                          final String s) {
        final SharedPreferences pref =
                PreferenceManager.getDefaultSharedPreferences(requireContext());
        final boolean autoplay = pref.getBoolean(getString(R.string.auto_queue_key), false);
        if (headerBinding != null) {
            headerBinding.autoplaySwitch.setChecked(autoplay);
        }
    }

    @Override
    protected boolean isGridLayout() {
        return false;
    }
}
