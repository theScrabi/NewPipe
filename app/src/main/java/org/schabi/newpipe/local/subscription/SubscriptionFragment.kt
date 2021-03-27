package org.schabi.newpipe.local.subscription

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import com.xwray.groupie.Group
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.Item
import com.xwray.groupie.Section
import com.xwray.groupie.viewbinding.GroupieViewHolder
import icepick.State
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.DialogTitleBinding
import org.schabi.newpipe.databinding.FeedItemCarouselBinding
import org.schabi.newpipe.databinding.FragmentSubscriptionBinding
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.fragments.BaseStateFragment
import org.schabi.newpipe.ktx.animate
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
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.IMPORT_COMPLETE_ACTION
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_MODE
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.KEY_VALUE
import org.schabi.newpipe.local.subscription.services.SubscriptionsImportService.PREVIOUS_EXPORT_MODE
import org.schabi.newpipe.streams.io.StoredFileHelper
import org.schabi.newpipe.util.NavigationHelper
import org.schabi.newpipe.util.OnClickGesture
import org.schabi.newpipe.util.external_communication.ShareUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max

class SubscriptionFragment : BaseStateFragment<SubscriptionState>() {
    private var _binding: FragmentSubscriptionBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SubscriptionViewModel
    private lateinit var subscriptionManager: SubscriptionManager
    private val disposables: CompositeDisposable = CompositeDisposable()

    private var subscriptionBroadcastReceiver: BroadcastReceiver? = null

    private val groupAdapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()
    private val feedGroupsSection = Section()
    private var feedGroupsCarousel: FeedGroupCarouselItem? = null
    private lateinit var importExportItem: FeedImportExportItem
    private lateinit var feedGroupsSortMenuItem: HeaderWithMenuItem
    private val subscriptionsSection = Section()

    private val requestExportLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestExportResult)
    private val requestImportLauncher =
        registerForActivityResult(StartActivityForResult(), this::requestImportResult)

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
        itemsListState = binding.itemsList.layoutManager?.onSaveInstanceState()
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
                _binding?.itemsList?.post {
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
        requestImportLauncher.launch(StoredFileHelper.getPicker(activity))
    }

    private fun onExportSelected() {
        val date = SimpleDateFormat("yyyyMMddHHmm", Locale.ENGLISH).format(Date())
        val exportName = "newpipe_subscriptions_$date.json"

        requestExportLauncher.launch(
            StoredFileHelper.getNewPicker(activity, exportName, "application/json", null)
        )
    }

    private fun openReorderDialog() {
        FeedGroupReorderDialog().show(parentFragmentManager, null)
    }

    fun requestExportResult(result: ActivityResult) {
        if (result.data != null && result.resultCode == Activity.RESULT_OK) {
            activity.startService(
                Intent(activity, SubscriptionsExportService::class.java)
                    .putExtra(SubscriptionsExportService.KEY_FILE_PATH, result.data?.data)
            )
        }
    }

    fun requestImportResult(result: ActivityResult) {
        if (result.data != null && result.resultCode == Activity.RESULT_OK) {
            ImportConfirmationDialog.show(
                this,
                Intent(activity, SubscriptionsImportService::class.java)
                    .putExtra(KEY_MODE, PREVIOUS_EXPORT_MODE)
                    .putExtra(KEY_VALUE, result.data?.data)
            )
        }
    }

    // ////////////////////////////////////////////////////////////////////////
    // Fragment Views
    // ////////////////////////////////////////////////////////////////////////

    private fun setupInitialLayout() {
        Section().apply {
            val carouselAdapter = GroupAdapter<GroupieViewHolder<FeedItemCarouselBinding>>()

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
                R.drawable.ic_sort,
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
            importExportItemExpandedState ?: false
        )
        groupAdapter.add(Section(importExportItem, listOf(subscriptionsSection)))
    }

    override fun initViews(rootView: View, savedInstanceState: Bundle?) {
        super.initViews(rootView, savedInstanceState)
        _binding = FragmentSubscriptionBinding.bind(rootView)

        val shouldUseGridLayout = shouldUseGridLayout()
        groupAdapter.spanCount = if (shouldUseGridLayout) getGridSpanCount() else 1
        binding.itemsList.layoutManager = GridLayoutManager(requireContext(), groupAdapter.spanCount).apply {
            spanSizeLookup = groupAdapter.spanSizeLookup
        }
        binding.itemsList.adapter = groupAdapter

        viewModel = ViewModelProvider(this).get(SubscriptionViewModel::class.java)
        viewModel.stateLiveData.observe(viewLifecycleOwner) { it?.let(this::handleResult) }
        viewModel.feedGroupsLiveData.observe(viewLifecycleOwner) { it?.let(this::handleFeedGroups) }
    }

    private fun showLongTapDialog(selectedItem: ChannelInfoItem) {
        val commands = arrayOf(
            getString(R.string.share),
            getString(R.string.open_in_browser),
            getString(R.string.unsubscribe)
        )

        val actions = DialogInterface.OnClickListener { _, i ->
            when (i) {
                0 -> ShareUtils.shareText(
                    requireContext(), selectedItem.name, selectedItem.url,
                    selectedItem.thumbnailUrl
                )
                1 -> ShareUtils.openUrlInBrowser(requireContext(), selectedItem.url)
                2 -> deleteChannel(selectedItem)
            }
        }

        val dialogTitleBinding = DialogTitleBinding.inflate(LayoutInflater.from(requireContext()))
        dialogTitleBinding.root.isSelected = true
        dialogTitleBinding.itemTitleView.text = selectedItem.name
        dialogTitleBinding.itemAdditionalDetails.visibility = View.GONE

        AlertDialog.Builder(requireContext())
            .setCustomTitle(dialogTitleBinding.root)
            .setItems(commands, actions)
            .create()
            .show()
    }

    private fun deleteChannel(selectedItem: ChannelInfoItem) {
        disposables.add(
            subscriptionManager.deleteSubscription(selectedItem.serviceId, selectedItem.url).subscribe {
                Toast.makeText(requireContext(), getString(R.string.channel_unsubscribed), Toast.LENGTH_SHORT).show()
            }
        )
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
        override fun selected(selectedItem: ChannelInfoItem) = NavigationHelper.openChannelFragment(
            fm,
            selectedItem.serviceId, selectedItem.url, selectedItem.name
        )

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
                    binding.itemsList.post {
                        importExportItem.isExpanded = true
                        importExportItem.notifyChanged(FeedImportExportItem.REFRESH_EXPANDED_STATUS)
                    }
                }

                if (itemsListState != null) {
                    binding.itemsList.layoutManager?.onRestoreInstanceState(itemsListState)
                    itemsListState = null
                }
            }
            is SubscriptionState.ErrorState -> {
                result.error?.let {
                    showError(ErrorInfo(result.error, UserAction.SOMETHING_ELSE, "Subscriptions"))
                }
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
        binding.itemsList.post { feedGroupsSortMenuItem.notifyChanged(PAYLOAD_UPDATE_VISIBILITY_MENU_ITEM) }
    }

    // /////////////////////////////////////////////////////////////////////////
    // Contract
    // /////////////////////////////////////////////////////////////////////////

    override fun showLoading() {
        super.showLoading()
        binding.itemsList.animate(false, 100)
    }

    override fun hideLoading() {
        super.hideLoading()
        binding.itemsList.animate(true, 200)
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
                configuration.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                    configuration.isLayoutSizeAtLeast(Configuration.SCREENLAYOUT_SIZE_LARGE)
            }
            getString(R.string.list_view_mode_grid_key) -> true
            else -> false
        }
    }

    private fun getGridSpanCount(): Int {
        val minWidth = resources.getDimensionPixelSize(R.dimen.channel_item_grid_min_width)
        return max(1, floor(resources.displayMetrics.widthPixels / minWidth.toDouble()).toInt())
    }
}
