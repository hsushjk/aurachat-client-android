@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.aurachat.client.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.aurachat.client.data.Prefs
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.ImageCache
import dev.aurachat.client.net.Live
import dev.aurachat.client.net.Media
import dev.aurachat.client.net.Message
import dev.aurachat.client.net.Recorder
import dev.aurachat.client.net.Sticker
import dev.aurachat.client.net.VoicePlayer
import dev.aurachat.client.net.decodeBytes
import dev.aurachat.client.net.decodeInline
import dev.aurachat.client.net.prepareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dayKey = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

/** 气泡最大 240dp，3x 屏下 720px 够用，再大纯占内存。 */
private const val THUMB_SIDE = 720

private const val BIG_SIDE = 2160

/** 当前播放的语音 id，跨气泡共享；切下一条时上一条自动复位。 */
private val playingVoiceId = mutableStateOf("")

private data class Pending(
    val key: String,
    val kind: String,
    val text: String,
    val dur: Int = 0,
    val sending: Boolean = true,
    val error: String = "",
)

private sealed interface Row {
    val key: String

    data class Sep(val label: String, override val key: String) : Row
    data class Item(val m: Message) : Row {
        override val key: String get() = "m:" + m.id
    }
    data class Fail(val p: Pending) : Row {
        override val key: String get() = "f:" + p.key
    }
}

@Composable
fun ChatScreen(api: AuraApi, prefs: Prefs, onLogout: () -> Unit) {
    val list = Live.messages
    var input by remember { mutableStateOf(prefs.draft.ifBlank { "" }) }
    var title by remember { mutableStateOf(prefs.name) }
    val online = Live.online
    val typing = Live.typing
    var typingSent by remember { mutableStateOf(false) }
    var showStickers by remember { mutableStateOf(false) }
    var stickers by remember { mutableStateOf<List<Sticker>>(emptyList()) }
    var note by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var unread by remember { mutableStateOf(0) }
    var atBottom by remember { mutableStateOf(true) }
    // 视口缩矮前先举旗；等缩完再判断 atBottom，最新几条已出视口，永远是 false
    var armedPin by remember { mutableStateOf(false) }
    val pending = remember { mutableStateListOf<Pending>() }
    val retries = remember { mutableStateMapOf<String, () -> JSONObject>() }
    val ctx = LocalContext.current
    val recorder = remember { Recorder(ctx) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        note = if (granted && recorder.start()) {
            recording = true
            ""
        } else {
            "不给麦克风权限就发不了语音"
        }
    }
    val state = rememberLazyListState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ui = Handler(Looper.getMainLooper())
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current

    // 前台不弹通知，退后台交给前台服务
    DisposableEffect(Unit) {
        Live.visible = true
        val arrive: (Message) -> Unit = { m ->
            if (m.who != "user" && !atBottom) unread += 1
        }
        Live.addArrival(arrive)
        onDispose {
            Live.visible = false
            Live.removeArrival(arrive)
            list.lastOrNull()?.let { prefs.lastRead = it.t }
            VoicePlayer.stop()
            playingVoiceId.value = ""
        }
    }

    LaunchedEffect(Unit) {
        Live.start(ctx, api)
        Live.reload() // 回界面补一次增量，不用等重连
        if (Live.botName.isNotBlank()) title = Live.botName
    }

    // 停手 0.5 秒才写草稿，别每敲一个字都落盘
    LaunchedEffect(input) {
        kotlinx.coroutines.delay(500)
        prefs.draft = input
    }

    val rows = remember(list.toList(), query, pending.toList()) { buildRows(list, query, pending) }

    // 首次进来落到底；之后只在本就在底部时才跟新消息，翻老记录不拽回底部
    var landed by remember { mutableStateOf(false) }
    val lastId = list.lastOrNull()?.id
    LaunchedEffect(lastId, rows.size) {
        if (rows.isEmpty() || searching) return@LaunchedEffect
        if (!landed) {
            // 图片/语音高度是异步量出来的，滚一次会停在半路，连滚几趟等布局稳住
            kotlinx.coroutines.delay(100)
            repeat(5) {
                state.scrollToItem(0)
                kotlinx.coroutines.delay(160)
            }
            landed = true
            return@LaunchedEffect
        }
        if (atBottom) state.scrollToItem(0)
    }

    // 底部可见 = 最小 index 出现在视口里（reverseLayout 里 0 在最底）
    LaunchedEffect(state) {
        snapshotFlow {
            val first = state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
            first <= 1
        }.collect { bottom ->
            atBottom = bottom
            if (bottom) unread = 0
        }
    }

    // 视口变矮（键盘/面板/底栏）时滚动偏移不会自己跟，最新几条会被顶出视口，得补一次钉底
    LaunchedEffect(state) {
        var prev = 0
        snapshotFlow { state.layoutInfo.viewportSize.height }.collect { h ->
            if (h > 0 && prev > 0 && h < prev - 4 && armedPin) {
                armedPin = false
                // 键盘/面板带动画长出来，滚一趟会被中途布局甩下，连滚几趟
                repeat(3) {
                    state.scrollToItem(0)
                    kotlinx.coroutines.delay(110)
                }
            }
            if (h > 0) prev = h
        }
    }

    // 每次开面板重拉表情，服务端增删不用重启客户端
    LaunchedEffect(showStickers) {
        if (!showStickers) return@LaunchedEffect
        val fresh = withContext(Dispatchers.IO) { runCatching { api.stickers() }.getOrNull() }
        if (fresh != null) stickers = fresh
    }


    fun track(kind: String, preview: String, dur: Int = 0, send: () -> JSONObject) {
        val key = "p" + System.nanoTime()
        pending.add(Pending(key, kind, preview, dur))
        retries[key] = send
        Thread {
            val r = runCatching { send() }.getOrNull()
            val ok = r?.optBoolean("ok") == true || r?.has("messages") == true
            ui.post {
                if (ok) {
                    pending.removeAll { it.key == key }
                    retries.remove(key)
                } else {
                    val reason = r?.optString("reason").orEmpty().ifBlank { "没网了吧" }
                    val i = pending.indexOfFirst { it.key == key }
                    if (i >= 0) pending[i] = pending[i].copy(sending = false, error = reason)
                }
            }
        }.apply { isDaemon = true }.start()
    }

    fun retry(p: Pending) {
        val f = retries[p.key] ?: return
        val i = pending.indexOfFirst { it.key == p.key }
        if (i >= 0) pending[i] = p.copy(sending = true, error = "")
        Thread {
            val r = runCatching { f() }.getOrNull()
            val ok = r?.optBoolean("ok") == true || r?.has("messages") == true
            ui.post {
                if (ok) {
                    pending.removeAll { it.key == p.key }
                    retries.remove(p.key)
                } else {
                    val i2 = pending.indexOfFirst { it.key == p.key }
                    if (i2 >= 0) pending[i2] = p.copy(sending = false, error = r?.optString("reason").orEmpty().ifBlank { "还是不行" })
                }
            }
        }.apply { isDaemon = true }.start()
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        note = "正在发图…"
        Thread {
            val ready = runCatching { prepareImage(ctx, uri) }.getOrNull()
            if (ready == null) {
                ui.post { note = "这张图读不出来" }
                return@Thread
            }
            ui.post {
                note = ""
                track("image", "[图片]") { api.sendImage(ready.first, ready.second) }
            }
        }.apply { isDaemon = true }.start()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (searching) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜文字 / 表情 / 图片说明") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { searching = false; query = "" }) {
                    Icon(Icons.Outlined.Close, contentDescription = "关闭搜索")
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Live.botName.ifBlank { title.ifBlank { "AuraChat" } },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (unread > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (unread > 99) "99+" else unread.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                    Text(
                        when {
                            recording -> "正在录音…再按一下发出去"
                            typing -> "对方正在输入…"
                            online -> "已连接"
                            else -> "连接断了，正在重试"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (recording) MaterialTheme.colorScheme.error else Color.Unspecified,
                    )
                }
                if (note.isNotEmpty()) {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(end = 4.dp))
                }
                IconButton(onClick = { searching = true }) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = state,
                // reverseLayout：0 在最底；旧消息往顶部追加，底部内容不位移，不用滚动锚定
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                // rows 是时间正序（旧→新），倒过来喂：index 0=最新在最底
                items(rows.asReversed(), key = { it.key }) { row ->
                    when (row) {
                        is Row.Sep -> DayDivider(row.label)
                        is Row.Item -> Bubble(api, row.m)
                        is Row.Fail -> FailBubble(row.p, onRetry = { retry(row.p) }, onDrop = {
                            pending.removeAll { it.key == row.p.key }
                            retries.remove(row.p.key)
                        })
                    }
                }
                // 「查看更早」放在最顶（reverseLayout 里是最后一项），滚到最顶才看得到
                if (!searching) {
                    item(key = "older") {
                        OlderHeader(hasMore = Live.hasMore, loading = Live.loadingOlder) {
                            Live.loadOlder()
                        }
                    }
                }
            }
            if (searching && rows.isEmpty()) {
                Text(
                    if (query.isBlank()) "输入关键词开始搜" else "没搜到",
                    Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                )
            }
            // 被翻到上面去时给个回到底部的入口，顺便吃掉未读数
            if ((!atBottom && rows.isNotEmpty()) || unread > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clickable {
                            unread = 0
                            atBottom = true
                            scope.launch { state.animateScrollToItem(0) }
                        },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ArrowDownward, contentDescription = "回到底部", modifier = Modifier.size(16.dp))
                        if (unread > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(if (unread > 99) "99+" else unread.toString(), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (showStickers) {
                    showStickers = false
                } else {
                    // 开面板前先收键盘，免得两个都占着
                    keyboard?.hide()
                    focus.clearFocus()
                    // 展开也会压矮视口，同样先举旗
                    armedPin = atBottom
                    showStickers = true
                }
            }) {
                Icon(
                    imageVector = if (showStickers) Icons.Outlined.Keyboard else Icons.Outlined.EmojiEmotions,
                    contentDescription = "表情",
                )
            }
            IconButton(onClick = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "发图")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { v ->
                    input = v
                    val on = v.isNotBlank()
                    if (on != typingSent) {
                        typingSent = on
                        Thread { runCatching { api.typing(on) } }.start()
                    }
                },
                placeholder = { Text("说点什么…") },
                modifier = Modifier.weight(1f).onFocusChanged {
                    if (it.isFocused) {
                        showStickers = false
                        // 键盘弹出会压矮视口，先举旗；等缩完再滚会被动画甩下，最新几条就漏在外面
                        armedPin = atBottom
                    }
                },
                maxLines = 4,
            )
            IconButton(onClick = {
                if (recording) {
                    val got = recorder.stopAndRead()
                    recording = false
                    if (got == null) {
                        note = "太短了，没发出去"
                    } else {
                        note = ""
                        track("voice", "[语音 ${got.second}s]", got.second) { api.sendVoice(got.first, "audio/wav", got.second) }
                    }
                } else if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    askMic.launch(Manifest.permission.RECORD_AUDIO)
                } else if (recorder.start()) {
                    recording = true
                    note = ""
                } else {
                    note = "麦克风打不开"
                }
            }) {
                Icon(if (recording) Icons.Outlined.Stop else Icons.Outlined.Mic, contentDescription = "语音")
            }
            IconButton(
                enabled = input.isNotBlank(),
                onClick = {
                    val t = input.trim()
                    input = ""
                    prefs.draft = ""
                    track("text", t) { api.send(t) }
                },
            ) { Text("发送") }
        }

        // 贴输入框往上长的一块，不是弹窗：点外面不会消、不挡聊天记录
        StickerPanel(
            visible = showStickers,
            api = api,
            stickers = stickers,
            onPick = { s ->
                track("sticker", "[表情] ${s.name}".trim()) { api.sendSticker(s.md5) }
            },
        )
    }
}

/** 按"天"插分隔条；搜索时只留命中的消息，也顺手把当天的分隔条留着。 */
private fun buildRows(list: List<Message>, query: String, pending: List<Pending>): List<Row> {
    val q = query.trim()
    val hit = if (q.isEmpty()) list else list.filter {
        it.text.contains(q, true) || it.name.contains(q, true) || it.caption.contains(q, true) ||
            (it.kind == "image" && "图片".contains(q)) || (it.kind == "voice" && "语音".contains(q))
    }
    val out = ArrayList<Row>(hit.size + 8)
    var lastDay = ""
    for (m in hit) {
        val d = dayKey.format(Date(if (m.t > 0) m.t else System.currentTimeMillis()))
        if (d != lastDay) {
            lastDay = d
            out.add(Row.Sep(dayLabel(m.t), "sep:" + d))
        }
        out.add(Row.Item(m))
    }
    if (q.isEmpty()) {
        pending.forEach { out.add(Row.Fail(it)) }
    }
    return out
}

private fun dayLabel(t: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = if (t > 0) t else now.timeInMillis }
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayMs = 24 * 60 * 60 * 1000L
    val today = now.clone() as Calendar
    today.set(Calendar.HOUR_OF_DAY, 0); today.set(Calendar.MINUTE, 0); today.set(Calendar.SECOND, 0); today.set(Calendar.MILLISECOND, 0)
    val todayStart = today.timeInMillis
    val tStart = then.timeInMillis - (then.get(Calendar.HOUR_OF_DAY) * 3600_000L + then.get(Calendar.MINUTE) * 60_000L + then.get(Calendar.SECOND) * 1000L + then.get(Calendar.MILLISECOND))
    return when {
        tStart >= todayStart -> "今天"
        tStart >= todayStart - dayMs -> "昨天"
        sameYear -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(then.timeInMillis))
        else -> SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(then.timeInMillis))
    }
}

@Composable
private fun DayDivider(label: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun FailBubble(p: Pending, onRetry: () -> Unit, onDrop: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(p.text, style = MaterialTheme.typography.bodyMedium)
            if (p.sending) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("发送中…", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            } else {
                Text(
                    p.error.ifBlank { "没发出去" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Row {
                    TextButton(onClick = onRetry) { Text("重试", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = onDrop) { Text("扔掉", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
}

/** 点一张就发，面板不收起。 */
@Composable
private fun StickerPanel(
    visible: Boolean,
    api: AuraApi,
    stickers: List<Sticker>,
    onPick: (Sticker) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            if (stickers.isEmpty()) {
                Text(
                    "表情库还是空的",
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(stickers, key = { it.md5 }) { s ->
                        StickerCell(api, s, Modifier.aspectRatio(1f)) { onPick(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerCell(api: AuraApi, s: Sticker, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val key = "sticker:" + s.md5
    var bmp by remember(key) { mutableStateOf(ImageCache.get(key)) }
    LaunchedEffect(key) {
        if (bmp != null) return@LaunchedEffect
        val b = withContext(Dispatchers.IO) { api.fetchBytes(api.stickerUrl(s.md5))?.let { decodeBytes(it, 360) } }
        if (b != null) {
            ImageCache.put(key, b)
            bmp = b
        }
    }
    Box(modifier.clip(RoundedCornerShape(8.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        bmp?.let { Image(it.asImageBitmap(), contentDescription = s.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp)) }
    }
}

@Composable
private fun LoadedBitmap(key: String, load: suspend () -> Bitmap?, modifier: Modifier) {
    var bmp by remember(key) { mutableStateOf(ImageCache.get(key)) }
    LaunchedEffect(key) {
        if (bmp != null) return@LaunchedEffect
        val b = withContext(Dispatchers.IO) { load() }
        if (b != null) {
            ImageCache.put(key, b)
            bmp = b
        }
    }
    bmp?.let {
        Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}

@Composable
private fun Bubble(api: AuraApi, m: Message) {
    val mine = m.who == "user"
    val rowMod = Modifier.fillMaxWidth()
    val arrangement = if (mine) Arrangement.End else Arrangement.Start
    val ctx = LocalContext.current
    var menu by remember(m.id) { mutableStateOf(false) }
    var tip by remember(m.id) { mutableStateOf("") }

    fun copyText() {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("AuraChat", m.text.ifBlank { m.caption }))
        tip = "已复制"
    }

    if (m.kind == "sticker" && m.sticker.isNotEmpty()) {
        Box {
            Row(rowMod, horizontalArrangement = arrangement) {
                LoadedBitmap(
                    key = "sticker:" + m.sticker,
                    load = {
                        api.fetchBytes(api.stickerUrl(m.sticker))?.let { decodeBytes(it, 360) }
                    },
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .combinedClickable(onClick = {}, onLongClick = { menu = true }),
                )
            }
            BubbleMenu(
                expanded = menu,
                onDismiss = { menu = false },
                onCopy = { menu = false; copyText() },
            )
        }
        return
    }

    if (m.kind == "image" && (m.mediaId.isNotEmpty() || m.image.isNotEmpty() || m.mediaExpired)) {
        var open by remember(m.id) { mutableStateOf(false) }
        // 列表只解 240dp 缩略图并缓存；之前每次滚回来按 1080 重解，滑动就卡
        var thumb by remember(m.id) { mutableStateOf<Bitmap?>(null) }
        var big by remember(m.id) { mutableStateOf<Bitmap?>(null) }
        var gone by remember(m.id) { mutableStateOf(m.mediaExpired) }
        LaunchedEffect(m.id) {
            if (m.mediaExpired) return@LaunchedEffect
            val key = "img:" + m.id
            ImageCache.get(key)?.let { thumb = it; return@LaunchedEffect }
            val b = withContext(Dispatchers.IO) {
                if (m.mediaId.isNotEmpty()) Media.bitmap(ctx, api, m.mediaId, THUMB_SIDE)
                else decodeInline(m.image, THUMB_SIDE)
            }
            if (b != null) ImageCache.put(key, b)
            thumb = b
            if (b == null) gone = true
        }
        // 点开大图时才解一张大的：文件已经在本地缓存里，不用再走网
        LaunchedEffect(open) {
            if (!open || big != null || m.mediaExpired) return@LaunchedEffect
            big = withContext(Dispatchers.IO) {
                if (m.mediaId.isNotEmpty()) Media.bitmap(ctx, api, m.mediaId, BIG_SIDE)
                else decodeInline(m.image, BIG_SIDE)
            }
        }
        Box {
            Row(rowMod, horizontalArrangement = arrangement) {
                when {
                    thumb != null -> Image(
                        bitmap = thumb!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .widthIn(min = 120.dp, max = 240.dp)
                            .heightIn(max = 240.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { open = true }, onLongClick = { menu = true }),
                    )
                    // 服务端按 keepDays 清过，媒体跟着没了
                    gone -> GoneBubble(mine)
                }
            }
            BubbleMenu(
                expanded = menu,
                onDismiss = { menu = false },
                onCopy = null,
                onSaveImage = thumb?.let { fallback ->
                    {
                        menu = false
                        Thread {
                            // 另存用大图，别把屏幕上的缩略图存进相册
                            val src = if (m.mediaId.isNotEmpty()) Media.bitmap(ctx, api, m.mediaId, BIG_SIDE)
                            else decodeInline(m.image, BIG_SIDE)
                            val ok = saveImage(ctx, src ?: fallback, "aurachat-" + m.id.take(8))
                            ui_tip(tip = { s -> tip = s }, ok)
                        }.apply { isDaemon = true }.start()
                    }
                },
            )
            if (tip.isNotEmpty()) {
                Text(tip, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(4.dp))
            }
        }
        if (open && thumb != null) {
            Dialog(
                onDismissRequest = { open = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xF20B0D10))
                        .clickable { open = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        bitmap = (big ?: thumb)!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        return
    }

    Row(rowMod, horizontalArrangement = arrangement) {
        val isPlaying = playingVoiceId.value == m.id
        var gone by remember(m.id) { mutableStateOf(m.mediaExpired) }
        var downloading by remember(m.id) { mutableStateOf(false) }
        var progress by remember(m.id) { mutableStateOf(0f) }
        var posSec by remember(m.id) { mutableStateOf(0) }
        // 只有还是自己时才复位，免得清掉下一条的状态
        val reset = {
            if (playingVoiceId.value == m.id) playingVoiceId.value = ""
            progress = 0f
            posSec = 0
            downloading = false
        }
        val onProgress = { cur: Int, total: Int ->
            progress = if (total > 0) (cur.toFloat() / total).coerceIn(0f, 1f) else 0f
            posSec = cur / 1000
        }
        // 语音得先下载到本地才能播，第一次点会等一下
        val onPlay = {
            when {
                isPlaying -> {
                    VoicePlayer.stop()
                    reset()
                }
                m.mediaId.isNotEmpty() -> {
                    playingVoiceId.value = m.id
                    progress = 0f
                    posSec = 0
                    if (Media.cached(ctx, m.mediaId) == null) downloading = true
                    Thread {
                        val f = Media.fetch(ctx, api, m.mediaId)
                        if (f == null) {
                            gone = true
                            reset()
                        } else {
                            downloading = false
                            if (!VoicePlayer.playFile(f, onDone = reset, onProgress = onProgress)) {
                                reset()
                            }
                        }
                    }.apply { isDaemon = true }.start()
                }
                m.voice.isNotEmpty() -> {
                    playingVoiceId.value = m.id
                    progress = 0f
                    posSec = 0
                    VoicePlayer.play(ctx, m.voice, onDone = reset, onProgress = onProgress)
                }
                else -> gone = true
            }
        }
        Column(
            Modifier
                // 气泡跟内容走，别把短消息抻成整行
                .then(
                    if (m.kind == "voice") Modifier.widthIn(min = 130.dp, max = 260.dp)
                    else Modifier.widthIn(max = 300.dp),
                )
                .background(
                    if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                )
                .then(
                    if (m.kind == "voice") {
                        Modifier.combinedClickable(onClick = { onPlay() }, onLongClick = { menu = true })
                    } else {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { menu = true },
                        )
                    },
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            when (m.kind) {
                "sticker" -> Text(m.caption.ifBlank { m.name.ifBlank { "[表情]" } })
                "image" -> Text(if (mine) "[图片]" else "[Ta 发来一张图]")
                "voice" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    VoiceBars(progress = if (isPlaying) progress else 0f, playing = isPlaying)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            gone -> "语音已过期"
                            downloading -> "下载中…"
                            isPlaying -> "${posSec.coerceAtLeast(0)}″ / ${m.dur}″"
                            m.dur > 0 -> "${m.dur}″"
                            else -> "语音"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                else -> Text(m.text)
            }
            Text(
                clock.format(Date(if (m.t > 0) m.t else System.currentTimeMillis())),
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.End),
            )
            if (tip.isNotEmpty()) {
                Text(tip, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
        Box {
            BubbleMenu(
                expanded = menu,
                onDismiss = { menu = false },
                onCopy = if (m.kind == "text") {
                    { menu = false; copyText() }
                } else null,
                onSaveVoice = if (m.kind == "voice") {
                    {
                        menu = false
                        Thread {
                            val f = when {
                                m.mediaId.isNotEmpty() -> Media.fetch(ctx, api, m.mediaId)
                                m.voice.isNotEmpty() -> writeTempWav(ctx, m.voice, m.id)
                                else -> null
                            }
                            val ok = f != null && saveFile(ctx, f, "aurachat-" + m.id.take(8) + ".wav", "audio/wav")
                            ui_tip(tip = { s -> tip = s }, ok)
                        }.apply { isDaemon = true }.start()
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun BubbleMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: (() -> Unit)? = null,
    onSaveImage: (() -> Unit)? = null,
    onSaveVoice: (() -> Unit)? = null,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onCopy != null) DropdownMenuItem(text = { Text("复制文字") }, onClick = onCopy)
        if (onSaveImage != null) DropdownMenuItem(text = { Text("保存图片") }, onClick = onSaveImage)
        if (onSaveVoice != null) DropdownMenuItem(text = { Text("保存语音") }, onClick = onSaveVoice)
        if (onCopy == null && onSaveImage == null && onSaveVoice == null) {
            DropdownMenuItem(text = { Text("长按没有可用操作") }, onClick = onDismiss, enabled = false)
        }
    }
}

/** 后台线程改提示，统一抛回主线程。 */
private fun ui_tip(tip: (String) -> Unit, ok: Boolean) {
    Handler(Looper.getMainLooper()).post { tip(if (ok) "已保存" else "保存失败") }
}

private fun saveImage(ctx: Context, bmp: Bitmap, name: String): Boolean = runCatching {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AuraChat")
        }
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: return false
    true
}.getOrDefault(false)

/** 语音存到 Music/AuraChat；P 以下没有 RELATIVE_PATH，直接写公共目录。 */
private fun saveFile(ctx: Context, src: File, name: String, mime: String): Boolean = runCatching {
    if (Build.VERSION.SDK_INT >= 29) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/AuraChat")
            put(MediaStore.Audio.Media.IS_MUSIC, true)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use { out -> src.inputStream().use { it.copyTo(out) } } ?: return false
        true
    } else {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "AuraChat").apply { mkdirs() }
        src.copyTo(File(dir, name), overwrite = true)
        true
    }
}.getOrDefault(false)

/** 老协议的内联语音：临时落个文件再统一走"保存"。 */
private fun writeTempWav(ctx: Context, b64: String, id: String): File? = runCatching {
    val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
    File(ctx.cacheDir, "voice-" + id.take(8) + ".wav").apply { writeBytes(raw) }
}.getOrNull()

@Composable
private fun VoiceBars(progress: Float, playing: Boolean) {
    val heights = listOf(6, 12, 18, 10, 15, 8)
    val lit = (progress.coerceIn(0f, 1f) * heights.size).toInt()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        heights.forEachIndexed { i, h ->
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (playing && i < lit) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    ),
            )
        }
    }
}

@Composable
private fun GoneBubble(mine: Boolean) {
    Text(
        if (mine) "[图片] 已过期" else "[图片已过期]",
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Gray,
        modifier = Modifier
            .background(
                if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun OlderHeader(hasMore: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            hasMore -> TextButton(onClick = onClick) {
                Text("查看更早的记录", style = MaterialTheme.typography.bodySmall)
            }
            else -> Text("没有更早的记录", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

object Notifier {
    fun ring(any: Any?) = Unit
}
