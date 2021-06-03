package org.schabi.newpipe.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FilenameUtilsTest {
    @Test
    public void testIsValidFileNameOnlyLowerCaseName() {
        assertTrue(FilenameUtils.isValidFileName("foo.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameLowerUpperCaseName() {
        assertTrue(FilenameUtils.isValidFileName("fooFSDFDWER.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameWithNumberName() {
        assertTrue(FilenameUtils.isValidFileName("foo77.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameWithDashName() {
        assertTrue(FilenameUtils.isValidFileName("foo-77.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameWithUnderlineName() {
        assertTrue(FilenameUtils.isValidFileName("foo_77.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameWithMultiDotsName() {
        assertTrue(FilenameUtils.isValidFileName("foo.export.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameAbsolutePath() {
        assertFalse(FilenameUtils.isValidFileName("/dev/null/file.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameRelativePath() {
        assertFalse(FilenameUtils.isValidFileName("../file.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameSpecialChars() {
        assertFalse(FilenameUtils.isValidFileName("file*.zip", "zip"));
    }

    @Test
    public void testIsValidFileNameBadExtension() {
        assertFalse(FilenameUtils.isValidFileName("file.rar", "zip"));
    }

}
