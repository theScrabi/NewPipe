package org.schabi.newpipe.database.subscription;

import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({NotificationMode.DISABLED, NotificationMode.ENABLED_DEFAULT})
@Retention(RetentionPolicy.SOURCE)
public @interface NotificationMode {

    int DISABLED = 0;
    int ENABLED_DEFAULT = 1;
    //other values reserved for the future
}
