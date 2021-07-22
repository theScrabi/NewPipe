package org.schabi.newpipe.settings.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckedTextView
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.settings.notifications.NotificationsConfigAdapter.SubscriptionHolder

class NotificationsConfigAdapter(
    private val listener: ModeToggleListener
) : RecyclerView.Adapter<SubscriptionHolder>() {

    private val differ = AsyncListDiffer(this, DiffCallback())

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): SubscriptionHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.item_notification_config, viewGroup, false)
        return SubscriptionHolder(view, listener)
    }

    override fun onBindViewHolder(subscriptionHolder: SubscriptionHolder, i: Int) {
        subscriptionHolder.bind(differ.currentList[i])
    }

    fun getItem(position: Int): SubscriptionItem = differ.currentList[position]

    override fun getItemCount() = differ.currentList.size

    override fun getItemId(position: Int): Long {
        return differ.currentList[position].id
    }

    fun update(newData: List<SubscriptionEntity>) {
        differ.submitList(
            newData.map {
                SubscriptionItem(
                    id = it.uid,
                    title = it.name,
                    notificationMode = it.notificationMode,
                    serviceId = it.serviceId,
                    url = it.url
                )
            }
        )
    }

    data class SubscriptionItem(
        val id: Long,
        val title: String,
        @NotificationMode
        val notificationMode: Int,
        val serviceId: Int,
        val url: String
    )

    class SubscriptionHolder(
        itemView: View,
        private val listener: ModeToggleListener
    ) : RecyclerView.ViewHolder(itemView), View.OnClickListener {

        private val checkedTextView = itemView as CheckedTextView

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(data: SubscriptionItem) {
            checkedTextView.text = data.title
            checkedTextView.isChecked = data.notificationMode != NotificationMode.DISABLED
        }

        override fun onClick(v: View) {
            val mode = if (checkedTextView.isChecked) {
                NotificationMode.DISABLED
            } else {
                NotificationMode.ENABLED_DEFAULT
            }
            listener.onModeToggle(adapterPosition, mode)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SubscriptionItem>() {

        override fun areItemsTheSame(oldItem: SubscriptionItem, newItem: SubscriptionItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SubscriptionItem, newItem: SubscriptionItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: SubscriptionItem, newItem: SubscriptionItem): Any? {
            if (oldItem.notificationMode != newItem.notificationMode) {
                return newItem.notificationMode
            } else {
                return super.getChangePayload(oldItem, newItem)
            }
        }
    }

    interface ModeToggleListener {
        fun onModeToggle(position: Int, @NotificationMode mode: Int)
    }
}
