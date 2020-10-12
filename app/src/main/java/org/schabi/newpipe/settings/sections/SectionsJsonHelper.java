package org.schabi.newpipe.settings.sections;

import androidx.annotation.Nullable;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import com.grack.nanojson.JsonStringWriter;
import com.grack.nanojson.JsonWriter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SectionsJsonHelper {
    private static final String JSON_SECTIONS_ARRAY_KEY = "tabs";

    private static final List<Section> FALLBACK_INITIAL_SECTION_LIST = Collections.unmodifiableList(
            Arrays.asList(
                    Section.Type.DEFAULT_KIOSK.getSection(),
                    Section.Type.DEFAULT_KIOSK.getSection(),
                    Section.Type.SUBSCRIPTIONS.getSection(),
                    Section.Type.FEED.getSection(),
                    Section.Type.BOOKMARKS.getSection(),
                    Section.Type.DOWNLOADS.getSection(),
                    Section.Type.HISTORY.getSection()));

    private SectionsJsonHelper() {  }
    /**
     * Try to reads the passed JSON and returns the list of sections if no error were encountered.
     * <p>
     * If the JSON is null or empty, or the list of sections that it represents is empty, the
     * {@link #getDefaultSections fallback list} will be returned.
     * <p>
     * Sections with invalid ids (i.e. not in the {@link Section.Type} enum) will be ignored.
     *
     * @param sectionsJson a JSON string got from {@link #getJsonToSave(List)}.
     * @return a list of {@link Section sections}.
     * @throws SectionsJsonHelper.InvalidJsonException if the JSON string is not valid
     */
    public static List<Section> getSectionsFromJson(@Nullable final String sectionsJson)
            throws InvalidJsonException {
        if (sectionsJson == null || sectionsJson.isEmpty()) {
            return getDefaultSections();
        }

        final List<Section> returnSections = new ArrayList<>();

        final JsonObject outerJsonObject;
        try {
            outerJsonObject = JsonParser.object().from(sectionsJson);

            if (!outerJsonObject.has(JSON_SECTIONS_ARRAY_KEY)) {
                throw new InvalidJsonException(
                        "JSON doesn't contain \"" + JSON_SECTIONS_ARRAY_KEY + "\" array");
            }

            final JsonArray tabsArray = outerJsonObject.getArray(JSON_SECTIONS_ARRAY_KEY);

            for (final Object o : tabsArray) {
                if (!(o instanceof JsonObject)) {
                    continue;
                }

                final Section section = Section.from((JsonObject) o);

                if (section != null) {
                    returnSections.add(section);
                }
            }
        } catch (final JsonParserException e) {
            throw new InvalidJsonException(e);
        }

        if (returnSections.isEmpty()) {
            return getDefaultSections();
        }

        return returnSections;
    }

    /**
     * Get a JSON representation from a list of tabs.
     *
     * @param sectionList a list of {@link Section section}.
     * @return a JSON string representing the list of tabs
     */
    public static String getJsonToSave(@Nullable final List<Section> sectionList) {
        final JsonStringWriter jsonWriter = JsonWriter.string();
        jsonWriter.object();

        jsonWriter.array(JSON_SECTIONS_ARRAY_KEY);
        if (sectionList != null) {
            for (final Section tab : sectionList) {
                tab.writeJsonOn(jsonWriter);
            }
        }
        jsonWriter.end();

        jsonWriter.end();
        return jsonWriter.done();
    }

    public static List<Section> getDefaultSections() {
        return FALLBACK_INITIAL_SECTION_LIST;
    }

    public static final class InvalidJsonException extends Exception {
        private InvalidJsonException() {
            super();
        }

        private InvalidJsonException(final String message) {
            super(message);
        }

        private InvalidJsonException(final Throwable cause) {
            super(cause);
        }
    }
}
