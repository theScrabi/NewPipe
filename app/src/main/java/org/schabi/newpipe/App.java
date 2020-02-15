package org.schabi.newpipe;

import android.annotation.TargetApi;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.nostra13.universalimageloader.cache.memory.impl.LRULimitedMemoryCache;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import org.acra.ACRA;
import org.acra.config.ACRAConfiguration;
import org.acra.config.ACRAConfigurationException;
import org.acra.config.ConfigurationBuilder;
import org.acra.sender.ReportSenderFactory;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.report.AcraReportSenderFactory;
import org.schabi.newpipe.report.ErrorActivity;
import org.schabi.newpipe.report.UserAction;
import org.schabi.newpipe.settings.SettingsActivity;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StateSaver;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import cafe.adriel.androidaudioconverter.AndroidAudioConverter;
import cafe.adriel.androidaudioconverter.callback.ILoadCallback;
import io.reactivex.annotations.NonNull;
import io.reactivex.exceptions.CompositeException;
import io.reactivex.exceptions.MissingBackpressureException;
import io.reactivex.exceptions.OnErrorNotImplementedException;
import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.functions.Consumer;
import io.reactivex.plugins.RxJavaPlugins;

/*
 * Copyright (C) Hans-Christoph Steiner 2016 <hans@eds.org>
 * App.java is part of NewPipe.
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

public class App extends Application {
    protected static final String TAG = App.class.toString();
    private RefWatcher refWatcher;
    private static App app;

    @SuppressWarnings("unchecked")
    private static final Class<? extends ReportSenderFactory>[]
            reportSenderFactoryClasses = new Class[]{AcraReportSenderFactory.class};

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        initACRA();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        refWatcher = installLeakCanary();

        app = this;

        // Initialize settings first because others inits can use its values
        SettingsActivity.initSettings(this);

        NewPipe.init(getDownloader(),
                Localization.getPreferredLocalization(this),
                Localization.getPreferredContentCountry(this));
        Localization.init(getApplicationContext());

        StateSaver.init(this);
        initNotificationChannel();

        ServiceHelper.initServices(this);

        // Initialize image loader
        ImageLoader.getInstance().init(getImageLoaderConfigurations(10, 50));

        configureRxJavaErrorHandler();

        // Check for new version
        new CheckForNewAppVersionTask().execute();
        AndroidAudioConverter.load(this, new ILoadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Android audio converter loaded successfully");
            }

            @Override
            public void onFailure(Exception error) {
                Log.e(TAG, "Android audio converter failed to load : FFmpeg is not supported by device");
            }
        });
    }

    protected Downloader getDownloader() {
        return DownloaderImpl.init(null);
    }

    private void configureRxJavaErrorHandler() {
        // https://github.com/ReactiveX/RxJava/wiki/What's-different-in-2.0#error-handling
        RxJavaPlugins.setErrorHandler(new Consumer<Throwable>() {
            @Override
            public void accept(@NonNull Throwable throwable) {
                Log.e(TAG, "RxJavaPlugins.ErrorHandler called with -> : " +
                        "throwable = [" + throwable.getClass().getName() + "]");

                if (throwable instanceof UndeliverableException) {
                    // As UndeliverableException is a wrapper, get the cause of it to get the "real" exception
                    throwable = throwable.getCause();
                }

                final List<Throwable> errors;
                if (throwable instanceof CompositeException) {
                    errors = ((CompositeException) throwable).getExceptions();
                } else {
                    errors = Collections.singletonList(throwable);
                }

                for (final Throwable error : errors) {
                    if (isThrowableIgnored(error)) return;
                    if (isThrowableCritical(error)) {
                        reportException(error);
                        return;
                    }
                }

                // Out-of-lifecycle exceptions should only be reported if a debug user wishes so,
                // When exception is not reported, log it
                if (isDisposedRxExceptionsReported()) {
                    reportException(throwable);
                } else {
                    Log.e(TAG, "RxJavaPlugin: Undeliverable Exception received: ", throwable);
                }
            }

            private boolean isThrowableIgnored(@NonNull final Throwable throwable) {
                // Don't crash the application over a simple network problem
                return ExtractorHelper.hasAssignableCauseThrowable(throwable,
                        IOException.class, SocketException.class, // network api cancellation
                        InterruptedException.class, InterruptedIOException.class); // blocking code disposed
            }

            private boolean isThrowableCritical(@NonNull final Throwable throwable) {
                // Though these exceptions cannot be ignored
                return ExtractorHelper.hasAssignableCauseThrowable(throwable,
                        NullPointerException.class, IllegalArgumentException.class, // bug in app
                        OnErrorNotImplementedException.class, MissingBackpressureException.class,
                        IllegalStateException.class); // bug in operator
            }

            private void reportException(@NonNull final Throwable throwable) {
                // Throw uncaught exception that will trigger the report system
                Thread.currentThread().getUncaughtExceptionHandler()
                        .uncaughtException(Thread.currentThread(), throwable);
            }
        });
    }

    private ImageLoaderConfiguration getImageLoaderConfigurations(final int memoryCacheSizeMb,
                                                                  final int diskCacheSizeMb) {
        return new ImageLoaderConfiguration.Builder(this)
                .memoryCache(new LRULimitedMemoryCache(memoryCacheSizeMb * 1024 * 1024))
                .diskCacheSize(diskCacheSizeMb * 1024 * 1024)
                .imageDownloader(new ImageDownloader(getApplicationContext()))
                .build();
    }

    private void initACRA() {
        try {
            final ACRAConfiguration acraConfig = new ConfigurationBuilder(this)
                    .setReportSenderFactoryClasses(reportSenderFactoryClasses)
                    .setBuildConfigClass(BuildConfig.class)
                    .build();
            ACRA.init(this, acraConfig);
        } catch (ACRAConfigurationException ace) {
            ace.printStackTrace();
            ErrorActivity.reportError(this,
                    ace,
                    null,
                    null,
                    ErrorActivity.ErrorInfo.make(UserAction.SOMETHING_ELSE, "none",
                    "Could not initialize ACRA crash report", R.string.app_ui_crash));
        }
    }

    public void initNotificationChannel() {
        if (Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            return;
        }

        final String id = getString(R.string.notification_channel_id);
        final CharSequence name = getString(R.string.notification_channel_name);
        final String description = getString(R.string.notification_channel_description);

        // Keep this below DEFAULT to avoid making noise on every notification update
        final int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel mChannel = new NotificationChannel(id, name, importance);
        mChannel.setDescription(description);

        NotificationManager mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(mChannel);

        setUpUpdateNotificationChannel(importance);
    }

    /**
     * Set up notification channel for app update.
     * @param importance
     */
    @TargetApi(Build.VERSION_CODES.O)
    private void setUpUpdateNotificationChannel(int importance) {

        final String appUpdateId
                = getString(R.string.app_update_notification_channel_id);
        final CharSequence appUpdateName
                = getString(R.string.app_update_notification_channel_name);
        final String appUpdateDescription
                = getString(R.string.app_update_notification_channel_description);

        NotificationChannel appUpdateChannel
                = new NotificationChannel(appUpdateId, appUpdateName, importance);
        appUpdateChannel.setDescription(appUpdateDescription);

        NotificationManager appUpdateNotificationManager
                = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        appUpdateNotificationManager.createNotificationChannel(appUpdateChannel);
    }

    @Nullable
    public static RefWatcher getRefWatcher(Context context) {
        final App application = (App) context.getApplicationContext();
        return application.refWatcher;
    }

    protected RefWatcher installLeakCanary() {
        return RefWatcher.DISABLED;
    }

    protected boolean isDisposedRxExceptionsReported() {
        return false;
    }

    public static App getApp() {
        return app;
    }
}
