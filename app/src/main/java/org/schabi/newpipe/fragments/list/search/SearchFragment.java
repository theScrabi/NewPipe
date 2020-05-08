package org.schabi.newpipe.fragments.list.search;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.database.history.model.SearchHistoryEntry;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.fragments.BackPressable;
import org.schabi.newpipe.fragments.list.BaseListFragment;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AndroidTvUtils;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.Constants;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

import icepick.State;
import io.reactivex.Flowable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static androidx.recyclerview.widget.ItemTouchHelper.Callback.makeMovementFlags;
import static java.util.Arrays.asList;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class SearchFragment extends BaseListFragment<SearchInfo, ListExtractor.InfoItemsPage>
        implements BackPressable {
    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * The suggestions will only be fetched from network if the query meet this threshold (>=).
     * (local ones will be fetched regardless of the length)
     */
    private static final int THRESHOLD_NETWORK_SUGGESTION = 1;

    /**
     * How much time have to pass without emitting a item (i.e. the user stop typing)
     * to fetch/show the suggestions, in milliseconds.
     */
    private static final int SUGGESTIONS_DEBOUNCE = 120; //ms
    private final PublishSubject<String> suggestionPublisher = PublishSubject.create();

    @State
    int filterItemCheckedId = -1;

    @State
    protected int serviceId = Constants.NO_SERVICE_ID;

    // these three represents the current search query
    @State
    String searchString;

    /**
     * No content filter should add like contentFilter = all
     * be aware of this when implementing an extractor.
     */
    @State
    String[] contentFilter = new String[0];

    @State
    String sortFilter;

    // these represents the last search
    @State
    String lastSearchedString;

    @State
    boolean wasSearchFocused = false;

    private Map<Integer, String> menuItemToFilterName;
    private StreamingService service;
    private String currentPageUrl;
    private String nextPageUrl;
    private String contentCountry;
    private boolean isSuggestionsEnabled = true;

    private Disposable searchDisposable;
    private Disposable suggestionDisposable;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private SuggestionListAdapter suggestionListAdapter;
    private HistoryRecordManager historyRecordManager;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private View searchToolbarContainer;
    private EditText searchEditText;
    private View searchClear;

    private View suggestionsPanel;
    private RecyclerView suggestionsRecyclerView;

    /*////////////////////////////////////////////////////////////////////////*/

    private TextWatcher textWatcher;

    public static SearchFragment getInstance(final int serviceId, final String searchString) {
        SearchFragment searchFragment = new SearchFragment();
        searchFragment.setQuery(serviceId, searchString, new String[0], "");

        if (!TextUtils.isEmpty(searchString)) {
            searchFragment.setSearchOnResume();
        }

        return searchFragment;
    }

    /**
     * Set wasLoading to true so when the fragment onResume is called, the initial search is done.
     */
    private void setSearchOnResume() {
        wasLoading.set(true);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);

        suggestionListAdapter = new SuggestionListAdapter(activity);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        boolean isSearchHistoryEnabled = preferences
                .getBoolean(getString(R.string.enable_search_history_key), true);
        suggestionListAdapter.setShowSuggestionHistory(isSearchHistoryEnabled);

        historyRecordManager = new HistoryRecordManager(context);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        isSuggestionsEnabled = preferences
                .getBoolean(getString(R.string.show_search_suggestions_key), true);
        contentCountry = preferences.getString(getString(R.string.content_country_key),
                getString(R.string.default_localization_key));
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        showSearchOnStart();
        initSearchListeners();
    }

    @Override
    public void onPause() {
        super.onPause();

        wasSearchFocused = searchEditText.hasFocus();

        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        if (suggestionDisposable != null) {
            suggestionDisposable.dispose();
        }
        if (disposables != null) {
            disposables.clear();
        }
        hideKeyboardSearch();
    }

    @Override
    public void onResume() {
        if (DEBUG) {
            Log.d(TAG, "onResume() called");
        }
        super.onResume();

        try {
            service = NewPipe.getService(serviceId);
        } catch (Exception e) {
            ErrorActivity.reportError(getActivity(), e, getActivity().getClass(),
                    getActivity().findViewById(android.R.id.content),
                    ErrorActivity.ErrorInfo.make(UserAction.UI_ERROR,
                            "",
                            "", R.string.general_error));
        }

        if (!TextUtils.isEmpty(searchString)) {
            if (wasLoading.getAndSet(false)) {
                search(searchString, contentFilter, sortFilter);
            } else if (infoListAdapter.getItemsList().size() == 0) {
                if (savedState == null) {
                    search(searchString, contentFilter, sortFilter);
                } else if (!isLoading.get() && !wasSearchFocused) {
                    infoListAdapter.clearStreamItemList();
                    showEmptyState();
                }
            }
        }

        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) {
            initSuggestionObserver();
        }

        if (TextUtils.isEmpty(searchString) || wasSearchFocused) {
            showKeyboardSearch();
            showSuggestionsPanel();
        } else {
            hideKeyboardSearch();
            hideSuggestionsPanel();
        }
        wasSearchFocused = false;
    }

    @Override
    public void onDestroyView() {
        if (DEBUG) {
            Log.d(TAG, "onDestroyView() called");
        }
        unsetSearchListeners();
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        if (suggestionDisposable != null) {
            suggestionDisposable.dispose();
        }
        if (disposables != null) {
            disposables.clear();
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        switch (requestCode) {
            case ReCaptchaActivity.RECAPTCHA_REQUEST:
                if (resultCode == Activity.RESULT_OK
                        && !TextUtils.isEmpty(searchString)) {
                    search(searchString, contentFilter, sortFilter);
                } else {
                    Log.e(TAG, "ReCaptcha failed");
                }
                break;

            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);
        suggestionsPanel = rootView.findViewById(R.id.suggestions_panel);
        suggestionsRecyclerView = rootView.findViewById(R.id.suggestions_list);
        suggestionsRecyclerView.setAdapter(suggestionListAdapter);
        new ItemTouchHelper(new ItemTouchHelper.Callback() {
            @Override
            public int getMovementFlags(@NonNull final RecyclerView recyclerView,
                                        @NonNull final RecyclerView.ViewHolder viewHolder) {
                return getSuggestionMovementFlags(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder viewHolder,
                                  @NonNull final RecyclerView.ViewHolder viewHolder1) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder, final int i) {
                onSuggestionItemSwiped(viewHolder, i);
            }
        }).attachToRecyclerView(suggestionsRecyclerView);

        searchToolbarContainer = activity.findViewById(R.id.toolbar_search_container);
        searchEditText = searchToolbarContainer.findViewById(R.id.toolbar_search_edit_text);
        searchClear = searchToolbarContainer.findViewById(R.id.toolbar_search_clear);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // State Saving
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void writeTo(final Queue<Object> objectsToSave) {
        super.writeTo(objectsToSave);
        objectsToSave.add(currentPageUrl);
        objectsToSave.add(nextPageUrl);
    }

    @Override
    public void readFrom(@NonNull final Queue<Object> savedObjects) throws Exception {
        super.readFrom(savedObjects);
        currentPageUrl = (String) savedObjects.poll();
        nextPageUrl = (String) savedObjects.poll();
    }

    @Override
    public void onSaveInstanceState(final Bundle bundle) {
        searchString = searchEditText != null
                ? searchEditText.getText().toString()
                : searchString;
        super.onSaveInstanceState(bundle);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init's
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void reloadContent() {
        if (!TextUtils.isEmpty(searchString)
                || (searchEditText != null && !TextUtils.isEmpty(searchEditText.getText()))) {
            search(!TextUtils.isEmpty(searchString)
                    ? searchString
                    : searchEditText.getText().toString(), this.contentFilter, "");
        } else {
            if (searchEditText != null) {
                searchEditText.setText("");
                showKeyboardSearch();
            }
            animateView(errorPanelRoot, false, 200);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(false);
            supportActionBar.setDisplayHomeAsUpEnabled(true);
        }

        menuItemToFilterName = new HashMap<>();

        int itemId = 0;
        boolean isFirstItem = true;
        final Context c = getContext();
        for (String filter : service.getSearchQHFactory().getAvailableContentFilter()) {
            if (filter.equals("music_songs")) {
                MenuItem musicItem = menu.add(2,
                        itemId++,
                        0,
                        "YouTube Music");
                musicItem.setEnabled(false);
            }
            menuItemToFilterName.put(itemId, filter);
            MenuItem item = menu.add(1,
                    itemId++,
                    0,
                    ServiceHelper.getTranslatedFilterString(filter, c));
            if (isFirstItem) {
                item.setChecked(true);
                isFirstItem = false;
            }
        }
        menu.setGroupCheckable(1, true, true);

        restoreFilterChecked(menu, filterItemCheckedId);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        List<String> cf = new ArrayList<>(1);
        cf.add(menuItemToFilterName.get(item.getItemId()));
        changeContentFilter(item, cf);

        return true;
    }

    private void restoreFilterChecked(final Menu menu, final int itemId) {
        if (itemId != -1) {
            MenuItem item = menu.findItem(itemId);
            if (item == null) {
                return;
            }

            item.setChecked(true);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search
    //////////////////////////////////////////////////////////////////////////*/

    private void showSearchOnStart() {
        if (DEBUG) {
            Log.d(TAG, "showSearchOnStart() called, searchQuery → "
                    + searchString
                    + ", lastSearchedQuery → "
                    + lastSearchedString);
        }
        searchEditText.setText(searchString);

        if (TextUtils.isEmpty(searchString) || TextUtils.isEmpty(searchEditText.getText())) {
            searchToolbarContainer.setTranslationX(100);
            searchToolbarContainer.setAlpha(0f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
            searchToolbarContainer.animate()
                    .translationX(0)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).start();
        } else {
            searchToolbarContainer.setTranslationX(0);
            searchToolbarContainer.setAlpha(1f);
            searchToolbarContainer.setVisibility(View.VISIBLE);
        }
    }

    private void initSearchListeners() {
        if (DEBUG) {
            Log.d(TAG, "initSearchListeners() called");
        }
        searchClear.setOnClickListener(v -> {
            if (DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }
            if (TextUtils.isEmpty(searchEditText.getText())) {
                NavigationHelper.gotoMainFragment(getFragmentManager());
                return;
            }

            searchEditText.setText("");
            suggestionListAdapter.setItems(new ArrayList<>());
            showKeyboardSearch();
        });

        TooltipCompat.setTooltipText(searchClear, getString(R.string.clear));

        searchEditText.setOnClickListener(v -> {
            if (DEBUG) {
                Log.d(TAG, "onClick() called with: v = [" + v + "]");
            }
            if (isSuggestionsEnabled && errorPanelRoot.getVisibility() != View.VISIBLE) {
                showSuggestionsPanel();
            }
            if (AndroidTvUtils.isTv(getContext())) {
                showKeyboardSearch();
            }
        });

        searchEditText.setOnFocusChangeListener((View v, boolean hasFocus) -> {
            if (DEBUG) {
                Log.d(TAG, "onFocusChange() called with: "
                        + "v = [" + v + "], hasFocus = [" + hasFocus + "]");
            }
            if (isSuggestionsEnabled && hasFocus
                    && errorPanelRoot.getVisibility() != View.VISIBLE) {
                showSuggestionsPanel();
            }
        });

        suggestionListAdapter.setListener(new SuggestionListAdapter.OnSuggestionItemSelected() {
            @Override
            public void onSuggestionItemSelected(final SuggestionItem item) {
                search(item.query, new String[0], "");
                searchEditText.setText(item.query);
            }

            @Override
            public void onSuggestionItemInserted(final SuggestionItem item) {
                searchEditText.setText(item.query);
                searchEditText.setSelection(searchEditText.getText().length());
            }

            @Override
            public void onSuggestionItemLongClick(final SuggestionItem item) {
                if (item.fromHistory) {
                    showDeleteSuggestionDialog(item);
                }
            }
        });

        if (textWatcher != null) {
            searchEditText.removeTextChangedListener(textWatcher);
        }
        textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start,
                                          final int count, final int after) { }

            @Override
            public void onTextChanged(final CharSequence s, final int start,
                                      final int before, final int count) { }

            @Override
            public void afterTextChanged(final Editable s) {
                String newText = searchEditText.getText().toString();
                suggestionPublisher.onNext(newText);
            }
        };
        searchEditText.addTextChangedListener(textWatcher);
        searchEditText.setOnEditorActionListener(
                (TextView v, int actionId, KeyEvent event) -> {
                    if (DEBUG) {
                        Log.d(TAG, "onEditorAction() called with: v = [" + v + "], "
                                + "actionId = [" + actionId + "], event = [" + event + "]");
                    }
                    if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                        hideKeyboardSearch();
                    } else if (event != null
                            && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                            || event.getAction() == EditorInfo.IME_ACTION_SEARCH)) {
                        search(searchEditText.getText().toString(), new String[0], "");
                        return true;
                    }
                    return false;
                });

        if (suggestionDisposable == null || suggestionDisposable.isDisposed()) {
            initSuggestionObserver();
        }
    }

    private void unsetSearchListeners() {
        if (DEBUG) {
            Log.d(TAG, "unsetSearchListeners() called");
        }
        searchClear.setOnClickListener(null);
        searchClear.setOnLongClickListener(null);
        searchEditText.setOnClickListener(null);
        searchEditText.setOnFocusChangeListener(null);
        searchEditText.setOnEditorActionListener(null);

        if (textWatcher != null) {
            searchEditText.removeTextChangedListener(textWatcher);
        }
        textWatcher = null;
    }

    private void showSuggestionsPanel() {
        if (DEBUG) {
            Log.d(TAG, "showSuggestionsPanel() called");
        }
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, true, 200);
    }

    private void hideSuggestionsPanel() {
        if (DEBUG) {
            Log.d(TAG, "hideSuggestionsPanel() called");
        }
        animateView(suggestionsPanel, AnimationUtils.Type.LIGHT_SLIDE_AND_ALPHA, false, 200);
    }

    private void showKeyboardSearch() {
        if (DEBUG) {
            Log.d(TAG, "showKeyboardSearch() called");
        }
        if (searchEditText == null) {
            return;
        }

        if (searchEditText.requestFocus()) {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(
                    Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchEditText, InputMethodManager.SHOW_FORCED);
        }
    }

    private void hideKeyboardSearch() {
        if (DEBUG) {
            Log.d(TAG, "hideKeyboardSearch() called");
        }
        if (searchEditText == null) {
            return;
        }

        InputMethodManager imm = (InputMethodManager) activity
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchEditText.getWindowToken(),
                InputMethodManager.RESULT_UNCHANGED_SHOWN);

        searchEditText.clearFocus();
    }

    private void showDeleteSuggestionDialog(final SuggestionItem item) {
        if (activity == null || historyRecordManager == null || suggestionPublisher == null
                || searchEditText == null || disposables == null) {
            return;
        }
        final String query = item.query;
        new AlertDialog.Builder(activity)
                .setTitle(query)
                .setMessage(R.string.delete_item_search_history)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    final Disposable onDelete = historyRecordManager.deleteSearchHistory(query)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                    howManyDeleted -> suggestionPublisher
                                            .onNext(searchEditText.getText().toString()),
                                    throwable -> showSnackBarError(throwable,
                                            UserAction.DELETE_FROM_HISTORY, "none",
                                            "Deleting item failed", R.string.general_error));
                    disposables.add(onDelete);
                })
                .show();
    }

    @Override
    public boolean onBackPressed() {
        if (suggestionsPanel.getVisibility() == View.VISIBLE
                && infoListAdapter.getItemsList().size() > 0
                && !isLoading.get()) {
            hideSuggestionsPanel();
            hideKeyboardSearch();
            searchEditText.setText(lastSearchedString);
            return true;
        }
        return false;
    }

    public void giveSearchEditTextFocus() {
        showKeyboardSearch();
    }

    private void initSuggestionObserver() {
        if (DEBUG) {
            Log.d(TAG, "initSuggestionObserver() called");
        }
        if (suggestionDisposable != null) {
            suggestionDisposable.dispose();
        }

        final Observable<String> observable = suggestionPublisher
                .debounce(SUGGESTIONS_DEBOUNCE, TimeUnit.MILLISECONDS)
                .startWith(searchString != null
                        ? searchString
                        : "")
                .filter(ss -> isSuggestionsEnabled);

        suggestionDisposable = observable
                .switchMap(query -> {
                    final Flowable<List<SearchHistoryEntry>> flowable = historyRecordManager
                            .getRelatedSearches(query, 3, 25);
                    final Observable<List<SuggestionItem>> local = flowable.toObservable()
                            .map(searchHistoryEntries -> {
                                List<SuggestionItem> result = new ArrayList<>();
                                for (SearchHistoryEntry entry : searchHistoryEntries) {
                                    result.add(new SuggestionItem(true, entry.getSearch()));
                                }
                                return result;
                            });

                    if (query.length() < THRESHOLD_NETWORK_SUGGESTION) {
                        // Only pass through if the query length
                        // is equal or greater than THRESHOLD_NETWORK_SUGGESTION
                        return local.materialize();
                    }

                    final Observable<List<SuggestionItem>> network = ExtractorHelper
                            .suggestionsFor(serviceId, query)
                            .toObservable()
                            .map(strings -> {
                                List<SuggestionItem> result = new ArrayList<>();
                                for (String entry : strings) {
                                    result.add(new SuggestionItem(false, entry));
                                }
                                return result;
                            });

                    return Observable.zip(local, network, (localResult, networkResult) -> {
                        List<SuggestionItem> result = new ArrayList<>();
                        if (localResult.size() > 0) {
                            result.addAll(localResult);
                        }

                        // Remove duplicates
                        final Iterator<SuggestionItem> iterator = networkResult.iterator();
                        while (iterator.hasNext() && localResult.size() > 0) {
                            final SuggestionItem next = iterator.next();
                            for (SuggestionItem item : localResult) {
                                if (item.query.equals(next.query)) {
                                    iterator.remove();
                                    break;
                                }
                            }
                        }

                        if (networkResult.size() > 0) {
                            result.addAll(networkResult);
                        }
                        return result;
                    }).materialize();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(listNotification -> {
                    if (listNotification.isOnNext()) {
                        handleSuggestions(listNotification.getValue());
                    } else if (listNotification.isOnError()) {
                        onSuggestionError(listNotification.getError());
                    }
                });
    }

    @Override
    protected void doInitialLoadLogic() {
        // no-op
    }

    private void search(final String ss, final String[] cf, final String sf) {
        if (DEBUG) {
            Log.d(TAG, "search() called with: query = [" + ss + "]");
        }
        if (ss.isEmpty()) {
            return;
        }

        try {
            final StreamingService streamingService = NewPipe.getServiceByUrl(ss);
            if (streamingService != null) {
                showLoading();
                disposables.add(Observable
                        .fromCallable(() ->
                                NavigationHelper.getIntentByLink(activity, streamingService, ss))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(intent -> {
                            getFragmentManager().popBackStackImmediate();
                            activity.startActivity(intent);
                        }, throwable ->
                                showError(getString(R.string.url_not_supported_toast), false)));
                return;
            }
        } catch (Exception ignored) {
            // Exception occurred, it's not a url
        }

        lastSearchedString = this.searchString;
        this.searchString = ss;
        infoListAdapter.clearStreamItemList();
        hideSuggestionsPanel();
        hideKeyboardSearch();

        historyRecordManager.onSearched(serviceId, ss)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ignored -> {
                        },
                        error -> showSnackBarError(error, UserAction.SEARCHED,
                                NewPipe.getNameOfService(serviceId), ss, 0)
                );
        suggestionPublisher.onNext(ss);
        startLoading(false);
    }

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);
        if (disposables != null) {
            disposables.clear();
        }
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        searchDisposable = ExtractorHelper.searchFor(serviceId,
                searchString,
                Arrays.asList(contentFilter),
                sortFilter)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((searchResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleResult, this::onError);

    }

    @Override
    protected void loadMoreItems() {
        if (nextPageUrl == null || nextPageUrl.isEmpty()) {
            return;
        }
        isLoading.set(true);
        showListFooter(true);
        if (searchDisposable != null) {
            searchDisposable.dispose();
        }
        searchDisposable = ExtractorHelper.getMoreSearchItems(
                serviceId,
                searchString,
                asList(contentFilter),
                sortFilter,
                nextPageUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnEvent((nextItemsResult, throwable) -> isLoading.set(false))
                .subscribe(this::handleNextItems, this::onError);
    }

    @Override
    protected boolean hasMoreItems() {
        // TODO: No way to tell if search has more items in the moment
        return true;
    }

    @Override
    protected void onItemSelected(final InfoItem selectedItem) {
        super.onItemSelected(selectedItem);
        hideKeyboardSearch();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    private void changeContentFilter(final MenuItem item, final List<String> cf) {
        this.filterItemCheckedId = item.getItemId();
        item.setChecked(true);

        this.contentFilter = new String[]{cf.get(0)};

        if (!TextUtils.isEmpty(searchString)) {
            search(searchString, this.contentFilter, sortFilter);
        }
    }

    private void setQuery(final int sid, final String ss, final String[] cf, final String sf) {
        this.serviceId = sid;
        this.searchString = searchString;
        this.contentFilter = cf;
        this.sortFilter = sf;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion Results
    //////////////////////////////////////////////////////////////////////////*/

    public void handleSuggestions(@NonNull final List<SuggestionItem> suggestions) {
        if (DEBUG) {
            Log.d(TAG, "handleSuggestions() called with: suggestions = [" + suggestions + "]");
        }
        suggestionsRecyclerView.smoothScrollToPosition(0);
        suggestionsRecyclerView.post(() -> suggestionListAdapter.setItems(suggestions));

        if (errorPanelRoot.getVisibility() == View.VISIBLE) {
            hideLoading();
        }
    }

    public void onSuggestionError(final Throwable exception) {
        if (DEBUG) {
            Log.d(TAG, "onSuggestionError() called with: exception = [" + exception + "]");
        }
        if (super.onError(exception)) {
            return;
        }

        int errorId = exception instanceof ParsingException
                ? R.string.parsing_error
                : R.string.general_error;
        onUnrecoverableError(exception, UserAction.GET_SUGGESTIONS,
                NewPipe.getNameOfService(serviceId), searchString, errorId);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void hideLoading() {
        super.hideLoading();
        showListFooter(false);
    }

    @Override
    public void showError(final String message, final boolean showRetryButton) {
        super.showError(message, showRetryButton);
        hideSuggestionsPanel();
        hideKeyboardSearch();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Search Results
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void handleResult(@NonNull final SearchInfo result) {
        final List<Throwable> exceptions = result.getErrors();
        if (!exceptions.isEmpty()
                && !(exceptions.size() == 1
                && exceptions.get(0) instanceof SearchExtractor.NothingFoundException)) {
            showSnackBarError(result.getErrors(), UserAction.SEARCHED,
                    NewPipe.getNameOfService(serviceId), searchString, 0);
        }

        lastSearchedString = searchString;
        nextPageUrl = result.getNextPageUrl();
        currentPageUrl = result.getUrl();

        if (infoListAdapter.getItemsList().size() == 0) {
            if (!result.getRelatedItems().isEmpty()) {
                infoListAdapter.addInfoItemList(result.getRelatedItems());
            } else {
                infoListAdapter.clearStreamItemList();
                showEmptyState();
                return;
            }
        }

        super.handleResult(result);
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage result) {
        showListFooter(false);
        currentPageUrl = result.getNextPageUrl();
        infoListAdapter.addInfoItemList(result.getItems());
        nextPageUrl = result.getNextPageUrl();

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(), UserAction.SEARCHED,
                    NewPipe.getNameOfService(serviceId),
                    "\"" + searchString + "\" → page: " + nextPageUrl, 0);
        }
        super.handleNextItems(result);
    }

    @Override
    protected boolean onError(final Throwable exception) {
        if (super.onError(exception)) {
            return true;
        }

        if (exception instanceof SearchExtractor.NothingFoundException) {
            infoListAdapter.clearStreamItemList();
            showEmptyState();
        } else {
            int errorId = exception instanceof ParsingException
                    ? R.string.parsing_error
                    : R.string.general_error;
            onUnrecoverableError(exception, UserAction.SEARCHED,
                    NewPipe.getNameOfService(serviceId), searchString, errorId);
        }

        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Suggestion item touch helper
    //////////////////////////////////////////////////////////////////////////*/

    public int getSuggestionMovementFlags(@NonNull final RecyclerView recyclerView,
                                          @NonNull final RecyclerView.ViewHolder viewHolder) {
        final int position = viewHolder.getAdapterPosition();
        final SuggestionItem item = suggestionListAdapter.getItem(position);
        return item.fromHistory ? makeMovementFlags(0,
                ItemTouchHelper.START | ItemTouchHelper.END) : 0;
    }

    public void onSuggestionItemSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                       final int i) {
        final int position = viewHolder.getAdapterPosition();
        final String query = suggestionListAdapter.getItem(position).query;
        final Disposable onDelete = historyRecordManager.deleteSearchHistory(query)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        howManyDeleted -> suggestionPublisher
                                .onNext(searchEditText.getText().toString()),
                        throwable -> showSnackBarError(throwable,
                                UserAction.DELETE_FROM_HISTORY, "none",
                                "Deleting item failed", R.string.general_error));
        disposables.add(onDelete);
    }
}
