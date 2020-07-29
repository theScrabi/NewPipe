package org.schabi.newpipe.player.playqueue;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.reactivex.SingleObserver;
import io.reactivex.disposables.Disposable;

abstract class AbstractInfoPlayQueue<T extends ListInfo, U extends InfoItem> extends PlayQueue {
    boolean isInitial;
    private boolean isComplete;

    final int serviceId;
    final String baseUrl;
    Page nextPage;

    private transient Disposable fetchReactor;

    AbstractInfoPlayQueue(final U item) {
        this(item.getServiceId(), item.getUrl(), null, Collections.emptyList(), 0);
    }

    AbstractInfoPlayQueue(final int serviceId, final String url, final Page nextPage,
                          final List<StreamInfoItem> streams, final int index) {
        super(index, extractListItems(streams));

        this.baseUrl = url;
        this.nextPage = nextPage;
        this.serviceId = serviceId;

        this.isInitial = streams.isEmpty();
        this.isComplete = !isInitial && !Page.isValid(nextPage);
    }

    protected abstract String getTag();

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    SingleObserver<T> getHeadListObserver(
            @Nullable final Runnable runnable) {
        return new SingleObserver<T>() {
            @Override
            public void onSubscribe(@NonNull final Disposable d) {
                if (isComplete || !isInitial || (fetchReactor != null
                        && !fetchReactor.isDisposed())) {
                    d.dispose();
                } else {
                    fetchReactor = d;
                }
            }

            @Override
            public void onSuccess(@NonNull final T result) {
                isInitial = false;
                if (!result.hasNextPage()) {
                    isComplete = true;
                }
                nextPage = result.getNextPage();

                append(extractListItems(result.getRelatedItems()));
                if (runnable != null) {
                    runnable.run();
                }

                fetchReactor.dispose();
                fetchReactor = null;
            }

            @Override
            public void onError(@NonNull final Throwable e) {
                Log.e(getTag(), "Error fetching more playlist, marking playlist as complete.", e);
                isComplete = true;

                append(); // Notify change
                if (runnable != null) {
                    runnable.run();
                }
            }
        };
    }

    SingleObserver<ListExtractor.InfoItemsPage> getNextPageObserver(
            @Nullable final Runnable runnable) {
        return new SingleObserver<ListExtractor.InfoItemsPage>() {
            @Override
            public void onSubscribe(@NonNull final Disposable d) {
                if (isComplete || isInitial || (fetchReactor != null
                        && !fetchReactor.isDisposed())) {
                    d.dispose();
                } else {
                    fetchReactor = d;
                }
            }

            @Override
            public void onSuccess(
                    @NonNull final ListExtractor.InfoItemsPage result) {
                if (!result.hasNextPage()) {
                    isComplete = true;
                }
                nextPage = result.getNextPage();

                append(extractListItems(result.getItems()));
                if (runnable != null) {
                    runnable.run();
                }

                fetchReactor.dispose();
                fetchReactor = null;
            }

            @Override
            public void onError(@NonNull final Throwable e) {
                Log.e(getTag(), "Error fetching more playlist, marking playlist as complete.", e);
                isComplete = true;

                append(); // Notify change
                if (runnable != null) {
                    runnable.run();
                }
            }
        };
    }

    @Override
    public void dispose() {
        super.dispose();
        if (fetchReactor != null) {
            fetchReactor.dispose();
        }
        fetchReactor = null;
    }

    private static List<PlayQueueItem> extractListItems(final List<StreamInfoItem> infos) {
        final List<PlayQueueItem> result = new ArrayList<>();
        for (final InfoItem stream : infos) {
            if (stream instanceof StreamInfoItem) {
                result.add(new PlayQueueItem((StreamInfoItem) stream));
            }
        }
        return result;
    }
}
