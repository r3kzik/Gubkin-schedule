package com.vadik.raspisanie.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ок в любом случае */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by vm.state.collectAsState()
            val dark = effectiveDark(state.prefs.style, state.prefs.theme)
            // цвет значков в строке состояния под выбранную тему
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            val ready = state.settings != null && state.onboarding == null
            LaunchedEffect(ready) { if (ready) maybeAskNotifications() }
            AppTheme(state.prefs) {
                AppRoot(state, vm)
            }
        }
    }

    /** Разрешение на уведомления спрашиваем после настройки группы, а не поверх приветствия. */
    private var askedNotifications = false

    private fun maybeAskNotifications() {
        if (askedNotifications) return
        askedNotifications = true
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStop() {
        super.onStop()
        // смена цвета иконки — когда приложение ушло в фон
        IconSwitcher.apply(this, vm.state.value.prefs)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
