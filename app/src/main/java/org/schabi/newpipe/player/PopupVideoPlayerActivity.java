package org.schabi.newpipe.player;

import android.content.Intent;
import android.view.MenuItem;

import org.schabi.newpipe.R;

import static org.schabi.newpipe.player.PopupVideoPlayer.ACTION_CLOSE;

public final class PopupVideoPlayerActivity extends ServicePlayerActivity {

    private static final String TAG = "PopupVideoPlayerActivity";

    @Override
    public String getTag() {
        return TAG;
    }

    @Override
    public String getSupportActionTitle() {
        return getResources().getString(R.string.title_activity_popup_player);
    }

    @Override
    public Intent getBindIntent() {
        return new Intent(this, PopupVideoPlayer.class);
    }

    @Override
    public void startPlayerListener() {
        if (player != null && player instanceof PopupVideoPlayer.VideoPlayerImpl) {
            ((PopupVideoPlayer.VideoPlayerImpl) player).setActivityListener(this);
        }
    }

    @Override
    public void stopPlayerListener() {
        if (player != null && player instanceof PopupVideoPlayer.VideoPlayerImpl) {
            ((PopupVideoPlayer.VideoPlayerImpl) player).removeActivityListener(this);
        }
    }

    @Override
    public int getPlayerOptionMenuResource() {
        return R.menu.menu_play_queue_popup;
    }

    @Override
    public boolean onPlayerOptionSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_switch_background) {
            this.player.setRecovery();
            getApplicationContext().sendBroadcast(getPlayerShutdownIntent());
            getApplicationContext().startService(
                getSwitchIntent(BackgroundPlayer.class)
                    .putExtra(BasePlayer.START_PAUSED, !this.player.isPlaying())
            );
            return true;
        }
        return false;
    }

    @Override
    public Intent getPlayerShutdownIntent() {
        return new Intent(ACTION_CLOSE);
    }
}
