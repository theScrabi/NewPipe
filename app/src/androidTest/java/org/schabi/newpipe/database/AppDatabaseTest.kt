package org.schabi.newpipe.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.schabi.newpipe.extractor.stream.StreamType

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    companion object {
        private const val DEFAULT_SERVICE_ID = 0
        private const val DEFAULT_URL = "https://www.youtube.com/watch?v=cDphUib5iG4"
        private const val DEFAULT_TITLE = "Test Title"
        private val DEFAULT_TYPE = StreamType.VIDEO_STREAM
        private const val DEFAULT_DURATION = 480L
        private const val DEFAULT_UPLOADER_NAME = "Uploader Test"
        private const val DEFAULT_THUMBNAIL = "https://example.com/example.jpg"

        private const val DEFAULT_SECOND_SERVICE_ID = 0
        private const val DEFAULT_SECOND_URL = "https://www.youtube.com/watch?v=ncQU6iBn5Fc"
    }

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName, FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrateDatabaseFrom2to3() {
        val databaseInV2 = testHelper.createDatabase(AppDatabase.DATABASE_NAME, Migrations.DB_VER_2)

        databaseInV2.run {
            insert(
                "streams", SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    // put("uid", null)
                    put("service_id", DEFAULT_SERVICE_ID)
                    put("url", DEFAULT_URL)
                    put("title", DEFAULT_TITLE)
                    put("stream_type", DEFAULT_TYPE.name)
                    put("duration", DEFAULT_DURATION)
                    put("uploader", DEFAULT_UPLOADER_NAME)
                    put("thumbnail_url", DEFAULT_THUMBNAIL)
                }
            )
            insert(
                "streams", SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    // put("uid", null)
                    put("service_id", DEFAULT_SECOND_SERVICE_ID)
                    put("url", DEFAULT_SECOND_URL)
                    // put("title", null)
                    // put("stream_type", null)
                    // put("duration", null)
                    // put("uploader", null)
                    // put("thumbnail_url", null)
                }
            )
            insert(
                "streams", SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    // put("uid", null)
                    put("service_id", DEFAULT_SERVICE_ID)
                    // put("url", null)
                    // put("title", null)
                    // put("stream_type", null)
                    // put("duration", null)
                    // put("uploader", null)
                    // put("thumbnail_url", null)
                }
            )
            close()
        }

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME, Migrations.DB_VER_3,
            true, Migrations.MIGRATION_2_3
        )

        testHelper.runMigrationsAndValidate(
            AppDatabase.DATABASE_NAME, Migrations.DB_VER_4,
            true, Migrations.MIGRATION_3_4
        )

        val migratedDatabaseV3 = getMigratedDatabase()
        val listFromDB = migratedDatabaseV3.streamDAO().all.blockingFirst()

        // Only expect 2, the one with the null url will be ignored
        assertEquals(2, listFromDB.size)

        val streamFromMigratedDatabase = listFromDB[0]
        assertEquals(DEFAULT_SERVICE_ID, streamFromMigratedDatabase.serviceId)
        assertEquals(DEFAULT_URL, streamFromMigratedDatabase.url)
        assertEquals(DEFAULT_TITLE, streamFromMigratedDatabase.title)
        assertEquals(DEFAULT_TYPE, streamFromMigratedDatabase.streamType)
        assertEquals(DEFAULT_DURATION, streamFromMigratedDatabase.duration)
        assertEquals(DEFAULT_UPLOADER_NAME, streamFromMigratedDatabase.uploader)
        assertEquals(DEFAULT_THUMBNAIL, streamFromMigratedDatabase.thumbnailUrl)
        assertNull(streamFromMigratedDatabase.viewCount)
        assertNull(streamFromMigratedDatabase.textualUploadDate)
        assertNull(streamFromMigratedDatabase.uploadDate)
        assertNull(streamFromMigratedDatabase.isUploadDateApproximation)

        val secondStreamFromMigratedDatabase = listFromDB[1]
        assertEquals(DEFAULT_SECOND_SERVICE_ID, secondStreamFromMigratedDatabase.serviceId)
        assertEquals(DEFAULT_SECOND_URL, secondStreamFromMigratedDatabase.url)
        assertEquals("", secondStreamFromMigratedDatabase.title)
        // Should fallback to VIDEO_STREAM
        assertEquals(StreamType.VIDEO_STREAM, secondStreamFromMigratedDatabase.streamType)
        assertEquals(0, secondStreamFromMigratedDatabase.duration)
        assertEquals("", secondStreamFromMigratedDatabase.uploader)
        assertEquals("", secondStreamFromMigratedDatabase.thumbnailUrl)
        assertNull(secondStreamFromMigratedDatabase.viewCount)
        assertNull(secondStreamFromMigratedDatabase.textualUploadDate)
        assertNull(secondStreamFromMigratedDatabase.uploadDate)
        assertNull(secondStreamFromMigratedDatabase.isUploadDateApproximation)
    }

    private fun getMigratedDatabase(): AppDatabase {
        val database: AppDatabase = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java, AppDatabase.DATABASE_NAME
        )
            .build()
        testHelper.closeWhenFinished(database)
        return database
    }
}
