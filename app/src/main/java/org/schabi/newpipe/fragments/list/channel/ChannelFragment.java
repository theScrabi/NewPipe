package org.schabi.newpipe.fragments.list.channel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.ContextCompat;

import com.jakewharton.rxbinding2.view.RxView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.fragments.list.BaseListInfoFragment;
import org.schabi.newpipe.local.subscription.SubscriptionManager;
import org.schabi.newpipe.player.playqueue.ChannelPlayQueue;
import org.schabi.newpipe.player.playqueue.PlayQueue;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.util.AnimationUtils;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.ImageDisplayConstants;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ShareUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Action;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

import static org.schabi.newpipe.util.AnimationUtils.DEFAULT_SHORT_ANIM_DURATION;
import static org.schabi.newpipe.util.AnimationUtils.animateBackgroundColor;
import static org.schabi.newpipe.util.AnimationUtils.animateTextColor;
import static org.schabi.newpipe.util.AnimationUtils.animateView;

public class ChannelFragment extends BaseListInfoFragment<ChannelInfo> {
    private static final int BUTTON_DEBOUNCE_INTERVAL = 100;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private Disposable subscribeButtonMonitor;

    /*//////////////////////////////////////////////////////////////////////////
    // Views
    //////////////////////////////////////////////////////////////////////////*/

    private SubscriptionManager subscriptionManager;
    private View headerRootLayout;
    private ImageView headerChannelBanner;
    private ImageView headerAvatarView;
    private TextView headerTitleView;
    private TextView headerSubscribersTextView;
    private Button headerSubscribeButton;
    private View playlistCtrl;
    private LinearLayout headerPlayAllButton;
    private LinearLayout headerPopupButton;
    private LinearLayout headerBackgroundButton;
    private MenuItem menuRssButton;
    private TextView contentNotSupportedTextView;
    private TextView kaomojiTextView;
    private TextView noVideosTextView;

    public static ChannelFragment getInstance(final int serviceId, final String url,
                                              final String name) {
        ChannelFragment instance = new ChannelFragment();
        instance.setInitialData(serviceId, url, name);
        return instance;
    }

    @Override
    public void setUserVisibleHint(final boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (activity != null
                && useAsFrontPage
                && isVisibleToUser) {
            setTitle(currentInfo != null ? currentInfo.getName() : name);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAttach(final Context context) {
        super.onAttach(context);
        subscriptionManager = new SubscriptionManager(activity);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channel, container, false);
    }

    @Override
    public void onViewCreated(final View rootView, final Bundle savedInstanceState) {
        super.onViewCreated(rootView, savedInstanceState);
        contentNotSupportedTextView = rootView.findViewById(R.id.error_content_not_supported);
        kaomojiTextView = rootView.findViewById(R.id.channel_kaomoji);
        noVideosTextView = rootView.findViewById(R.id.channel_no_videos);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (disposables != null) {
            disposables.clear();
        }
        if (subscribeButtonMonitor != null) {
            subscribeButtonMonitor.dispose();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Init
    //////////////////////////////////////////////////////////////////////////*/

    protected View getListHeader() {
        headerRootLayout = activity.getLayoutInflater()
                .inflate(R.layout.channel_header, itemsList, false);
        headerChannelBanner = headerRootLayout.findViewById(R.id.channel_banner_image);
        headerAvatarView = headerRootLayout.findViewById(R.id.channel_avatar_view);
        headerTitleView = headerRootLayout.findViewById(R.id.channel_title_view);
        headerSubscribersTextView = headerRootLayout.findViewById(R.id.channel_subscriber_view);
        headerSubscribeButton = headerRootLayout.findViewById(R.id.channel_subscribe_button);
        playlistCtrl = headerRootLayout.findViewById(R.id.playlist_control);


        headerPlayAllButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_all_button);
        headerPopupButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_popup_button);
        headerBackgroundButton = headerRootLayout.findViewById(R.id.playlist_ctrl_play_bg_button);

        return headerRootLayout;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        ActionBar supportActionBar = activity.getSupportActionBar();
        if (useAsFrontPage && supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        } else {
            inflater.inflate(R.menu.menu_channel, menu);

            if (DEBUG) {
                Log.d(TAG, "onCreateOptionsMenu() called with: "
                        + "menu = [" + menu + "], inflater = [" + inflater + "]");
            }
            menuRssButton = menu.findItem(R.id.menu_item_rss);
        }
    }

    private void openRssFeed() {
        final ChannelInfo info = currentInfo;
        if (info != null) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.getFeedUrl()));
            startActivity(intent);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                NavigationHelper.openSettings(requireContext());
                break;
            case R.id.menu_item_rss:
                openRssFeed();
                break;
            case R.id.menu_item_openInBrowser:
                if (currentInfo != null) {
                    ShareUtils.openUrlInBrowser(requireContext(), currentInfo.getOriginalUrl());
                }
                break;
            case R.id.menu_item_share:
                if (currentInfo != null) {
                    ShareUtils.shareUrl(requireContext(), name, currentInfo.getOriginalUrl());
                }
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Channel Subscription
    //////////////////////////////////////////////////////////////////////////*/

    private void monitorSubscription(final ChannelInfo info) {
        final Consumer<Throwable> onError = (Throwable throwable) -> {
            animateView(headerSubscribeButton, false, DEFAULT_SHORT_ANIM_DURATION);
            showSnackBarError(throwable, UserAction.SUBSCRIPTION,
                    NewPipe.getNameOfService(currentInfo.getServiceId()),
                    "Get subscription status", 0);
        };

        final Observable<List<SubscriptionEntity>> observable = subscriptionManager
                .subscriptionTable()
                .getSubscriptionFlowable(info.getServiceId(), info.getUrl())
                .toObservable();

        disposables.add(observable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getSubscribeUpdateMonitor(info), onError));

        disposables.add(observable
                // Some updates are very rapid
                // (for example when calling the updateSubscription(info))
                // so only update the UI for the latest emission
                // ("sync" the subscribe button's state)
                .debounce(100, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((List<SubscriptionEntity> subscriptionEntities) ->
                        updateSubscribeButton(!subscriptionEntities.isEmpty()), onError));

    }

    private Function<Object, Object> mapOnSubscribe(final SubscriptionEntity subscription,
                                                    final ChannelInfo info) {
        return (@NonNull Object o) -> {
            subscriptionManager.insertSubscription(subscription, info);
            return o;
        };
    }

    private Function<Object, Object> mapOnUnsubscribe(final SubscriptionEntity subscription) {
        return (@NonNull Object o) -> {
            subscriptionManager.deleteSubscription(subscription);
            return o;
        };
    }

    private void updateSubscription(final ChannelInfo info) {
        if (DEBUG) {
            Log.d(TAG, "updateSubscription() called with: info = [" + info + "]");
        }
        final Action onComplete = () -> {
            if (DEBUG) {
                Log.d(TAG, "Updated subscription: " + info.getUrl());
            }
        };

        final Consumer<Throwable> onError = (@NonNull Throwable throwable) ->
                onUnrecoverableError(throwable,
                        UserAction.SUBSCRIPTION,
                        NewPipe.getNameOfService(info.getServiceId()),
                        "Updating Subscription for " + info.getUrl(),
                        R.string.subscription_update_failed);

        disposables.add(subscriptionManager.updateChannelInfo(info)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(onComplete, onError));
    }

    private Disposable monitorSubscribeButton(final Button subscribeButton,
                                              final Function<Object, Object> action) {
        final Consumer<Object> onNext = (@NonNull Object o) -> {
            if (DEBUG) {
                Log.d(TAG, "Changed subscription status to this channel!");
            }
        };

        final Consumer<Throwable> onError = (@NonNull Throwable throwable) ->
                onUnrecoverableError(throwable,
                        UserAction.SUBSCRIPTION,
                        NewPipe.getNameOfService(currentInfo.getServiceId()),
                        "Subscription Change",
                        R.string.subscription_change_failed);

        /* Emit clicks from main thread unto io thread */
        return RxView.clicks(subscribeButton)
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(Schedulers.io())
                .debounce(BUTTON_DEBOUNCE_INTERVAL, TimeUnit.MILLISECONDS) // Ignore rapid clicks
                .map(action)
                .subscribe(onNext, onError);
    }

    private Consumer<List<SubscriptionEntity>> getSubscribeUpdateMonitor(final ChannelInfo info) {
        return (List<SubscriptionEntity> subscriptionEntities) -> {
            if (DEBUG) {
                Log.d(TAG, "subscriptionManager.subscriptionTable.doOnNext() called with: "
                        + "subscriptionEntities = [" + subscriptionEntities + "]");
            }
            if (subscribeButtonMonitor != null) {
                subscribeButtonMonitor.dispose();
            }

            if (subscriptionEntities.isEmpty()) {
                if (DEBUG) {
                    Log.d(TAG, "No subscription to this channel!");
                }
                SubscriptionEntity channel = new SubscriptionEntity();
                channel.setServiceId(info.getServiceId());
                channel.setUrl(info.getUrl());
                channel.setData(info.getName(),
                        info.getAvatarUrl(),
                        info.getDescription(),
                        info.getSubscriberCount());
                subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton,
                        mapOnSubscribe(channel, info));
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Found subscription to this channel!");
                }
                final SubscriptionEntity subscription = subscriptionEntities.get(0);
                subscribeButtonMonitor = monitorSubscribeButton(headerSubscribeButton,
                        mapOnUnsubscribe(subscription));
            }
        };
    }

    private void updateSubscribeButton(final boolean isSubscribed) {
        if (DEBUG) {
            Log.d(TAG, "updateSubscribeButton() called with: "
                    + "isSubscribed = [" + isSubscribed + "]");
        }

        boolean isButtonVisible = headerSubscribeButton.getVisibility() == View.VISIBLE;
        int backgroundDuration = isButtonVisible ? 300 : 0;
        int textDuration = isButtonVisible ? 200 : 0;

        int subscribeBackground = ContextCompat
                .getColor(activity, R.color.subscribe_background_color);
        int subscribeText = ContextCompat.getColor(activity, R.color.subscribe_text_color);
        int subscribedBackground = ContextCompat
                .getColor(activity, R.color.subscribed_background_color);
        int subscribedText = ContextCompat.getColor(activity, R.color.subscribed_text_color);

        if (!isSubscribed) {
            headerSubscribeButton.setText(R.string.subscribe_button_title);
            animateBackgroundColor(headerSubscribeButton, backgroundDuration, subscribedBackground,
                    subscribeBackground);
            animateTextColor(headerSubscribeButton, textDuration, subscribedText, subscribeText);
        } else {
            headerSubscribeButton.setText(R.string.subscribed_button_title);
            animateBackgroundColor(headerSubscribeButton, backgroundDuration, subscribeBackground,
                    subscribedBackground);
            animateTextColor(headerSubscribeButton, textDuration, subscribeText, subscribedText);
        }

        animateView(headerSubscribeButton, AnimationUtils.Type.LIGHT_SCALE_AND_ALPHA,
                true, DEFAULT_SHORT_ANIM_DURATION);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Load and handle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected Single<ListExtractor.InfoItemsPage> loadMoreItemsLogic() {
        return ExtractorHelper.getMoreChannelItems(serviceId, url, currentNextPageUrl);
    }

    @Override
    protected Single<ChannelInfo> loadResult(final boolean forceLoad) {
        return ExtractorHelper.getChannelInfo(serviceId, url, forceLoad);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Contract
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void showLoading() {
        super.showLoading();

        IMAGE_LOADER.cancelDisplayTask(headerChannelBanner);
        IMAGE_LOADER.cancelDisplayTask(headerAvatarView);
        animateView(headerSubscribeButton, false, DEFAULT_SHORT_ANIM_DURATION);
    }

    @Override
    public void handleResult(@NonNull final ChannelInfo result) {
        super.handleResult(result);

        headerRootLayout.setVisibility(View.VISIBLE);
        IMAGE_LOADER.displayImage(result.getBannerUrl(), headerChannelBanner,
                ImageDisplayConstants.DISPLAY_BANNER_OPTIONS);
        IMAGE_LOADER.displayImage(result.getAvatarUrl(), headerAvatarView,
                ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);

        headerSubscribersTextView.setVisibility(View.VISIBLE);
        if (result.getSubscriberCount() >= 0) {
            headerSubscribersTextView.setText(Localization
                    .shortSubscriberCount(activity, result.getSubscriberCount()));
        } else {
            headerSubscribersTextView.setText(R.string.subscribers_count_not_available);
        }

        if (menuRssButton != null) {
            menuRssButton.setVisible(!TextUtils.isEmpty(result.getFeedUrl()));
        }

        playlistCtrl.setVisibility(View.VISIBLE);

        List<Throwable> errors = new ArrayList<>(result.getErrors());
        if (!errors.isEmpty()) {

            // handling ContentNotSupportedException not to show the error but an appropriate string
            // so that crashes won't be sent uselessly and the user will understand what happened
            for (Iterator<Throwable> it = errors.iterator(); it.hasNext();) {
                Throwable throwable = it.next();
                if (throwable instanceof ContentNotSupportedException) {
                    showContentNotSupported();
                    it.remove();
                }
            }

            if (!errors.isEmpty()) {
                showSnackBarError(errors, UserAction.REQUESTED_CHANNEL,
                        NewPipe.getNameOfService(result.getServiceId()), result.getUrl(), 0);
            }
        }

        if (disposables != null) {
            disposables.clear();
        }
        if (subscribeButtonMonitor != null) {
            subscribeButtonMonitor.dispose();
        }
        updateSubscription(result);
        monitorSubscription(result);

        headerPlayAllButton.setOnClickListener(view -> NavigationHelper
                .playOnMainPlayer(activity, getPlayQueue(), false));
        headerPopupButton.setOnClickListener(view -> NavigationHelper
                .playOnPopupPlayer(activity, getPlayQueue(), false));
        headerBackgroundButton.setOnClickListener(view -> NavigationHelper
                .playOnBackgroundPlayer(activity, getPlayQueue(), false));

        headerPopupButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnPopupPlayer(activity, getPlayQueue(), true);
            return true;
        });

        headerBackgroundButton.setOnLongClickListener(view -> {
            NavigationHelper.enqueueOnBackgroundPlayer(activity, getPlayQueue(), true);
            return true;
        });
    }

    private void showContentNotSupported() {
        contentNotSupportedTextView.setVisibility(View.VISIBLE);
        kaomojiTextView.setText("(︶︹︺)");
        kaomojiTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 45f);
        noVideosTextView.setVisibility(View.GONE);
    }

    private PlayQueue getPlayQueue() {
        return getPlayQueue(0);
    }

    private PlayQueue getPlayQueue(final int index) {
        final List<StreamInfoItem> streamItems = new ArrayList<>();
        for (InfoItem i : infoListAdapter.getItemsList()) {
            if (i instanceof StreamInfoItem) {
                streamItems.add((StreamInfoItem) i);
            }
        }
        return new ChannelPlayQueue(currentInfo.getServiceId(), currentInfo.getUrl(),
                currentInfo.getNextPageUrl(), streamItems, index);
    }

    @Override
    public void handleNextItems(final ListExtractor.InfoItemsPage result) {
        super.handleNextItems(result);

        if (!result.getErrors().isEmpty()) {
            showSnackBarError(result.getErrors(),
                    UserAction.REQUESTED_CHANNEL,
                    NewPipe.getNameOfService(serviceId),
                    "Get next page of: " + url,
                    R.string.general_error);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // OnError
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    protected boolean onError(final Throwable exception) {
        if (super.onError(exception)) {
            return true;
        }

        int errorId = exception instanceof ExtractionException
                ? R.string.parsing_error : R.string.general_error;

        onUnrecoverableError(exception, UserAction.REQUESTED_CHANNEL,
                NewPipe.getNameOfService(serviceId), url, errorId);

        return true;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void setTitle(final String title) {
        super.setTitle(title);
        if (!useAsFrontPage) {
            headerTitleView.setText(title);
        }
    }
}
