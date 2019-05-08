package org.schabi.newpipe.notifications;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.assist.ImageSize;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleEmitter;
import io.reactivex.rxjava3.core.SingleOnSubscribe;

final class NotificationIcon implements SingleOnSubscribe<Bitmap> {

    private final String url;
    private final int size;

    NotificationIcon(final Context context, final String url) {
        this.url = url;
        this.size = getIconSize(context);
    }

    @Override
    public void subscribe(@NonNull final SingleEmitter<Bitmap> emitter) throws Throwable {
        ImageLoader.getInstance().loadImage(
                url,
                new ImageSize(size, size),
                new SimpleImageLoadingListener() {

                    @Override
                    public void onLoadingFailed(final String imageUri,
                                                final View view,
                                                final FailReason failReason) {
                        emitter.onError(failReason.getCause());
                    }

                    @Override
                    public void onLoadingComplete(final String imageUri,
                                                  final View view,
                                                  final Bitmap loadedImage) {
                        emitter.onSuccess(loadedImage);
                    }
                }
        );
    }

    private static int getIconSize(final Context context) {
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE
        );
        final int size2 = activityManager != null ? activityManager.getLauncherLargeIconSize() : 0;
        final int size1 = context.getResources()
                .getDimensionPixelSize(android.R.dimen.app_icon_size);
        return Math.max(size2, size1);
    }
}
