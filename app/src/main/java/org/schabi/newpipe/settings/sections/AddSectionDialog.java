package org.schabi.newpipe.settings.sections;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.ThemeHelper;

public final class AddSectionDialog {
    private final AlertDialog dialog;

    public AddSectionDialog(@NonNull final Context context,
                            @NonNull final ChooseSectionListItem[] items,
                            @NonNull final DialogInterface.OnClickListener actions) {

        dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.section_choose))
                .setAdapter(new DialogListAdapter(context, items), actions)
                .create();
    }

    public void show() {
        dialog.show();
    }

    public static final class ChooseSectionListItem {
        public final int sectionId;
        final String itemName;
        @DrawableRes
        final int itemIcon;

        public ChooseSectionListItem(final Context context, final Section section) {
            this(section.getSectionId(), section.getSectionName(context),
                    section.getSectionIconRes(context));
        }

        public ChooseSectionListItem(final int sectionId, final String itemName,
                                     @DrawableRes final int itemIcon) {
            this.sectionId = sectionId;
            this.itemName = itemName;
            this.itemIcon = itemIcon;
        }
    }

    private static final class DialogListAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final ChooseSectionListItem[] items;

        @DrawableRes
        private final int fallbackIcon;

        private DialogListAdapter(final Context context, final ChooseSectionListItem[] items) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.fallbackIcon = ThemeHelper.resolveResourceIdFromAttr(context, R.attr.ic_kiosk_hot);
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public ChooseSectionListItem getItem(final int position) {
            return items[position];
        }

        @Override
        public long getItemId(final int position) {
            return getItem(position).sectionId;
        }

        @Override
        public View getView(final int position, final View view, final ViewGroup parent) {
            View convertView = view;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_choose_tabs_dialog, parent, false);
            }

            final ChooseSectionListItem item = getItem(position);
            final AppCompatImageView sectionIconView = convertView.findViewById(R.id.tabIcon);
            final TextView sectionNameView = convertView.findViewById(R.id.tabName);

            sectionIconView.setImageResource(item.itemIcon > 0 ? item.itemIcon : fallbackIcon);
            sectionNameView.setText(item.itemName);

            return convertView;
        }
    }
}
