/*
 * Copyright 2019 Mauricio Colli <mauriciocolli@outlook.com>
 * FeedFragment.kt is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.local.feed

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.preference.PreferenceManager
import icepick.State
import java.util.Calendar
import kotlinx.android.synthetic.main.error_retry.error_button_retry
import kotlinx.android.synthetic.main.error_retry.error_message_view
import kotlinx.android.synthetic.main.fragment_feed.empty_state_view
import kotlinx.android.synthetic.main.fragment_feed.error_panel
import kotlinx.android.synthetic.main.fragment_feed.items_list
import kotlinx.android.synthetic.main.fragment_feed.loading_progress_bar
import kotlinx.android.synthetic.main.fragment_feed.loading_progress_text
import kotlinx.android.synthetic.main.fragment_feed.refresh_root_view
import kotlinx.android.synthetic.main.fragment_feed.refresh_subtitle_text
import kotlinx.android.synthetic.main.fragment_feed.refresh_text
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.fragments.list.BaseListFragment
import org.schabi.newpipe.local.feed.service.FeedLoadService
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.Localization

class FeedFragment : BaseListFragment<FeedState, Unit>() {
    private lateinit var viewModel: FeedViewModel
    @State
    @JvmField
    var listState: Parcelable? = null

    private var groupId = FeedGroupEntity.GROUP_ALL_ID
    private var groupName = ""
    private var oldestSubscriptionUpdate: Calendar? = null

    init {
        setHasOptionsMenu(true)
        setUseDefaultStateSaving(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        groupId = arguments?.getLong(KEY_GROUP_ID, FeedGroupEntity.GROUP_ALL_ID)
                ?: FeedGroupEntity.GROUP_ALL_ID
        groupName = arguments?.getString(KEY_GROUP_NAME) ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_feed, container, false)
    }

    override fun onViewCreated(rootView: View, savedInstanceState: Bundle?) {
        super.onViewCreated(rootView, savedInstanceState)

        viewModel = ViewModelProviders.of(this, FeedViewModel.Factory(requireContext(), groupId)).get(FeedViewModel::class.java)
        viewModel.stateLiveData.observe(viewLifecycleOwner, Observer { it?.let(::handleResult) })
    }

    override fun onPause() {
        super.onPause()
        listState = items_list?.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        updateRelativeTimeViews()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)

        if (!isVisibleToUser && view != null) {
            updateRelativeTimeViews()
        }
    }

    override fun initListeners() {
        super.initListeners()
        refresh_root_view.setOnClickListener {
            triggerUpdate()
        }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Menu
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        activity.supportActionBar?.setTitle(R.string.fragment_feed_title)
        activity.supportActionBar?.subtitle = groupName

        inflater.inflate(R.menu.menu_feed_fragment, menu)

        if (useAsFrontPage) {
            menu.findItem(R.id.menu_item_feed_help).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_feed_help) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            val usingDedicatedMethod = sharedPreferences.getBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), false)
            val enableDisableButtonText = when {
                usingDedicatedMethod -> R.string.feed_use_dedicated_fetch_method_disable_button
                else -> R.string.feed_use_dedicated_fetch_method_enable_button
            }

            AlertDialog.Builder(requireContext())
                    .setMessage(R.string.feed_use_dedicated_fetch_method_help_text)
                    .setNeutralButton(enableDisableButtonText) { _, _ ->
                        sharedPreferences.edit()
                                .putBoolean(getString(R.string.feed_use_dedicated_fetch_method_key), !usingDedicatedMethod)
                                .apply()
                    }
                    .setPositiveButton(resources.getString(R.string.finish), null)
                    .create()
                    .show()
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onDestroyOptionsMenu() {
        super.onDestroyOptionsMenu()
        activity?.supportActionBar?.subtitle = null
    }

    override fun onDestroy() {
        super.onDestroy()
        activity?.supportActionBar?.subtitle = null
    }

    // /////////////////////////////////////////////////////////////////////////
    // Handling
    // /////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        animateView(refresh_root_view, false, 0)
        animateView(items_list, false, 0)

        animateView(loading_progress_bar, true, 200)
        animateView(loading_progress_text, true, 200)

        empty_state_view?.let { animateView(it, false, 0) }
        animateView(error_panel, false, 0)
    }

    override fun hideLoading() {
        animateView(refresh_root_view, true, 200)
        animateView(items_list, true, 300)

        animateView(loading_progress_bar, false, 0)
        animateView(loading_progress_text, false, 0)

        empty_state_view?.let { animateView(it, false, 0) }
        animateView(error_panel, false, 0)
    }

    override fun showEmptyState() {
        animateView(refresh_root_view, true, 200)
        animateView(items_list, false, 0)

        animateView(loading_progress_bar, false, 0)
        animateView(loading_progress_text, false, 0)

        empty_state_view?.let { animateView(it, true, 800) }
        animateView(error_panel, false, 0)
    }

    override fun showError(message: String, showRetryButton: Boolean) {
        itemListAdapter.clearStreamItemList()
        animateView(refresh_root_view, false, 120)
        animateView(items_list, false, 120)

        animateView(loading_progress_bar, false, 120)
        animateView(loading_progress_text, false, 120)

        error_message_view.text = message
        animateView(error_button_retry, showRetryButton, if (showRetryButton) 600 else 0)
        animateView(error_panel, true, 300)
    }

    override fun handleResult(result: FeedState) {
        when (result) {
            is FeedState.ProgressState -> handleProgressState(result)
            is FeedState.LoadedState -> handleLoadedState(result)
            is FeedState.ErrorState -> if (handleErrorState(result)) return
        }

        updateRefreshViewState()
    }

    private fun handleProgressState(progressState: FeedState.ProgressState) {
        showLoading()

        val isIndeterminate = progressState.currentProgress == -1 &&
                progressState.maxProgress == -1

        if (!isIndeterminate) {
            loading_progress_text.text = "${progressState.currentProgress}/${progressState.maxProgress}"
        } else if (progressState.progressMessage > 0) {
            loading_progress_text?.setText(progressState.progressMessage)
        } else {
            loading_progress_text?.text = "∞/∞"
        }

        loading_progress_bar.isIndeterminate = isIndeterminate ||
                (progressState.maxProgress > 0 && progressState.currentProgress == 0)
        loading_progress_bar.progress = progressState.currentProgress

        loading_progress_bar.max = progressState.maxProgress
    }

    private fun handleLoadedState(loadedState: FeedState.LoadedState) {
        itemListAdapter.setItemList(loadedState.items)
        listState?.run {
            items_list.layoutManager?.onRestoreInstanceState(listState)
            listState = null
        }

        oldestSubscriptionUpdate = loadedState.oldestUpdate

        if (loadedState.notLoadedCount > 0) {
            refresh_subtitle_text.visibility = View.VISIBLE
            refresh_subtitle_text.text = getString(R.string.feed_subscription_not_loaded_count, loadedState.notLoadedCount)
        } else {
            refresh_subtitle_text.visibility = View.GONE
        }

        if (loadedState.itemsErrors.isNotEmpty()) {
            showSnackBarError(loadedState.itemsErrors, UserAction.REQUESTED_FEED,
                    "none", "Loading feed", R.string.general_error)
        }

        if (loadedState.items.isEmpty()) {
            showEmptyState()
        } else {
            hideLoading()
        }
    }

    private fun handleErrorState(errorState: FeedState.ErrorState): Boolean {
        hideLoading()
        errorState.error?.let {
            onError(errorState.error)
            return true
        }
        return false
    }

    private fun updateRelativeTimeViews() {
        updateRefreshViewState()
        itemListAdapter.notifyDataSetChanged()
    }

    private fun updateRefreshViewState() {
        val oldestSubscriptionUpdateText = when {
            oldestSubscriptionUpdate != null -> Localization.relativeTime(oldestSubscriptionUpdate!!)
            else -> "—"
        }

        refresh_text?.text = getString(R.string.feed_oldest_subscription_update, oldestSubscriptionUpdateText)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Load Service Handling
    // /////////////////////////////////////////////////////////////////////////

    override fun doInitialLoadLogic() {}
    override fun reloadContent() = triggerUpdate()
    override fun loadMoreItems() {}
    override fun hasMoreItems() = false

    private fun triggerUpdate() {
        getActivity()?.startService(Intent(requireContext(), FeedLoadService::class.java).apply {
            putExtra(FeedLoadService.EXTRA_GROUP_ID, groupId)
        })
        listState = null
    }

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        if (useAsFrontPage) {
            showSnackBarError(exception, UserAction.REQUESTED_FEED, "none", "Loading Feed", 0)
            return true
        }

        onUnrecoverableError(exception, UserAction.REQUESTED_FEED, "none", "Loading Feed", 0)
        return true
    }

    companion object {
        const val KEY_GROUP_ID = "ARG_GROUP_ID"
        const val KEY_GROUP_NAME = "ARG_GROUP_NAME"

        @JvmStatic
        fun newInstance(groupId: Long = FeedGroupEntity.GROUP_ALL_ID, groupName: String? = null): FeedFragment {
            val feedFragment = FeedFragment()

            feedFragment.arguments = Bundle().apply {
                putLong(KEY_GROUP_ID, groupId)
                putString(KEY_GROUP_NAME, groupName)
            }

            return feedFragment
        }
    }
}
