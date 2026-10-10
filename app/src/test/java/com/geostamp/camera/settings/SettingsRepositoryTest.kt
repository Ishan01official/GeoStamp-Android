package com.geostamp.camera.settings

import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.geostamp.camera.stamps.StampPosition
import com.geostamp.camera.stamps.StampTemplate
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun firstInstallDefaultsToProfessionalStampAtBottom() = runTest {
        val repository = repository("first-install.preferences_pb")

        val settings = repository.settings.first()

        assertEquals(StampTemplate.PROFESSIONAL, settings.stamp.template)
        assertEquals(StampPosition.BOTTOM, settings.stamp.position)
        with(settings.stamp.fields) {
            assertTrue(dateTime)
            assertTrue(address)
            assertTrue(coordinates)
            assertTrue(accuracy)
            assertTrue(heading)
            assertTrue(altitude)
            assertTrue(speed)
            assertTrue(weather)
            assertTrue(map)
        }
    }

    @Test
    fun savedTemplateSelectionIsPreservedAcrossReads() = runTest {
        val repository = repository("saved-template.preferences_pb")

        repository.update { it.copy(stamp = it.stamp.copy(template = StampTemplate.MINIMAL)) }

        assertEquals(StampTemplate.MINIMAL, repository.settings.first().stamp.template)
    }

    @Test
    fun existingStoredTemplateIsNotMigratedToProfessional() = runTest {
        val file = File(temporaryFolder.root, "existing-template.preferences_pb")
        val dataStore = dataStore(file)
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("stamp.template")] = StampTemplate.CLASSIC.name
        }

        val settings = SettingsRepository(dataStore).settings.first()

        assertEquals(StampTemplate.CLASSIC, settings.stamp.template)
    }

    private fun repository(fileName: String): SettingsRepository =
        SettingsRepository(dataStore(File(temporaryFolder.root, fileName)))

    private fun dataStore(file: File) =
        PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { file }
        )
}
