package io.github.stickerbug.dsapichat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.activity.compose.BackHandler
import io.github.stickerbug.dsapichat.ui.chat.ChatRoute
import io.github.stickerbug.dsapichat.ui.settings.SettingsRoute
import io.github.stickerbug.dsapichat.ui.theme.Ds
import io.github.stickerbug.dsapichat.ui.theme.DsTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // 三键导航下系统默认给导航栏叠加半透明对比 scrim，会让深色底偏暗（实测 #0F0F0F 变 #090909）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        val forceReturning = intent.getBooleanExtra("replica_returning", false)
        // 调试用：指向本地 mock 服务，验证流式解析（不影响正常使用路径）。
        val mockBase = intent.getStringExtra("replica_base")
        if (mockBase != null) {
            val cur = vm.settings.value
            vm.saveSettings(
                cur.copy(
                    baseUrl = mockBase,
                    apiKey = intent.getStringExtra("replica_key") ?: "sk-mock",
                    model = intent.getStringExtra("replica_model") ?: "deepseek-flash",
                    protocol = intent.getStringExtra("replica_proto") ?: "anthropic",
                    thinking = intent.getBooleanExtra("replica_thinking", true),
                    search = intent.getBooleanExtra("replica_search_tool", false)
                )
            )
        }
        if (intent.getBooleanExtra("replica_attach_upload", false)) vm.debugAttach()
        if (intent.getBooleanExtra("replica_attach", false)) vm.seedAttach()
        else if (intent.getBooleanExtra("replica_search", false)) vm.seedSearch()
        else if (intent.getBooleanExtra("replica_code", false)) vm.seedRich()
        else if (intent.getBooleanExtra("replica_think", false)) vm.seedDemo(answer = "")
        else if (intent.getBooleanExtra("replica_demo", false)) vm.seedDemo()
        setContent { DsAppRoot(vm, forceReturning) }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c != null) {
        if (c is Activity) return c
        c = (c as? ContextWrapper)?.baseContext
    }
    return null
}

private enum class Route { Chat, Settings }

@Composable
private fun DsAppRoot(vm: AppViewModel, forceReturning: Boolean) {
    val settings by vm.settings.collectAsState()
    val dark = when (settings.darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    DsTheme(dark = dark) {
        val view = LocalView.current
        LaunchedEffect(dark) {
            val act = view.context.findActivity() ?: return@LaunchedEffect
            WindowCompat.getInsetsController(act.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
        Surface(modifier = Modifier.fillMaxSize(), color = Ds.color.bg) {
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                var route by remember { mutableStateOf(Route.Chat) }
                BackHandler(enabled = route != Route.Chat) { route = Route.Chat }
                when (route) {
                    Route.Chat -> ChatRoute(
                        vm = vm,
                        onOpenSettings = { route = Route.Settings },
                        forceReturning = forceReturning
                    )
                    Route.Settings -> SettingsRoute(vm = vm, onBack = { route = Route.Chat })
                }
            }
        }
    }
}
