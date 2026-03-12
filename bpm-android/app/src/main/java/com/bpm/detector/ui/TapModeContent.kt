package com.bpm.detector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bpm.detector.R
import com.bpm.detector.viewmodel.TapUiState

@Composable
fun TapModeContent(
    state: TapUiState,
    onTap: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // BPM display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 32.dp),
        ) {
            Text(
                text = state.formattedBpm,
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            if (state.tempoMarking.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.tempoMarking,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.bpmMs > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%.0f ms / beat".format(state.bpmMs),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        // TAP button — uses pointerInput/onPress instead of Button.onClick.
        // onClick fires on the UP event (~50–100 ms after finger contact), introducing
        // systematic latency. At 180 BPM (beat = 333 ms) that is a ~25% timing error.
        // detectTapGestures { onPress } fires on the DOWN event, eliminating this bias.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { onTap() })
                },
        ) {
            Text(
                text = stringResource(R.string.tap_button),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }

        // Tap count + Reset
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.tapCount > 0) {
                Text(
                    text = stringResource(R.string.taps_count, state.tapCount),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            OutlinedButton(onClick = onReset) {
                Text(text = stringResource(R.string.reset_button))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
