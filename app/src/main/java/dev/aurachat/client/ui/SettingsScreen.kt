package dev.aurachat.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurachat.client.Version
import dev.aurachat.client.data.Prefs
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.SettingsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private val OWNER_GROUPS = listOf(
    "master" to "总开关",
    "identity" to "身份",
    "appearance" to "外观",
    "model" to "模型",
    "reply" to "回复节奏",
    "diary" to "空间日记",
    "voice" to "语音",
    "stt" to "听语音",
    "imagegen" to "生图",
    "sticker" to "表情库",
    "history" to "聊天记录",
    "logs" to "日志",
    "speakup" to "闲置搭话",
    "selfdrive" to "自驱",
    "web" to "网页入口",
    "guest" to "客人",
    "account" to "账号",
    "about" to "关于",
    "cache" to "本地缓存",
)

private val GUEST_GROUPS = listOf(
    "appearance" to "外观",
    "account" to "账号",
    "about" to "关于",
    "cache" to "本地缓存",
)

/**
 * 改动先攒在 CfgState.patch 里，顶部「保存设置」统一 PATCH。
 */
@Composable
fun SettingsScreen(
    api: AuraApi,
    prefs: Prefs,
    onThemeChange: (String) -> Unit,
    onLogout: () -> Unit,
) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var st by remember { mutableStateOf<JSONObject?>(null) }
    var cfg by remember { mutableStateOf<CfgState?>(null) }
    var loadErr by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var themeKey by remember { mutableStateOf(prefs.theme) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var saving by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        loading = true
        loadErr = null
        val sRes = withContext(Dispatchers.IO) { runCatching { SettingsApi.stateMe(api) } }
        st = sRes.getOrNull()
        if (sRes.isFailure) loadErr = "拉不到状态：${sRes.exceptionOrNull()?.message ?: "网络不对"}"
        val role = sRes.getOrNull()?.optJSONObject("me")?.optString("role").orEmpty().ifBlank { prefs.role }
        if (role == "owner") {
            val cRes = withContext(Dispatchers.IO) { runCatching { SettingsApi.config(api) } }
            cfg = cRes.getOrNull()?.optJSONObject("config")?.let { CfgState(it) }
            if (cRes.isFailure) loadErr = "拉不到设置：${cRes.exceptionOrNull()?.message ?: "网络不对"}"
        } else {
            cfg = null
        }
        cacheBytes = withContext(Dispatchers.IO) {
            File(ctx.cacheDir, "media").listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
        }
        loading = false
    }

    val me = st?.optJSONObject("me")
    val role = me?.optString("role").orEmpty().ifBlank { prefs.role }
    val owner = role == "owner"

    fun save() {
        val c = cfg ?: return
        if (c.patch.length() == 0) {
            note = "没有改动"
            return
        }
        scope.launch {
            saving = true
            val r = withContext(Dispatchers.IO) { runCatching { SettingsApi.patchConfig(api, c.patch) }.getOrNull() }
            saving = false
            val ok = r?.optBoolean("ok") == true
            note = if (ok) "存好了" else msgOf(r)
            if (ok) tick++
        }
    }

    fun clearCache() {
        runCatching { File(ctx.cacheDir, "media").listFiles()?.forEach { it.delete() } }
        cacheBytes = 0L
        note = "媒体缓存已清空"
    }

    val onTheme: (String) -> Unit = {
        themeKey = it
        prefs.theme = it
        onThemeChange(it)
    }

    val groups = if (owner) OWNER_GROUPS else GUEST_GROUPS
    var section by remember { mutableStateOf("master") }
    val active = if (groups.any { it.first == section }) section else groups.first().first
    val c = cfg

    Column(Modifier.fillMaxSize()) {
        PageHeader("设置") {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else if (owner && (c?.dirty ?: 0) > 0) {
                Text("${c?.dirty ?: 0} 处未保存", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            if (owner) {
                TextButton(enabled = c != null && !saving && (c?.dirty ?: 0) > 0, onClick = { save() }) {
                    Text(if (saving) "保存中…" else "保存设置", fontSize = 13.sp)
                }
            }
        }
        if (note.isNotBlank()) {
            Text(note, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp))
        }
        if (loadErr != null) {
            Text(loadErr!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 14.dp))
        }

        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(
                Modifier
                    .width(140.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                groups.forEach { (key, label) ->
                    val selected = key == active
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clickable { section = key }
                            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                    ) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    !owner -> LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        item {
                            when (active) {
                                "appearance" -> AppearanceSection(placeholderCfg(), themeKey, onTheme, onNote = { note = it }, showBackground = false)
                                "account" -> AccountSection(api, prefs, me, onLogout)
                                "about" -> AboutCard(prefs, st)
                                else -> CacheSection(cacheBytes) { clearCache() }
                            }
                        }
                        item { Hint("这份地址里你是访客，只能改主题、密码和本地缓存；其余设置在主人那边。") }
                    }
                    c == null -> LoadingRow()
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        item {
                            when (active) {
                                "master" -> MasterSection(c)
                                "identity" -> IdentitySection(api, c, onNote = { note = it })
                                "appearance" -> AppearanceSection(c, themeKey, onTheme, onNote = { note = it })
                                "model" -> ModelSection(api, c)
                                "reply" -> ReplySection(c)
                                "diary" -> DiarySection(c)
                                "voice" -> VoiceSection(api, c, onNote = { note = it })
                                "stt" -> SttSection(api, c)
                                "imagegen" -> ImageGenSection(c)
                                "sticker" -> StickerSection(api, c, onNote = { note = it })
                                "history" -> HistorySection(c)
                                "logs" -> LogsCfgSection(c)
                                "speakup" -> SpeakUpSection(c)
                                "selfdrive" -> SelfDriveSection(api, c, onNote = { note = it })
                                "web" -> WebSection(c)
                                "guest" -> GuestSection(api, onNote = { note = it })
                                "account" -> AccountSection(api, prefs, me, onLogout)
                                "about" -> AboutCard(prefs, st)
                                else -> CacheSection(cacheBytes) { clearCache() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 访客不拉 config，外观那块只用到主题，给个空壳避免空指针。 */
private fun placeholderCfg(): CfgState = CfgState(JSONObject())

@Composable
private fun AboutCard(prefs: Prefs, st: JSONObject?) {
    Panel("关于") {
        InfoRow("客户端版本", Version.APP)
        InfoRow("平台", "Android 客户端 · Jetpack Compose")
        InfoRow("服务端", prefs.base)
        InfoRow("服务端版本", st?.optString("version").orEmpty().ifBlank { "—" })
        InfoRow("当前人设", st?.optString("botName").orEmpty().ifBlank { "—" })
    }
}

@Composable
internal fun LoadingRow() {
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

@Composable
internal fun Err(msg: String) {
    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}
