/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * ExtractorHelper.java is part of NewPipe
 *
 * License: GPL-3.0+
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.util;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.text.HtmlCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.ReCaptchaActivity;
import org.schabi.newpipe.extractor.Info;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage;
import org.schabi.newpipe.extractor.ListInfo;
import org.schabi.newpipe.extractor.MetaInfo;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.channel.ChannelInfo;
import org.schabi.newpipe.extractor.comments.CommentsInfo;
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException;
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException;
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException;
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException;
import org.schabi.newpipe.extractor.exceptions.PaidContentException;
import org.schabi.newpipe.extractor.exceptions.ParsingException;
import org.schabi.newpipe.extractor.exceptions.PrivateContentException;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import org.schabi.newpipe.extractor.exceptions.SoundCloudGoPlusContentException;
import org.schabi.newpipe.extractor.exceptions.YoutubeMusicPremiumContentException;
import org.schabi.newpipe.extractor.feed.FeedExtractor;
import org.schabi.newpipe.extractor.feed.FeedInfo;
import org.schabi.newpipe.extractor.kiosk.KioskInfo;
import org.schabi.newpipe.extractor.playlist.PlaylistInfo;
import org.schabi.newpipe.extractor.search.SearchInfo;
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor;
import org.schabi.newpipe.ktx.ExceptionUtils;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.ErrorInfo;
import org.schabi.newpipe.report.UserAction;

import java.util.List;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;

import static org.schabi.newpipe.extractor.utils.Utils.isNullOrEmpty;

public final class ExtractorHelper {
    private static final String TAG = ExtractorHelper.class.getSimpleName();
    private static final InfoCache CACHE = InfoCache.getInstance();

    private ExtractorHelper() {
        //no instance
    }

    private static void checkServiceId(final int serviceId) {
        if (serviceId == Constants.NO_SERVICE_ID) {
            throw new IllegalArgumentException("serviceId is NO_SERVICE_ID");
        }
    }

    public static Single<SearchInfo> searchFor(final int serviceId, final String searchString,
                                               final List<String> contentFilter,
                                               final String sortFilter) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                SearchInfo.getInfo(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                                .getSearchQHFactory()
                                .fromQuery(searchString, contentFilter, sortFilter)));
    }

    public static Single<InfoItemsPage> getMoreSearchItems(final int serviceId,
                                                           final String searchString,
                                                           final List<String> contentFilter,
                                                           final String sortFilter,
                                                           final Page page) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                SearchInfo.getMoreItems(NewPipe.getService(serviceId),
                        NewPipe.getService(serviceId)
                                .getSearchQHFactory()
                                .fromQuery(searchString, contentFilter, sortFilter), page));

    }

    public static Single<List<String>> suggestionsFor(final int serviceId, final String query) {
        checkServiceId(serviceId);
        return Single.fromCallable(() -> {
            final SuggestionExtractor extractor = NewPipe.getService(serviceId)
                    .getSuggestionExtractor();
            return extractor != null ? extractor.suggestionList(query) : List.of();
        });
    }

    public static Single<StreamInfo> getStreamInfo(final int serviceId, final String url,
                                                   final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.STREAM,
                Single.fromCallable(() -> StreamInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<ChannelInfo> getChannelInfo(final int serviceId, final String url,
                                                     final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.CHANNEL,
                Single.fromCallable(() ->
                        ChannelInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreChannelItems(final int serviceId, final String url,
                                                            final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                ChannelInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    public static Single<ListInfo<StreamInfoItem>> getFeedInfoFallbackToChannelInfo(
            final int serviceId, final String url) {
        final Maybe<ListInfo<StreamInfoItem>> maybeFeedInfo = Maybe.fromCallable(() -> {
            final StreamingService service = NewPipe.getService(serviceId);
            final FeedExtractor feedExtractor = service.getFeedExtractor(url);

            if (feedExtractor == null) {
                return null;
            }

            return FeedInfo.getInfo(feedExtractor);
        });

        return maybeFeedInfo.switchIfEmpty(getChannelInfo(serviceId, url, true));
    }

    public static Single<CommentsInfo> getCommentsInfo(final int serviceId, final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.COMMENT,
                Single.fromCallable(() ->
                        CommentsInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreCommentItems(final int serviceId,
                                                            final CommentsInfo info,
                                                            final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                CommentsInfo.getMoreItems(NewPipe.getService(serviceId), info, nextPage));
    }

    public static Single<PlaylistInfo> getPlaylistInfo(final int serviceId, final String url,
                                                       final boolean forceLoad) {
        checkServiceId(serviceId);
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST,
                Single.fromCallable(() ->
                        PlaylistInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMorePlaylistItems(final int serviceId, final String url,
                                                             final Page nextPage) {
        checkServiceId(serviceId);
        return Single.fromCallable(() ->
                PlaylistInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    public static Single<KioskInfo> getKioskInfo(final int serviceId, final String url,
                                                 final boolean forceLoad) {
        return checkCache(forceLoad, serviceId, url, InfoItem.InfoType.PLAYLIST,
                Single.fromCallable(() -> KioskInfo.getInfo(NewPipe.getService(serviceId), url)));
    }

    public static Single<InfoItemsPage> getMoreKioskItems(final int serviceId, final String url,
                                                          final Page nextPage) {
        return Single.fromCallable(() ->
                KioskInfo.getMoreItems(NewPipe.getService(serviceId), url, nextPage));
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Utils
    //////////////////////////////////////////////////////////////////////////*/

    /**
     * Check if we can load it from the cache (forceLoad parameter), if we can't,
     * load from the network (Single loadFromNetwork)
     * and put the results in the cache.
     *
     * @param <I>             the item type's class that extends {@link Info}
     * @param forceLoad       whether to force loading from the network instead of from the cache
     * @param serviceId       the service to load from
     * @param url             the URL to load
     * @param infoType        the {@link InfoItem.InfoType} of the item
     * @param loadFromNetwork the {@link Single} to load the item from the network
     * @return a {@link Single} that loads the item
     */
    private static <I extends Info> Single<I> checkCache(final boolean forceLoad,
                                                         final int serviceId, final String url,
                                                         final InfoItem.InfoType infoType,
                                                         final Single<I> loadFromNetwork) {
        checkServiceId(serviceId);
        final Single<I> actualLoadFromNetwork = loadFromNetwork
                .doOnSuccess(info -> CACHE.putInfo(serviceId, url, info, infoType));

        final Single<I> load;
        if (forceLoad) {
            CACHE.removeInfo(serviceId, url, infoType);
            load = actualLoadFromNetwork;
        } else {
            load = Maybe.concat(ExtractorHelper.loadFromCache(serviceId, url, infoType),
                    actualLoadFromNetwork.toMaybe())
                    .firstElement() // Take the first valid
                    .toSingle();
        }

        return load;
    }

    /**
     * Default implementation uses the {@link InfoCache} to get cached results.
     *
     * @param <I>             the item type's class that extends {@link Info}
     * @param serviceId       the service to load from
     * @param url             the URL to load
     * @param infoType        the {@link InfoItem.InfoType} of the item
     * @return a {@link Single} that loads the item
     */
    private static <I extends Info> Maybe<I> loadFromCache(final int serviceId, final String url,
                                                           final InfoItem.InfoType infoType) {
        checkServiceId(serviceId);
        return Maybe.defer(() -> {
            //noinspection unchecked
            final I info = (I) CACHE.getFromKey(serviceId, url, infoType);
            if (MainActivity.DEBUG) {
                Log.d(TAG, "loadFromCache() called, info > " + info);
            }

            // Only return info if it's not null (it is cached)
            if (info != null) {
                return Maybe.just(info);
            }

            return Maybe.empty();
        });
    }

    public static boolean isCached(final int serviceId, final String url,
                                   final InfoItem.InfoType infoType) {
        return null != loadFromCache(serviceId, url, infoType).blockingGet();
    }

    /**
     * A simple and general error handler that show a Toast for known exceptions,
     * and for others, opens the report error activity with the (optional) error message.
     *
     * @param context              Android app context
     * @param serviceId            the service the exception happened in
     * @param url                  the URL where the exception happened
     * @param exception            the exception to be handled
     * @param userAction           the action of the user that caused the exception
     * @param optionalErrorMessage the optional error message
     */
    public static void handleGeneralException(final Context context, final int serviceId,
                                              final String url, final Throwable exception,
                                              final UserAction userAction,
                                              final String optionalErrorMessage) {
        final Handler handler = new Handler(context.getMainLooper());

        handler.post(() -> {
            if (exception instanceof ReCaptchaException) {
                Toast.makeText(context, R.string.recaptcha_request_toast, Toast.LENGTH_LONG).show();
                // Starting ReCaptcha Challenge Activity
                final Intent intent = new Intent(context, ReCaptchaActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else if (ExceptionUtils.isNetworkRelated(exception)) {
                Toast.makeText(context, R.string.network_error, Toast.LENGTH_LONG).show();
            } else if (exception instanceof AgeRestrictedContentException) {
                Toast.makeText(context, R.string.restricted_video_no_stream,
                        Toast.LENGTH_LONG).show();
            } else if (exception instanceof GeographicRestrictionException) {
                Toast.makeText(context, R.string.georestricted_content, Toast.LENGTH_LONG).show();
            } else if (exception instanceof PaidContentException) {
                Toast.makeText(context, R.string.paid_content, Toast.LENGTH_LONG).show();
            } else if (exception instanceof PrivateContentException) {
                Toast.makeText(context, R.string.private_content, Toast.LENGTH_LONG).show();
            } else if (exception instanceof SoundCloudGoPlusContentException) {
                Toast.makeText(context, R.string.soundcloud_go_plus_content,
                        Toast.LENGTH_LONG).show();
            } else if (exception instanceof YoutubeMusicPremiumContentException) {
                Toast.makeText(context, R.string.youtube_music_premium_content,
                        Toast.LENGTH_LONG).show();
            } else if (exception instanceof ContentNotAvailableException) {
                Toast.makeText(context, R.string.content_not_available, Toast.LENGTH_LONG).show();
            } else if (exception instanceof ContentNotSupportedException) {
                Toast.makeText(context, R.string.content_not_supported, Toast.LENGTH_LONG).show();
            } else {
                final int errorId = exception instanceof YoutubeStreamExtractor.DeobfuscateException
                        ? R.string.youtube_signature_deobfuscation_error
                        : exception instanceof ParsingException
                        ? R.string.parsing_error : R.string.general_error;
                ErrorActivity.reportError(handler, context, exception, MainActivity.class, null,
                        ErrorInfo.make(userAction, serviceId == -1 ? "none"
                                : NewPipe.getNameOfService(serviceId),
                                url + (optionalErrorMessage == null ? ""
                                        : optionalErrorMessage), errorId));
            }
        });
    }

    /**
     * Formats the text contained in the meta info list as HTML and puts it into the text view,
     * while also making the separator visible. If the list is null or empty, or the user chose not
     * to see meta information, both the text view and the separator are hidden
     * @param metaInfos a list of meta information, can be null or empty
     * @param metaInfoTextView the text view in which to show the formatted HTML
     * @param metaInfoSeparator another view to be shown or hidden accordingly to the text view
     * @return a disposable to be stored somewhere and disposed when activity/fragment is destroyed
     */
    public static Disposable showMetaInfoInTextView(@Nullable final List<MetaInfo> metaInfos,
                                                    final TextView metaInfoTextView,
                                                    final View metaInfoSeparator) {
        final Context context = metaInfoTextView.getContext();
        final boolean showMetaInfo = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(context.getString(R.string.show_meta_info_key), true);

        if (!showMetaInfo || metaInfos == null || metaInfos.isEmpty()) {
            metaInfoTextView.setVisibility(View.GONE);
            metaInfoSeparator.setVisibility(View.GONE);
            return Disposable.empty();

        } else {
            final StringBuilder stringBuilder = new StringBuilder();
            for (final MetaInfo metaInfo : metaInfos) {
                if (!isNullOrEmpty(metaInfo.getTitle())) {
                    stringBuilder.append("<b>").append(metaInfo.getTitle()).append("</b>")
                            .append(Localization.DOT_SEPARATOR);
                }

                String content = metaInfo.getContent().getContent().trim();
                if (content.endsWith(".")) {
                    content = content.substring(0, content.length() - 1); // remove . at end
                }
                stringBuilder.append(content);

                for (int i = 0; i < metaInfo.getUrls().size(); i++) {
                    if (i == 0) {
                        stringBuilder.append(Localization.DOT_SEPARATOR);
                    } else {
                        stringBuilder.append("<br/><br/>");
                    }

                    stringBuilder
                            .append("<a href=\"").append(metaInfo.getUrls().get(i)).append("\">")
                            .append(capitalizeIfAllUppercase(metaInfo.getUrlTexts().get(i).trim()))
                            .append("</a>");
                }
            }

            metaInfoSeparator.setVisibility(View.VISIBLE);
            return TextLinkifier.createLinksFromHtmlBlock(context, stringBuilder.toString(),
                    metaInfoTextView, HtmlCompat.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING);
        }
    }

    private static String capitalizeIfAllUppercase(final String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLowerCase(text.charAt(i))) {
                return text; // there is at least a lowercase letter -> not all uppercase
            }
        }

        if (text.isEmpty()) {
            return text;
        } else {
            return text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase();
        }
    }
}
