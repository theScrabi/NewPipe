package org.schabi.newpipe.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.ImageSize;

import org.schabi.newpipe.BuildConfig;
import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.subscription.SubscriptionEntity;
import org.schabi.newpipe.extractor.channel.ChannelInfoItem;

import java.util.Collections;
import java.util.List;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.CheckReturnValue;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public final class ShortcutsHelper {

    public static final String ACTION_OPEN_SHORTCUT = "org.schabi.newpipe.action.OPEN_SHORTCUT";

    private ShortcutsHelper() {
    }

    @Nullable
    @CheckReturnValue
    public static Disposable addShortcut(@Nullable final Context context, @NonNull final ChannelInfoItem data) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || context == null) {
            return null;
        }
        final ShortcutManager manager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        if (manager == null) {
            return null;
        }
        return Single.fromCallable(() -> getIcon(context, data.getThumbnailUrl(), manager.getIconMaxWidth(),
                manager.getIconMaxHeight(), R.drawable.ic_newpipe_triangle_white))
                .subscribeOn(Schedulers.io())
                .map(icon -> new ShortcutInfo.Builder(context, getShortcutId(data.getUrl()))
                        .setShortLabel(data.getName())
                        .setLongLabel(data.getName())
                        .setIcon(icon.toIcon())
                        .setIntent(createIntent(context, data)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(builder -> {
                    final String id = getShortcutId(data.getUrl());
                    final List<ShortcutInfo> shortcuts = manager.getDynamicShortcuts();
                    final int limit = manager.getMaxShortcutCountPerActivity();
                    for (int i = 0; i < shortcuts.size(); i++) {
                        final ShortcutInfo shortcut = shortcuts.get(i);
                        if (id.equals(shortcut.getId())) {
                            // if shortcut already exists - increase rank and update it
                            builder.setRank(shortcut.getRank() + 1);
                            manager.updateShortcuts(Collections.singletonList(builder.build()));
                            return;
                        }
                    }
                    builder.setRank(1);
                    if (!shortcuts.isEmpty() && shortcuts.size() >= limit) {
                        // we will get an exception if shortcuts count exceed the limit
                        manager.removeDynamicShortcuts(Collections.singletonList(shortcuts.get(shortcuts.size() - 1).getId()));
                    }
                    manager.addDynamicShortcuts(Collections.singletonList(builder.build()));
                }, e -> {
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace();
                    }
                });
    }

    public static void removeShortcut(@Nullable final Context context, @NonNull SubscriptionEntity subscription) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1 || context == null) {
            return;
        }
        final ShortcutManager manager = (ShortcutManager) context.getSystemService(Context.SHORTCUT_SERVICE);
        if (manager == null) {
            return;
        }
        final String shortcutId = getShortcutId(subscription.getUrl());
        manager.removeDynamicShortcuts(Collections.singletonList(shortcutId));
    }

    @Nullable
    @CheckReturnValue
    public static Disposable pinShortcut(@Nullable final Context context, @NonNull final ChannelInfoItem data) {
        if (context == null) {
            return null;
        }
        final ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return null;
        }
        final int iconSize = am.getLauncherLargeIconSize();
        return Single.fromCallable(() -> getIcon(context, data.getThumbnailUrl(), iconSize, iconSize, R.mipmap.ic_launcher))
                .subscribeOn(Schedulers.io())
                .map(icon -> new ShortcutInfoCompat.Builder(context, getShortcutId(data.getUrl()))
                        .setShortLabel(data.getName())
                        .setLongLabel(data.getName())
                        .setIcon(icon)
                        .setIntent(createIntent(context, data)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(builder -> ShortcutManagerCompat.requestPinShortcut(context, builder.build(), null), e -> {
                    if (BuildConfig.DEBUG) {
                        e.printStackTrace();
                    }
                });
    }

    @NonNull
    private static Intent createIntent(@NonNull Context context, @NonNull ChannelInfoItem channel) {
        final Intent intent = new Intent(context, MainActivity.class);
        intent.setAction(ACTION_OPEN_SHORTCUT);
        intent.putExtra(Constants.KEY_URL, channel.getUrl());
        intent.putExtra(Constants.KEY_SERVICE_ID, channel.getServiceId());
        intent.putExtra(Constants.KEY_TITLE, channel.getName());
        return intent;
    }

    @NonNull
    private static String getShortcutId(@NonNull String channelUrl) {
        return "s_" + channelUrl.hashCode();
    }

    @NonNull
    private static IconCompat getIcon(@NonNull Context context, final String url, int width,
                                      int height, @DrawableRes int defaultIcon) {
        Bitmap bitmap = ImageLoader.getInstance().loadImageSync(url, new ImageSize(width, height),
                ImageDisplayConstants.DISPLAY_AVATAR_OPTIONS);
        return bitmap != null ? IconCompat.createWithBitmap(createCircleBitmap(bitmap, true)) :
                IconCompat.createWithResource(context, defaultIcon);
    }

    @NonNull
    private static Bitmap createCircleBitmap(@NonNull final Bitmap bitmap, boolean recycleInput) {
        final Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
                bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);
        canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2,
                bitmap.getWidth() / 2, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);
        if (recycleInput) {
            bitmap.recycle();
        }
        return output;
    }
}
