package com.filemanager.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.filemanager.app.ui.ThemeViewModel
import com.filemanager.app.ui.navigation.AppNavHost
import com.filemanager.app.ui.screens.onboarding.StoragePermissionGate
import com.filemanager.app.ui.theme.FileManagerTheme
import com.filemanager.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val themeMode by themeViewModel.themeMode.collectAsState()

            FileManagerTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var hasAccess by remember {
                        mutableStateOf(PermissionUtils.hasFullStorageAccess(this))
                    }

                    // Re-check whenever the user returns to the app (e.g. after
                    // granting "All files access" in system Settings) so we don't
                    // require a manual app restart.
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                hasAccess = PermissionUtils.hasFullStorageAccess(this@MainActivity)
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    if (hasAccess) {
                        AppNavHost()
                    } else {
                        StoragePermissionGate(
                            onAccessGranted = { hasAccess = true }
                        )
                    }
                }
            }
        }
    }
}
