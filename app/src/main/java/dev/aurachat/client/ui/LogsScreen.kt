package dev.aurachat.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.SettingsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** 仅 owner 可进，入口在 MainActivity。 */
@Composable
fun LogsScreen(api: AuraApi) {
    var tick by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        err = null
        val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.logs(api) } }
        if (r.isSuccess) data = r.getOrNull() else err = r.exceptionOrNull()?.message ?: "拉不到日志"
    }

    val logs = data?.opt("logs") as? JSONArray ?: JSONArray()

    fun clear() {
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.clearLogs(api) } }.getOrNull()
            note = if (r != null && r.optBoolean("ok")) "清掉了 ${r.optInt("cleared")} 条" else "没清掉"
            tick++
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        item {
            PageHeader("日志") {
                TextButton(onClick = { tick++ }) { Text("刷新", fontSize = 12.sp) }
                TextButton(onClick = { clear() }) { Text("清空", fontSize = 12.sp) }
            }
        }
        if (note.isNotBlank()) item { Note(note) }
        if (err != null) item {
            Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }
        if (data != null && logs.length() == 0) item { EmptyText("还没有日志") }
        items((0 until logs.length()).reversed().toList()) { i ->
            val e = logs.optJSONObject(i) ?: return@items
            Text(
                "${e.optString("time")} [${e.optString("level")}] ${e.optString("area")} ${e.optString("text")}",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = when (e.optString("level")) {
                    "error" -> MaterialTheme.colorScheme.error
                    "warn" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
