package org.schabi.newpipe.local.subscription.item

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import com.xwray.groupie.viewbinding.BindableItem
import com.xwray.groupie.viewbinding.GroupieViewHolder
import org.schabi.newpipe.R
import org.schabi.newpipe.database.feed.model.FeedGroupEntity
import org.schabi.newpipe.databinding.FeedGroupReorderItemBinding
import org.schabi.newpipe.local.subscription.FeedGroupIcon

data class FeedGroupReorderItem(
    val groupId: Long = FeedGroupEntity.GROUP_ALL_ID,
    val name: String,
    val icon: FeedGroupIcon,
    val dragCallback: ItemTouchHelper
) : BindableItem<FeedGroupReorderItemBinding>() {
    constructor (feedGroupEntity: FeedGroupEntity, dragCallback: ItemTouchHelper) :
        this(feedGroupEntity.uid, feedGroupEntity.name, feedGroupEntity.icon, dragCallback)

    override fun getId(): Long {
        return when (groupId) {
            FeedGroupEntity.GROUP_ALL_ID -> super.getId()
            else -> groupId
        }
    }

    override fun getLayout(): Int = R.layout.feed_group_reorder_item

    override fun bind(viewBinding: FeedGroupReorderItemBinding, position: Int) {
        viewBinding.groupName.text = name
        viewBinding.groupIcon.setImageResource(icon.getDrawableRes())
    }

    override fun bind(viewHolder: GroupieViewHolder<FeedGroupReorderItemBinding>, position: Int, payloads: MutableList<Any>) {
        super.bind(viewHolder, position, payloads)
        viewHolder.binding.handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                dragCallback.startDrag(viewHolder)
                return@setOnTouchListener true
            }

            false
        }
    }

    override fun getDragDirs(): Int {
        return UP or DOWN
    }

    override fun initializeViewBinding(view: View) = FeedGroupReorderItemBinding.bind(view)
}
