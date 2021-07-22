package org.schabi.newpipe.settings.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.NotificationMode;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class NotificationsChannelsConfigFragment extends Fragment
        implements NotificationsConfigAdapter.ModeToggleListener {

    private NotificationsConfigAdapter adapter;
    @Nullable
    private Disposable loader = null;
    private CompositeDisposable updaters;

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = new NotificationsConfigAdapter(this);
        updaters = new CompositeDisposable();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channels_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (loader != null) {
            loader.dispose();
        }
        loader = new SubscriptionManager(requireContext())
                .subscriptions()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(adapter::update);
    }

    @Override
    public void onDestroy() {
        if (loader != null) {
            loader.dispose();
        }
        updaters.dispose();
        super.onDestroy();
    }

    @Override
    public void onModeToggle(final int position, @NotificationMode final int mode) {
        final NotificationsConfigAdapter.SubscriptionItem subscription = adapter.getItem(position);
        updaters.add(
                new SubscriptionManager(requireContext())
                        .updateNotificationMode(subscription.getServiceId(),
                                subscription.getUrl(), mode)
                        .subscribeOn(Schedulers.io())
                        .subscribe()
        );
    }
}
