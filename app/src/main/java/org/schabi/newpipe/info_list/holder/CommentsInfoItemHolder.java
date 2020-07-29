package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.comments.CommentsInfoItem;
import org.schabi.newpipe.info_list.ItemHandler;
import org.schabi.newpipe.local.history.HistoryRecordManager;

/*
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * ChannelInfoItemHolder .java is part of NewPipe.
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

public class CommentsInfoItemHolder extends CommentsMiniInfoItemHolder {
    public final TextView itemTitleView;

    public CommentsInfoItemHolder(final ItemHandler itemHandler, final ViewGroup parent) {
        super(itemHandler, R.layout.list_comments_item, parent);

        itemTitleView = itemView.findViewById(R.id.itemTitleView);
    }

    @Override
    public void updateFromObject(final Object object,
                                 final HistoryRecordManager historyRecordManager) {
        super.updateFromObject(object, historyRecordManager);

        if (!(object instanceof CommentsInfoItem)) {
            return;
        }

        itemTitleView.setText(((CommentsInfoItem) object).getUploaderName());
    }
}
