package org.schabi.newpipe.local.subscription.dialog

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.OnItemClickListener
import com.xwray.groupie.Section
import icepick.Icepick
import icepick.State
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.DialogFeedGroupCreateBinding
import org.schabi.newpipe.databinding.ToolbarSearchLayoutBinding
import org.schabi.newpipe.fragments.BackPressable
import org.schabi.newpipe.local.subscription.FeedGroupIcon
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.DeleteScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.IconPickerScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.InitialScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialog.ScreenState.SubscriptionsPickerScreen
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialogViewModel.DialogEvent.ProcessingEvent
import org.schabi.newpipe.local.subscription.dialog.FeedGroupDialogViewModel.DialogEvent.SuccessEvent
import org.schabi.newpipe.local.subscription.item.EmptyPlaceholderItem
import org.schabi.newpipe.local.subscription.item.PickerIconItem
import org.schabi.newpipe.local.subscription.item.PickerSubscriptionItem
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.ThemeHelper
import java.io.Serializable

class FeedGroupDialog : DialogFragment(), BackPressable {
    private var _feedGroupCreateBinding: DialogFeedGroupCreateBinding? = null
    private val feedGroupCreateBinding get() = _feedGroupCreateBinding!!

    private var _searchLayoutBinding: ToolbarSearchLayoutBinding? = null
    private val searchLayoutBinding get() = _searchLayoutBinding!!

    private lateinit var viewModel: FeedGroupDialogViewModel
    private var groupId: Long = NO_GROUP_SELECTED
    private var groupIcon: FeedGroupIcon? = null
    private var groupSortOrder: Long = -1

    sealed class ScreenState : Serializable {
        object InitialScreen : ScreenState()
        object IconPickerScreen : ScreenState()
        object SubscriptionsPickerScreen : ScreenState()
        object DeleteScreen : ScreenState()
    }

    @State @JvmField var selectedIcon: FeedGroupIcon? = null
    @State @JvmField var selectedSubscriptions: HashSet<Long> = HashSet()
    @State @JvmField var wasSubscriptionSelectionChanged: Boolean = false
    @State @JvmField var currentScreen: ScreenState = InitialScreen

    @State @JvmField var subscriptionsListState: Parcelable? = null
    @State @JvmField var iconsListState: Parcelable? = null
    @State @JvmField var wasSearchSubscriptionsVisible = false
    @State @JvmField var subscriptionsCurrentSearchQuery = ""
    @State @JvmField var subscriptionsShowOnlyUngrouped = false

    private val subscriptionMainSection = Section()
    private val subscriptionEmptyFooter = Section()
    private lateinit var subscriptionGroupAdapter: GroupAdapter<GroupieViewHolder>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Icepick.restoreInstanceState(this, savedInstanceState)

        setStyle(STYLE_NO_TITLE, ThemeHelper.getMinWidthDialogTheme(requireContext()))
        groupId = arguments?.getLong(KEY_GROUP_ID, NO_GROUP_SELECTED) ?: NO_GROUP_SELECTED
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_feed_group_create, container)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return object : Dialog(requireActivity(), theme) {
            override fun onBackPressed() {
                if (!this@FeedGroupDialog.onBackPressed()) {
                    super.onBackPressed()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        wasSearchSubscriptionsVisible = isSearchVisible()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        iconsListState = feedGroupCreateBinding.iconSelector.layoutManager?.onSaveInstanceState()
        subscriptionsListState = feedGroupCreateBinding.subscriptionsSelectorList.layoutManager?.onSaveInstanceState()

        Icepick.saveInstanceState(this, outState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _feedGroupCreateBinding = DialogFeedGroupCreateBinding.bind(view)
        _searchLayoutBinding = feedGroupCreateBinding.subscriptionsHeaderSearchContainer

        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
            // KitKat doesn't apply container's theme to <include> content
            val contrastColor = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.contrastColor))
            searchLayoutBinding.toolbarSearchEditText.setTextColor(contrastColor)
            searchLayoutBinding.toolbarSearchEditText.setHintTextColor(contrastColor.withAlpha(128))
            ImageViewCompat.setImageTintList(searchLayoutBinding.toolbarSearchClearIcon, contrastColor)
        }

        viewModel = ViewModelProvider(
            this,
            FeedGroupDialogViewModel.Factory(
                requireContext(),
                groupId, subscriptionsCurrentSearchQuery, subscriptionsShowOnlyUngrouped
            )
        ).get(FeedGroupDialogViewModel::class.java)

        viewModel.groupLiveData.observe(viewLifecycleOwner, Observer(::handleGroup))
        viewModel.subscriptionsLiveData.observe(
            viewLifecycleOwner,
            Observer {
                setupSubscriptionPicker(it.first, it.second)
            }
        )
        viewModel.dialogEventLiveData.observe(
            viewLifecycleOwner,
            Observer {
                when (it) {
                    ProcessingEvent -> disableInput()
                    SuccessEvent -> dismiss()
                }
            }
        )

        subscriptionGroupAdapter = GroupAdapter<GroupieViewHolder>().apply {
            add(subscriptionMainSection)
            add(subscriptionEmptyFooter)
            spanCount = 4
        }
        feedGroupCreateBinding.subscriptionsSelectorList.apply {
            // Disable animations, too distracting.
            itemAnimator = null
            adapter = subscriptionGroupAdapter
            layoutManager = GridLayoutManager(
                requireContext(), subscriptionGroupAdapter.spanCount,
                RecyclerView.VERTICAL, false
            ).apply {
                spanSizeLookup = subscriptionGroupAdapter.spanSizeLookup
            }
        }

        setupIconPicker()
        setupListeners()

        showScreen(currentScreen)

        if (currentScreen == SubscriptionsPickerScreen && wasSearchSubscriptionsVisible) {
            showSearch()
        } else if (currentScreen == InitialScreen && groupId == NO_GROUP_SELECTED) {
            showKeyboard()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        feedGroupCreateBinding.subscriptionsSelectorList.adapter = null
        feedGroupCreateBinding.iconSelector.adapter = null

        _feedGroupCreateBinding = null
        _searchLayoutBinding = null
    }

    /*/​//////////////////////////////////////////////////////////////////////////
    // Setup
    //​//////////////////////////////////////////////////////////////////////// */

    override fun onBackPressed(): Boolean {
        if (currentScreen is SubscriptionsPickerScreen && isSearchVisible()) {
            hideSearch()
            return true
        } else if (currentScreen !is InitialScreen) {
            showScreen(InitialScreen)
            return true
        }

        return false
    }

    private fun setupListeners() {
        feedGroupCreateBinding.deleteButton.setOnClickListener { showScreen(DeleteScreen) }

        feedGroupCreateBinding.cancelButton.setOnClickListener {
            when (currentScreen) {
                InitialScreen -> dismiss()
                else -> showScreen(InitialScreen)
            }
        }

        feedGroupCreateBinding.groupNameInputContainer.error = null
        feedGroupCreateBinding.groupNameInput.doOnTextChanged { text, _, _, _ ->
            if (feedGroupCreateBinding.groupNameInputContainer.isErrorEnabled && !text.isNullOrBlank()) {
                feedGroupCreateBinding.groupNameInputContainer.error = null
            }
        }

        feedGroupCreateBinding.confirmButton.setOnClickListener { handlePositiveButton() }

        feedGroupCreateBinding.selectChannelButton.setOnClickListener {
            feedGroupCreateBinding.subscriptionsSelectorList.scrollToPosition(0)
            showScreen(SubscriptionsPickerScreen)
        }

        val headerMenu = feedGroupCreateBinding.subscriptionsHeaderToolbar.menu
        requireActivity().menuInflater.inflate(R.menu.menu_feed_group_dialog, headerMenu)

        headerMenu.findItem(R.id.action_search).setOnMenuItemClickListener {
            showSearch()
            true
        }

        headerMenu.findItem(R.id.feed_group_toggle_show_only_ungrouped_subscriptions).apply {
            isChecked = subscriptionsShowOnlyUngrouped
            setOnMenuItemClickListener {
                subscriptionsShowOnlyUngrouped = !subscriptionsShowOnlyUngrouped
                it.isChecked = subscriptionsShowOnlyUngrouped
                viewModel.toggleShowOnlyUngrouped(subscriptionsShowOnlyUngrouped)
                true
            }
        }

        searchLayoutBinding.toolbarSearchClear.setOnClickListener {
            if (searchLayoutBinding.toolbarSearchEditText.text.isNullOrEmpty()) {
                hideSearch()
                return@setOnClickListener
            }
            resetSearch()
            showKeyboardSearch()
        }

        searchLayoutBinding.toolbarSearchEditText.setOnClickListener {
            if (DeviceUtils.isTv(context)) {
                showKeyboardSearch()
            }
        }

        searchLayoutBinding.toolbarSearchEditText.doOnTextChanged { _, _, _, _ ->
            val newQuery: String = searchLayoutBinding.toolbarSearchEditText.text.toString()
            subscriptionsCurrentSearchQuery = newQuery
            viewModel.filterSubscriptionsBy(newQuery)
        }

        subscriptionGroupAdapter.setOnItemClickListener(subscriptionPickerItemListener)
    }

    private fun handlePositiveButton() = when {
        currentScreen is InitialScreen -> handlePositiveButtonInitialScreen()
        currentScreen is DeleteScreen -> viewModel.deleteGroup()
        currentScreen is SubscriptionsPickerScreen && isSearchVisible() -> hideSearch()
        else -> showScreen(InitialScreen)
    }

    private fun handlePositiveButtonInitialScreen() {
        val name = feedGroupCreateBinding.groupNameInput.text.toString().trim()
        val icon = selectedIcon ?: groupIcon ?: FeedGroupIcon.ALL

        if (name.isBlank()) {
            feedGroupCreateBinding.groupNameInputContainer.error = getString(R.string.feed_group_dialog_empty_name)
            feedGroupCreateBinding.groupNameInput.text = null
            feedGroupCreateBinding.groupNameInput.requestFocus()
            return
        } else {
            feedGroupCreateBinding.groupNameInputContainer.error = null
        }

        if (selectedSubscriptions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.feed_group_dialog_empty_selection), Toast.LENGTH_SHORT).show()
            return
        }

        when (groupId) {
            NO_GROUP_SELECTED -> viewModel.createGroup(name, icon, selectedSubscriptions)
            else -> viewModel.updateGroup(name, icon, selectedSubscriptions, groupSortOrder)
        }
    }

    private fun handleGroup(feedGroupEntity: FeedGroupEntity? = null) {
        val icon = feedGroupEntity?.icon ?: FeedGroupIcon.ALL
        val name = feedGroupEntity?.name ?: ""
        groupIcon = feedGroupEntity?.icon
        groupSortOrder = feedGroupEntity?.sortOrder ?: -1

        val feedGroupIcon = if (selectedIcon == null) icon else selectedIcon!!
        feedGroupCreateBinding.iconPreview.setImageResource(feedGroupIcon.getDrawableRes())

        if (feedGroupCreateBinding.groupNameInput.text.isNullOrBlank()) {
            feedGroupCreateBinding.groupNameInput.setText(name)
        }
    }

    private val subscriptionPickerItemListener = OnItemClickListener { item, view ->
        if (item is PickerSubscriptionItem) {
            val subscriptionId = item.subscriptionEntity.uid
            wasSubscriptionSelectionChanged = true

            val isSelected = if (this.selectedSubscriptions.contains(subscriptionId)) {
                this.selectedSubscriptions.remove(subscriptionId)
                false
            } else {
                this.selectedSubscriptions.add(subscriptionId)
                true
            }

            item.updateSelected(view, isSelected)
            updateSubscriptionSelectedCount()
        }
    }

    private fun setupSubscriptionPicker(
        subscriptions: List<PickerSubscriptionItem>,
        selectedSubscriptions: Set<Long>
    ) {
        if (!wasSubscriptionSelectionChanged) {
            this.selectedSubscriptions.addAll(selectedSubscriptions)
        }

        updateSubscriptionSelectedCount()

        if (subscriptions.isEmpty()) {
            subscriptionEmptyFooter.clear()
            subscriptionEmptyFooter.add(EmptyPlaceholderItem())
        } else {
            subscriptionEmptyFooter.clear()
        }

        subscriptions.forEach {
            it.isSelected = this@FeedGroupDialog.selectedSubscriptions
                .contains(it.subscriptionEntity.uid)
        }

        subscriptionMainSection.update(subscriptions, false)

        if (subscriptionsListState != null) {
            feedGroupCreateBinding.subscriptionsSelectorList.layoutManager?.onRestoreInstanceState(subscriptionsListState)
            subscriptionsListState = null
        } else {
            feedGroupCreateBinding.subscriptionsSelectorList.scrollToPosition(0)
        }
    }

    private fun updateSubscriptionSelectedCount() {
        val selectedCount = this.selectedSubscriptions.size
        val selectedCountText = resources.getQuantityString(
            R.plurals.feed_group_dialog_selection_count,
            selectedCount, selectedCount
        )
        feedGroupCreateBinding.selectedSubscriptionCountView.text = selectedCountText
        feedGroupCreateBinding.subscriptionsHeaderInfo.text = selectedCountText
    }

    private fun setupIconPicker() {
        val groupAdapter = GroupAdapter<GroupieViewHolder>()
        groupAdapter.addAll(FeedGroupIcon.values().map { PickerIconItem(it) })

        feedGroupCreateBinding.iconSelector.apply {
            layoutManager = GridLayoutManager(requireContext(), 7, RecyclerView.VERTICAL, false)
            adapter = groupAdapter

            if (iconsListState != null) {
                layoutManager?.onRestoreInstanceState(iconsListState)
                iconsListState = null
            }
        }

        groupAdapter.setOnItemClickListener { item, _ ->
            when (item) {
                is PickerIconItem -> {
                    selectedIcon = item.icon
                    feedGroupCreateBinding.iconPreview.setImageResource(item.iconRes)

                    showScreen(InitialScreen)
                }
            }
        }
        feedGroupCreateBinding.iconPreview.setOnClickListener {
            feedGroupCreateBinding.iconSelector.scrollToPosition(0)
            showScreen(IconPickerScreen)
        }

        if (groupId == NO_GROUP_SELECTED) {
            val icon = selectedIcon ?: FeedGroupIcon.ALL
            feedGroupCreateBinding.iconPreview.setImageResource(icon.getDrawableRes())
        }
    }

    /*/​//////////////////////////////////////////////////////////////////////////
    // Screen Selector
    //​//////////////////////////////////////////////////////////////////////// */

    private fun showScreen(screen: ScreenState) {
        currentScreen = screen

        feedGroupCreateBinding.optionsRoot.onlyVisibleIn(InitialScreen)
        feedGroupCreateBinding.iconSelector.onlyVisibleIn(IconPickerScreen)
        feedGroupCreateBinding.subscriptionsSelector.onlyVisibleIn(SubscriptionsPickerScreen)
        feedGroupCreateBinding.deleteScreenMessage.onlyVisibleIn(DeleteScreen)

        feedGroupCreateBinding.separator.onlyVisibleIn(SubscriptionsPickerScreen, IconPickerScreen)
        feedGroupCreateBinding.cancelButton.onlyVisibleIn(InitialScreen, DeleteScreen)

        feedGroupCreateBinding.confirmButton.setText(
            when {
                currentScreen == InitialScreen && groupId == NO_GROUP_SELECTED -> R.string.create
                else -> android.R.string.ok
            }
        )

        feedGroupCreateBinding.deleteButton.isGone = currentScreen != InitialScreen || groupId == NO_GROUP_SELECTED

        hideKeyboard()
        hideSearch()
    }

    private fun View.onlyVisibleIn(vararg screens: ScreenState) {
        isVisible = currentScreen in screens
    }

    /*/​//////////////////////////////////////////////////////////////////////////
    // Utils
    //​//////////////////////////////////////////////////////////////////////// */

    private fun isSearchVisible() = _searchLayoutBinding?.root?.visibility == View.VISIBLE

    private fun resetSearch() {
        searchLayoutBinding.toolbarSearchEditText.setText("")
        subscriptionsCurrentSearchQuery = ""
        viewModel.clearSubscriptionsFilter()
    }

    private fun hideSearch() {
        resetSearch()
        searchLayoutBinding.root.visibility = View.GONE
        feedGroupCreateBinding.subscriptionsHeaderInfoContainer.visibility = View.VISIBLE
        feedGroupCreateBinding.subscriptionsHeaderToolbar.menu.findItem(R.id.action_search).isVisible = true
        hideKeyboardSearch()
    }

    private fun showSearch() {
        searchLayoutBinding.root.visibility = View.VISIBLE
        feedGroupCreateBinding.subscriptionsHeaderInfoContainer.visibility = View.GONE
        feedGroupCreateBinding.subscriptionsHeaderToolbar.menu.findItem(R.id.action_search).isVisible = false
        showKeyboardSearch()
    }

    private val inputMethodManager by lazy {
        requireActivity().getSystemService<InputMethodManager>()!!
    }

    private fun showKeyboardSearch() {
        if (searchLayoutBinding.toolbarSearchEditText.requestFocus()) {
            inputMethodManager.showSoftInput(
                searchLayoutBinding.toolbarSearchEditText,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun hideKeyboardSearch() {
        inputMethodManager.hideSoftInputFromWindow(
            searchLayoutBinding.toolbarSearchEditText.windowToken,
            InputMethodManager.RESULT_UNCHANGED_SHOWN
        )
        searchLayoutBinding.toolbarSearchEditText.clearFocus()
    }

    private fun showKeyboard() {
        if (feedGroupCreateBinding.groupNameInput.requestFocus()) {
            inputMethodManager.showSoftInput(
                feedGroupCreateBinding.groupNameInput,
                InputMethodManager.SHOW_IMPLICIT
            )
        }
    }

    private fun hideKeyboard() {
        inputMethodManager.hideSoftInputFromWindow(
            feedGroupCreateBinding.groupNameInput.windowToken,
            InputMethodManager.RESULT_UNCHANGED_SHOWN
        )
        feedGroupCreateBinding.groupNameInput.clearFocus()
    }

    private fun disableInput() {
        _feedGroupCreateBinding?.deleteButton?.isEnabled = false
        _feedGroupCreateBinding?.confirmButton?.isEnabled = false
        _feedGroupCreateBinding?.cancelButton?.isEnabled = false
        isCancelable = false

        hideKeyboard()
    }

    companion object {
        private const val KEY_GROUP_ID = "KEY_GROUP_ID"
        private const val NO_GROUP_SELECTED = -1L

        fun newInstance(groupId: Long = NO_GROUP_SELECTED): FeedGroupDialog {
            val dialog = FeedGroupDialog()
            dialog.arguments = bundleOf(KEY_GROUP_ID to groupId)
            return dialog
        }
    }
}
