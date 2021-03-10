package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.services.peertube.PeertubeInstance;

import java.util.ArrayList;
import java.util.List;

public final class PeertubeHelper {
    private PeertubeHelper() { }

    public static List<PeertubeInstance> getInstanceList(final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        final String savedInstanceListKey = context.getString(R.string.peertube_instance_list_key);
        final String savedJson = sharedPreferences.getString(savedInstanceListKey, null);
        if (null == savedJson) {
            return List.of(getCurrentInstance());
        }

        try {
            final JsonArray array = JsonParser.object().from(savedJson).getArray("instances");
            final List<PeertubeInstance> result = new ArrayList<>();
            for (final Object o : array) {
                if (o instanceof JsonObject) {
                    final JsonObject instance = (JsonObject) o;
                    final String name = instance.getString("name");
                    final String url = instance.getString("url");
                    result.add(new PeertubeInstance(url, name));
                }
            }
            return result;
        } catch (final JsonParserException e) {
            return List.of(getCurrentInstance());
        }
    }

    public static PeertubeInstance selectInstance(final PeertubeInstance instance,
                                                  final Context context) {
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        final String selectedInstanceKey
                = context.getString(R.string.peertube_selected_instance_key);
        final JsonStringWriter jsonWriter = JsonWriter.string().object();
        jsonWriter.value("name", instance.getName());
        jsonWriter.value("url", instance.getUrl());
        final String jsonToSave = jsonWriter.end().done();
        sharedPreferences.edit().putString(selectedInstanceKey, jsonToSave).apply();
        ServiceList.PeerTube.setInstance(instance);
        return instance;
    }

    public static PeertubeInstance getCurrentInstance() {
        return ServiceList.PeerTube.getInstance();
    }
}
