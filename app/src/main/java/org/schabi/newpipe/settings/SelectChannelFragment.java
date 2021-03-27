package org.schabi.newpipe.settings;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.error.ErrorActivity;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.util.PicassoHelper;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.List;
import java.util.Vector;

import de.hdodenhof.circleimageview.CircleImageView;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Created by Christian Schabesberger on 26.09.17.
 * SelectChannelFragment.java is part of NewPipe.
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p>
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class SelectChannelFragment extends DialogFragment {

    private OnSelectedListener onSelectedListener = null;
    private OnCancelListener onCancelListener = null;

    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;

    private List<SubscriptionEntity> subscriptions = new Vector<>();

    public void setOnSelectedListener(final OnSelectedListener listener) {
        onSelectedListener = listener;
    }

    public void setOnCancelListener(final OnCancelListener listener) {
        onCancelListener = listener;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, ThemeHelper.getMinWidthDialogTheme(requireContext()));
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, final ViewGroup container,
                             final Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.select_channel_fragment, container, false);
        recyclerView = v.findViewById(R.id.items_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        final SelectChannelAdapter channelAdapter = new SelectChannelAdapter();
        recyclerView.setAdapter(channelAdapter);

        progressBar = v.findViewById(R.id.progressBar);
        emptyView = v.findViewById(R.id.empty_state_view);
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);


        final SubscriptionManager subscriptionManager = new SubscriptionManager(requireContext());
        subscriptionManager.subscriptions().toObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscriptionObserver());

        return v;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Handle actions
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCancel(@NonNull final DialogInterface dialogInterface) {
        super.onCancel(dialogInterface);
        if (onCancelListener != null) {
            onCancelListener.onCancel();
        }
    }

    private void clickedItem(final int position) {
        if (onSelectedListener != null) {
            final SubscriptionEntity entry = subscriptions.get(position);
            onSelectedListener
                    .onChannelSelected(entry.getServiceId(), entry.getUrl(), entry.getName());
        }
        dismiss();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Item handling
    //////////////////////////////////////////////////////////////////////////*/

    private void displayChannels(final List<SubscriptionEntity> newSubscriptions) {
        this.subscriptions = newSubscriptions;
        progressBar.setVisibility(View.GONE);
        if (newSubscriptions.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        recyclerView.setVisibility(View.VISIBLE);

    }

    private Observer<List<SubscriptionEntity>> getSubscriptionObserver() {
        return new Observer<List<SubscriptionEntity>>() {
            @Override
            public void onSubscribe(@NonNull final Disposable disposable) { }

            @Override
            public void onNext(@NonNull final List<SubscriptionEntity> newSubscriptions) {
                displayChannels(newSubscriptions);
            }

            @Override
            public void onError(@NonNull final Throwable exception) {
                ErrorActivity.reportUiErrorInSnackbar(SelectChannelFragment.this,
                        "Loading subscription", exception);
            }

            @Override
            public void onComplete() { }
        };
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Interfaces
    //////////////////////////////////////////////////////////////////////////*/

    public interface OnSelectedListener {
        void onChannelSelected(int serviceId, String url, String name);
    }

    public interface OnCancelListener {
        void onCancel();
    }

    private class SelectChannelAdapter
            extends RecyclerView.Adapter<SelectChannelAdapter.SelectChannelItemHolder> {
        @NonNull
        @Override
        public SelectChannelItemHolder onCreateViewHolder(final ViewGroup parent,
                                                          final int viewType) {
            final View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.select_channel_item, parent, false);
            return new SelectChannelItemHolder(item);
        }

        @Override
        public void onBindViewHolder(final SelectChannelItemHolder holder, final int position) {
            final SubscriptionEntity entry = subscriptions.get(position);
            holder.titleView.setText(entry.getName());
            holder.view.setOnClickListener(view -> clickedItem(position));
            PicassoHelper.loadAvatar(entry.getAvatarUrl()).into(holder.thumbnailView);
        }

        @Override
        public int getItemCount() {
            return subscriptions.size();
        }

        public class SelectChannelItemHolder extends RecyclerView.ViewHolder {
            public final View view;
            final CircleImageView thumbnailView;
            final TextView titleView;
            SelectChannelItemHolder(final View v) {
                super(v);
                this.view = v;
                thumbnailView = v.findViewById(R.id.itemThumbnailView);
                titleView = v.findViewById(R.id.itemTitleView);
            }
        }
    }
}
