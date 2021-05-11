package org.schabi.newpipe.local.subscription;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import org.schabi.newpipe.R;

import icepick.Icepick;
import icepick.State;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

public class ImportConfirmationDialog extends DialogFragment {
    @State
    protected Intent resultServiceIntent;

    public static void show(@NonNull final Fragment fragment,
                            @NonNull final Intent resultServiceIntent) {
        if (fragment.getFragmentManager() == null) {
            return;
        }

        final ImportConfirmationDialog confirmationDialog = new ImportConfirmationDialog();
        confirmationDialog.setResultServiceIntent(resultServiceIntent);
        confirmationDialog.show(fragment.getFragmentManager(), null);
    }

    public void setResultServiceIntent(final Intent resultServiceIntent) {
        this.resultServiceIntent = resultServiceIntent;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable final Bundle savedInstanceState) {
        assureCorrectAppLanguage(getContext());
        return new AlertDialog.Builder(getContext())
                .setMessage(R.string.import_network_expensive_warning)
                .setCancelable(true)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.finish, (dialogInterface, i) -> {
                    if (resultServiceIntent != null && getContext() != null) {
                        getContext().startService(resultServiceIntent);
                    }
                    dismiss();
                })
                .create();
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (resultServiceIntent == null) {
            throw new IllegalStateException("Result intent is null");
        }

        Icepick.restoreInstanceState(this, savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        Icepick.saveInstanceState(this, outState);
    }
}
