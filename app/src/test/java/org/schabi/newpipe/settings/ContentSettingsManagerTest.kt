package org.schabi.newpipe.settings

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.io.ObjectInputStream
import java.nio.file.Files
import java.util.zip.ZipFile

@RunWith(MockitoJUnitRunner::class)
class ContentSettingsManagerTest {

    companion object {
        private val classloader = ContentSettingsManager::class.java.classLoader!!
    }

    private lateinit var fileLocator: NewPipeFileLocator

    @Before
    fun setupFileLocator() {
        fileLocator = Mockito.mock(NewPipeFileLocator::class.java, withSettings().stubOnly())
    }

    @Test
    fun `The settings must be exported successfully in the correct format`() {
        val db = File(classloader.getResource("settings/newpipe.db")!!.file)
        val newpipeSettings = File.createTempFile("newpipe_", "")
        `when`(fileLocator.db).thenReturn(db)
        `when`(fileLocator.settings).thenReturn(newpipeSettings)

        val expectedPreferences = mapOf("such pref" to "much wow")
        val sharedPreferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        `when`(sharedPreferences.all).thenReturn(expectedPreferences)

        val output = File.createTempFile("newpipe_", "")
        ContentSettingsManager(fileLocator).exportDatabase(sharedPreferences, output.absolutePath)

        val zipFile = ZipFile(output)
        val entries = zipFile.entries().toList()
        assertEquals(2, entries.size)

        zipFile.getInputStream(entries.first { it.name == "newpipe.db" }).use { actual ->
            db.inputStream().use { expected ->
                assertEquals(expected.reader().readText(), actual.reader().readText())
            }
        }

        zipFile.getInputStream(entries.first { it.name == "newpipe.settings" }).use { actual ->
            val actualPreferences = ObjectInputStream(actual).readObject()
            assertEquals(expectedPreferences, actualPreferences)
        }
    }

    @Test
    fun `Settings file must be deleted`() {
        val settings = File.createTempFile("newpipe_", "")
        `when`(fileLocator.settings).thenReturn(settings)

        ContentSettingsManager(fileLocator).deleteSettingsFile()

        assertFalse(settings.exists())
    }

    @Test
    fun `Deleting settings file must do nothing if none exist`() {
        val settings = File("non_existent")
        `when`(fileLocator.settings).thenReturn(settings)

        ContentSettingsManager(fileLocator).deleteSettingsFile()

        assertFalse(settings.exists())
    }

    @Test
    fun `Ensuring db directory existence must work`() {
        val dir = Files.createTempDirectory("newpipe_").toFile()
        Assume.assumeTrue(dir.delete())
        `when`(fileLocator.dbDir).thenReturn(dir)

        ContentSettingsManager(fileLocator).ensureDbDirectoryExists()
        assertTrue(dir.exists())
    }

    @Test
    fun `Ensuring db directory existence must work when the directory already exists`() {
        val dir = Files.createTempDirectory("newpipe_").toFile()
        `when`(fileLocator.dbDir).thenReturn(dir)

        ContentSettingsManager(fileLocator).ensureDbDirectoryExists()
        assertTrue(dir.exists())
    }

    @Test
    fun `The database must be extracted from the zip file`() {
        val db = File.createTempFile("newpipe_", "")
        val dbJournal = File.createTempFile("newpipe_", "")
        val dbWal = File.createTempFile("newpipe_", "")
        val dbShm = File.createTempFile("newpipe_", "")
        `when`(fileLocator.db).thenReturn(db)
        `when`(fileLocator.dbJournal).thenReturn(dbJournal)
        `when`(fileLocator.dbShm).thenReturn(dbShm)
        `when`(fileLocator.dbWal).thenReturn(dbWal)

        val zip = File(classloader.getResource("settings/newpipe.zip")?.file!!)
        val success = ContentSettingsManager(fileLocator).extractDb(zip.path)

        assertTrue(success)
        assertFalse(dbJournal.exists())
        assertFalse(dbWal.exists())
        assertFalse(dbShm.exists())
        assertTrue("database file size is zero", Files.size(db.toPath()) > 0)
    }

    @Test
    fun `Extracting the database from an empty zip must not work`() {
        val db = File.createTempFile("newpipe_", "")
        val dbJournal = File.createTempFile("newpipe_", "")
        val dbWal = File.createTempFile("newpipe_", "")
        val dbShm = File.createTempFile("newpipe_", "")
        `when`(fileLocator.db).thenReturn(db)

        val emptyZip = File(classloader.getResource("settings/empty.zip")?.file!!)
        val success = ContentSettingsManager(fileLocator).extractDb(emptyZip.path)

        assertFalse(success)
        assertTrue(dbJournal.exists())
        assertTrue(dbWal.exists())
        assertTrue(dbShm.exists())
        assertEquals(0, Files.size(db.toPath()))
    }

    @Test
    fun `Contains setting must return true if a settings file exists in the zip`() {
        val settings = File.createTempFile("newpipe_", "")
        `when`(fileLocator.settings).thenReturn(settings)

        val zip = File(classloader.getResource("settings/newpipe.zip")?.file!!)
        val contains = ContentSettingsManager(fileLocator).extractSettings(zip.path)

        assertTrue(contains)
    }

    @Test
    fun `Contains setting must return false if a no settings file exists in the zip`() {
        val settings = File.createTempFile("newpipe_", "")
        `when`(fileLocator.settings).thenReturn(settings)

        val emptyZip = File(classloader.getResource("settings/empty.zip")?.file!!)
        val contains = ContentSettingsManager(fileLocator).extractSettings(emptyZip.path)

        assertFalse(contains)
    }

    @Test
    fun `IsValid must return false if the zip has not the two necessary files`() {
        val newpipe = File(classloader.getResource("settings/newpipe.zip")?.file!!)
        val empty = File(classloader.getResource("settings/empty.zip")?.file!!)

        val newpipeValid = ContentSettingsManager(fileLocator).isValidBackupFile(newpipe.path)
        val emptyValid = ContentSettingsManager(fileLocator).isValidBackupFile(empty.path)

        assertTrue(newpipeValid)
        assertFalse(emptyValid)
    }

    @Test
    fun `Preferences must be set from the settings file`() {
        val settings = File(classloader.getResource("settings/newpipe.settings")!!.path)
        `when`(fileLocator.settings).thenReturn(settings)

        val preferences = Mockito.mock(SharedPreferences::class.java, withSettings().stubOnly())
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        `when`(preferences.edit()).thenReturn(editor)

        ContentSettingsManager(fileLocator).loadSharedPreferences(preferences)

        verify(editor, atLeastOnce()).putBoolean(anyString(), anyBoolean())
        verify(editor, atLeastOnce()).putString(anyString(), anyString())
        verify(editor, atLeastOnce()).putInt(anyString(), anyInt())
    }
}
