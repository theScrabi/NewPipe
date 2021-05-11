package org.schabi.newpipe.settings.custom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.widget.TextViewCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import org.schabi.newpipe.R;
import org.schabi.newpipe.player.MainPlayer;
import org.schabi.newpipe.player.NotificationConstants;
import org.schabi.newpipe.util.DeviceUtils;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.FocusOverlayView;

import java.util.List;

public class NotificationActionsPreference extends Preference {

    public NotificationActionsPreference(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.settings_notification);
    }


    @Nullable private NotificationSlot[] notificationSlots = null;
    @Nullable private List<Integer> compactSlots = null;

    ////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setClickable(false);
        setupActions(holder.itemView);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        saveChanges();
        getContext().sendBroadcast(new Intent(MainPlayer.ACTION_RECREATE_NOTIFICATION));
    }


    ////////////////////////////////////////////////////////////////////////////
    // Setup
    ////////////////////////////////////////////////////////////////////////////

    private void setupActions(@NonNull final View view) {
        compactSlots =
                NotificationConstants.getCompactSlotsFromPreferences(
                        getContext(), getSharedPreferences(), 5);
        notificationSlots = new NotificationSlot[5];
        for (int i = 0; i < 5; i++) {
            notificationSlots[i] = new NotificationSlot(i, view);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    // Saving
    ////////////////////////////////////////////////////////////////////////////

    private void saveChanges() {
        if (compactSlots != null && notificationSlots != null) {
            final SharedPreferences.Editor editor = getSharedPreferences().edit();

            for (int i = 0; i < 3; i++) {
                editor.putInt(getContext().getString(
                        NotificationConstants.SLOT_COMPACT_PREF_KEYS[i]),
                        (i < compactSlots.size() ? compactSlots.get(i) : -1));
            }

            for (int i = 0; i < 5; i++) {
                editor.putInt(getContext().getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                        notificationSlots[i].selectedAction);
            }

            editor.apply();
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    // Notification action
    ////////////////////////////////////////////////////////////////////////////

    private static final int[] SLOT_ITEMS = {
            R.id.notificationAction0,
            R.id.notificationAction1,
            R.id.notificationAction2,
            R.id.notificationAction3,
            R.id.notificationAction4,
    };

    private static final int[] SLOT_TITLES = {
            R.string.notification_action_0_title,
            R.string.notification_action_1_title,
            R.string.notification_action_2_title,
            R.string.notification_action_3_title,
            R.string.notification_action_4_title,
    };

    private class NotificationSlot {

        final int i;
        @NotificationConstants.Action int selectedAction;

        ImageView icon;
        TextView summary;

        NotificationSlot(final int actionIndex, final View parentView) {
            this.i = actionIndex;

            final View view = parentView.findViewById(SLOT_ITEMS[i]);
            setupSelectedAction(view);
            setupTitle(view);
            setupCheckbox(view);
        }

        void setupTitle(final View view) {
            ((TextView) view.findViewById(R.id.notificationActionTitle))
                    .setText(SLOT_TITLES[i]);
            view.findViewById(R.id.notificationActionClickableArea).setOnClickListener(
                    v -> openActionChooserDialog());
        }

        void setupCheckbox(final View view) {
            final CheckBox compactSlotCheckBox = view.findViewById(R.id.notificationActionCheckBox);
            compactSlotCheckBox.setChecked(compactSlots.contains(i));
            view.findViewById(R.id.notificationActionCheckBoxClickableArea).setOnClickListener(
                    v -> {
                        if (compactSlotCheckBox.isChecked()) {
                            compactSlots.remove((Integer) i);
                        } else if (compactSlots.size() < 3) {
                            compactSlots.add(i);
                        } else {
                            Toast.makeText(getContext(),
                                    R.string.notification_actions_at_most_three,
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        compactSlotCheckBox.toggle();
                    });
        }

        void setupSelectedAction(final View view) {
            icon = view.findViewById(R.id.notificationActionIcon);
            summary = view.findViewById(R.id.notificationActionSummary);
            selectedAction = getSharedPreferences().getInt(
                    getContext().getString(NotificationConstants.SLOT_PREF_KEYS[i]),
                    NotificationConstants.SLOT_DEFAULTS[i]);
            updateInfo();
        }

        void updateInfo() {
            if (NotificationConstants.ACTION_ICONS[selectedAction] == 0) {
                icon.setImageDrawable(null);
            } else {
                icon.setImageDrawable(AppCompatResources.getDrawable(getContext(),
                        NotificationConstants.ACTION_ICONS[selectedAction]));
            }

            summary.setText(NotificationConstants.getActionName(getContext(), selectedAction));
        }

        void openActionChooserDialog() {
            final LayoutInflater inflater = LayoutInflater.from(getContext());
            final LinearLayout rootLayout = (LinearLayout) inflater.inflate(
                    R.layout.single_choice_dialog_view, null, false);
            final RadioGroup radioGroup = rootLayout.findViewById(android.R.id.list);

            final AlertDialog alertDialog = new AlertDialog.Builder(getContext())
                    .setTitle(SLOT_TITLES[i])
                    .setView(radioGroup)
                    .setCancelable(true)
                    .create();

            final View.OnClickListener radioButtonsClickListener = v -> {
                selectedAction = NotificationConstants.SLOT_ALLOWED_ACTIONS[i][v.getId()];
                updateInfo();
                alertDialog.dismiss();
            };

            for (int id = 0; id < NotificationConstants.SLOT_ALLOWED_ACTIONS[i].length; ++id) {
                final int action = NotificationConstants.SLOT_ALLOWED_ACTIONS[i][id];
                final RadioButton radioButton
                        = (RadioButton) inflater.inflate(R.layout.list_radio_icon_item, null);

                // if present set action icon with correct color
                if (NotificationConstants.ACTION_ICONS[action] != 0) {
                    Drawable drawable = AppCompatResources.getDrawable(getContext(),
                            NotificationConstants.ACTION_ICONS[action]);
                    if (drawable != null) {
                        final int color = ThemeHelper.resolveColorFromAttr(getContext(),
                                android.R.attr.textColorPrimary);
                        drawable = DrawableCompat.wrap(drawable).mutate();
                        DrawableCompat.setTint(drawable, color);
                        TextViewCompat.setCompoundDrawablesRelativeWithIntrinsicBounds(radioButton,
                                null, null, drawable, null);
                    }
                }

                radioButton.setText(NotificationConstants.getActionName(getContext(), action));
                radioButton.setChecked(action == selectedAction);
                radioButton.setId(id);
                radioButton.setLayoutParams(new RadioGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                radioButton.setOnClickListener(radioButtonsClickListener);
                radioGroup.addView(radioButton);
            }
            alertDialog.show();

            if (DeviceUtils.isTv(getContext())) {
                FocusOverlayView.setupFocusObserver(alertDialog);
            }
        }
    }
}
