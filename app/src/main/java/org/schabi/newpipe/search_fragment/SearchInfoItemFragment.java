package org.schabi.newpipe.search_fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ProgressBar;
import android.widget.Toast;

import org.schabi.newpipe.ChannelActivity;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.search.SearchEngine;
import org.schabi.newpipe.extractor.search.SearchResult;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.detail.VideoItemDetailActivity;
import org.schabi.newpipe.detail.VideoItemDetailFragment;
import org.schabi.newpipe.info_list.InfoListAdapter;
import org.schabi.newpipe.util.NavStack;

import java.util.EnumSet;

import static android.app.Activity.RESULT_OK;
import static org.schabi.newpipe.ReCaptchaActivity.RECAPTCHA_REQUEST;

/**
 * Created by Christian Schabesberger on 02.08.16.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * SearchInfoItemFragment.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class SearchInfoItemFragment extends Fragment {

    private static final String TAG = SearchInfoItemFragment.class.toString();

    private EnumSet<SearchEngine.Filter> filter =
            EnumSet.of(SearchEngine.Filter.CHANNEL, SearchEngine.Filter.STREAM);

    /**
     * Listener for search queries
     */
    public class SearchQueryListener implements SearchView.OnQueryTextListener {

        @Override
        public boolean onQueryTextSubmit(String query) {
            Activity a = getActivity();
            try {
                search(query);

                // hide virtual keyboard
                InputMethodManager inputManager =
                        (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
                try {
                    //noinspection ConstantConditions
                    inputManager.hideSoftInputFromWindow(
                            a.getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    ErrorActivity.reportError(a, e, null,
                            a.findViewById(android.R.id.content),
                            ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                    NewPipe.getNameOfService(streamingServiceId),
                                    "Could not get widget with focus", R.string.general_error));
                }
                // clear focus
                // 1. to not open up the keyboard after switching back to this
                // 2. It's a workaround to a seeming bug by the Android OS it self, causing
                //    onQueryTextSubmit to trigger twice when focus is not cleared.
                // See: http://stackoverflow.com/questions/17874951/searchview-onquerytextsubmit-runs-twice-while-i-pressed-once
                a.getCurrentFocus().clearFocus();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            if (!newText.isEmpty()) {
                searchSuggestions(newText);
            }
            return true;
        }
    }

    private int streamingServiceId = -1;
    private String searchQuery = "";
    private boolean isLoading = false;

    private ProgressBar loadingIndicator = null;
    private int pageNumber = 0;
    private SuggestionListAdapter suggestionListAdapter = null;
    private InfoListAdapter infoListAdapter = null;
    private LinearLayoutManager streamInfoListLayoutManager = null;

    // savedInstanceBundle arguments
    private static final String QUERY = "query";
    private static final String STREAMING_SERVICE = "streaming_service";

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public SearchInfoItemFragment() {
    }

    @SuppressWarnings("unused")
    public static SearchInfoItemFragment newInstance(int streamingServiceId, String searchQuery) {
        Bundle args = new Bundle();
        args.putInt(STREAMING_SERVICE, streamingServiceId);
        args.putString(QUERY, searchQuery);
        SearchInfoItemFragment fragment = new SearchInfoItemFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        searchQuery = "";
        if (savedInstanceState != null) {
            searchQuery = savedInstanceState.getString(QUERY);
            streamingServiceId = savedInstanceState.getInt(STREAMING_SERVICE);
        } else {
            try {
                Bundle args = getArguments();
                if(args != null) {
                    searchQuery = args.getString(QUERY);
                    streamingServiceId = args.getInt(STREAMING_SERVICE);
                } else {
                    streamingServiceId = NewPipe.getIdOfService("Youtube");
                }
            } catch (Exception e) {
                e.printStackTrace();
                ErrorActivity.reportError(getActivity(), e, null,
                        getActivity().findViewById(android.R.id.content),
                        ErrorActivity.ErrorInfo.make(ErrorActivity.SEARCHED,
                                NewPipe.getNameOfService(streamingServiceId),
                                "", R.string.general_error));
            }
        }

        setHasOptionsMenu(true);

        SearchWorker sw = SearchWorker.getInstance();
        sw.setSearchWorkerResultListener(new SearchWorker.SearchWorkerResultListener() {
            @Override
            public void onResult(SearchResult result) {
                infoListAdapter.addInfoItemList(result.resultList);
                setDoneLoading();
            }

            @Override
            public void onNothingFound(int stringResource) {
                //setListShown(true);
                Toast.makeText(getActivity(), getString(stringResource),
                        Toast.LENGTH_SHORT).show();
                setDoneLoading();
            }

            @Override
            public void onError(String message) {
                //setListShown(true);
                Toast.makeText(getActivity(), message,
                        Toast.LENGTH_LONG).show();
                setDoneLoading();
            }

            @Override
            public void onReCaptchaChallenge() {
                Toast.makeText(getActivity(), "ReCaptcha Challenge requested",
                        Toast.LENGTH_LONG).show();

                // Starting ReCaptcha Challenge Activity
                startActivityForResult(
                        new Intent(getActivity(), ReCaptchaActivity.class),
                        RECAPTCHA_REQUEST);
            }
        });
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_searchinfoitem, container, false);

        Context context = view.getContext();
        loadingIndicator = (ProgressBar) view.findViewById(R.id.progressBar);
        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.list);
        streamInfoListLayoutManager = new LinearLayoutManager(context);
        recyclerView.setLayoutManager(streamInfoListLayoutManager);

        infoListAdapter = new InfoListAdapter(getActivity(),
                getActivity().findViewById(android.R.id.content));
        infoListAdapter.setFooter(inflater.inflate(R.layout.pignate_footer, recyclerView, false));
        infoListAdapter.showFooter(false);
        infoListAdapter.setOnStreamInfoItemSelectedListener(
                new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(String url, int serviceId) {
                NavStack.getInstance()
                    .openDetailActivity(getContext(), url, serviceId);
            }
        });
        infoListAdapter.setOnChannelInfoItemSelectedListener(new InfoItemBuilder.OnInfoItemSelectedListener() {
            @Override
            public void selected(String url, int serviceId) {
                NavStack.getInstance()
                        .openChannelActivity(getContext(), url, serviceId);
            }
        });
        recyclerView.setAdapter(infoListAdapter);
        recyclerView.clearOnScrollListeners();
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                int pastVisiblesItems, visibleItemCount, totalItemCount;
                super.onScrolled(recyclerView, dx, dy);
                if (dy > 0) //check for scroll down
                {
                    visibleItemCount = streamInfoListLayoutManager.getChildCount();
                    totalItemCount = streamInfoListLayoutManager.getItemCount();
                    pastVisiblesItems = streamInfoListLayoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount && !isLoading) {
                        pageNumber++;
                        search(searchQuery, pageNumber);
                    }
                }
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        if(!searchQuery.isEmpty()) {
            search(searchQuery);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(QUERY, searchQuery);
        outState.putInt(STREAMING_SERVICE, streamingServiceId);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.search_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        setupSearchView(searchView);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_filter_all:
                changeFilter(item, EnumSet.of(SearchEngine.Filter.STREAM, SearchEngine.Filter.CHANNEL));
                return true;
            case R.id.menu_filter_video:
                changeFilter(item, EnumSet.of(SearchEngine.Filter.STREAM));
                return true;
            case R.id.menu_filter_channel:
                changeFilter(item, EnumSet.of(SearchEngine.Filter.CHANNEL));
                return true;
            default:
                return false;
        }
    }

    private void changeFilter(MenuItem item, EnumSet<SearchEngine.Filter> filter) {
        this.filter = filter;
        item.setChecked(true);
        if(searchQuery != null && !searchQuery.isEmpty()) {
            Log.d(TAG, "Fuck+ " + searchQuery);
            search(searchQuery);
        }
    }

    private void setupSearchView(SearchView searchView) {
        suggestionListAdapter = new SuggestionListAdapter(getActivity());
        searchView.setSuggestionsAdapter(suggestionListAdapter);
        searchView.setOnSuggestionListener(new SearchSuggestionListener(searchView, suggestionListAdapter));
        searchView.setOnQueryTextListener(new SearchQueryListener());
        if (searchQuery != null && !searchQuery.isEmpty()) {
            searchView.setQuery(searchQuery, false);
            searchView.setIconifiedByDefault(false);
        }
    }

    private void search(String query) {
        infoListAdapter.clearSteamItemList();
        infoListAdapter.showFooter(false);
        pageNumber = 0;
        searchQuery = query;
        search(query, pageNumber);
        hideBackground();
        loadingIndicator.setVisibility(View.VISIBLE);
    }

    private void search(String query, int page) {
        isLoading = true;
        SearchWorker sw = SearchWorker.getInstance();
        sw.search(streamingServiceId,
                query,
                page,
                getActivity(),
                filter);
    }

    private void setDoneLoading() {
        this.isLoading = false;
        loadingIndicator.setVisibility(View.GONE);
        infoListAdapter.showFooter(true);
    }

    /**
     * Hides the "dummy" background when no results are shown
     */
    private void hideBackground() {
        View view = getView();
        if(view == null) return;
        view.findViewById(R.id.mainBG).setVisibility(View.GONE);
    }

    private void searchSuggestions(String query) {
        SuggestionSearchRunnable suggestionSearchRunnable =
                new SuggestionSearchRunnable(streamingServiceId, query, getActivity(), suggestionListAdapter);
        Thread suggestionThread = new Thread(suggestionSearchRunnable);
        suggestionThread.start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RECAPTCHA_REQUEST:
                if (resultCode == RESULT_OK) {
                    if (searchQuery.length() != 0) {
                        search(searchQuery);
                    }
                } else {
                    Log.d(TAG, "ReCaptcha failed");
                }
                break;

            default:
                Log.e(TAG, "Request code from activity not supported [" + requestCode + "]");
                break;
        }
    }
}
