package org.schabi.newpipe.player.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;

public class AudioReactor implements AudioManager.OnAudioFocusChangeListener, AnalyticsListener {

    private static final String TAG = "AudioFocusReactor";

    private static final int DUCK_DURATION = 1500;
    private static final float DUCK_AUDIO_TO = .2f;

    private static final int FOCUS_GAIN_TYPE = AudioManagerCompat.AUDIOFOCUS_GAIN;
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final SimpleExoPlayer player;
    private final Context context;
    private final AudioManager audioManager;

    private final AudioFocusRequestCompat request;

    public AudioReactor(@NonNull final Context context,
                        @NonNull final SimpleExoPlayer player) {
        this.player = player;
        this.context = context;
        this.audioManager = ContextCompat.getSystemService(context, AudioManager.class);
        player.addAnalyticsListener(this);

        request = new AudioFocusRequestCompat.Builder(FOCUS_GAIN_TYPE)
                //.setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this)
                .build();
    }

    public void dispose() {
        abandonAudioFocus();
        player.removeAnalyticsListener(this);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Manager
    //////////////////////////////////////////////////////////////////////////*/

    public void requestAudioFocus() {
        AudioManagerCompat.requestAudioFocus(audioManager, request);
    }

    public void abandonAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, request);
    }

    public int getVolume() {
        return audioManager.getStreamVolume(STREAM_TYPE);
    }

    public void setVolume(final int volume) {
        audioManager.setStreamVolume(STREAM_TYPE, volume, 0);
    }

    public int getMaxVolume() {
        return AudioManagerCompat.getStreamMaxVolume(audioManager, STREAM_TYPE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // AudioFocus
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioFocusChange(final int focusChange) {
        Log.d(TAG, "onAudioFocusChange() called with: focusChange = [" + focusChange + "]");
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                onAudioFocusGain();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                onAudioFocusLossCanDuck();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                onAudioFocusLoss();
                break;
        }
    }

    private void onAudioFocusGain() {
        Log.d(TAG, "onAudioFocusGain() called");
        player.setVolume(DUCK_AUDIO_TO);
        animateAudio(DUCK_AUDIO_TO, 1.0f);

        if (PlayerHelper.isResumeAfterAudioFocusGain(context)) {
            player.play();
        }
    }

    private void onAudioFocusLoss() {
        Log.d(TAG, "onAudioFocusLoss() called");
        player.pause();
    }

    private void onAudioFocusLossCanDuck() {
        Log.d(TAG, "onAudioFocusLossCanDuck() called");
        // Set the volume to 1/10 on ducking
        player.setVolume(DUCK_AUDIO_TO);
    }

    private void animateAudio(final float from, final float to) {
        final ValueAnimator valueAnimator = new ValueAnimator();
        valueAnimator.setFloatValues(from, to);
        valueAnimator.setDuration(AudioReactor.DUCK_DURATION);
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(final Animator animation) {
                player.setVolume(from);
            }

            @Override
            public void onAnimationCancel(final Animator animation) {
                player.setVolume(to);
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                player.setVolume(to);
            }
        });
        valueAnimator.addUpdateListener(animation ->
                player.setVolume(((float) animation.getAnimatedValue())));
        valueAnimator.start();
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Processing
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioSessionIdChanged(final EventTime eventTime, final int audioSessionId) {
        if (!PlayerHelper.isUsingDSP()) {
            return;
        }

        final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }
}
