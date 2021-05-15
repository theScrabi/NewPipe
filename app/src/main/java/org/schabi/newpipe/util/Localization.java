package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.icu.text.CompactDecimalFormat;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceManager;

import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.units.Decade;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.localization.ContentCountry;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


/*
 * Created by chschtsch on 12/29/15.
 *
 * Copyright (C) Gregory Arkhipov 2015
 * Localization.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public final class Localization {

    public static final String DOT_SEPARATOR = " • ";
    private static PrettyTime prettyTime;

    private Localization() { }

    @NonNull
    public static String concatenateStrings(final String... strings) {
        return concatenateStrings(Arrays.asList(strings));
    }

    @NonNull
    public static String concatenateStrings(final List<String> strings) {
        if (strings.isEmpty()) {
            return "";
        }

        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(strings.get(0));

        for (int i = 1; i < strings.size(); i++) {
            final String string = strings.get(i);
            if (!TextUtils.isEmpty(string)) {
                stringBuilder.append(DOT_SEPARATOR).append(strings.get(i));
            }
        }

        return stringBuilder.toString();
    }

    public static org.schabi.newpipe.extractor.localization.Localization getPreferredLocalization(
            final Context context) {
        final String contentLanguage = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.content_language_key),
                        context.getString(R.string.default_localization_key));
        if (contentLanguage.equals(context.getString(R.string.default_localization_key))) {
            return org.schabi.newpipe.extractor.localization.Localization
                    .fromLocale(Locale.getDefault());
        }
        return org.schabi.newpipe.extractor.localization.Localization
                .fromLocalizationCode(contentLanguage);
    }

    public static ContentCountry getPreferredContentCountry(final Context context) {
        final String contentCountry = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.content_country_key),
                        context.getString(R.string.default_localization_key));
        if (contentCountry.equals(context.getString(R.string.default_localization_key))) {
            return new ContentCountry(Locale.getDefault().getCountry());
        }
        return new ContentCountry(contentCountry);
    }

    public static Locale getPreferredLocale(final Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        final String languageCode = sp.getString(context.getString(R.string.content_language_key),
                context.getString(R.string.default_localization_key));

        try {
            if (languageCode.length() == 2) {
                return new Locale(languageCode);
            } else if (languageCode.contains("_")) {
                final String country = languageCode.substring(languageCode.indexOf("_"));
                return new Locale(languageCode.substring(0, 2), country);
            }
        } catch (final Exception ignored) {
        }

        return Locale.getDefault();
    }

    public static String localizeNumber(final Context context, final long number) {
        return localizeNumber(context, (double) number);
    }

    public static String localizeNumber(final Context context, final double number) {
        final NumberFormat nf = NumberFormat.getInstance(getAppLocale(context));
        return nf.format(number);
    }

    public static String formatDate(final OffsetDateTime offsetDateTime, final Context context) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                .withLocale(getAppLocale(context)).format(offsetDateTime
                        .atZoneSameInstant(ZoneId.systemDefault()));
    }

    @SuppressLint("StringFormatInvalid")
    public static String localizeUploadDate(final Context context,
                                            final OffsetDateTime offsetDateTime) {
        return context.getString(R.string.upload_date_text, formatDate(offsetDateTime, context));
    }

    public static String localizeViewCount(final Context context, final long viewCount) {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                localizeNumber(context, viewCount));
    }

    public static String localizeStreamCount(final Context context, final long streamCount) {
        switch ((int) streamCount) {
            case (int) ListExtractor.ITEM_COUNT_UNKNOWN:
                return "";
            case (int) ListExtractor.ITEM_COUNT_INFINITE:
                return context.getResources().getString(R.string.infinite_videos);
            case (int) ListExtractor.ITEM_COUNT_MORE_THAN_100:
                return context.getResources().getString(R.string.more_than_100_videos);
            default:
                return getQuantity(context, R.plurals.videos, R.string.no_videos, streamCount,
                        localizeNumber(context, streamCount));
        }
    }

    public static String localizeStreamCountMini(final Context context, final long streamCount) {
        switch ((int) streamCount) {
            case (int) ListExtractor.ITEM_COUNT_UNKNOWN:
                return "";
            case (int) ListExtractor.ITEM_COUNT_INFINITE:
                return context.getResources().getString(R.string.infinite_videos_mini);
            case (int) ListExtractor.ITEM_COUNT_MORE_THAN_100:
                return context.getResources().getString(R.string.more_than_100_videos_mini);
            default:
                return String.valueOf(streamCount);
        }
    }

    public static String localizeWatchingCount(final Context context, final long watchingCount) {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                localizeNumber(context, watchingCount));
    }

    public static String shortCount(final Context context, final long count) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return CompactDecimalFormat.getInstance(getAppLocale(context),
                    CompactDecimalFormat.CompactStyle.SHORT).format(count);
        }

        final double value = (double) count;
        if (count >= 1000000000) {
            return localizeNumber(context, round(value / 1000000000, 1))
                    + context.getString(R.string.short_billion);
        } else if (count >= 1000000) {
            return localizeNumber(context, round(value / 1000000, 1))
                    + context.getString(R.string.short_million);
        } else if (count >= 1000) {
            return localizeNumber(context, round(value / 1000, 1))
                    + context.getString(R.string.short_thousand);
        } else {
            return localizeNumber(context, value);
        }
    }

    public static String listeningCount(final Context context, final long listeningCount) {
        return getQuantity(context, R.plurals.listening, R.string.no_one_listening, listeningCount,
                shortCount(context, listeningCount));
    }

    public static String shortWatchingCount(final Context context, final long watchingCount) {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount,
                shortCount(context, watchingCount));
    }

    public static String shortViewCount(final Context context, final long viewCount) {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount,
                shortCount(context, viewCount));
    }

    public static String shortSubscriberCount(final Context context, final long subscriberCount) {
        return getQuantity(context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount,
                shortCount(context, subscriberCount));
    }

    private static String getQuantity(final Context context, @PluralsRes final int pluralId,
                                      @StringRes final int zeroCaseStringId, final long count,
                                      final String formattedCount) {
        if (count == 0) {
            return context.getString(zeroCaseStringId);
        }

        // As we use the already formatted count
        // is not the responsibility of this method handle long numbers
        // (it probably will fall in the "other" category,
        // or some language have some specific rule... then we have to change it)
        final int safeCount = count > Integer.MAX_VALUE ? Integer.MAX_VALUE
                : count < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) count;
        return context.getResources().getQuantityString(pluralId, safeCount, formattedCount);
    }

    public static String getDurationString(final long duration) {
        return getDurationString(duration, true);
    }

    public static String getDurationString(final long duration, final boolean isDurationComplete) {
        final String output;

        final long days = duration / (24 * 60 * 60L); /* greater than a day */
        final long hours = duration % (24 * 60 * 60L) / (60 * 60L); /* greater than an hour */
        final long minutes = duration % (24 * 60 * 60L) % (60 * 60L) / 60L;
        final long seconds = duration % 60L;

        if (duration < 0) {
            output = "0:00";
        } else if (days > 0) {
            //handle days
            output = String.format(Locale.US, "%d:%02d:%02d:%02d", days, hours, minutes, seconds);
        } else if (hours > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            output = String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
        final String durationPostfix = isDurationComplete ? "" : "+";
        return output + durationPostfix;
    }

    /**
     * Localize an amount of seconds into a human readable string.
     *
     * <p>The seconds will be converted to the closest whole time unit.
     * <p>For example, 60 seconds would give "1 minute", 119 would also give "1 minute".
     *
     * @param context        used to get plurals resources.
     * @param durationInSecs an amount of seconds.
     * @return duration in a human readable string.
     */
    @NonNull
    public static String localizeDuration(final Context context, final int durationInSecs) {
        if (durationInSecs < 0) {
            throw new IllegalArgumentException("duration can not be negative");
        }

        final int days = (int) (durationInSecs / (24 * 60 * 60L));
        final int hours = (int) (durationInSecs % (24 * 60 * 60L) / (60 * 60L));
        final int minutes = (int) (durationInSecs % (24 * 60 * 60L) % (60 * 60L) / 60L);
        final int seconds = (int) (durationInSecs % (24 * 60 * 60L) % (60 * 60L) % 60L);

        final Resources resources = context.getResources();

        if (days > 0) {
            return resources.getQuantityString(R.plurals.days, days, days);
        } else if (hours > 0) {
            return resources.getQuantityString(R.plurals.hours, hours, hours);
        } else if (minutes > 0) {
            return resources.getQuantityString(R.plurals.minutes, minutes, minutes);
        } else {
            return resources.getQuantityString(R.plurals.seconds, seconds, seconds);
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Pretty Time
    //////////////////////////////////////////////////////////////////////////*/

    public static void initPrettyTime(final PrettyTime time) {
        prettyTime = time;
        // Do not use decades as YouTube doesn't either.
        prettyTime.removeUnit(Decade.class);
    }

    public static PrettyTime resolvePrettyTime(final Context context) {
        return new PrettyTime(getAppLocale(context));
    }

    public static String relativeTime(final OffsetDateTime offsetDateTime) {
        return prettyTime.formatUnrounded(offsetDateTime);
    }

    private static void changeAppLanguage(final Locale loc, final Resources res) {
        final DisplayMetrics dm = res.getDisplayMetrics();
        final Configuration conf = res.getConfiguration();
        conf.setLocale(loc);
        res.updateConfiguration(conf, dm);
    }

    public static Locale getAppLocale(final Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lang = prefs.getString(context.getString(R.string.app_language_key), "en");
        final Locale loc;
        if (lang.equals(context.getString(R.string.default_localization_key))) {
            loc = Locale.getDefault();
        } else if (lang.matches(".*-.*")) {
            //to differentiate different versions of the language
            //for example, pt (portuguese in Portugal) and pt-br (portuguese in Brazil)
            final String[] localisation = lang.split("-");
            lang = localisation[0];
            final String country = localisation[1];
            loc = new Locale(lang, country);
        } else {
            loc = new Locale(lang);
        }
        return loc;
    }

    public static void assureCorrectAppLanguage(final Context c) {
        changeAppLanguage(getAppLocale(c), c.getResources());
    }

    private static double round(final double value, final int places) {
        return new BigDecimal(value).setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * Workaround to match normalized captions like english to English or deutsch to Deutsch.
     * @param list the list to search into
     * @param toFind the string to look for
     * @return whether the string was found or not
     */
    public static boolean containsCaseInsensitive(final List<String> list, final String toFind) {
        for (final String i : list) {
            if (i.equalsIgnoreCase(toFind)) {
                return true;
            }
        }
        return false;
    }
}
