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
    @Test
    fun addressDetailDefaultsToDetailedAndPersists() = runTest {
        val repository = repository("address-detail.preferences_pb")
        assertEquals(com.geostamp.camera.environment.AddressDetail.DETAILED, repository.settings.first().location.addressDetail)
        repository.update { it.copy(location = it.location.copy(addressDetail = com.geostamp.camera.environment.AddressDetail.SHORT)) }
        assertEquals(com.geostamp.camera.environment.AddressDetail.SHORT, repository.settings.first().location.addressDetail)
    }

    @Test
    fun legacyHouseNumberFlagDoesNotSuppressNewDetailedDefault() = runTest {
        val dataStore = dataStore(File(temporaryFolder.root, "legacy-address.preferences_pb"))
        dataStore.edit {
            it[booleanPreferencesKey("location.show_house_numbers")] = false
            it[stringPreferencesKey("location.address_detail")] = "UNKNOWN"
        }
        assertEquals(com.geostamp.camera.environment.AddressDetail.DETAILED,
            SettingsRepository(dataStore).settings.first().location.addressDetail)
    }

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
        assertTrue(settings.services.addressLookup)
        assertTrue(settings.services.mapTiles)
        assertFalse(settings.services.weather)
        assertEquals(AppSettings().services, settings.services)
    }

    @Test
    fun onlineServiceOptOutsPersistAcrossUnrelatedSettingsChanges() = runTest {
        val repository = repository("service-opt-outs.preferences_pb")
        repository.update { it.copy(services = it.services.copy(addressLookup = false, mapTiles = false)) }
        repository.update { it.copy(camera = it.camera.copy(gridEnabled = true)) }
        val settings = repository.settings.first()
        assertFalse(settings.services.addressLookup)
        assertFalse(settings.services.mapTiles)
        assertFalse(settings.services.weather)
    }

    @Test
    fun missingServiceKeysUseNewDefaultsWithoutOverridingStoredChoices() = runTest {
        val dataStore = dataStore(File(temporaryFolder.root, "partial-service-settings.preferences_pb"))
        dataStore.edit { it[booleanPreferencesKey("services.address")] = false }
        val settings = SettingsRepository(dataStore).settings.first()
        assertFalse(settings.services.addressLookup)
        assertTrue(settings.services.mapTiles)
        assertFalse(settings.services.weather)
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

    @Test
    fun newInstallDefaultsToNormalMapTypeAndGoogleMaps() = runTest {
        val settings = repository("map-defaults.preferences_pb").settings.first()
        assertEquals(com.geostamp.camera.maps.MapType.NORMAL, settings.services.mapType)
        assertEquals(MapLinkProvider.GOOGLE_MAPS, settings.services.mapLinkProvider)
    }

    @Test
    fun mapTypePersistsAcrossRestartsAndUnrelatedChanges() = runTest {
        val file = File(temporaryFolder.root, "map-type.preferences_pb")
        val dataStore = dataStore(file)
        SettingsRepository(dataStore).update { it.copy(services = it.services.copy(mapType = com.geostamp.camera.maps.MapType.TERRAIN)) }
        SettingsRepository(dataStore).update { it.copy(camera = it.camera.copy(gridEnabled = true)) }
        assertEquals(com.geostamp.camera.maps.MapType.TERRAIN, SettingsRepository(dataStore).settings.first().services.mapType)
    }

    @Test
    fun upgradeKeepsStoredOpenStreetMapChoice() = runTest {
        val dataStore = dataStore(File(temporaryFolder.root, "legacy-map-link.preferences_pb"))
        dataStore.edit {
            it[stringPreferencesKey("services.map_link")] = MapLinkProvider.OPEN_STREET_MAP.name
            it[booleanPreferencesKey("services.weather")] = true
            it[booleanPreferencesKey("privacy.exif_location")] = true
        }
        val settings = SettingsRepository(dataStore).settings.first()
        assertEquals(MapLinkProvider.OPEN_STREET_MAP, settings.services.mapLinkProvider)
        assertEquals(com.geostamp.camera.maps.MapType.NORMAL, settings.services.mapType)
        assertTrue(settings.services.weather)
        assertTrue(settings.stamp.writeExifLocation)
    }

    @Test
    fun unknownStoredMapTypeFallsBackToNormal() = runTest {
        val dataStore = dataStore(File(temporaryFolder.root, "bad-map-type.preferences_pb"))
        dataStore.edit { it[stringPreferencesKey("services.map_type")] = "STREET_VIEW" }
        assertEquals(com.geostamp.camera.maps.MapType.NORMAL, SettingsRepository(dataStore).settings.first().services.mapType)
    }

    @Test
    fun qrLocationTemplateDefaultsAndQrFieldPersist() = runTest {
        val repository = repository("qr-template.preferences_pb")
        repository.update { it.copy(stamp = it.stamp.copy(template = StampTemplate.QR_LOCATION)) }
        val fields = repository.settings.first().stamp.fields
        assertTrue(fields.qrCode)
        assertTrue(fields.map)
        assertTrue(fields.address)
        assertFalse(fields.logo)
        repository.update { it.copy(stamp = it.stamp.withFields(it.stamp.fields.copy(qrCode = false))) }
        assertFalse(repository.settings.first().stamp.fields.qrCode)
        assertFalse(repository.settings.first().stamp.fieldsByTemplate.getValue(StampTemplate.MAP_CARD).qrCode)
    }
}
