package org.schabi.newpipe.player.helper;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.session.MediaButtonReceiver;

import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector;

import org.schabi.newpipe.MainActivity;
import org.schabi.newpipe.player.mediasession.MediaSessionCallback;
import org.schabi.newpipe.player.mediasession.PlayQueueNavigator;
import org.schabi.newpipe.player.mediasession.PlayQueuePlaybackController;

public class MediaSessionManager {
    private static final String TAG = MediaSessionManager.class.getSimpleName();
    public static final boolean DEBUG = MainActivity.DEBUG;

    @NonNull
    private final MediaSessionCompat mediaSession;
    @NonNull
    private final MediaSessionConnector sessionConnector;

    private int lastAlbumArtHashCode;

    public MediaSessionManager(@NonNull final Context context,
                               @NonNull final Player player,
                               @NonNull final MediaSessionCallback callback) {
        mediaSession = new MediaSessionCompat(context, TAG);
        mediaSession.setActive(true);

        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, -1, 1)
                .setActions(PlaybackStateCompat.ACTION_SEEK_TO
                        | PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PAUSE // was play and pause now play/pause
                        | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                        | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                        | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                        | PlaybackStateCompat.ACTION_STOP)
                .build());

        sessionConnector = new MediaSessionConnector(mediaSession);
        sessionConnector.setControlDispatcher(new PlayQueuePlaybackController(callback));
        sessionConnector.setQueueNavigator(new PlayQueueNavigator(mediaSession, callback));
        sessionConnector.setPlayer(player);
    }

    @Nullable
    @SuppressWarnings("UnusedReturnValue")
    public KeyEvent handleMediaButtonIntent(final Intent intent) {
        return MediaButtonReceiver.handleIntent(mediaSession, intent);
    }

    public MediaSessionCompat.Token getSessionToken() {
        return mediaSession.getSessionToken();
    }

    public void setMetadata(final String title,
                            final String artist,
                            final Bitmap albumArt,
                            final long duration) {
        if (albumArt == null || !mediaSession.isActive()) {
            return;
        }

        if (DEBUG) {
            if (getMetadataAlbumArt() == null) {
                Log.d(TAG, "N_getMetadataAlbumArt: thumb == null");
            }
            if (getMetadataTitle() == null) {
                Log.d(TAG, "N_getMetadataTitle: title == null");
            }
            if (getMetadataArtist() == null) {
                Log.d(TAG, "N_getMetadataArtist: artist == null");
            }
            if (getMetadataDuration() <= 1) {
                Log.d(TAG, "N_getMetadataDuration: duration <= 1; " + getMetadataDuration());
            }
        }

        if (getMetadataAlbumArt() == null || getMetadataTitle() == null
                || getMetadataArtist() == null || getMetadataDuration() <= 1
                || albumArt.hashCode() != lastAlbumArtHashCode) {
            if (DEBUG) {
                Log.d(TAG, "setMetadata: N_Metadata update: t: " + title + " a: " + artist
                        + " thumb: " + albumArt.hashCode() + " d: " + duration);
            }

            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, albumArt)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration).build());
            lastAlbumArtHashCode = albumArt.hashCode();
        }
    }

    private Bitmap getMetadataAlbumArt() {
        return mediaSession.getController().getMetadata()
                .getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART);
    }

    private String getMetadataTitle() {
        return mediaSession.getController().getMetadata()
                .getString(MediaMetadataCompat.METADATA_KEY_TITLE);
    }

    private String getMetadataArtist() {
        return mediaSession.getController().getMetadata()
                .getString(MediaMetadataCompat.METADATA_KEY_ARTIST);
    }

    private long getMetadataDuration() {
        return mediaSession.getController().getMetadata()
                .getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
    }

    /**
     * Should be called on player destruction to prevent leakage.
     */
    public void dispose() {
        sessionConnector.setPlayer(null);
        sessionConnector.setQueueNavigator(null);
        mediaSession.setActive(false);
        mediaSession.release();
    }
}
