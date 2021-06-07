package us.shandian.giga.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Builder;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadActivity;
import org.schabi.newpipe.player.helper.LockManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.get.MissionRecoveryInfo;
import us.shandian.giga.io.StoredDirectoryHelper;
import us.shandian.giga.io.StoredFileHelper;
import us.shandian.giga.postprocessing.Postprocessing;
import us.shandian.giga.service.DownloadManager.NetworkState;

import static org.schabi.newpipe.BuildConfig.APPLICATION_ID;
import static org.schabi.newpipe.BuildConfig.DEBUG;

public class DownloadManagerService extends Service {

    private static final String TAG = "DownloadManagerService";

    public static final int MESSAGE_RUNNING = 0;
    public static final int MESSAGE_PAUSED = 1;
    public static final int MESSAGE_FINISHED = 2;
    public static final int MESSAGE_ERROR = 3;
    public static final int MESSAGE_DELETED = 4;

    private static final int FOREGROUND_NOTIFICATION_ID = 1000;
    private static final int DOWNLOADS_NOTIFICATION_ID = 1001;

    private static final String EXTRA_URLS = "DownloadManagerService.extra.urls";
    private static final String EXTRA_KIND = "DownloadManagerService.extra.kind";
    private static final String EXTRA_THREADS = "DownloadManagerService.extra.threads";
    private static final String EXTRA_POSTPROCESSING_NAME = "DownloadManagerService.extra.postprocessingName";
    private static final String EXTRA_POSTPROCESSING_ARGS = "DownloadManagerService.extra.postprocessingArgs";
    private static final String EXTRA_SOURCE = "DownloadManagerService.extra.source";
    private static final String EXTRA_NEAR_LENGTH = "DownloadManagerService.extra.nearLength";
    private static final String EXTRA_PATH = "DownloadManagerService.extra.storagePath";
    private static final String EXTRA_PARENT_PATH = "DownloadManagerService.extra.storageParentPath";
    private static final String EXTRA_STORAGE_TAG = "DownloadManagerService.extra.storageTag";
    private static final String EXTRA_RECOVERY_INFO = "DownloadManagerService.extra.recoveryInfo";

    private static final String ACTION_RESET_DOWNLOAD_FINISHED = APPLICATION_ID + ".reset_download_finished";
    private static final String ACTION_OPEN_DOWNLOADS_FINISHED = APPLICATION_ID + ".open_downloads_finished";

    private DownloadManagerBinder mBinder;
    private DownloadManager mManager;
    private Notification mNotification;
    private Handler mHandler;
    private boolean mForeground = false;
    private NotificationManager mNotificationManager = null;
    private boolean mDownloadNotificationEnable = true;

    private int downloadDoneCount = 0;
    private Builder downloadDoneNotification = null;
    private StringBuilder downloadDoneList = null;

    private final ArrayList<Callback> mEchoObservers = new ArrayList<>(1);

    private ConnectivityManager mConnectivityManager;
    private BroadcastReceiver mNetworkStateListener = null;
    private ConnectivityManager.NetworkCallback mNetworkStateListenerL = null;

    private SharedPreferences mPrefs = null;
    private final OnSharedPreferenceChangeListener mPrefChangeListener = this::handlePreferenceChange;

    private boolean mLockAcquired = false;
    private LockManager mLock = null;

    private int downloadFailedNotificationID = DOWNLOADS_NOTIFICATION_ID + 1;
    private Builder downloadFailedNotification = null;
    private final SparseArray<DownloadMission> mFailedDownloads = new SparseArray<>(5);

    private Bitmap icLauncher;
    private Bitmap icDownloadDone;
    private Bitmap icDownloadFailed;

    private PendingIntent mOpenDownloadList;

    /**
     * notify media scanner on downloaded media file ...
     *
     * @param file the downloaded file uri
     */
    private void notifyMediaScanner(Uri file) {
        sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, file));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (DEBUG) {
            Log.d(TAG, "onCreate");
        }

        mBinder = new DownloadManagerBinder();
        mHandler = new Handler(this::handleMessage);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        mManager = new DownloadManager(this, mHandler, loadMainVideoStorage(), loadMainAudioStorage());

        Intent openDownloadListIntent = new Intent(this, DownloadActivity.class)
                .setAction(Intent.ACTION_MAIN);

        mOpenDownloadList = PendingIntent.getActivity(this, 0,
                openDownloadListIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        icLauncher = BitmapFactory.decodeResource(this.getResources(), R.drawable.ic_launcher);

        Builder builder = new Builder(this, getString(R.string.notification_channel_id))
                .setContentIntent(mOpenDownloadList)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setLargeIcon(icLauncher)
                .setContentTitle(getString(R.string.msg_running))
                .setContentText(getString(R.string.msg_running_detail));

        mNotification = builder.build();

        mNotificationManager = ContextCompat.getSystemService(this,
                NotificationManager.class);
        mConnectivityManager = ContextCompat.getSystemService(this,
                ConnectivityManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mNetworkStateListenerL = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    handleConnectivityState(false);
                }

                @Override
                public void onLost(Network network) {
                    handleConnectivityState(false);
                }
            };
            mConnectivityManager.registerNetworkCallback(new NetworkRequest.Builder().build(), mNetworkStateListenerL);
        } else {
            mNetworkStateListener = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    handleConnectivityState(false);
                }
            };
            registerReceiver(mNetworkStateListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }

        mPrefs.registerOnSharedPreferenceChangeListener(mPrefChangeListener);

        handlePreferenceChange(mPrefs, getString(R.string.downloads_cross_network));
        handlePreferenceChange(mPrefs, getString(R.string.downloads_maximum_retry));
        handlePreferenceChange(mPrefs, getString(R.string.downloads_queue_limit));

        mLock = new LockManager(this);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.d(TAG, intent == null ? "Restarting" : "Starting");
        }

        if (intent == null) return START_NOT_STICKY;

        Log.i(TAG, "Got intent: " + intent);
        String action = intent.getAction();
        if (action != null) {
            if (action.equals(Intent.ACTION_RUN)) {
                mHandler.post(() -> startMission(intent));
            } else if (downloadDoneNotification != null) {
                if (action.equals(ACTION_RESET_DOWNLOAD_FINISHED) || action.equals(ACTION_OPEN_DOWNLOADS_FINISHED)) {
                    downloadDoneCount = 0;
                    downloadDoneList.setLength(0);
                }
                if (action.equals(ACTION_OPEN_DOWNLOADS_FINISHED)) {
                    startActivity(new Intent(this, DownloadActivity.class)
                            .setAction(Intent.ACTION_MAIN)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    );
                }
                return START_NOT_STICKY;
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (DEBUG) {
            Log.d(TAG, "Destroying");
        }

        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);

        if (mNotificationManager != null && downloadDoneNotification != null) {
            downloadDoneNotification.setDeleteIntent(null);// prevent NewPipe running when is killed, cleared from recent, etc
            mNotificationManager.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification.build());
        }

        manageLock(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mConnectivityManager.unregisterNetworkCallback(mNetworkStateListenerL);
        else
            unregisterReceiver(mNetworkStateListener);

        mPrefs.unregisterOnSharedPreferenceChangeListener(mPrefChangeListener);

        if (icDownloadDone != null) icDownloadDone.recycle();
        if (icDownloadFailed != null) icDownloadFailed.recycle();
        if (icLauncher != null) icLauncher.recycle();

        mHandler = null;
        mManager.pauseAllMissions(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        /*
        int permissionCheck;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
            permissionCheck = PermissionChecker.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            if (permissionCheck == PermissionChecker.PERMISSION_DENIED) {
                Toast.makeText(this, "Permission denied (read)", Toast.LENGTH_SHORT).show();
            }
        }

        permissionCheck = PermissionChecker.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissionCheck == PermissionChecker.PERMISSION_DENIED) {
            Toast.makeText(this, "Permission denied (write)", Toast.LENGTH_SHORT).show();
        }
        */

        return mBinder;
    }

    private boolean handleMessage(@NonNull Message msg) {
        if (mHandler == null) return true;

        DownloadMission mission = (DownloadMission) msg.obj;

        switch (msg.what) {
            case MESSAGE_FINISHED:
                notifyMediaScanner(mission.storage.getUri());
                notifyFinishedDownload(mission.storage.getName());
                mManager.setFinished(mission);
                handleConnectivityState(false);
                updateForegroundState(mManager.runMissions());
                break;
            case MESSAGE_RUNNING:
                updateForegroundState(true);
                break;
            case MESSAGE_ERROR:
                notifyFailedDownload(mission);
                handleConnectivityState(false);
                updateForegroundState(mManager.runMissions());
                break;
            case MESSAGE_PAUSED:
                updateForegroundState(mManager.getRunningMissionsCount() > 0);
                break;
        }

        if (msg.what != MESSAGE_ERROR)
            mFailedDownloads.delete(mFailedDownloads.indexOfValue(mission));

        for (Callback observer : mEchoObservers)
            observer.handleMessage(msg);

        return true;
    }

    private void handleConnectivityState(boolean updateOnly) {
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        NetworkState status;

        if (info == null) {
            status = NetworkState.Unavailable;
            Log.i(TAG, "Active network [connectivity is unavailable]");
        } else {
            boolean connected = info.isConnected();
            boolean metered = mConnectivityManager.isActiveNetworkMetered();

            if (connected)
                status = metered ? NetworkState.MeteredOperating : NetworkState.Operating;
            else
                status = NetworkState.Unavailable;

            Log.i(TAG, "Active network [connected=" + connected + " metered=" + metered + "] " + info.toString());
        }

        if (mManager == null) return;// avoid race-conditions while the service is starting
        mManager.handleConnectivityState(status, updateOnly);
    }

    private void handlePreferenceChange(SharedPreferences prefs, @NonNull String key) {
        if (key.equals(getString(R.string.downloads_maximum_retry))) {
            try {
                String value = prefs.getString(key, getString(R.string.downloads_maximum_retry_default));
                mManager.mPrefMaxRetry = value == null ? 0 : Integer.parseInt(value);
            } catch (Exception e) {
                mManager.mPrefMaxRetry = 0;
            }
            mManager.updateMaximumAttempts();
        } else if (key.equals(getString(R.string.downloads_cross_network))) {
            mManager.mPrefMeteredDownloads = prefs.getBoolean(key, false);
        } else if (key.equals(getString(R.string.downloads_queue_limit))) {
            mManager.mPrefQueueLimit = prefs.getBoolean(key, true);
        } else if (key.equals(getString(R.string.download_path_video_key))) {
            mManager.mMainStorageVideo = loadMainVideoStorage();
        } else if (key.equals(getString(R.string.download_path_audio_key))) {
            mManager.mMainStorageAudio = loadMainAudioStorage();
        }
    }

    public void updateForegroundState(boolean state) {
        if (state == mForeground) return;

        if (state) {
            startForeground(FOREGROUND_NOTIFICATION_ID, mNotification);
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        }

        manageLock(state);

        mForeground = state;
    }

    /**
     * Start a new download mission
     *
     * @param context      the activity context
     * @param urls         array of urls to download
     * @param storage      where the file is saved
     * @param kind         type of file (a: audio  v: video  s: subtitle ?: file-extension defined)
     * @param threads      the number of threads maximal used to download chunks of the file.
     * @param psName       the name of the required post-processing algorithm, or {@code null} to ignore.
     * @param source       source url of the resource
     * @param psArgs       the arguments for the post-processing algorithm.
     * @param nearLength   the approximated final length of the file
     * @param recoveryInfo array of MissionRecoveryInfo, in case is required recover the download
     */
    public static void startMission(Context context, String[] urls, StoredFileHelper storage,
                                    char kind, int threads, String source, String psName,
                                    String[] psArgs, long nearLength, MissionRecoveryInfo[] recoveryInfo) {
        Intent intent = new Intent(context, DownloadManagerService.class);
        intent.setAction(Intent.ACTION_RUN);
        intent.putExtra(EXTRA_URLS, urls);
        intent.putExtra(EXTRA_KIND, kind);
        intent.putExtra(EXTRA_THREADS, threads);
        intent.putExtra(EXTRA_SOURCE, source);
        intent.putExtra(EXTRA_POSTPROCESSING_NAME, psName);
        intent.putExtra(EXTRA_POSTPROCESSING_ARGS, psArgs);
        intent.putExtra(EXTRA_NEAR_LENGTH, nearLength);
        intent.putExtra(EXTRA_RECOVERY_INFO, recoveryInfo);

        intent.putExtra(EXTRA_PARENT_PATH, storage.getParentUri());
        intent.putExtra(EXTRA_PATH, storage.getUri());
        intent.putExtra(EXTRA_STORAGE_TAG, storage.getTag());

        context.startService(intent);
    }

    private void startMission(Intent intent) {
        String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
        Uri path = intent.getParcelableExtra(EXTRA_PATH);
        Uri parentPath = intent.getParcelableExtra(EXTRA_PARENT_PATH);
        int threads = intent.getIntExtra(EXTRA_THREADS, 1);
        char kind = intent.getCharExtra(EXTRA_KIND, '?');
        String psName = intent.getStringExtra(EXTRA_POSTPROCESSING_NAME);
        String[] psArgs = intent.getStringArrayExtra(EXTRA_POSTPROCESSING_ARGS);
        String source = intent.getStringExtra(EXTRA_SOURCE);
        long nearLength = intent.getLongExtra(EXTRA_NEAR_LENGTH, 0);
        String tag = intent.getStringExtra(EXTRA_STORAGE_TAG);
        Parcelable[] parcelRecovery = intent.getParcelableArrayExtra(EXTRA_RECOVERY_INFO);

        StoredFileHelper storage;
        try {
            storage = new StoredFileHelper(this, parentPath, path, tag);
        } catch (IOException e) {
            throw new RuntimeException(e);// this never should happen
        }

        Postprocessing ps;
        if (psName == null)
            ps = null;
        else
            ps = Postprocessing.getAlgorithm(psName, psArgs);

        MissionRecoveryInfo[] recovery = new MissionRecoveryInfo[parcelRecovery.length];
        for (int i = 0; i < parcelRecovery.length; i++)
            recovery[i] = (MissionRecoveryInfo) parcelRecovery[i];

        final DownloadMission mission = new DownloadMission(urls, storage, kind, ps);
        mission.threadCount = threads;
        mission.source = source;
        mission.nearLength = nearLength;
        mission.recoveryInfo = recovery;

        if (ps != null)
            ps.setTemporalDir(DownloadManager.pickAvailableTemporalDir(this));

        handleConnectivityState(true);// first check the actual network status

        mManager.startMission(mission);
    }

    public void notifyFinishedDownload(String name) {
        if (!mDownloadNotificationEnable || mNotificationManager == null) {
            return;
        }

        if (downloadDoneNotification == null) {
            downloadDoneList = new StringBuilder(name.length());

            icDownloadDone = BitmapFactory.decodeResource(this.getResources(), android.R.drawable.stat_sys_download_done);
            downloadDoneNotification = new Builder(this, getString(R.string.notification_channel_id))
                    .setAutoCancel(true)
                    .setLargeIcon(icDownloadDone)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setDeleteIntent(makePendingIntent(ACTION_RESET_DOWNLOAD_FINISHED))
                    .setContentIntent(makePendingIntent(ACTION_OPEN_DOWNLOADS_FINISHED));
        }

        if (downloadDoneCount < 1) {
            downloadDoneList.append(name);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                downloadDoneNotification.setContentTitle(getString(R.string.app_name));
            } else {
                downloadDoneNotification.setContentTitle(null);
            }

            downloadDoneNotification.setContentText(getString(R.string.download_finished));
            downloadDoneNotification.setStyle(new NotificationCompat.BigTextStyle()
                    .setBigContentTitle(getString(R.string.download_finished))
                    .bigText(name)
            );
        } else {
            downloadDoneList.append('\n');
            downloadDoneList.append(name);

            downloadDoneNotification.setStyle(new NotificationCompat.BigTextStyle().bigText(downloadDoneList));
            downloadDoneNotification.setContentTitle(getString(R.string.download_finished_more, String.valueOf(downloadDoneCount + 1)));
            downloadDoneNotification.setContentText(downloadDoneList);
        }

        mNotificationManager.notify(DOWNLOADS_NOTIFICATION_ID, downloadDoneNotification.build());
        downloadDoneCount++;
    }

    public void notifyFailedDownload(DownloadMission mission) {
        if (!mDownloadNotificationEnable || mFailedDownloads.indexOfValue(mission) >= 0) return;

        int id = downloadFailedNotificationID++;
        mFailedDownloads.put(id, mission);

        if (downloadFailedNotification == null) {
            icDownloadFailed = BitmapFactory.decodeResource(this.getResources(), android.R.drawable.stat_sys_warning);
            downloadFailedNotification = new Builder(this, getString(R.string.notification_channel_id))
                    .setAutoCancel(true)
                    .setLargeIcon(icDownloadFailed)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentIntent(mOpenDownloadList);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            downloadFailedNotification.setContentTitle(getString(R.string.app_name));
            downloadFailedNotification.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(getString(R.string.download_failed).concat(": ").concat(mission.storage.getName())));
        } else {
            downloadFailedNotification.setContentTitle(getString(R.string.download_failed));
            downloadFailedNotification.setContentText(mission.storage.getName());
            downloadFailedNotification.setStyle(new NotificationCompat.BigTextStyle()
                    .bigText(mission.storage.getName()));
        }

        mNotificationManager.notify(id, downloadFailedNotification.build());
    }

    private PendingIntent makePendingIntent(String action) {
        Intent intent = new Intent(this, DownloadManagerService.class).setAction(action);
        return PendingIntent.getService(this, intent.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void manageLock(boolean acquire) {
        if (acquire == mLockAcquired) return;

        if (acquire)
            mLock.acquireWifiAndCpu();
        else
            mLock.releaseWifiAndCpu();

        mLockAcquired = acquire;
    }

    private StoredDirectoryHelper loadMainVideoStorage() {
        return loadMainStorage(R.string.download_path_video_key, DownloadManager.TAG_VIDEO);
    }

    private StoredDirectoryHelper loadMainAudioStorage() {
        return loadMainStorage(R.string.download_path_audio_key, DownloadManager.TAG_AUDIO);
    }

    private StoredDirectoryHelper loadMainStorage(@StringRes int prefKey, String tag) {
        String path = mPrefs.getString(getString(prefKey), null);

        if (path == null || path.isEmpty()) return null;

        if (path.charAt(0) == File.separatorChar) {
            Log.i(TAG, "Old save path style present: " + path);

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                path = Uri.fromFile(new File(path)).toString();
            else
                path = "";

            mPrefs.edit().putString(getString(prefKey), "").apply();
        }

        try {
            return new StoredDirectoryHelper(this, Uri.parse(path), tag);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load the storage of " + tag + " from " + path, e);
            Toast.makeText(this, R.string.no_available_dir, Toast.LENGTH_LONG).show();
        }

        return null;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Wrappers for DownloadManager
    ////////////////////////////////////////////////////////////////////////////////////////////////

    public class DownloadManagerBinder extends Binder {
        public DownloadManager getDownloadManager() {
            return mManager;
        }

        @Nullable
        public StoredDirectoryHelper getMainStorageVideo() {
            return mManager.mMainStorageVideo;
        }

        @Nullable
        public StoredDirectoryHelper getMainStorageAudio() {
            return mManager.mMainStorageAudio;
        }

        public boolean askForSavePath() {
            return DownloadManagerService.this.mPrefs.getBoolean(
                    DownloadManagerService.this.getString(R.string.downloads_storage_ask),
                    false
            );
        }

        public void addMissionEventListener(Callback handler) {
            mEchoObservers.add(handler);
        }

        public void removeMissionEventListener(Callback handler) {
            mEchoObservers.remove(handler);
        }

        public void clearDownloadNotifications() {
            if (mNotificationManager == null) return;
            if (downloadDoneNotification != null) {
                mNotificationManager.cancel(DOWNLOADS_NOTIFICATION_ID);
                downloadDoneList.setLength(0);
                downloadDoneCount = 0;
            }
            if (downloadFailedNotification != null) {
                for (; downloadFailedNotificationID > DOWNLOADS_NOTIFICATION_ID; downloadFailedNotificationID--) {
                    mNotificationManager.cancel(downloadFailedNotificationID);
                }
                mFailedDownloads.clear();
                downloadFailedNotificationID++;
            }
        }

        public void enableNotifications(boolean enable) {
            mDownloadNotificationEnable = enable;
        }

    }

}
