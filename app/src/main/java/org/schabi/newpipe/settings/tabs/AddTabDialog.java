package org.schabi.newpipe.settings.tabs;

import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;

import org.schabi.newpipe.R;

public final class AddTabDialog {
    private final AlertDialog dialog;

    AddTabDialog(@NonNull final Context context, @NonNull final ChooseTabListItem[] items,
                 @NonNull final DialogInterface.OnClickListener actions) {

        dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.tab_choose))
                .setAdapter(new DialogListAdapter(context, items), actions)
                .create();
    }

    public void show() {
        dialog.show();
    }

    static final class ChooseTabListItem {
        final int tabId;
        final String itemName;
        @DrawableRes
        final int itemIcon;

        ChooseTabListItem(final Context context, final Tab tab) {
            this(tab.getTabId(), tab.getTabName(context), tab.getTabIconRes(context));
        }

        ChooseTabListItem(final int tabId, final String itemName,
                          @DrawableRes final int itemIcon) {
            this.tabId = tabId;
            this.itemName = itemName;
            this.itemIcon = itemIcon;
        }
    }

    private static final class DialogListAdapter extends BaseAdapter {
        private final LayoutInflater inflater;
        private final ChooseTabListItem[] items;

        @DrawableRes
        private final int fallbackIcon;

        private DialogListAdapter(final Context context, final ChooseTabListItem[] items) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.fallbackIcon = R.drawable.ic_whatshot;
        }

        @Override
        public int getCount() {
            return items.length;
        }

        @Override
        public ChooseTabListItem getItem(final int position) {
            return items[position];
        }

        @Override
        public long getItemId(final int position) {
            return getItem(position).tabId;
        }

        @Override
        public View getView(final int position, final View view, final ViewGroup parent) {
            View convertView = view;
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.list_choose_tabs_dialog, parent, false);
            }

            final ChooseTabListItem item = getItem(position);
            final AppCompatImageView tabIconView = convertView.findViewById(R.id.tabIcon);
            final TextView tabNameView = convertView.findViewById(R.id.tabName);

            tabIconView.setImageResource(item.itemIcon > 0 ? item.itemIcon : fallbackIcon);
            tabNameView.setText(item.itemName);

            return convertView;
        }
    }
}
