package com.geostamp.camera.privacy

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.geostamp.camera.BuildConfig
import com.geostamp.camera.R
import com.geostamp.camera.ui.components.SettingsSection
import com.geostamp.camera.ui.theme.Dimens

private data class PolicySection(@StringRes val title: Int, @StringRes val body: Int)

private val SECTIONS = listOf(
    PolicySection(R.string.privacy_data_title, R.string.privacy_data_body),
    PolicySection(R.string.privacy_ads_title, R.string.privacy_ads_body),
    PolicySection(R.string.privacy_tracking_title, R.string.privacy_tracking_body),
    PolicySection(R.string.privacy_camera_title, R.string.privacy_camera_body),
    PolicySection(R.string.privacy_location_title, R.string.privacy_location_body),
    PolicySection(R.string.privacy_microphone_title, R.string.privacy_microphone_body),
    PolicySection(R.string.privacy_storage_title, R.string.privacy_storage_body),
    PolicySection(R.string.privacy_exif_title, R.string.privacy_exif_body),
    PolicySection(R.string.privacy_maps_title, R.string.privacy_maps_body),
    PolicySection(R.string.privacy_weather_title, R.string.privacy_weather_body),
    PolicySection(R.string.privacy_geocoding_title, R.string.privacy_geocoding_body),
    PolicySection(R.string.privacy_sharing_title, R.string.privacy_sharing_body)
)

/** The full privacy policy, bundled with the app so it reads offline and in the device language. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.privacy_policy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    Column(Modifier.padding(horizontal = Dimens.SpaceL + Dimens.SpaceXs, vertical = Dimens.SpaceS)) {
                        Text(stringResource(R.string.privacy_policy_intro), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(Dimens.SpaceS))
                        Text(
                            stringResource(R.string.privacy_policy_updated),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(SECTIONS) { section -> PolicyCard(stringResource(section.title), stringResource(section.body)) }
                item { PolicyCard(stringResource(R.string.privacy_credits_title), stringResource(R.string.privacy_credits_body)) }
                item {
                    PolicyCard(
                        stringResource(R.string.privacy_contact_title),
                        stringResource(R.string.privacy_contact_body, stringResource(R.string.app_name), BuildConfig.VERSION_NAME, BuildConfig.APPLICATION_ID)
                    )
                }
                item { Spacer(Modifier.navigationBarsPadding().height(Dimens.SpaceL)) }
            }
        }
    }
}

@Composable
private fun PolicyCard(title: String, body: String) {
    SettingsSection(title) {
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceM)
        )
    }
}
