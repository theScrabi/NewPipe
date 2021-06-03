package org.schabi.newpipe.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import org.schabi.newpipe.R;

import java.util.regex.Pattern;

public final class FilenameUtils {
    private static final String CHARSET_MOST_SPECIAL = "[\\n\\r|?*<\":\\\\>/']+";
    private static final String CHARSET_ONLY_LETTERS_AND_DIGITS = "[^\\w\\d]+";
    private static final String CHARSET_VALID_IN_FILE_NAMES = "[-_. A-Za-z0-9]+";

    private FilenameUtils() { }

    /**
     * #143 #44 #42 #22: make sure that the filename does not contain illegal chars.
     *
     * @param context the context to retrieve strings and preferences from
     * @param title   the title to create a filename from
     * @return the filename
     */
    public static String createFilename(final Context context, final String title) {
        final SharedPreferences sharedPreferences = PreferenceManager
                .getDefaultSharedPreferences(context);

        final String charsetLd = context.getString(R.string.charset_letters_and_digits_value);
        final String charsetMs = context.getString(R.string.charset_most_special_value);
        final String defaultCharset = context.getString(R.string.default_file_charset_value);

        final String replacementChar = sharedPreferences.getString(
                context.getString(R.string.settings_file_replacement_character_key), "_");
        String selectedCharset = sharedPreferences.getString(
                context.getString(R.string.settings_file_charset_key), null);

        final String charset;

        if (selectedCharset == null || selectedCharset.isEmpty()) {
            selectedCharset = defaultCharset;
        }

        if (selectedCharset.equals(charsetLd)) {
            charset = CHARSET_ONLY_LETTERS_AND_DIGITS;
        } else if (selectedCharset.equals(charsetMs)) {
            charset = CHARSET_MOST_SPECIAL;
        } else {
            charset = selectedCharset; // Is the user using a custom charset?
        }

        final Pattern pattern = Pattern.compile(charset);

        return createFilename(title, pattern, replacementChar);
    }

    /**
     * Create a valid filename.
     *
     * @param title             the title to create a filename from
     * @param invalidCharacters patter matching invalid characters
     * @param replacementChar   the replacement
     * @return the filename
     */
    private static String createFilename(final String title, final Pattern invalidCharacters,
                                         final String replacementChar) {
        return title.replaceAll(invalidCharacters.pattern(), replacementChar);
    }

    /**
     * Get a file name and extension and check the file name is a valid file name or not.
     *
     * @param fileName  name of file to check like "foo.zip"
     * @param extension extension of file like "zip"
     * @return is filename valid or not
     */
    public static boolean isValidFileName(final String fileName, final String extension) {
        return fileName.matches(CHARSET_VALID_IN_FILE_NAMES + "\\." + extension);
    }
}
