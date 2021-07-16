package org.schabi.newpipe.local.subscription.item

import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.databinding.PickerSubscriptionItemBinding
import org.schabi.newpipe.ktx.AnimationType
import org.schabi.newpipe.ktx.animate
import org.schabi.newpipe.util.PicassoHelper

data class PickerSubscriptionItem(
    val subscriptionEntity: SubscriptionEntity,
    var isSelected: Boolean = false
) : BindableItem<PickerSubscriptionItemBinding>() {
    override fun getId(): Long = subscriptionEntity.uid
    override fun getLayout(): Int = R.layout.picker_subscription_item
    override fun getSpanSize(spanCount: Int, position: Int): Int = 1

    override fun bind(viewBinding: PickerSubscriptionItemBinding, position: Int) {
        PicassoHelper.loadAvatar(subscriptionEntity.avatarUrl).into(viewBinding.thumbnailView)
        viewBinding.titleView.text = subscriptionEntity.name
        viewBinding.selectedHighlight.isVisible = isSelected
    }

    override fun unbind(viewHolder: GroupieViewHolder<PickerSubscriptionItemBinding>) {
        super.unbind(viewHolder)

        viewHolder.binding.selectedHighlight.apply {
            animate().setListener(null).cancel()
            isGone = true
            alpha = 1F
        }
    }

    override fun initializeViewBinding(view: View) = PickerSubscriptionItemBinding.bind(view)

    fun updateSelected(containerView: View, isSelected: Boolean) {
        this.isSelected = isSelected
        PickerSubscriptionItemBinding.bind(containerView).selectedHighlight
            .animate(isSelected, 150, AnimationType.LIGHT_SCALE_AND_ALPHA)
    }
}
