package com.orbit.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.assistant.data.preferences.OrbitSettings
import com.orbit.assistant.domain.repository.PreferencesRepository
import com.orbit.assistant.navigation.OrbitAppScaffold
import com.orbit.assistant.ui.theme.OrbitTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by preferencesRepository.settingsFlow
                .collectAsStateWithLifecycle(initialValue = OrbitSettings())

            OrbitTheme(darkTheme = settings.isDarkMode) {
                OrbitAppScaffold()
            }
        }
    }
}
