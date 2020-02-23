package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.exceptions.ExtractionException;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;

import java.util.concurrent.TimeUnit;

import static org.schabi.newpipe.extractor.ServiceList.SoundCloud;

public class ServiceHelper {
    private static final StreamingService DEFAULT_FALLBACK_SERVICE = ServiceList.YouTube;

    @DrawableRes
    public static int getIcon(int serviceId) {
        switch (serviceId) {
            case 0:
                return R.drawable.place_holder_youtube;
            case 1:
                return R.drawable.place_holder_cloud;
            case 2:
                return R.drawable.place_holder_gadse;
            case 3:
                return R.drawable.place_holder_peertube;
            default:
                return R.drawable.place_holder_circle;
        }
    }

    public static String getTranslatedFilterString(String filter, Context c) {
        switch (filter) {
            case "all": return c.getString(R.string.all);
            case "videos": return c.getString(R.string.videos_string);
            case "channels": return c.getString(R.string.channels);
            case "playlists": return c.getString(R.string.playlists);
            case "tracks": return c.getString(R.string.tracks);
            case "users": return c.getString(R.string.users);
            case "conferences" : return c.getString(R.string.conferences);
            case "events" : return c.getString(R.string.events);
            default: return filter;
        }
    }

    /**
     * Get a resource string with instructions for importing subscriptions for each service.
     *
     * @return the string resource containing the instructions or -1 if the service don't support it
     */
    @StringRes
    public static int getImportInstructions(int serviceId) {
        switch (serviceId) {
            case 0:
                return R.string.import_youtube_instructions;
            case 1:
                return R.string.import_soundcloud_instructions;
            default:
                return -1;
        }
    }

    /**
     * For services that support importing from a channel url, return a hint that will
     * be used in the EditText that the user will type in his channel url.
     *
     * @return the hint's string resource or -1 if the service don't support it
     */
    @StringRes
    public static int getImportInstructionsHint(int serviceId) {
        switch (serviceId) {
            case 1:
                return R.string.import_soundcloud_instructions_hint;
            default:
                return -1;
        }
    }

    public static int getSelectedServiceId(Context context) {

        final String serviceName = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(context.getString(R.string.current_service_key), context.getString(R.string.default_service_value));

        int serviceId;
        try {
            serviceId = NewPipe.getService(serviceName).getServiceId();
        } catch (ExtractionException e) {
            serviceId = DEFAULT_FALLBACK_SERVICE.getServiceId();
        }

        return serviceId;
    }

    public static void setSelectedServiceId(Context context, int serviceId) {
        String serviceName;
        try {
            serviceName = NewPipe.getService(serviceId).getServiceInfo().getName();
        } catch (ExtractionException e) {
            serviceName = DEFAULT_FALLBACK_SERVICE.getServiceInfo().getName();
        }

        setSelectedServicePreferences(context, serviceName);
    }

    public static void setSelectedServiceId(Context context, String serviceName) {
        int serviceId = NewPipe.getIdOfService(serviceName);
        if (serviceId == -1) serviceName = DEFAULT_FALLBACK_SERVICE.getServiceInfo().getName();

        setSelectedServicePreferences(context, serviceName);
    }

    private static void setSelectedServicePreferences(Context context, String serviceName) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().
                putString(context.getString(R.string.current_service_key), serviceName).apply();
    }

    public static long getCacheExpirationMillis(final int serviceId) {
        if (serviceId == SoundCloud.getServiceId()) {
            return TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES);
        } else {
            return TimeUnit.MILLISECONDS.convert(1, TimeUnit.HOURS);
        }
    }

    public static boolean isBeta(final StreamingService s) {
        switch (s.getServiceInfo().getName()) {
            case "YouTube": return false;
            default: return true;
        }
    }

    public static void initService(Context context, int serviceId) {
        if (serviceId == ServiceList.PeerTube.getServiceId()) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
            String json = sharedPreferences.getString(context.getString(R.string.peertube_selected_instance_key), null);
            if (null == json) {
                return;
            }

            JsonObject jsonObject = null;
            try {
                jsonObject = JsonParser.object().from(json);
            } catch (JsonParserException e) {
                return;
            }
            String name = jsonObject.getString("name");
            String url = jsonObject.getString("url");
            PeertubeInstance instance = new PeertubeInstance(url, name);
            ServiceList.PeerTube.setInstance(instance);
        }
    }

    public static void initServices(Context context) {
        for (StreamingService s : ServiceList.all()) {
            initService(context, s.getServiceId());
        }
    }
}
