package org.schabi.newpipe.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;

import com.squareup.picasso.Cache;
import com.squareup.picasso.LruCache;
import com.squareup.picasso.OkHttp3Downloader;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;

import org.schabi.newpipe.R;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;

import static org.schabi.newpipe.extractor.utils.Utils.isBlank;

public final class PicassoHelper {

    private PicassoHelper() {
    }

    private static Cache picassoCache;
    private static OkHttpClient picassoDownloaderClient;

    // suppress because terminate() is called in App.onTerminate(), preventing leaks
    @SuppressLint("StaticFieldLeak")
    private static Picasso picassoInstance;

    private static boolean shouldLoadImages;

    public static void init(final Context context) {
        picassoCache = new LruCache(10 * 1024 * 1024);
        picassoDownloaderClient = new OkHttpClient.Builder()
                .cache(new okhttp3.Cache(new File(context.getExternalCacheDir(), "picasso"),
                        50 * 1024 * 1024))
                .build();

        picassoInstance = new Picasso.Builder(context)
                .memoryCache(picassoCache) // memory cache
                .downloader(new OkHttp3Downloader(picassoDownloaderClient)) // disk cache
                .defaultBitmapConfig(Bitmap.Config.RGB_565)
                .build();
    }

    public static void terminate() {
        picassoCache = null;
        picassoDownloaderClient = null;

        if (picassoInstance != null) {
            picassoInstance.shutdown();
            picassoInstance = null;
        }
    }

    public static void clearCache(final Context context) throws IOException {
        picassoInstance.shutdown();
        picassoCache.clear(); // clear memory cache
        picassoDownloaderClient.cache().delete(); // clear disk cache
        init(context);
    }

    public static void cancelTag(final Object tag) {
        picassoInstance.cancelTag(tag);
    }

    public static void setShouldLoadImages(final boolean shouldLoadImages) {
        PicassoHelper.shouldLoadImages = shouldLoadImages;
    }

    public static boolean getShouldLoadImages() {
        return shouldLoadImages;
    }


    public static RequestCreator loadAvatar(final String url) {
        return loadImageDefault(url, R.drawable.buddy);
    }

    public static RequestCreator loadThumbnail(final String url) {
        return loadImageDefault(url, R.drawable.dummy_thumbnail);
    }

    public static RequestCreator loadBanner(final String url) {
        return loadImageDefault(url, R.drawable.channel_banner);
    }

    public static RequestCreator loadPlaylistThumbnail(final String url) {
        return loadImageDefault(url, R.drawable.dummy_thumbnail_playlist);
    }


    private static RequestCreator loadImageDefault(final String url, final int placeholderResId) {
        return picassoInstance
                .load((!shouldLoadImages || isBlank(url)) ? null : url)
                .placeholder(placeholderResId)
                .error(placeholderResId);
    }
}
