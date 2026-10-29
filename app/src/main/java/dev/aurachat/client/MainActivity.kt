package dev.aurachat.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import dev.aurachat.client.data.Prefs
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.Live
import dev.aurachat.client.net.SettingsApi
import dev.aurachat.client.ui.AppTheme
import dev.aurachat.client.ui.Backdrop
import dev.aurachat.client.ui.ChatScreen
import dev.aurachat.client.ui.ConnectScreen
import dev.aurachat.client.ui.LogsScreen
import dev.aurachat.client.ui.MemoryScreen
import dev.aurachat.client.ui.MoodScreen
import dev.aurachat.client.ui.OverviewScreen
import dev.aurachat.client.ui.SettingsScreen
import dev.aurachat.client.ui.SpaceScreen
import dev.aurachat.client.ui.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        // 有登录态直接进聊天，断网由 ChatScreen 重连
        val resume = prefs.base.isNotBlank() && prefs.token.isNotBlank()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (resume) LiveService.start(this)
        setContent {
            var themeKey by remember { mutableStateOf(prefs.theme) }
            AppTheme(ThemeMode.of(themeKey)) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
                    AppRoot(prefs, resume, onThemeChange = { themeKey = it })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Live.visible = true
        LiveService.clearMessages(this)
    }

    override fun onPause() {
        Live.visible = false
        super.onPause()
    }
}

/** 底栏 7 项；概览/记忆/日志只给 owner。 */
private data class Tab(val key: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("chat", "聊天", Icons.Outlined.ChatBubbleOutline),
    Tab("space", "空间", Icons.Outlined.Article),
    Tab("mood", "心情", Icons.Outlined.Mood),
    Tab("overview", "概览", Icons.Outlined.Insights),
    Tab("memory", "记忆", Icons.Outlined.AutoAwesome),
    Tab("settings", "设置", Icons.Outlined.Settings),
    Tab("logs", "日志", Icons.Outlined.ReceiptLong),
)

private fun ownerOnly(key: String) = key == "overview" || key == "memory" || key == "logs"

@Composable
private fun AppRoot(prefs: Prefs, resume: Boolean, onThemeChange: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var api by remember { mutableStateOf(if (resume) AuraApi(prefs.base, prefs.token).also { it.rebuild() } else null) }
    var screen by remember { mutableStateOf("chat") }
    // 角色只决定底栏显示几项；拿不到就按 prefs 里记的走
    var role by remember { mutableStateOf(prefs.role) }
    var subject by remember { mutableStateOf("") }

    LaunchedEffect(api) {
        val a = api ?: return@LaunchedEffect
        val st = withContext(Dispatchers.IO) { runCatching { SettingsApi.stateMe(a) }.getOrNull() }
        val me = st?.optJSONObject("me")
        val r = me?.optString("role").orEmpty()
        if (r.isNotBlank()) {
            role = r
            prefs.role = r
        }
        subject = me?.optString("subject").orEmpty()
    }

    if (api == null) {
        ConnectScreen(prefs, onConnected = { connected ->
            api = connected
            Backdrop.load(ctx)
            LiveService.start(ctx)
        })
        return
    }
    val owner = role == "owner"
    if (!owner && ownerOnly(screen)) screen = "chat"
    val logout = {
        Live.clear()
        Backdrop.clear()
        ctx.stopService(android.content.Intent(ctx, LiveService::class.java))
        screen = "chat"
        api = null
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // 键盘弹起底栏随之上移，不盖住输入栏和最新消息
            NavigationBar(modifier = Modifier.imePadding()) {
                TABS.forEach { tab ->
                    if (ownerOnly(tab.key) && !owner) return@forEach
                    NavigationBarItem(
                        selected = screen == tab.key,
                        onClick = { screen = tab.key },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when (screen) {
                "chat" -> {
                    LaunchedEffect(Unit) { Backdrop.load(ctx) }
                    ChatScreen(
                        api!!,
                        prefs,
                        onLogout = logout,
                    )
                }
                "space" -> SpaceScreen(api!!, owner = owner, subject = subject)
                "mood" -> MoodScreen(api!!, owner = owner)
                "overview" -> OverviewScreen(api!!, owner = owner)
                "memory" -> MemoryScreen(api!!)
                "logs" -> LogsScreen(api!!)
                "settings" -> SettingsScreen(
                    api = api!!,
                    prefs = prefs,
                    onThemeChange = onThemeChange,
                    onLogout = logout,
                )
            }
        }
    }
}
