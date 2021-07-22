package org.schabi.newpipe.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.Color
import android.os.Bundle
import androidx.preference.Preference
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import org.schabi.newpipe.R
import org.schabi.newpipe.database.subscription.NotificationMode
import org.schabi.newpipe.database.subscription.SubscriptionEntity
import org.schabi.newpipe.error.ErrorActivity
import org.schabi.newpipe.error.ErrorInfo
import org.schabi.newpipe.error.UserAction
import org.schabi.newpipe.local.feed.notifications.NotificationHelper
import org.schabi.newpipe.local.feed.notifications.NotificationWorker
import org.schabi.newpipe.local.feed.notifications.ScheduleOptions
import org.schabi.newpipe.local.subscription.SubscriptionManager

class NotificationsSettingsFragment : BasePreferenceFragment(), OnSharedPreferenceChangeListener {

    private var notificationWarningSnackbar: Snackbar? = null
    private var loader: Disposable? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.notifications_settings)
    }

    override fun onStart() {
        super.onStart()
        defaultPreferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStop() {
        defaultPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val context = context ?: return
        if (key == getString(R.string.streams_notifications_interval_key) || key == getString(R.string.streams_notifications_network_key)) {
            NotificationWorker.schedule(context, ScheduleOptions.from(context), true)
        }
    }

    override fun onResume() {
        super.onResume()
        val enabled = NotificationHelper.isNotificationsEnabledNative(requireContext())
        preferenceScreen.isEnabled = enabled
        if (!enabled) {
            if (notificationWarningSnackbar == null) {
                notificationWarningSnackbar = Snackbar.make(
                    listView,
                    R.string.notifications_disabled,
                    Snackbar.LENGTH_INDEFINITE
                ).apply {
                    setAction(R.string.settings) { v ->
                        NotificationHelper.openNativeSettingsScreen(v.context)
                    }
                    setActionTextColor(Color.YELLOW)
                    addCallback(object : Snackbar.Callback() {
                        override fun onDismissed(transientBottomBar: Snackbar, event: Int) {
                            super.onDismissed(transientBottomBar, event)
                            notificationWarningSnackbar = null
                        }
                    })
                    show()
                }
            }
        } else {
            notificationWarningSnackbar?.dismiss()
            notificationWarningSnackbar = null
        }
        loader?.dispose()
        loader = SubscriptionManager(requireContext())
            .subscriptions()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::updateSubscriptions, this::onError)
    }

    override fun onPause() {
        loader?.dispose()
        loader = null
        super.onPause()
    }

    private fun updateSubscriptions(subscriptions: List<SubscriptionEntity>) {
        var notified = 0
        for (subscription in subscriptions) {
            if (subscription.notificationMode != NotificationMode.DISABLED) {
                notified++
            }
        }
        val preference = findPreference<Preference>(getString(R.string.streams_notifications_channels_key))
        if (preference != null) {
            preference.summary = preference.context.getString(
                R.string.streams_notifications_channels_summary,
                notified,
                subscriptions.size
            )
        }
    }

    private fun onError(e: Throwable) {
        ErrorActivity.reportErrorInSnackbar(
            this,
            ErrorInfo(e, UserAction.SUBSCRIPTION_GET, "Get subscriptions list")
        )
    }
}
