package dev.aurachat.client.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.SettingsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject


@Composable
fun SpaceScreen(api: AuraApi, owner: Boolean = false, subject: String = "") {
    var tick by remember { mutableStateOf(0) }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        err = null
        val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.diary(api) } }
        if (r.isSuccess) data = r.getOrNull() else err = r.exceptionOrNull()?.message ?: "拉不到空间"
    }

    val entries = (data?.opt("entries") as? JSONArray ?: JSONArray())
    val stats = data?.optJSONObject("stats") ?: JSONObject()

    Column(Modifier.fillMaxSize()) {
        PageHeader("空间") {
            val total = stats.optInt("total", entries.length())
            val today = stats.optInt("today", 0)
            val planned = stats.optInt("planned", 0)
            var line = "共 $total 篇 · 今天 $today 篇"
            if (planned > 0) line += " · 计划 $planned 篇"
            Text(line, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = { tick++ }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
        }
        Note(note)
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(12.dp),
        ) {
            if (err != null) item { Text(err!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (data != null && entries.length() == 0) item { EmptyText("还没有动态。") }
            items((0 until entries.length()).toList()) { i ->
                val e = entries.optJSONObject(i) ?: return@items
                EntryCard(
                    api = api,
                    entry = e,
                    owner = owner,
                    subject = subject,
                    onChanged = { tick++ },
                    onNote = { note = it },
                )
            }
        }
    }
}

@Composable
private fun EntryCard(
    api: AuraApi,
    entry: JSONObject,
    owner: Boolean,
    subject: String,
    onChanged: () -> Unit,
    onNote: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val id = entry.optString("id")
    val comments = entry.opt("comments") as? JSONArray ?: JSONArray()
    val drafts = remember { mutableStateMapOf<String, String>() }
    var busy by remember { mutableStateOf(false) }

    fun sendComment() {
        val text = drafts[id].orEmpty().trim()
        if (text.isEmpty() || busy) return
        busy = true
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.diaryComment(api, id, text) } }.getOrNull()
            busy = false
            if (r?.optBoolean("ok") != false) {
                drafts[id] = ""
                onChanged()
            } else {
                onNote(r?.optString("reason").orEmpty().ifBlank { "没发出去" })
            }
        }
    }

    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(fmtWhen(entry.optLong("at")), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (entry.optString("visibility", "all") == "self") {
                Text("  仅主人可见", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (owner) {
                IconButton(onClick = {
                    scope.launch {
                        val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.diaryDelete(api, id) } }.getOrNull()
                        onNote(if (r?.optBoolean("ok") != false) "删掉了" else r?.optString("reason").orEmpty().ifBlank { "没删掉" })
                        onChanged()
                    }
                }) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
            }
        }
        Text(entry.optString("text"), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        if (comments.length() == 0) {
            EmptyText("还没有评论")
        } else {
            for (ci in 0 until comments.length()) {
                val c = comments.optJSONObject(ci) ?: continue
                CommentRow(
                    api = api,
                    comment = c,
                    entryId = id,
                    replyId = "",
                    commentId = c.optString("id"),
                    owner = owner,
                    subject = subject,
                    indent = 0,
                    onChanged = onChanged,
                    onNote = onNote,
                )
                val replies = c.opt("replies") as? JSONArray ?: JSONArray()
                for (ri in 0 until replies.length()) {
                    val rep = replies.optJSONObject(ri) ?: continue
                    CommentRow(
                        api = api,
                        comment = rep,
                        entryId = id,
                        replyId = rep.optString("id"),
                        commentId = c.optString("id"),
                        owner = owner,
                        subject = subject,
                        indent = 1,
                        onChanged = onChanged,
                        onNote = onNote,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = drafts[id].orEmpty(),
                onValueChange = { drafts[id] = it },
                placeholder = { Text("说点什么…", fontSize = 13.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendComment() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { sendComment() }, enabled = !busy) { Text("评论", fontSize = 13.sp) }
        }
    }
}

@Composable
private fun CommentRow(
    api: AuraApi,
    comment: JSONObject,
    entryId: String,
    replyId: String,
    commentId: String = "",
    owner: Boolean,
    subject: String,
    indent: Int,
    onChanged: () -> Unit,
    onNote: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val from = comment.optString("from")
    val mine = subject.isNotBlank() && from == subject
    var replying by remember { mutableStateOf(false) }
    var replyText by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun sendReply() {
        val text = replyText.trim()
        if (text.isEmpty() || busy) return
        busy = true
        scope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.diaryReply(api, entryId, commentId, text) } }.getOrNull()
            busy = false
            if (r?.optBoolean("ok") != false) {
                replyText = ""
                replying = false
                onChanged()
            } else {
                onNote(r?.optString("reason").orEmpty().ifBlank { "没发出去" })
            }
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(start = if (indent > 0) 20.dp else 0.dp, top = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                comment.optString("name").ifBlank { from },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(" ${comment.optString("text")}", fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("  " + fmtStamp(comment.optLong("at")), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (indent == 0) {
                IconButton(onClick = { replying = !replying }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Reply, contentDescription = "回复")
                }
            }
            if (owner || mine) {
                IconButton(onClick = {
                    scope.launch {
                        val r = runCatching {
                            withContext(Dispatchers.IO) {
                                SettingsApi.diaryCommentDelete(api = api, entryId = entryId, commentId = commentId, replyId = replyId)
                            }
                        }.getOrNull()
                        onNote(if (r?.optBoolean("ok") != false) "删掉了" else r?.optString("reason").orEmpty().ifBlank { "没删掉" })
                        onChanged()
                    }
                }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
            }
        }
        if (replying) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replyText,
                    onValueChange = { replyText = it },
                    placeholder = { Text("回复…", fontSize = 13.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendReply() }),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = { sendReply() }, enabled = !busy) { Text("回复", fontSize = 13.sp) }
            }
        }
    }
}


@Composable
fun MoodScreen(api: AuraApi, owner: Boolean = false) {
    var tick by remember { mutableStateOf(0) }
    var mood by remember { mutableStateOf<JSONObject?>(null) }
    var cfgJson by remember { mutableStateOf<JSONObject?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        err = null
        val m = runCatching { withContext(Dispatchers.IO) { SettingsApi.mood(api) } }
        if (m.isSuccess) mood = m.getOrNull() else err = m.exceptionOrNull()?.message ?: "拉不到心情"
        if (owner) {
            cfgJson = withContext(Dispatchers.IO) { runCatching { SettingsApi.config(api) } }.getOrNull()?.optJSONObject("config")
        }
    }

    val state = mood?.optJSONObject("state") ?: JSONObject()
    val band = state.optJSONObject("band") ?: JSONObject()
    val lo = band.optDouble("low", 50.0)
    val hi = band.optDouble("high", 60.0)
    val value = state.optDouble("value", 0.0)
    val history = mood?.opt("history") as? JSONArray ?: JSONArray()
    val cfg = cfgJson?.let { remember(cfgJson) { CfgState(it) } }
    val halfHours = cfg?.dbl("mood.halfLifeHours", 3.0) ?: 3.0

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item { PageHeader("心情") { IconButton(onClick = { tick++ }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") } } }
        if (note.isNotBlank()) item { Note(note) }
        if (err != null) item { Text(err!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp)) }
        item {
            Panel("心情值") {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(round0(value), fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(" / 100 · ${state.optString("label", "")}", fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                MoodBar(value = value, low = lo, high = hi)
                Spacer(Modifier.height(6.dp))
                Hint(
                    "平常区间 ${round0(lo)}–${round0(hi)}：高于区间慢慢往回落、低于区间慢慢往回升。" +
                        "半衰期 ${numStr(halfHours)} 小时——离平常差多少，过这么久就减半。",
                )
                val reason = state.optString("reason")
                if (reason.isNotBlank()) Hint("最近一次变化：$reason")
            }
        }
        if (owner && cfg != null) {
            item {
                Panel("心情怎么算") {
                    CfgText("半衰期（小时）", "mood.halfLifeHours", cfg, hint = "心情偏离平常后，几小时落回去一半", numeric = true, decimals = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            CfgText("平常区间下限", "mood.neutralLow", cfg, numeric = true)
                        }
                        Text("到", fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(Modifier.weight(1f)) {
                            CfgText("平常区间上限", "mood.neutralHigh", cfg, numeric = true)
                        }
                    }
                    Hint("低了往上升回下限，高了往下降回上限")
                    Toggle("判断情绪", cfg.bool("mood.judge", true), hint = "让模型判断情绪") { cfg.set("mood.judge", it) }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val body = JSONObject().put(
                                    "mood",
                                    JSONObject()
                                        .put("halfLifeHours", cfg.dbl("mood.halfLifeHours", 3.0))
                                        .put("neutralLow", cfg.int("mood.neutralLow", 50))
                                        .put("neutralHigh", cfg.int("mood.neutralHigh", 60))
                                        .put("judge", cfg.bool("mood.judge", true)),
                                )
                                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.patchConfig(api, body) } }.getOrNull()
                                note = if (r?.optBoolean("ok") == true) "存好了" else r?.optString("reason").orEmpty().ifBlank { "没存上" }
                                tick++
                            }
                        }) { Text("保存") }
                        TextButton(onClick = {
                            scope.launch {
                                val r = runCatching { withContext(Dispatchers.IO) { SettingsApi.resetMood(api) } }.getOrNull()
                                note = if (r?.optBoolean("ok") == true) "回到平常了" else r?.optString("reason").orEmpty().ifBlank { "没重置成" }
                                tick++
                            }
                        }) { Text("重置到平常") }
                    }
                }
            }
        }
        item {
            Panel("心情变化记录") {
                if (history.length() == 0) {
                    EmptyText("还没有记录。")
                } else {
                    for (i in 0 until minOf(history.length(), 60)) {
                        val h = history.optJSONObject(i) ?: continue
                        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(h.optString("reason").ifBlank { "（没写原因）" }, fontSize = 13.sp)
                            Text(
                                "${fmtStamp(h.optLong("at"))} · ${numStr(h.optDouble("from", 0.0))} → ${numStr(h.optDouble("to", 0.0))}",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodBar(value: Double, low: Double, high: Double) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val dot = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(14.dp).padding(vertical = 2.dp)) {
        val h = size.height
        val r = CornerRadius(h / 2f)
        drawRoundRect(color = track, size = Size(size.width, h), cornerRadius = r)
        val x1 = (size.width * (low / 100.0)).toFloat().coerceIn(0f, size.width)
        val x2 = (size.width * (high / 100.0)).toFloat().coerceIn(0f, size.width)
        drawRoundRect(color = bandColor, topLeft = Offset(x1, 0f), size = Size((x2 - x1).coerceAtLeast(2f), h), cornerRadius = r)
        val x = (size.width * (value / 100.0)).toFloat().coerceIn(0f, size.width)
        drawCircle(color = dot, radius = h * 1.15f, center = Offset(x, h / 2f))
    }
}

private fun round0(v: Double): String = v.toInt().toString()

private fun numStr(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.CHINA, "%.1f", v)
