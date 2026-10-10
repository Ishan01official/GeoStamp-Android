package com.geostamp.camera.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun firstInstallDefaultsToMapCardStampAtBottom() = runTest {
        val repository = repository("first-install.preferences_pb")

        val settings = repository.settings.first()

        assertEquals(StampTemplate.MAP_CARD, settings.stamp.template)
        assertEquals(StampPosition.BOTTOM, settings.stamp.position)
        with(settings.stamp.fields) {
            assertTrue(dateTime)
            assertTrue(map)
            assertTrue(address)
            assertTrue(coordinates)
        }
        // Map thumbnails need network consent; the stamp falls back to a coordinate panel until given.
        assertEquals(false, settings.services.mapTiles)
    }

    @Test
    fun upgradeKeepsSavedProfessionalTemplate() = runTest {
        val file = File(temporaryFolder.root, "upgrade-professional.preferences_pb")
        val dataStore = dataStore(file)
        dataStore.edit { it[stringPreferencesKey("stamp.template")] = StampTemplate.PROFESSIONAL.name }

        assertEquals(StampTemplate.PROFESSIONAL, SettingsRepository(dataStore).settings.first().stamp.template)
    }

    @Test
    fun upgradeKeepsSavedPerTemplateFieldChoices() = runTest {
        val file = File(temporaryFolder.root, "upgrade-fields.preferences_pb")
        val dataStore = dataStore(file)
        dataStore.edit {
            it[stringPreferencesKey("stamp.template")] = StampTemplate.MAP_CARD.name
            it[booleanPreferencesKey("stamp.fields.MAP_CARD.map")] = false
        }

        val settings = SettingsRepository(dataStore).settings.first()

        assertEquals(StampTemplate.MAP_CARD, settings.stamp.template)
        assertFalse(settings.stamp.fields.map)
    }

    @Test
    fun unrelatedSettingsChangeDoesNotResetSavedTemplate() = runTest {
        val repository = repository("unrelated-change.preferences_pb")
        repository.update { it.copy(stamp = it.stamp.copy(template = StampTemplate.CLASSIC)) }

        repository.update { it.copy(camera = it.camera.copy(gridEnabled = true)) }

        assertEquals(StampTemplate.CLASSIC, repository.settings.first().stamp.template)
    }

    @Test
    fun savedTemplateSelectionIsPreservedAcrossReads() = runTest {
        val repository = repository("saved-template.preferences_pb")

        repository.update { it.copy(stamp = it.stamp.copy(template = StampTemplate.MINIMAL)) }

        assertEquals(StampTemplate.MINIMAL, repository.settings.first().stamp.template)
    }

    @Test
    fun simpleCameraModeDefaultsOffAndPersistsWhenEnabled() = runTest {
        val repository = repository("simple-mode.preferences_pb")

        assertEquals(false, repository.settings.first().camera.simpleMode)

        repository.update { it.copy(camera = it.camera.copy(simpleMode = true)) }

        assertEquals(true, repository.settings.first().camera.simpleMode)
    }

    @Test
    fun existingStoredClassicTemplateIsNotMigratedToMapCard() = runTest {
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
