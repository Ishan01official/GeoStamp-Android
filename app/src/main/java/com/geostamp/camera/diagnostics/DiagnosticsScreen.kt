package com.geostamp.camera.diagnostics

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.geostamp.camera.R
import com.geostamp.camera.appContainer
import com.geostamp.camera.ui.components.InfoRow
import com.geostamp.camera.ui.components.SettingsSection
import com.geostamp.camera.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sections = remember {
        val container = context.appContainer
        DiagnosticReport.build(
            device = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})",
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            cameras = container.cameraCapabilityRepository.capabilities(),
            dualResults = container.cameraCapabilityRepository.dualResults(),
            sensors = container.compassRepository.capabilities
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, DiagnosticReport.asText(sections))
                        context.startActivity(Intent.createChooser(send, null))
                    }) {
                        Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.diagnostics_share))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.diagnostics_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.SpaceL, vertical = Dimens.SpaceS)
                )
            }
            items(sections) { section ->
                SettingsSection(section.title) {
                    section.rows.forEach { (label, value) -> InfoRow(label, value) }
                }
            }
        }
    }
}
