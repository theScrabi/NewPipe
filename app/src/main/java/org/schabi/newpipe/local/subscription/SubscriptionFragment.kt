package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.ViewModelProviders
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.GridLayoutManager
import com.nononsenseapps.filepicker.Utils
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.Section
import com.xwray.groupie.kotlinandroidextensions.GroupieViewHolder
import icepick.State
import io.reactivex.disposables.CompositeDisposable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlinx.android.synthetic.main.dialog_title.view.itemAdditionalDetails
import kotlinx.android.synthetic.main.dialog_title.view.itemTitleView
import kotlinx.android.synthetic.main.fragment_subscription.items_list
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.local.subscription.SubscriptionViewModel.SubscriptionState
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog
import org.schabi.newpipe.local.subscription.dialog.FeedGroupReorderDialog
import org.schabi.newpipe.local.subscription.item.ChannelItem
import org.schabi.newpipe.local.subscription.item.EmptyPlaceholderItem
import org.schabi.newpipe.local.subscription.item.FeedGroupAddItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCardItem
import org.schabi.newpipe.local.subscription.item.FeedGroupCarouselItem
import org.schabi.newpipe.local.subscription.item.FeedImportExportItem
import org.schabi.newpipe.local.subscription.item.HeaderWithMenuItem
import org.schabi.newpipe.local.subscription.item.HeaderWithMenuItem.Companion.PAYLOAD_UPDATE_VISIBILITY_MENU_ITEM
import org.schabi.newpipe.local.subscription.services.SubscriptionsExportService
import org.schabi.newpipe.local.subscription.services.SubscriptionsExportService.EXPORT_COMPLETE_ACTION
import org.schabi.newpipe.local.subscription.services.SubscriptionsExportService.KEY_FILE_PATH
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.IMPORT_COMPLETE_ACTION
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_MODE
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_VALUE
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.PREVIOUS_EXPORT_MODE
import org.schabi.newpipe.report.UserAction
import org.schabi.newpipe.util.AnimationUtils.animateView
import org.schabi.newpipe.util.FilePickerActivityHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.ShareUtils
import org.schabi.newpipe.util.ThemeHelper

class SubscriptionFragment : BaseStateFragment<SubscriptionState>() {
    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var subscriptionManager: SubscriptionManager
    private val disposables: CompositeDisposable = CompositeDisposable()

    private var subscriptionBroadcastReceiver: BroadcastReceiver? = null

    private val groupAdapter = GroupAdapter<GroupieViewHolder>()
    private val feedGroupsSection = Section()
    private var feedGroupsCarousel: FeedGroupCarouselItem? = null
    private lateinit var importExportItem: FeedImportExportItem
    private lateinit var feedGroupsSortMenuItem: HeaderWithMenuItem
    private val subscriptionsSection = Section()

    @State
    @JvmField
    var itemsListState: Parcelable? = null
    @State
    @JvmField
    var feedGroupsListState: Parcelable? = null
    @State
    @JvmField
    var importExportItemExpandedState: Boolean? = null

    init {
        setHasOptionsMenu(true)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle
    // /////////////////////////////////////////////////////////////////////////

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupInitialLayout()
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (activity != null && isVisibleToUser) {
            setTitle(activity.getString(R.string.tab_subscriptions))
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        subscriptionManager = SubscriptionManager(requireContext())
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_subscription, container, false)
    }

    override fun onResume() {
        super.onResume()
        setupBroadcastReceiver()
    }

    override fun onPause() {
        super.onPause()
        itemsListState = items_list.layoutManager?.onSaveInstanceState()
        feedGroupsListState = feedGroupsCarousel?.onSaveInstanceState()
        importExportItemExpandedState = importExportItem.isExpanded

        if (subscriptionBroadcastReceiver != null && activity != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(subscriptionBroadcastReceiver!!)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    // ////////////////////////////////////////////////////////////////////////
    // Menu
    // ////////////////////////////////////////////////////////////////////////

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        val supportActionBar = activity.supportActionBar
        if (supportActionBar != null) {
            supportActionBar.setDisplayShowTitleEnabled(true)
            setTitle(getString(R.string.tab_subscriptions))
        }
    }

    private fun setupBroadcastReceiver() {
        if (activity == null) return

        if (subscriptionBroadcastReceiver != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(subscriptionBroadcastReceiver!!)
        }

        val filters = IntentFilter()
        filters.addAction(EXPORT_COMPLETE_ACTION)
        filters.addAction(IMPORT_COMPLETE_ACTION)
        subscriptionBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                items_list?.post {
                    importExportItem.isExpanded = false
                    importExportItem.notifyChanged(FeedImportExportItem.REFRESH_EXPANDED_STATUS)
                }
            }
        }

        LocalBroadcastManager.getInstance(activity).registerReceiver(subscriptionBroadcastReceiver!!, filters)
    }

    private fun onImportFromServiceSelected(serviceId: Int) {
        val fragmentManager = fm
        NavigationHelper.openSubscriptionsImportFragment(fragmentManager, serviceId)
    }

    private fun onImportPreviousSelected() {
        startActivityForResult(FilePickerActivityHelper.chooseSingleFile(activity), REQUEST_IMPORT_CODE)
    }

    private fun onExportSelected() {
        val date = SimpleDateFormat("yyyyMMddHHmm", Locale.ENGLISH).format(Date())
        val exportName = "newpipe_subscriptions_$date.json"
        val exportFile = File(Environment.getExternalStorageDirectory(), exportName)

        startActivityForResult(FilePickerActivityHelper.chooseFileToSave(activity, exportFile.absolutePath), REQUEST_EXPORT_CODE)
    }

    private fun openReorderDialog() {
        FeedGroupReorderDialog().show(requireFragmentManager(), null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (data != null && data.data != null && resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_EXPORT_CODE) {
                val exportFile = Utils.getFileForUri(data.data!!)
                if (!exportFile.parentFile.canWrite() || !exportFile.parentFile.canRead()) {
                    Toast.makeText(activity, R.string.invalid_directory, Toast.LENGTH_SHORT).show()
                } else {
                    activity.startService(Intent(activity, SubscriptionsExportService::class.java)
                            .putExtra(KEY_FILE_PATH, exportFile.absolutePath))
                }
            } else if (requestCode == REQUEST_IMPORT_CODE) {
                val path = Utils.getFileForUri(data.data!!).absolutePath
                ImportConfirmationDialog.show(this, Intent(activity, SubscriptionsImportService::class.java)
                        .putExtra(KEY_MODE, PREVIOUS_EXPORT_MODE)
                        .putExtra(KEY_VALUE, path))
            }
        }
    }

    // ////////////////////////////////////////////////////////////////////////
    // Fragment Views
    // ////////////////////////////////////////////////////////////////////////

    private fun setupInitialLayout() {
        Section().apply {
            val carouselAdapter = GroupAdapter<GroupieViewHolder>()

            carouselAdapter.add(FeedGroupCardItem(-1, getString(R.string.all), FeedGroupIcon.RSS))
            carouselAdapter.add(feedGroupsSection)
            carouselAdapter.add(FeedGroupAddItem())

            carouselAdapter.setOnItemClickListener { item, _ ->
                listenerFeedGroups.selected(item)
            }
            carouselAdapter.setOnItemLongClickListener { item, _ ->
                if (item is FeedGroupCardItem) {
                    if (item.groupId == FeedGroupEntity.GROUP_ALL_ID) {
                        return@setOnItemLongClickListener false
                    }
                }
                listenerFeedGroups.held(item)
                return@setOnItemLongClickListener true
            }

            feedGroupsCarousel = FeedGroupCarouselItem(requireContext(), carouselAdapter)
            feedGroupsSortMenuItem = HeaderWithMenuItem(
                    getString(R.string.feed_groups_header_title),
                    ThemeHelper.resolveResourceIdFromAttr(requireContext(), R.attr.ic_sort),
                    menuItemOnClickListener = ::openReorderDialog
            )
            add(Section(feedGroupsSortMenuItem, listOf(feedGroupsCarousel)))

            groupAdapter.add(this)
        }

        subscriptionsSection.setPlaceholder(EmptyPlaceholderItem())
        subscriptionsSection.setHideWhenEmpty(true)

        importExportItem = FeedImportExportItem(
                { onImportPreviousSelected() },
                { onImportFromServiceSelected(it) },
                { onExportSelected() },
                importExportItemExpandedState ?: false)
        groupAdapter.add(Section(importExportItem, listOf(subscriptionsSection)))
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)

        val shouldUseGridLayout = shouldUseGridLayout()
        groupAdapter.spanCount = if (shouldUseGridLayout) getGridSpanCount() else 1
        items_list.layoutManager = GridLayoutManager(requireContext(), groupAdapter.spanCount).apply {
            spanSizeLookup = groupAdapter.spanSizeLookup
        }
        items_list.adapter = groupAdapter

        viewModel = ViewModelProviders.of(this).get(SubscriptionViewModel::class.java)
        viewModel.stateLiveData.observe(viewLifecycleOwner, androidx.lifecycle.Observer { it?.let(this::handleResult) })
        viewModel.feedGroupsLiveData.observe(viewLifecycleOwner, androidx.lifecycle.Observer { it?.let(this::handleFeedGroups) })
    }

    private fun showLongTapDialog(selectedItem: ChannelInfoItem) {
        val commands = arrayOf(
                getString(R.string.share),
                getString(R.string.unsubscribe)
        )

        val actions = DialogInterface.OnClickListener { _, i ->
            when (i) {
                0 -> ShareUtils.shareUrl(requireContext(), selectedItem.name, selectedItem.url)
                1 -> deleteChannel(selectedItem)
            }
        }

        val bannerView = View.inflate(requireContext(), R.layout.dialog_title, null)
        bannerView.isSelected = true
        bannerView.itemTitleView.text = selectedItem.name
        bannerView.itemAdditionalDetails.visibility = View.GONE

        AlertDialog.Builder(requireContext())
                .setCustomTitle(bannerView)
                .setItems(commands, actions)
                .create()
                .show()
    }

    private fun deleteChannel(selectedItem: ChannelInfoItem) {
        disposables.add(subscriptionManager.deleteSubscription(selectedItem.serviceId, selectedItem.url).subscribe {
            Toast.makeText(requireContext(), getString(R.string.channel_unsubscribed), Toast.LENGTH_SHORT).show()
        })
    }

    override fun doInitialLoadLogic() = Unit
    override fun startLoading(forceLoad: Boolean) = Unit

    private val listenerFeedGroups = object : OnClickGesture<Item<*>>() {
        override fun selected(selectedItem: Item<*>?) {
            when (selectedItem) {
                is FeedGroupCardItem -> NavigationHelper.openFeedFragment(fm, selectedItem.groupId, selectedItem.name)
                is FeedGroupAddItem -> FeedGroupDialog.newInstance().show(fm, null)
            }
        }

        override fun held(selectedItem: Item<*>?) {
            when (selectedItem) {
                is FeedGroupCardItem -> FeedGroupDialog.newInstance(selectedItem.groupId).show(fm, null)
            }
        }
    }

    private val listenerChannelItem = object : OnClickGesture<ChannelInfoItem>() {
        override fun selected(selectedItem: ChannelInfoItem) = NavigationHelper.openChannelFragment(fm,
                selectedItem.serviceId, selectedItem.url, selectedItem.name)

        override fun held(selectedItem: ChannelInfoItem) = showLongTapDialog(selectedItem)
    }

    override fun handleResult(result: SubscriptionState) {
        super.handleResult(result)

        val shouldUseGridLayout = shouldUseGridLayout()
        when (result) {
            is SubscriptionState.LoadedState -> {
                result.subscriptions.forEach {
                    if (it is ChannelItem) {
                        it.gesturesListener = listenerChannelItem
                        it.itemVersion = when {
                            shouldUseGridLayout -> ChannelItem.ItemVersion.GRID
                            else -> ChannelItem.ItemVersion.MINI
                        }
                    }
                }

                subscriptionsSection.update(result.subscriptions)
                subscriptionsSection.setHideWhenEmpty(false)

                if (result.subscriptions.isEmpty() && importExportItemExpandedState == null) {
                    items_list.post {
                        importExportItem.isExpanded = true
                        importExportItem.notifyChanged(FeedImportExportItem.REFRESH_EXPANDED_STATUS)
                    }
                }

                if (itemsListState != null) {
                    items_list.layoutManager?.onRestoreInstanceState(itemsListState)
                    itemsListState = null
                }
            }
            is SubscriptionState.ErrorState -> {
                result.error?.let { onError(result.error) }
            }
        }
    }

    private fun handleFeedGroups(groups: List<Group>) {
        feedGroupsSection.update(groups)

        if (feedGroupsListState != null) {
            feedGroupsCarousel?.onRestoreInstanceState(feedGroupsListState)
            feedGroupsListState = null
        }

        feedGroupsSortMenuItem.showMenuItem = groups.size > 1
        items_list.post { feedGroupsSortMenuItem.notifyChanged(PAYLOAD_UPDATE_VISIBILITY_MENU_ITEM) }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Contract
    // /////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        animateView(items_list, false, 100)
    }

    override fun hideLoading() {
        super.hideLoading()
        animateView(items_list, true, 200)
    }

    // /////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    // /////////////////////////////////////////////////////////////////////////

    override fun onError(exception: Throwable): Boolean {
        if (super.onError(exception)) return true

        onUnrecoverableError(exception, UserAction.SOMETHING_ELSE, "none", "Subscriptions", R.string.general_error)
        return true
    }

    // /////////////////////////////////////////////////////////////////////////
    // Grid Mode
    // /////////////////////////////////////////////////////////////////////////

    // TODO: Move these out of this class, as it can be reused

    private fun shouldUseGridLayout(): Boolean {
        val listMode = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getString(getString(R.string.list_view_mode_key), getString(R.string.list_view_mode_value))

        return when (listMode) {
            getString(R.string.list_view_mode_auto_key) -> {
                val configuration = resources.configuration

                (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                        configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE))
            }
            getString(R.string.list_view_mode_grid_key) -> true
            else -> false
        }
    }

    private fun getGridSpanCount(): Int {
        val minWidth = resources.getDimensionPixelSize(R.dimen.video_item_grid_thumbnail_image_width)
        return max(1, floor(resources.displayMetrics.widthPixels / minWidth.toDouble()).toInt())
    }

    companion object {
        private const val REQUEST_EXPORT_CODE = 666
        private const val REQUEST_IMPORT_CODE = 667
    }
}
