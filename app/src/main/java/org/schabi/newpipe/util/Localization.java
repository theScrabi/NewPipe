package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;

import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.units.Decade;
import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.localization.ContentCountry;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
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

public class Localization {

    private static final String DOT_SEPARATOR = " • ";
    private static PrettyTime prettyTime;

    private Localization() {
    }

    public static void init(Context context) {
        initPrettyTime(context);
    }

    @NonNull
    public static String concatenateStrings(final String... strings) {
        return concatenateStrings(Arrays.asList(strings));
    }

    @NonNull
    public static String concatenateStrings(final List<String> strings) {
        if (strings.isEmpty()) return "";

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

    public static org.schabi.newpipe.extractor.localization.Localization getPreferredLocalization(final Context context) {
        final String contentLanguage = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.content_language_key), context.getString(R.string.default_localization_key));
        if (contentLanguage.equals(context.getString(R.string.default_localization_key))) {
            return org.schabi.newpipe.extractor.localization.Localization.fromLocale(Locale.getDefault());
        }
        return org.schabi.newpipe.extractor.localization.Localization.fromLocalizationCode(contentLanguage);
    }

    public static ContentCountry getPreferredContentCountry(final Context context) {
        final String contentCountry = PreferenceManager
                .getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.content_country_key), context.getString(R.string.default_localization_key));
        if (contentCountry.equals(context.getString(R.string.default_localization_key))) {
            return new ContentCountry(Locale.getDefault().getCountry());
        }
        return new ContentCountry(contentCountry);
    }

    public static Locale getPreferredLocale(Context context) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        String languageCode = sp.getString(context.getString(R.string.content_language_key),
                context.getString(R.string.default_localization_key));

        try {
            if (languageCode.length() == 2) {
                return new Locale(languageCode);
            } else if (languageCode.contains("_")) {
                String country = languageCode.substring(languageCode.indexOf("_"), languageCode.length());
                return new Locale(languageCode.substring(0, 2), country);
            }
        } catch (Exception ignored) {
        }

        return Locale.getDefault();
    }

    public static String localizeNumber(Context context, long number) {
        NumberFormat nf = NumberFormat.getInstance(getAppLocale(context));
        return nf.format(number);
    }

    public static String formatDate(Date date, Context context) {
        return DateFormat.getDateInstance(DateFormat.MEDIUM, getAppLocale(context)).format(date);
    }

    @SuppressLint("StringFormatInvalid")
    public static String localizeUploadDate(Context context, Date date) {
        return context.getString(R.string.upload_date_text, formatDate(date, context));
    }

    public static String localizeViewCount(Context context, long viewCount) {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount, localizeNumber(context, viewCount));
    }

    public static String localizeStreamCount(Context context, long streamCount) {
        return getQuantity(context, R.plurals.videos, R.string.no_videos, streamCount, localizeNumber(context, streamCount));
    }

    public static String localizeWatchingCount(Context context, long watchingCount) {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount, localizeNumber(context, watchingCount));
    }

    public static String shortCount(Context context, long count) {
        if (count >= 1000000000) {
            return Long.toString(count / 1000000000) + context.getString(R.string.short_billion);
        } else if (count >= 1000000) {
            return Long.toString(count / 1000000) + context.getString(R.string.short_million);
        } else if (count >= 1000) {
            return Long.toString(count / 1000) + context.getString(R.string.short_thousand);
        } else {
            return Long.toString(count);
        }
    }

    public static String listeningCount(Context context, long listeningCount) {
        return getQuantity(context, R.plurals.listening, R.string.no_one_listening, listeningCount, shortCount(context, listeningCount));
    }

    public static String shortWatchingCount(Context context, long watchingCount) {
        return getQuantity(context, R.plurals.watching, R.string.no_one_watching, watchingCount, shortCount(context, watchingCount));
    }

    public static String shortViewCount(Context context, long viewCount) {
        return getQuantity(context, R.plurals.views, R.string.no_views, viewCount, shortCount(context, viewCount));
    }

    public static String shortSubscriberCount(Context context, long subscriberCount) {
        return getQuantity(context, R.plurals.subscribers, R.string.no_subscribers, subscriberCount, shortCount(context, subscriberCount));
    }

    private static String getQuantity(Context context, @PluralsRes int pluralId, @StringRes int zeroCaseStringId, long count, String formattedCount) {
        if (count == 0) return context.getString(zeroCaseStringId);

        // As we use the already formatted count, is not the responsibility of this method handle long numbers
        // (it probably will fall in the "other" category, or some language have some specific rule... then we have to change it)
        int safeCount = count > Integer.MAX_VALUE ? Integer.MAX_VALUE : count < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) count;
        return context.getResources().getQuantityString(pluralId, safeCount, formattedCount);
    }

    public static String getDurationString(long duration) {
        if (duration < 0) {
            duration = 0;
        }
        String output;
        long days = duration / (24 * 60 * 60L); /* greater than a day */
        duration %= (24 * 60 * 60L);
        long hours = duration / (60 * 60L); /* greater than an hour */
        duration %= (60 * 60L);
        long minutes = duration / 60L;
        long seconds = duration % 60L;

        //handle days
        if (days > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d:%02d", days, hours, minutes, seconds);
        } else if (hours > 0) {
            output = String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            output = String.format(Locale.US, "%d:%02d", minutes, seconds);
        }
        return output;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Pretty Time
    //////////////////////////////////////////////////////////////////////////*/

    private static void initPrettyTime(Context context) {
        prettyTime = new PrettyTime(getAppLocale(context));
        // Do not use decades as YouTube doesn't either.
        prettyTime.removeUnit(Decade.class);
    }

    private static PrettyTime getPrettyTime() {
        return prettyTime;
    }

    public static String relativeTime(Calendar calendarTime) {
        String time = getPrettyTime().formatUnrounded(calendarTime);
        return time.startsWith("-") ? time.substring(1) : time;
        //workaround fix for russian showing -1 day ago, -19hrs ago…
    }

    private static void changeAppLanguage(Locale loc, Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        Configuration conf = res.getConfiguration();
        conf.setLocale(loc);
        res.updateConfiguration(conf, dm);
    }

    public static Locale getAppLocale(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lang = prefs.getString(context.getString(R.string.app_language_key), "en");
        Locale loc;
        if (lang.equals(context.getString(R.string.default_localization_key))) {
            loc = Locale.getDefault();
        } else if (lang.matches(".*-.*")) {
            //to differentiate different versions of the language
            //for example, pt (portuguese in Portugal) and pt-br (portuguese in Brazil)
            String[] localisation = lang.split("-");
            lang = localisation[0];
            String country = localisation[1];
            loc = new Locale(lang, country);
        } else {
            loc = new Locale(lang);
        }
        return loc;
    }

    public static void assureCorrectAppLanguage(Context c) {
        changeAppLanguage(getAppLocale(c), c.getResources());
    }
}
