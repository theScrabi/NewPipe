package org.schabi.newpipe.fragments.list;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;

import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.views.NewPipeRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import icepick.State;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class BaseListInfoFragment<I extends ListInfo>
        extends BaseListFragment<I, ListExtractor.InfoItemsPage> {
    @State
    protected int serviceId = Constants.NO_SERVICE_ID;
    @State
    protected String name;
    @State
    protected String url;

    private final UserAction errorUserAction;
    protected I currentInfo;
    protected Page currentNextPage;
    protected Disposable currentWorker;

    protected BaseListInfoFragment(final UserAction errorUserAction) {
        this.errorUserAction = errorUserAction;
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        setTitle(name);
        showListFooter(hasMoreItems());
    }

    @Override
    public void onPause() {
        super.onPause();
        if (currentWorker != null) {
            currentWorker.dispose();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Check if it was loading when the fragment was stopped/paused,
        if (wasLoading.getAndSet(false)) {
            if (hasMoreItems() && infoListAdapter.getItemsList().size() > 0) {
                loadMoreItems();
            } else {
                doInitialLoadLogic();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (currentWorker != null) {
            currentWorker.dispose();
            currentWorker = null;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(currentInfo);
        objectsToSave.add(currentNextPage);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        currentInfo = (I) savedObjects.poll();
        currentNextPage = (Page) savedObjects.poll();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    protected void doInitialLoadLogic() {
        if (DEBUG) {
            Log.d(TAG, "doInitialLoadLogic() called");
        }
        if (currentInfo == null) {
            startLoading(false);
        } else {
            handleResult(currentInfo);
        }
    }

    /**
     * Implement the logic to load the info from the network.<br/>
     * You can use the default implementations from {@link org.schabi.newpipe.util.ExtractorHelper}.
     *
     * @param forceLoad allow or disallow the result to come from the cache
     * @return Rx {@link Single} containing the {@link ListInfo}
     */
    protected abstract Single<I> loadResult(boolean forceLoad);

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        showListFooter(false);
        infoListAdapter.clearStreamItemList();

        currentInfo = null;
        if (currentWorker != null) {
            currentWorker.dispose();
        }
        currentWorker = loadResult(forceLoad)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((@NonNull I result) -> {
                    isLoading.set(false);
                    currentInfo = result;
                    currentNextPage = result.getNextPage();
                    handleResult(result);
                }, throwable ->
                        showError(new ErrorInfo(throwable, errorUserAction,
                                "Start loading: " + url, serviceId)));
    }

    /**
     * Implement the logic to load more items.
     * <p>You can use the default implementations
     * from {@link org.schabi.newpipe.util.ExtractorHelper}.</p>
     *
     * @return Rx {@link Single} containing the {@link ListExtractor.InfoItemsPage}
     */
    protected abstract Single<ListExtractor.InfoItemsPage> loadMoreItemsLogic();

    protected void loadMoreItems() {
        isLoading.set(true);

        if (currentWorker != null) {
            currentWorker.dispose();
        }

        forbidDownwardFocusScroll();

        currentWorker = loadMoreItemsLogic()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doFinally(this::allowDownwardFocusScroll)
                .subscribe((@NonNull ListExtractor.InfoItemsPage InfoItemsPage) -> {
                    isLoading.set(false);
                    handleNextItems(InfoItemsPage);
                }, (@NonNull Throwable throwable) ->
                        dynamicallyShowErrorPanelOrSnackbar(new ErrorInfo(throwable,
                                errorUserAction, "Loading more items: " + url, serviceId)));
    }

    private void forbidDownwardFocusScroll() {
        if (itemsList instanceof NewPipeRecyclerView) {
            ((NewPipeRecyclerView) itemsList).setFocusScrollAllowed(false);
        }
    }

    private void allowDownwardFocusScroll() {
        if (itemsList instanceof NewPipeRecyclerView) {
            ((NewPipeRecyclerView) itemsList).setFocusScrollAllowed(true);
        }
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage result) {
        super.handleNextItems(result);

        currentNextPage = result.getNextPage();
        infoListAdapter.addInfoItemList(result.getItems());

        showListFooter(hasMoreItems());

        if (!result.getErrors().isEmpty()) {
            dynamicallyShowErrorPanelOrSnackbar(new ErrorInfo(result.getErrors(), errorUserAction,
                    "Get next items of: " + url, serviceId));
        }
    }

    @Override
    protected boolean hasMoreItems() {
        return Page.isValid(currentNextPage);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final I result) {
        super.handleResult(result);

        name = result.getName();
        setTitle(name);

        if (infoListAdapter.getItemsList().isEmpty()) {
            if (result.getRelatedItems().size() > 0) {
                infoListAdapter.addInfoItemList(result.getRelatedItems());
                showListFooter(hasMoreItems());
            } else {
                infoListAdapter.clearStreamItemList();
                // showEmptyState should be called only if there is no item as
                // well as no header in infoListAdapter
                if (!(result instanceof ChannelInfo && infoListAdapter.getItemCount() == 1)) {
                    showEmptyState();
                }
            }
        }

        if (!result.getErrors().isEmpty()) {
            final List<Throwable> errors = new ArrayList<>(result.getErrors());
            // handling ContentNotSupportedException not to show the error but an appropriate string
            // so that crashes won't be sent uselessly and the user will understand what happened
            errors.removeIf(throwable -> throwable instanceof ContentNotSupportedException);

            if (!errors.isEmpty()) {
                dynamicallyShowErrorPanelOrSnackbar(new ErrorInfo(result.getErrors(),
                        errorUserAction, "Start loading: " + url, serviceId));
            }
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    protected void setInitialData(final int sid, final String u, final String title) {
        this.serviceId = sid;
        this.url = u;
        this.name = !TextUtils.isEmpty(title) ? title : "";
    }

    private void dynamicallyShowErrorPanelOrSnackbar(final ErrorInfo errorInfo) {
        if (infoListAdapter.getItemCount() == 0) {
            // show error panel only if no items already visible
            showError(errorInfo);
        } else {
            isLoading.set(false);
            showSnackBarError(errorInfo);
        }
    }
}
