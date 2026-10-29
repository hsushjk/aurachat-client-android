package dev.aurachat.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.SettingsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 概览：6 格统计 + 状态；「让Ta…」这类动作全客户端只此一处（仅 owner）。
 */
@Composable
fun OverviewScreen(api: AuraApi, owner: Boolean = false) {
    var tick by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf<JSONObject?>(null) }
    var cfg by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        err = null
        val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.status(api) } }
        if (r.isSuccess) status = r.getOrNull() else err = r.exceptionOrNull()?.message ?: "拉不到概览"
        if (owner) {
            cfg = withContext(Dispatchers.IO) { runCatching { SettingsApi.config(api) } }.getOrNull()?.optJSONObject("config")
        }
    }

    val st = status?.optJSONObject("stats") ?: JSONObject()
    val mood = status?.optJSONObject("mood") ?: JSONObject()
    val diaryStats = status?.optJSONObject("diaryStats") ?: status?.optJSONObject("diary") ?: JSONObject()
    val speakUp = status?.optJSONObject("speakUp") ?: JSONObject()
    val selfDrive = status?.optJSONObject("selfDrive") ?: JSONObject()
    val memory = status?.optJSONObject("memory") ?: JSONObject()
    val sessions = status?.opt("sessions")
    val sessionCount = when (sessions) {
        is JSONObject -> sessions.length()
        is org.json.JSONArray -> sessions.length()
        else -> 0
    }
    val speakUpCfg = cfg?.optJSONObject("speakUp") ?: JSONObject()

    fun fire(what: String) {
        if (busy.isNotBlank()) return
        busy = what
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.test(api, what) } }.getOrNull()
            busy = ""
            note = if (r != null && r.optBoolean("ok")) {
                r.optString("message").ifBlank { "做了" }
            } else {
                r?.optString("reason").orEmpty().ifBlank { "没做成" }
            }
            tick++
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 14.dp),
    ) {
        item { PageHeader("概览") { IconButton(onClick = { tick++ }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") } } }
        if (note.isNotBlank()) item { Note(note) }
        if (err != null) item { Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }

        item {
            val cells = listOf(
                "心情" to "${mood.optDouble("value", 0.0).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }} ${mood.optString("label")}".trim(),
                "动态" to "${diaryStats.optInt("total")} 篇",
                "今天写了" to "${diaryStats.optInt("today")} 篇",
                "今天主动搭话" to "${speakUp.optInt("count")} 次",
                "自驱" to "${if (selfDrive.optBoolean("enabled")) "开启" else "关闭"} · 今天 ${selfDrive.optInt("count")} 次",
                "记忆" to "${memory.optInt("count")} 条",
            )
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (row in cells.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in row) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                            ) {
                                Text(c.first, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(3.dp))
                                Text(c.second, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item {
            Panel("状态") {
                InfoRow("总开关", if (cfg?.optBoolean("enabled") ?: false) "开" else "关")
                InfoRow(
                    "闲置搭话",
                    if (speakUpCfg.optBoolean("enabled")) "${speakUpCfg.optInt("idleMinutes")} 分钟" else "关",
                )
                InfoRow(
                    "时间段",
                    "${speakUpCfg.optString("quietFrom").ifBlank { "23:00" }} – ${speakUpCfg.optString("quietTo").ifBlank { "08:00" }}",
                )
                InfoRow("聊天轮数", st.optInt("turns").toString())
                InfoRow("消息条数", st.optInt("messages").toString())
                InfoRow("语音 / 表情 / 图片", "${st.optInt("voice")} 条 · 表情 ${st.optInt("stickers")} · 图 ${st.optInt("images")}")
                InfoRow("会话", "$sessionCount 个")
            }
        }

        if (owner) {
            item {
                Panel("调试 / 试一下") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { fire("speakup") }, enabled = busy.isBlank(), modifier = Modifier.weight(1f)) {
                                Text("让Ta现在来找你", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { fire("diary") }, enabled = busy.isBlank(), modifier = Modifier.weight(1f)) {
                                Text("让Ta写一篇日记", fontSize = 12.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { fire("selfdrive") }, enabled = busy.isBlank(), modifier = Modifier.weight(1f)) {
                                Text("让Ta自驱一次", fontSize = 12.sp)
                            }
                            OutlinedButton(onClick = { fire("social") }, enabled = busy.isBlank(), modifier = Modifier.weight(1f)) {
                                Text("让Ta回访评论", fontSize = 12.sp)
                            }
                        }
                        if (busy.isNotBlank()) Text("正在做…", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
