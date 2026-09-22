package com.zerotap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.zerotap.data.datastore.UserPreferences
import com.zerotap.ui.navigation.NavGraph
import com.zerotap.ui.theme.ZeroTapTheme

class MainActivity : ComponentActivity() {

    private lateinit var userPreferences: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userPreferences = UserPreferences(applicationContext)

        setContent {
            val themeMode by userPreferences.themeMode.collectAsStateWithLifecycle(initialValue = "SYSTEM")
            ZeroTapTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                NavGraph(navController = navController, appContext = applicationContext)
            }
        }
    }
}
