package dev.aurachat.client.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.SettingsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun MemoryScreen(api: AuraApi) {
    var tick by remember { mutableStateOf(0) }
    var mem by remember { mutableStateOf<JSONObject?>(null) }
    var cfgJson by remember { mutableStateOf<JSONObject?>(null) }
    var people by remember { mutableStateOf(listOf("owner" to "主人")) }
    var tab by remember { mutableStateOf(0) }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(tick) {
        err = null
        val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.memory(api) } }
        if (r.isSuccess) mem = r.getOrNull() else err = r.exceptionOrNull()?.message ?: "拉不到记忆"
        cfgJson = withContext(Dispatchers.IO) { runCatching { SettingsApi.config(api) } }.getOrNull()?.optJSONObject("config")
        val g = withContext(Dispatchers.IO) { runCatching { SettingsApi.guests(api) } }.getOrNull()
        val list = mutableListOf("owner" to "主人")
        val arr = g?.optJSONArray("guests")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                list += o.optString("subject") to o.optString("displayName").ifBlank { o.optString("username") }
            }
        }
        people = list
    }

    val tabs = listOf("档案", "相处经验", "说话方式", "全部记忆", "设置")
    val count = mem?.optJSONObject("stats")?.optInt("count") ?: 0
    val fail: (String) -> Unit = { msg -> note = msg; tick++ }

    Column(Modifier.fillMaxSize()) {
        PageHeader("记忆") {
            Text("共 $count 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { tick++ }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
        }
        TabStrip(tabs, tab) { tab = it }
        if (note.isNotBlank()) {
            Text(note, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        }
        if (err != null) {
            Text(err!!, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 14.dp))
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> ProfilesTab(api, mem, people, fail)
                1 -> InsightsTab(api, mem, fail)
                2 -> StylesTab(api, mem, fail)
                3 -> ItemsTab(api, mem, fail)
                else -> MemorySettingsTab(api, cfgJson, fail)
            }
        }
    }
}


@Composable
private fun ProfilesTab(api: AuraApi, mem: JSONObject?, people: List<Pair<String, String>>, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val profiles = mem?.optJSONArray("profiles") ?: JSONArray()
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${profiles.length()} 个", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { creating = true }) { Text("＋ 记一个人", fontSize = 12.sp) }
            }
        }
        if (profiles.length() == 0) item { EmptyText("还没有人物档案。") }
        items((0 until profiles.length()).toList()) { i ->
            val p = profiles.optJSONObject(i) ?: return@items
            val id = p.optString("id")
            val name = p.optString("name")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name.ifBlank { id }, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    if (name.isNotBlank()) Text("  $id", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { editing = p }) { Icon(Icons.Outlined.Edit, contentDescription = "修改") }
                }
                Text(p.optString("text").ifBlank { "（还没有印象）" }, fontSize = 13.sp)
            }
        }
    }

    val target = editing
    if (target != null) {
        ProfileDialog(
            people = people,
            about0 = target.optString("id"),
            name0 = target.optString("name"),
            text0 = target.optString("text"),
            isNew = false,
            onDismiss = { editing = null },
        ) { about, name, text ->
            editing = null
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.memoryProfile(api, about, text, name) } }.getOrNull()
                onDone(if (r?.optBoolean("ok") == true) "存好了" else "没存上")
            }
        }
    }
    if (creating) {
        ProfileDialog(
            people = people,
            about0 = people.firstOrNull()?.first ?: "owner",
            name0 = "",
            text0 = "",
            isNew = true,
            onDismiss = { creating = false },
        ) { about, name, text ->
            creating = false
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.memoryProfile(api, about, text, name) } }.getOrNull()
                onDone(if (r?.optBoolean("ok") == true) "记下了" else "没记下")
            }
        }
    }
}

@Composable
private fun ProfileDialog(
    people: List<Pair<String, String>>,
    about0: String,
    name0: String,
    text0: String,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onOk: (about: String, name: String, text: String) -> Unit,
) {
    var about by remember { mutableStateOf(about0) }
    var name by remember { mutableStateOf(name0) }
    var text by remember { mutableStateOf(text0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "记一个人" else "改档案") },
        text = {
            Column {
                if (isNew) PickRow("是谁", about, people) { about = it }
                else InfoRow("关于", people.firstOrNull { it.first == about }?.second ?: about)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名字") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("印象") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onOk(about, name.trim(), text.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("算了") } },
    )
}


@Composable
private fun InsightsTab(api: AuraApi, mem: JSONObject?, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val groups = mem?.optJSONArray("insights") ?: JSONArray()
    var target by remember { mutableStateOf<LineEdit?>(null) }

    fun save(key: String, name: String, list: JSONArray) {
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.memoryInsightRaw(api, key, name, list) } }.getOrNull()
            onDone(if (r?.optBoolean("ok") == true) "存好了" else "没存上")
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
        if (groups.length() == 0) item { EmptyText("还没有相处经验。") }
        items((0 until groups.length()).toList()) { gi ->
            val g = groups.optJSONObject(gi) ?: return@items
            val key = g.optString("key")
            val name = g.optString("name")
            val list = g.opt("list") as? JSONArray ?: JSONArray()
            Panel(name.ifBlank { key }) {
                for (idx in 0 until list.length()) {
                    val item = list.optJSONObject(idx) ?: continue
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(item.optString("text"), fontSize = 13.sp)
                            Text(fmtStamp(item.optLong("at")), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            target = LineEdit(key, name, item.optString("text"), list, idx)
                        }) { Icon(Icons.Outlined.Edit, contentDescription = "修改") }
                        IconButton(onClick = {
                            val next = JSONArray()
                            for (j in 0 until list.length()) if (j != idx) next.put(list.opt(j))
                            save(key, name, next)
                        }) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                TextButton(onClick = { target = LineEdit(key, name, "", list, -1) }) { Text("＋ 加一条", fontSize = 11.sp) }
            }
        }
    }

    val t = target
    if (t != null) {
        TextEditDialog(
            title = if (t.index < 0) "加一条" else "改这条",
            initial = t.text,
            onDismiss = { target = null },
        ) { txt ->
            target = null
            val next = JSONArray()
            for (j in 0 until t.list.length()) {
                if (j == t.index) next.put(JSONObject().put("text", txt).put("at", t.list.optJSONObject(j)?.optLong("at") ?: System.currentTimeMillis())) else next.put(t.list.opt(j))
            }
            if (t.index < 0) next.put(JSONObject().put("text", txt).put("at", System.currentTimeMillis()))
            save(t.key, t.name, next)
        }
    }
}

/** 一条待编辑的文本 + 它属于哪一组哪一项（index<0 表示新增）。 */
private class LineEdit(val key: String, val name: String, val text: String, val list: JSONArray, val index: Int)


@Composable
private fun StylesTab(api: AuraApi, mem: JSONObject?, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val styles = mem?.optJSONObject("styles") ?: JSONObject()
    var target by remember { mutableStateOf<LineEdit?>(null) }
    val keys = styles.keys().asSequence().toList()

    fun save(key: String, list: JSONArray) {
        scope.launch {
            val lines = (0 until list.length()).map { list.opt(it)?.toString().orEmpty() }.filter { it.isNotBlank() }
            val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.memoryStyle(api, key, lines) } }.getOrNull()
            onDone(if (r?.optBoolean("ok") == true) "存好了" else "没存上")
        }
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
        if (keys.isEmpty()) item { EmptyText("还没有说话方式。") }
        items(keys) { key ->
            val raw = styles.optJSONArray(key) ?: JSONArray()
            val label = if (key == "self") "Ta自己养成的说法" else "和 $key 说话的方式"
            Panel(label) {
                for (idx in 0 until raw.length()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(raw.optString(idx), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { target = LineEdit(key, "", raw.optString(idx), raw, idx) }) { Icon(Icons.Outlined.Edit, contentDescription = "修改") }
                        IconButton(onClick = {
                            val next = JSONArray()
                            for (j in 0 until raw.length()) if (j != idx) next.put(raw.opt(j))
                            save(key, next)
                        }) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
                    }
                }
                TextButton(onClick = { target = LineEdit(key, "", "", raw, -1) }) { Text("＋ 加一条", fontSize = 11.sp) }
            }
        }
    }

    val t = target
    if (t != null) {
        TextEditDialog(
            title = if (t.index < 0) "加一条" else "改这条",
            initial = t.text,
            onDismiss = { target = null },
        ) { txt ->
            target = null
            val next = JSONArray()
            for (j in 0 until t.list.length()) {
                if (j == t.index) next.put(txt) else next.put(t.list.opt(j))
            }
            if (t.index < 0) next.put(txt)
            save(t.key, next)
        }
    }
}


@Composable
private fun ItemsTab(api: AuraApi, mem: JSONObject?, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val items = mem?.optJSONArray("items") ?: JSONArray()
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("共 ${items.length()} 条", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { adding = true }) { Text("＋ 加一条", fontSize = 12.sp) }
            }
        }
        if (items.length() == 0) item { EmptyText("还没有记忆。") }
        items((0 until items.length()).toList()) { i ->
            val m = items.optJSONObject(i) ?: return@items
            val id = m.optString("id")
            val tags = m.optJSONArray("tags") ?: JSONArray()
            Panel {
                Text(m.optString("text"), fontSize = 13.sp)
                Text(
                    "${fmtStamp(m.optLong("at"))} · 重要度 ${(m.optDouble("importance", 0.0) * 100).roundToInt()}% · ${m.optString("source")}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (tags.length() > 0) {
                    val t = (0 until tags.length()).map { tags.optString(it) }.joinToString(" ")
                    Text(t, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                }
                Row {
                    IconButton(onClick = { editing = m }) { Icon(Icons.Outlined.Edit, contentDescription = "修改") }
                    IconButton(onClick = {
                        scope.launch {
                            val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.deleteMemory(api, id) } }.getOrNull()
                            onDone(if (r?.optBoolean("ok") != false) "删掉了" else "没删掉")
                        }
                    }) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (adding) {
        TextEditDialog(title = "加一条记忆", initial = "", onDismiss = { adding = false }) { txt ->
            adding = false
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.addMemory(api, txt, source = "manual") } }.getOrNull()
                onDone(if (r?.optBoolean("ok") != false) "记下了" else "没记下")
            }
        }
    }
    val e = editing
    if (e != null) {
        TextEditDialog(title = "改这条记忆", initial = e.optString("text"), onDismiss = { editing = null }) { txt ->
            editing = null
            scope.launch {
                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.updateMemory(api, e.optString("id"), txt) } }.getOrNull()
                onDone(if (r?.optBoolean("ok") != false) "改好了" else "没改成")
            }
        }
    }
}


@Composable
private fun MemorySettingsTab(api: AuraApi, cfgJson: JSONObject?, onDone: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val cfg = cfgJson?.let { remember(cfgJson) { CfgState(it) } }
    var note by remember { mutableStateOf("") }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
        item {
            if (cfg == null) {
                LoadingRow()
            } else {
                Panel("记忆设置") {
                    Toggle("开启记忆", cfg.bool("memory.enabled", true), onChange = { cfg.set("memory.enabled", it) })
                    CfgText("每次翻几条相关的", "memory.recallCount", cfg, numeric = true)
                    Toggle("每天自动整理记忆", cfg.bool("memory.dailyConsolidate", true), onChange = { cfg.set("memory.dailyConsolidate", it) })
                    // 服务端存数组，界面显示成逗号串，保存时拆回去
                    val timesRaw = cfg.arr("memory.consolidateTimes")
                    val timesDefault = (0 until timesRaw.length()).map { timesRaw.optString(it).trim() }.filter { it.isNotBlank() }.joinToString(",")
                    var times by remember(timesDefault) { mutableStateOf(timesDefault) }

                    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        OutlinedTextField(
                            value = times,
                            onValueChange = { times = it },
                            label = { Text("整理时间") },
                            placeholder = { Text("04:00,16:00") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Hint("整理时间写 24 小时制，多个用逗号隔开，例如 04:00,16:00")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val list = times.split(',', '，').map { it.trim() }.filter { it.isNotBlank() }
                                val body = JSONObject()
                                val pk = cfg.patch.keys()
                                while (pk.hasNext()) { val k = pk.next(); body.put(k, cfg.patch.opt(k)) }
                                val mem = body.optJSONObject("memory") ?: JSONObject()
                                mem.put("consolidateTimes", JSONArray(list))
                                body.put("memory", mem)
                                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.patchConfig(api, body) } }.getOrNull()
                                note = if (r?.optBoolean("ok") == true) "存好了" else "没存上"
                                onDone("")
                            }
                        }) { Text("保存记忆设置") }
                        TextButton(onClick = {
                            scope.launch {
                                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.consolidateMemory(api) } }.getOrNull()
                                note = if (r?.optBoolean("ok") == true) "整理完了" else "没整理成"
                            }
                        }) { Text("现在整理一次") }
                    }
                    Hint(note)
                }
            }
        }
        item { Note(note) }
    }
}


@Composable
private fun TextEditDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var txt by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = txt,
                onValueChange = { txt = it },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = txt.isNotBlank(), onClick = { onOk(txt.trim()) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("算了") } },
    )
}
