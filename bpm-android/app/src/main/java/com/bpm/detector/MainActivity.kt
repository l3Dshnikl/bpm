package com.bpm.detector

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bpm.detector.ui.MainScreen
import com.bpm.detector.ui.theme.BpmDetectorTheme
import com.bpm.detector.viewmodel.BpmViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: BpmViewModel by viewModels()

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            viewModel.onMicPermissionResult(granted)
            if (!granted) {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BpmDetectorTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        viewModel = viewModel,
                        onRequestMicPermission = {
                            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                }
            }
        }
    }
}
