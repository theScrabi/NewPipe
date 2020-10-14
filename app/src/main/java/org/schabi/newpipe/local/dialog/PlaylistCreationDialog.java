package org.schabi.newpipe.local.dialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.stream.model.StreamEntity;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;

public final class PlaylistCreationDialog extends PlaylistDialog {
    public static PlaylistCreationDialog newInstance(final List<StreamEntity> streams) {
        final PlaylistCreationDialog dialog = new PlaylistCreationDialog();
        dialog.setInfo(streams);
        return dialog;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Dialog
    //////////////////////////////////////////////////////////////////////////*/

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        if (getStreams() == null) {
            return super.onCreateDialog(savedInstanceState);
        }

        final View dialogView = View.inflate(getContext(), R.layout.dialog_playlist_name, null);
        final EditText nameInput = dialogView.findViewById(R.id.playlist_name);

        final AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.create, (dialogInterface, i) -> {
                    final String name = nameInput.getText().toString();
                    final LocalPlaylistManager playlistManager =
                            new LocalPlaylistManager(NewPipeDatabase.getInstance(getContext()));
                    final Toast successToast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_success,
                            Toast.LENGTH_SHORT);
                    final Toast duplicateToast = Toast.makeText(getActivity(),
                            R.string.playlist_exists,
                            Toast.LENGTH_SHORT);
                    final Toast failedToast = Toast.makeText(getActivity(),
                            R.string.playlist_creation_failed,
                            Toast.LENGTH_SHORT);
                    playlistManager.createPlaylist(name, getStreams())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(longs -> resultAction(successToast, Activity.RESULT_OK),
                                e -> {
                                    if (e instanceof IllegalArgumentException) {
                                        resultAction(duplicateToast, Activity.RESULT_CANCELED);
                                    } else {
                                        resultAction(failedToast, Activity.RESULT_CANCELED);
                                    }
                            });
                });

        return dialogBuilder.create();
    }

    private void resultAction(final Toast toast, final int resultCode) {
        toast.show();
        final Fragment target = getTargetFragment();
        if (target != null) {
            target.onActivityResult(getTargetRequestCode(), resultCode, null);
        }
    }

}
