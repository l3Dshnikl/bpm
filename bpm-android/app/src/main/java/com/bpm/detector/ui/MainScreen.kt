package com.bpm.detector.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bpm.detector.R
import com.bpm.detector.viewmodel.BpmViewModel
import com.bpm.detector.viewmodel.DetectionMode

@Composable
fun MainScreen(
    viewModel: BpmViewModel,
    onRequestMicPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val micState by viewModel.micUiState.collectAsStateWithLifecycle()
    val tapState by viewModel.tapUiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = if (selectedMode == DetectionMode.TAP) 0 else 1) {
            Tab(
                selected = selectedMode == DetectionMode.TAP,
                onClick = { viewModel.selectMode(DetectionMode.TAP) },
                text = { Text(text = stringResource(R.string.tab_tap)) },
            )
            Tab(
                selected = selectedMode == DetectionMode.MICROPHONE,
                onClick = { viewModel.selectMode(DetectionMode.MICROPHONE) },
                text = { Text(text = stringResource(R.string.tab_microphone)) },
            )
        }

        when (selectedMode) {
            DetectionMode.TAP -> TapModeContent(
                state = tapState,
                onTap = viewModel::onTap,
                onReset = viewModel::resetTaps,
            )
            DetectionMode.MICROPHONE -> MicModeContent(
                state = micState,
                onRequestPermission = onRequestMicPermission,
                onStart = viewModel::startMic,
                onStop = viewModel::stopMic,
            )
        }
    }
}
