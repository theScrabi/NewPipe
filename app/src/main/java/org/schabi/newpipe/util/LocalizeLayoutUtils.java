package org.schabi.newpipe.util;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;

public final class LocalizeLayoutUtils {
    private LocalizeLayoutUtils() { }

    public static int getLayoutPosition(final boolean isRTL, final int count, final int position) {
        return isRTL
                ? count - 1 - position
                : position;
    }

    public static boolean isRTL(final Context context) {
        Configuration config = context.getResources().getConfiguration();

        return config.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }
}
