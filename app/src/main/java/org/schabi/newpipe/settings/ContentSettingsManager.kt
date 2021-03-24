package org.schabi.newpipe.settings

import android.content.SharedPreferences
import org.schabi.newpipe.util.ZipHelper
import java.io.BufferedOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ContentSettingsManager(private val fileLocator: NewPipeFileLocator) {

    /**
     * Exports given [SharedPreferences] to the file in given outputPath.
     * It also creates the file.
     */
    @Throws(Exception::class)
    fun exportDatabase(preferences: SharedPreferences, outputPath: String) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputPath)))
            .use { outZip ->
                ZipHelper.addFileToZip(outZip, fileLocator.db.path, "newpipe.db")

                try {
                    ObjectOutputStream(FileOutputStream(fileLocator.settings)).use { output ->
                        output.writeObject(preferences.all)
                        output.flush()
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

                ZipHelper.addFileToZip(outZip, fileLocator.settings.path, "newpipe.settings")
            }
    }

    fun deleteSettingsFile() {
        fileLocator.settings.delete()
    }

    /**
     * Tries to create database directory if it does not exist.
     *
     * @return Whether the directory exists afterwards.
     */
    fun ensureDbDirectoryExists(): Boolean {
        return fileLocator.dbDir.exists() || fileLocator.dbDir.mkdir()
    }

    fun isValidBackupFile(filePath: String): Boolean {
        try {
            val zipFile = ZipFile(filePath)
            if (zipFile.size() != 2)
                return false

            val entries = zipFile.entries()
            var containsDbFile = false
            var containsSettingsFile = false

            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name == "newpipe.db")
                    containsDbFile = true
                else if (entry.name == "newpipe.settings")
                    containsSettingsFile = true
            }

            return containsDbFile && containsSettingsFile
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
        return false
    }

    fun extractDb(filePath: String): Boolean {
        val success = ZipHelper.extractFileFromZip(filePath, fileLocator.db.path, "newpipe.db")
        if (success) {
            fileLocator.dbJournal.delete()
            fileLocator.dbWal.delete()
            fileLocator.dbShm.delete()
        }

        return success
    }

    fun extractSettings(filePath: String): Boolean {
        return ZipHelper
            .extractFileFromZip(filePath, fileLocator.settings.path, "newpipe.settings")
    }

    fun loadSharedPreferences(preferences: SharedPreferences) {
        try {
            val preferenceEditor = preferences.edit()

            ObjectInputStream(FileInputStream(fileLocator.settings)).use { input ->
                preferenceEditor.clear()
                @Suppress("UNCHECKED_CAST")
                val entries = input.readObject() as Map<String, *>
                for ((key, value) in entries) {
                    when (value) {
                        is Boolean -> {
                            preferenceEditor.putBoolean(key, value)
                        }
                        is Float -> {
                            preferenceEditor.putFloat(key, value)
                        }
                        is Int -> {
                            preferenceEditor.putInt(key, value)
                        }
                        is Long -> {
                            preferenceEditor.putLong(key, value)
                        }
                        is String -> {
                            preferenceEditor.putString(key, value)
                        }
                    }
                }
                preferenceEditor.commit()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }
}
