package com.mynotes

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.mynotes.ui.NavGraph
import com.mynotes.ui.SettingsViewModel
import com.mynotes.ui.theme.MyNotesTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var settingsViewModel: SettingsViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val vm: SettingsViewModel = hiltViewModel()
            settingsViewModel = vm
            val isDarkTheme by vm.isDarkTheme.collectAsState()

            MyNotesTheme(darkTheme = isDarkTheme ?: isSystemInDarkTheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.data?.let { uri ->
            if (uri.scheme == "mynotes" && uri.host == "onedrive-auth") {
                settingsViewModel?.handleAuthRedirect(uri)
            }
        }
    }
}
