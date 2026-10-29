package dev.aurachat.client.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.aurachat.client.data.Prefs
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.SettingsApi
import dev.aurachat.client.net.VoicePlayer
import dev.aurachat.client.net.prepareImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File


/** 接口统一回 {ok, reason|message}，失败先看 reason。 */
internal fun msgOf(r: JSONObject?): String = when {
    r == null -> "没连上服务端"
    r.optBoolean("ok") -> r.optString("message").ifBlank { "好了" }
    else -> r.optString("reason").ifBlank { r.optString("message").ifBlank { "没成" } }
}

@Composable
internal fun LoadJson(key: String, loader: suspend () -> JSONObject?, content: @Composable (JSONObject) -> Unit) {
    var j by remember(key) { mutableStateOf<JSONObject?>(null) }
    var err by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        val r = runCatching { loader() }
        j = r.getOrNull()
        if (r.isFailure) err = r.exceptionOrNull()?.message ?: "拉不到数据"
    }
    when {
        err != null -> Err(err!!)
        j == null -> LoadingRow()
        else -> content(j!!)
    }
}

private const val NEW_PERSONA = "__new__"
private val EDGE_VOICES = listOf(
    "zh-CN-XiaoxiaoNeural" to "晓晓 · 温暖女声",
    "zh-CN-XiaoyiNeural" to "晓伊 · 活泼女声",
    "zh-CN-XiaochenNeural" to "晓辰 · 清亮女声",
    "zh-CN-XiaohanNeural" to "晓涵 · 知性女声",
    "zh-CN-XiaomengNeural" to "晓梦 · 少女",
    "zh-CN-XiaomoNeural" to "晓墨 · 低柔女声",
    "zh-CN-YunxiNeural" to "云希 · 少年",
    "zh-CN-YunjianNeural" to "云健 · 浑厚男声",
    "zh-CN-YunxiaNeural" to "云夏 · 少年",
    "zh-CN-YunyangNeural" to "云扬 · 播报男声",
    "zh-CN-liaoning-XiaobeiNeural" to "晓北 · 东北女声",
    "zh-CN-shaanxi-XiaoniNeural" to "晓妮 · 陕西女声",
    "zh-HK-HiuMaanNeural" to "曉曼 · 粤语女声",
    "zh-HK-WanLungNeural" to "雲龍 · 粤语男声",
    "zh-TW-HsiaoChenNeural" to "曉臻 · 台湾女声",
    "zh-TW-YunJheNeural" to "雲哲 · 台湾男声",
)


@Composable
internal fun MasterSection(cfg: CfgState) {
    Panel("总开关") {
        Toggle("总开关", cfg.bool("enabled", true), hint = "停用所有功能：不回消息、不主动搭话、不写动态、不自驱", onChange = { cfg.set("enabled", it) })
    }
}


@Composable
internal fun IdentitySection(api: AuraApi, cfg: CfgState, onNote: (String) -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var editor by remember { mutableStateOf<String?>(null) }

    Panel("身份") {
        CfgText("名字", "botName", cfg, hint = "Ta的名字，一般跟着人设走")
        CfgText("个性签名", "botTag", cfg, hint = "可留空；显示在聊天页名字后面")
        CfgText("怎么称呼我", "ownerName", cfg, hint = "Ta对你的称呼")
        LoadJson("personas-$tick", loader = { withContext(Dispatchers.IO) { runCatching { SettingsApi.personas(api) }.getOrNull() } }) { j ->
            val arr = j.optJSONArray("personas") ?: JSONArray()
            val opts = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                opts.add(Pair(p.optString("id"), p.optString("name")))
            }
            opts.add(Pair(NEW_PERSONA, "＋ 新建一个人设…"))
            PickRow("人设", cfg.str("persona.current"), opts) { id ->
                if (id == NEW_PERSONA) editor = "" else cfg.set("persona.current", id)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { editor = cfg.str("persona.current") }) { Text("编辑当前人设", fontSize = 13.sp) }
        }
        Spacer(Modifier.height(4.dp))
        EntryImageRow(
            label = "头像",
            round = true,
            note = onNote,
            loadBytes = { SettingsApi.avatarBytes(api) },
            upload = { (b64, mime) -> msgOf(SettingsApi.uploadAvatar(api, b64, mime)) },
            clear = { msgOf(SettingsApi.uploadAvatar(api, "", "")) },
        )
    }

    val ed = editor
    if (ed != null) {
        PersonaEditorDialog(
            api = api,
            id = ed,
            onDismiss = { editor = null },
            onSaved = { newId, msg ->
                editor = null
                onNote(msg)
                if (newId.isNotBlank()) cfg.set("persona.current", newId)
                tick++
            },
        )
    }
}

@Composable
private fun PersonaEditorDialog(api: AuraApi, id: String, onDismiss: () -> Unit, onSaved: (String, String) -> Unit) {
    var name by remember(id) { mutableStateOf("") }
    var tags by remember(id) { mutableStateOf("") }
    var body by remember(id) { mutableStateOf("") }
    var loaded by remember(id) { mutableStateOf(id.isBlank()) }
    var note by remember(id) { mutableStateOf("") }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(id) {
        if (id.isNotBlank()) {
            val d = withContext(Dispatchers.IO) { runCatching { SettingsApi.personaDetail(api, id) }.getOrNull() }
                ?.optJSONObject("persona")
            if (d != null) {
                name = d.optString("name")
                val tg = d.optJSONArray("tags")
                tags = if (tg == null) "" else (0 until tg.length()).joinToString(",") { tg.optString(it) }
                body = d.optString("body")
            }
            loaded = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (id.isBlank()) "新建一个人设" else "改人设") },
        text = {
            if (!loaded) {
                LoadingRow()
            } else {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("名字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("标签（逗号隔开）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        body,
                        { body = it },
                        label = { Text("人设正文") },
                        minLines = 5,
                        maxLines = 9,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (note.isNotBlank()) Hint(note)
                    if (id.isNotBlank()) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("删掉这个人设", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = loaded && name.isNotBlank(), onClick = {
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { SettingsApi.personaSave(api, id, name.trim(), tags.trim(), body) }.getOrNull()
                    }
                    if (r?.optBoolean("ok") == true) {
                        onSaved(r.optJSONObject("persona")?.optString("id") ?: id, "人设存好了")
                    } else {
                        note = msgOf(r)
                    }
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("算了") } },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删掉这个人设？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { SettingsApi.personaDelete(api, id) }.getOrNull() }
                        if (r?.optBoolean("ok") == true) onSaved("", "删掉了") else note = msgOf(r)
                    }
                }) { Text("删掉") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("算了") } },
        )
    }
}


@Composable
internal fun AppearanceSection(cfg: CfgState, theme: String, onTheme: (String) -> Unit, onNote: (String) -> Unit, showBackground: Boolean = true) {
    val ctx = LocalContext.current
    Panel("外观") {
        PickRow("主题", theme, ThemeMode.entries.map { it.key to it.label }) { onTheme(it) }
        Hint("仅 亚克力/毛玻璃 支持背景图")
        if (showBackground) {
            val file = File(ctx.filesDir, "backdrop.jpg")
            EntryImageRow(
                label = "背景图",
                round = false,
                note = onNote,
                loadBytes = { file.takeIf { it.exists() && it.length() > 0 }?.readBytes() },
                upload = { (b64, _) ->
                    file.writeBytes(android.util.Base64.decode(b64, android.util.Base64.DEFAULT))
                    Backdrop.reload(ctx)
                    "存好了"
                },
                clear = {
                    file.delete()
                    Backdrop.reload(ctx)
                    "已清除"
                },
            )
        }
    }
}

/** 头像走服务端，背景图只存本机。 */
@Composable
private fun EntryImageRow(
    label: String,
    round: Boolean,
    note: (String) -> Unit,
    loadBytes: suspend () -> ByteArray?,
    upload: suspend (prepared: Pair<String, String>) -> String,
    clear: suspend () -> String,
) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var bytes by remember(tick) { mutableStateOf<ByteArray?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick) {
        bytes = withContext(Dispatchers.IO) { runCatching { loadBytes() }.getOrNull() }
    }

    fun doUpload(uri: android.net.Uri) {
        scope.launch {
            note("上传中…")
            val prepared = withContext(Dispatchers.IO) { runCatching { prepareImage(ctx, uri) }.getOrNull() }
            if (prepared == null) {
                note("这张图读不出来")
            } else {
                val msg = withContext(Dispatchers.IO) { runCatching { upload(prepared) }.getOrNull() }
                note(msg ?: "没存上")
                tick++
            }
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) doUpload(uri)
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        val shape = if (round) CircleShape else RoundedCornerShape(10.dp)
        val bmp = bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() }
        Box(
            Modifier
                .then(if (round) Modifier.size(56.dp) else Modifier.width(84.dp).height(56.dp))
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(bmp.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(if (round) "选张图" else "上传", fontSize = 13.sp)
            }
            TextButton(onClick = {
                scope.launch {
                    val msg = withContext(Dispatchers.IO) { runCatching { clear() }.getOrNull() }
                    note(msg ?: "好了")
                    tick++
                }
            }) { Text(if (round) "用默认图标" else "清除", fontSize = 13.sp) }
        }
    }
}


@Composable
internal fun ModelSection(api: AuraApi, cfg: CfgState) {
    Panel("模型") {
        LoadJson("models", loader = { withContext(Dispatchers.IO) { runCatching { SettingsApi.models(api) }.getOrNull() } }) { j ->
            val arr = j.optJSONArray("models") ?: JSONArray()
            val opts = mutableListOf("" to "默认（跟随 DSH）")
            for (i in 0 until arr.length()) {
                val m = arr.optJSONObject(i) ?: continue
                val prov = m.optString("provider")
                val id = m.optString("model")
                opts += (prov + "/" + id) to (m.optString("name").ifBlank { id } + " · " + prov)
            }
            val prov = cfg.str("model.provider")
            val cur = if (prov.isBlank()) "" else prov + "/" + cfg.str("model.model")
            PickRow("模型", cur, opts) { v ->
                if (v.isBlank()) {
                    cfg.set("model.provider", "")
                    cfg.set("model.model", "")
                } else {
                    val i = v.indexOf('/')
                    if (i > 0) {
                        cfg.set("model.provider", v.substring(0, i))
                        cfg.set("model.model", v.substring(i + 1))
                    }
                }
            }
        }
        PickRow(
            "思考强度",
            cfg.str("model.reasoningEffort", "low"),
            listOf("off" to "关", "low" to "低", "medium" to "中", "high" to "高"),
        ) { cfg.set("model.reasoningEffort", it) }
    }
}


@Composable
internal fun ReplySection(cfg: CfgState) {
    Panel("回复节奏") {
        Toggle("连发合并", cfg.bool("merge.enabled", true), onChange = { cfg.set("merge.enabled", it) })
        CfgText("停顿几秒再回", "merge.waitSec", cfg, numeric = true, decimals = true)
        CfgText("最多等几秒", "merge.maxSec", cfg, numeric = true, decimals = true)
        CfgText("你在打字时先等几秒", "merge.waitTypingSec", cfg, numeric = true)
    }
}


@Composable
internal fun DiarySection(cfg: CfgState) {
    Panel("空间日记") {
        Toggle("每天写", cfg.bool("diary.enabled", true), onChange = { cfg.set("diary.enabled", it) })
        CfgText("每天几篇", "diary.timesPerDay", cfg, numeric = true)
        CfgText("时间段开始", "diary.windowStart", cfg)
        CfgText("时间段结束", "diary.windowEnd", cfg)
        CfgText("两篇至少隔（分钟）", "diary.minGapMinutes", cfg, numeric = true)
        CfgText("最少字数", "diary.minChars", cfg, numeric = true)
        CfgText("最多字数", "diary.maxChars", cfg, numeric = true)
        PickRow("谁能看", cfg.str("diary.visibility", "all"), listOf("all" to "任何人", "self" to "仅主人")) {
            cfg.set("diary.visibility", it)
        }
    }
}


@Composable
internal fun VoiceSection(api: AuraApi, cfg: CfgState, onNote: (String) -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("晚安啦，今天也辛苦了，明天见～") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val provider = cfg.str("voice.provider", "auto")

    Panel("语音（实验性）") {
        Toggle("发语音（TTS）", cfg.bool("voice.enabled"), onChange = { cfg.set("voice.enabled", it) })
        PickRow(
            "引擎",
            provider,
            listOf(
                "auto" to "自动",
                "edge" to "微软 edge-tts",
                "cosyvoice" to "本地 CosyVoice",
                "gptsovits" to "本地 GPT-SoVITS",
                "openai" to "OpenAI 兼容",
                "http" to "其他 HTTP 接口",
                "windows" to "Windows 自带",
            ),
        ) { cfg.set("voice.provider", it) }

        when (provider) {
            "auto", "edge" -> {
                var edgeTick by remember { mutableStateOf(0) }
                LoadJson("edge-$edgeTick", loader = {
                    withContext(Dispatchers.IO) { runCatching { SettingsApi.edgeInstalled(api) }.getOrNull() }
                }) { j ->
                    val installed = j?.optBoolean("installed") == true
                    InfoRow("edge-tts 状态", if (installed) "已经装好了" else "还没装")
                    if (!installed) {
                        TextButton(onClick = {
                            scope.launch {
                                busy = true
                                val r = withContext(Dispatchers.IO) { runCatching { SettingsApi.edgeInstall(api) }.getOrNull() }
                                busy = false
                                note = if (r?.optBoolean("ok") == true) "装好了" else (r?.optString("reason").orEmpty().ifBlank { "没装上" })
                                onNote(note)
                                edgeTick++
                            }
                        }) { Text("安装", fontSize = 13.sp) }
                    }
                }
                PickRow("音色", cfg.str("voice.voice"), EDGE_VOICES) { cfg.set("voice.voice", it) }
            }
            "windows" -> CfgText("音色", "voice.voice", cfg, hint = "Windows 里装的语音名，例如 Microsoft Huihui Desktop")
        }
        if (provider == "auto" || provider == "edge" || provider == "windows") {
            PickRow("语速", cfg.str("voice.rate", "+0%"), listOf("-30%" to "-30%", "-15%" to "-15%", "+0%" to "+0%", "+15%" to "+15%", "+30%" to "+30%")) {
                cfg.set("voice.rate", it)
            }
        }
        SliderRow(
            "短回复变语音的概率",
            cfg.dbl("voice.autoChance", 0.15).toFloat(),
            fmt = { pct(it.toDouble()) },
        ) { cfg.set("voice.autoChance", it.toDouble()) }
        CfgText("多短算短回复", "voice.autoMaxChars", cfg, numeric = true)

        when (provider) {
            "cosyvoice" -> {
                CfgText("服务地址", "voice.cosyvoice.url", cfg, hint = "CosyVoice 自带 runtime/python/fastapi/server.py")
                PickRow("模式", cfg.str("voice.cosyvoice.mode", "sft"), listOf(
                    "sft" to "预置音色（sft）",
                    "instruct" to "自然语言控制（instruct）",
                    "zero_shot" to "零样本克隆（zero_shot）",
                    "cross_lingual" to "跨语种（cross_lingual）",
                )) { cfg.set("voice.cosyvoice.mode", it) }
                CfgText("音色 ID", "voice.cosyvoice.spkId", cfg, hint = "例如 中文女、中文男")
                PickRow("采样率", cfg.str("voice.cosyvoice.sampleRate", "22050"), listOf(
                    "22050" to "22050 Hz", "24000" to "24000 Hz", "16000" to "16000 Hz",
                )) { cfg.set("voice.cosyvoice.sampleRate", it.toIntOrNull() ?: 22050) }
                CfgText("控制指令", "voice.cosyvoice.instruct", cfg, hint = "instruct 模式下用，例如「用四川话说」")
            }
            "gptsovits" -> {
                CfgText("服务地址", "voice.gptsovits.url", cfg)
                CfgText("参考音频", "voice.gptsovits.refAudio", cfg)
                CfgText("提示文本", "voice.gptsovits.promptText", cfg)
            }
            "openai" -> {
                CfgText("接口地址", "voice.ttsBaseUrl", cfg)
                CfgText("密钥", "voice.ttsApiKey", cfg, secret = true, hint = "留空就用服务端已存的")
                CfgText("模型", "voice.ttsModel", cfg)
                CfgText("音色", "voice.ttsVoice", cfg)
            }
            "http" -> CfgText("地址模板", "voice.http.urlTemplate", cfg, hint = "用 {text} 占位")
        }

        OutlinedTextField(
            text,
            { text = it },
            label = { Text("试听") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
        TextButton(enabled = !busy && text.isNotBlank(), onClick = {
            scope.launch {
                busy = true
                note = "合成中…"
                val r = withContext(Dispatchers.IO) { runCatching { SettingsApi.tts(api, text.trim()) }.getOrNull() }
                busy = false
                if (r?.optBoolean("ok") == true && r.optString("data").isNotBlank()) {
                    note = "试听一下"
                    VoicePlayer.play(ctx, r.optString("data"))
                } else {
                    note = r?.optString("reason").orEmpty().ifBlank { "合成不出来" }
                }
                onNote(note)
            }
        }) { Text("生成", fontSize = 13.sp) }
        Hint("先保存设置再试听；试听不影响真正发消息的开关")
        Hint(note)
    }
}


@Composable
internal fun SttSection(api: AuraApi, cfg: CfgState) {
    Panel("听语音（实验性）") {
        Toggle("听语音（STT）", cfg.bool("voice.asr"), onChange = { cfg.set("voice.asr", it) })
        Hint("识别走 SenseVoice（dsh 自带），本地推理，不用填地址，也不用联网；只在 DSH 桌面版里可用。")
        LoadJson("asr-status", loader = { withContext(Dispatchers.IO) { runCatching { SettingsApi.status(api) }.getOrNull() } }) { j ->
            val a = j.optJSONObject("asr")
            val state = when {
                a == null || !a.optBoolean("host", false) -> "宿主没有提供语音识别（桌面版要启用语音输入）"
                else -> {
                    val arr = a.optJSONArray("providers")
                    if (arr != null && arr.length() > 0) {
                        "可用：" + (0 until arr.length()).joinToString("、") { arr.optString(it) }
                    } else "宿主有服务，但识别器还没注册"
                }
            }
            InfoRow("识别服务", state)
        }
        CfgText("识别语言", "voice.asrLanguage", cfg, hint = "zh / en，留空自动")
    }
}


@Composable
internal fun ImageGenSection(cfg: CfgState) {
    val provider = cfg.str("imageGen.provider", "openai")
    Panel("生图（实验性）") {
        Toggle("允许画图", cfg.bool("imageGen.enabled"), onChange = { cfg.set("imageGen.enabled", it) })
        PickRow(
            "引擎",
            provider,
            listOf(
                "openai" to "OpenAI 兼容",
                "sdwebui" to "SD WebUI",
                "comfyui" to "ComfyUI",
                "code" to "让 Ta 写代码画",
            ),
        ) { cfg.set("imageGen.provider", it) }
        if (provider != "code") {
            CfgText("地址", "imageGen.baseUrl", cfg, hint = "例如 http://127.0.0.1:7860")
            if (provider == "openai") CfgText("密钥", "imageGen.apiKey", cfg, secret = true, hint = "本地服务一般不用填")
            CfgText("模型 / 检查点", "imageGen.model", cfg, hint = "SD WebUI 可留空，ComfyUI 填 ckpt 名")
        }
        if (provider == "openai" || provider == "sdwebui") CfgText("画面尺寸", "imageGen.size", cfg, hint = "例如 1024x1024")
        if (provider != "code") CfgText("风格补充", "imageGen.stylePrompt", cfg, multiline = true, hint = "每次画图都会拼在提示词前面")
        if (provider == "sdwebui" || provider == "comfyui") CfgText("步数", "imageGen.steps", cfg, numeric = true)
        if (provider == "sdwebui") {
            CfgText("CFG Scale", "imageGen.cfgScale", cfg, numeric = true, decimals = true)
            CfgText("采样器", "imageGen.sampler", cfg, hint = "例如 DPM++ 2M Karras")
        }
        if (provider != "code") CfgText("负面提示词", "imageGen.negativePrompt", cfg, multiline = true, hint = "不想让Ta画出来的东西")
        if (provider == "code") Hint("代码画：Ta 会直接写一段绘图代码把图画出来，不用连接任何服务。")
    }
}


@Composable
internal fun StickerSection(api: AuraApi, cfg: CfgState, onNote: (String) -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var confirmMd5 by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                note = "上传中…"
                for (uri in uris) {
                    val prepared = withContext(Dispatchers.IO) { runCatching { prepareImage(ctx, uri) }.getOrNull() } ?: continue
                    withContext(Dispatchers.IO) { runCatching { SettingsApi.uploadSticker(api, prepared.first, "") } }
                }
                note = "传完了"
                onNote(note)
                tick++
            }
        }
    }

    Panel("表情库") {
        Toggle("自动配表情", cfg.bool("stickers.enabled", true), onChange = { cfg.set("stickers.enabled", it) })
        SliderRow(
            "自动配表情的概率",
            cfg.dbl("stickers.autoChance", 0.35).toFloat(),
            min = 0.1f,
            max = 1f,
            hint = "每次回完，按这个概率补一张表情",
            fmt = { pct(it.toDouble()) },
        ) { cfg.set("stickers.autoChance", it.toDouble()) }
        Toggle("偷表情", cfg.bool("stickers.steal", true), hint = "Ta收到你发的图/表情，会自己挑着收进库") {
            cfg.set("stickers.steal", it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text("选图（可多选）", fontSize = 13.sp)
            }
            IconButton(onClick = { tick++ }) { Icon(Icons.Outlined.Refresh, contentDescription = "刷新") }
        }
        Hint("支持动图；点一下缩略图可以改名或删掉")
        LoadJson("stickers-$tick", loader = { withContext(Dispatchers.IO) { runCatching { SettingsApi.stickers(api) }.getOrNull() } }) { j ->
            val items = j.optJSONArray("items") ?: JSONArray()
            Text("${items.length()} 张", fontSize = 12.sp)
            var row = 0
            val shown = minOf(items.length(), 60)
            while (row * 5 < shown) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0 until 5) {
                        val idx = row * 5 + col
                        if (idx >= shown) break
                        val s = items.optJSONObject(idx) ?: continue
                        val md5 = s.optString("md5")
                        var bmp by remember(md5) { mutableStateOf<android.graphics.Bitmap?>(null) }
                        LaunchedEffect(md5) {
                            val bytes = withContext(Dispatchers.IO) { runCatching { api.fetchBytes("api/sticker/" + md5) }.getOrNull() }
                            if (bytes != null) bmp = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                        }
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .clickable { editing = s },
                        ) {
                            bmp?.let { Image(it.asImageBitmap(), null, modifier = Modifier.fillMaxWidth()) }
                        }
                    }
                }
                row++
            }
        }
        Hint(note)
    }

    val ed = editing
    if (ed != null) {
        val md5 = ed.optString("md5")
        var name by remember(md5) { mutableStateOf(ed.optString("name")) }
        val tg = ed.optJSONArray("tags")
        var tags by remember(md5) { mutableStateOf(if (tg == null) "" else (0 until tg.length()).joinToString(",") { tg.optString(it) }) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("这张表情") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("名字") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(tags, { tags = it }, label = { Text("标签（逗号隔开）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = null
                    scope.launch {
                        val list = tags.split(',', '，', ' ').map { it.trim() }.filter { it.isNotBlank() }
                        val r = withContext(Dispatchers.IO) {
                            runCatching { SettingsApi.renameSticker(api, md5, name.trim(), list) }.getOrNull()
                        }
                        note = msgOf(r)
                        onNote(note)
                        tick++
                    }
                }) { Text("存下") }
            },
            dismissButton = {
                TextButton(onClick = { confirmMd5 = md5; editing = null }) {
                    Text("删掉", color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }

    if (confirmMd5.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirmMd5 = "" },
            title = { Text("删掉这张表情？") },
            confirmButton = {
                TextButton(onClick = {
                    val md5 = confirmMd5
                    confirmMd5 = ""
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { SettingsApi.deleteSticker(api, md5) } }
                        note = "删掉了"
                        tick++
                    }
                }) { Text("删掉") }
            },
            dismissButton = { TextButton(onClick = { confirmMd5 = "" }) { Text("算了") } },
        )
    }
}


@Composable
internal fun HistorySection(cfg: CfgState) {
    Panel("聊天记录") {
        CfgText("保留天数", "history.keepDays", cfg, numeric = true, hint = "聊天记录、图片、语音一起按天清理")
        Hint(
            "聊完（空闲 2 分钟）后按每 100 条自动切成一个文件，一个文件就是界面的一页；" +
                "翻到顶可以点「查看更早的记录」取上一页。图片和语音单独存放，过期后服务器和浏览器缓存一起清。",
        )
    }
}


@Composable
internal fun LogsCfgSection(cfg: CfgState) {
    Panel("日志") {
        CfgText("占用空间（MB）", "logs.maxMB", cfg, numeric = true, decimals = true, hint = "日志不落盘")
    }
}


@Composable
internal fun SpeakUpSection(cfg: CfgState) {
    Panel("闲置搭话") {
        Toggle("开启", cfg.bool("speakUp.enabled", true), hint = "你一阵子没说话，Ta会自己开口") {
            cfg.set("speakUp.enabled", it)
        }
        CfgText("闲置搭话（分钟）", "speakUp.idleMinutes", cfg, numeric = true, hint = "多久没说话就让Ta开口，从你最后一条消息算起")
        CfgText("两次至少隔（分钟）", "speakUp.minGapMinutes", cfg, numeric = true)
        CfgText("每天最多几次", "speakUp.maxPerDay", cfg, numeric = true)
        CfgText("时间段开始", "speakUp.quietFrom", cfg)
        CfgText("时间段结束", "speakUp.quietTo", cfg)
    }
}


@Composable
internal fun SelfDriveSection(api: AuraApi, cfg: CfgState, onNote: (String) -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var text by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Panel("自驱") {
        Toggle("开启", cfg.bool("selfDrive.enabled"), onChange = { cfg.set("selfDrive.enabled", it) })
        CfgText("多久看一次（分钟）", "selfDrive.intervalMinutes", cfg, numeric = true)
        CfgText("每天最多几次", "selfDrive.maxPerDay", cfg, numeric = true)
        CfgText("时间段开始", "selfDrive.quietFrom", cfg)
        CfgText("时间段结束", "selfDrive.quietTo", cfg)
        LoadJson("drive-$tick", loader = { withContext(Dispatchers.IO) { runCatching { SettingsApi.selfDrive(api) }.getOrNull() } }) { j ->
            val cur = text ?: j.optString("text")
            OutlinedTextField(
                cur,
                { text = it },
                label = { Text("共享便签（Ta没事干的时候会做什么）") },
                minLines = 4,
                maxLines = 7,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { SettingsApi.setSelfDrive(api, text ?: j.optString("text")) }.getOrNull()
                        }
                        note = msgOf(r)
                        onNote(note)
                        tick++
                    }
                }) { Text("保存便签", fontSize = 13.sp) }
                TextButton(onClick = { text = null; tick++ }) { Text("恢复", fontSize = 13.sp) }
            }
            Hint(note)
        }
    }
}


@Composable
internal fun WebSection(cfg: CfgState) {
    Panel("网页入口") {
        CfgText("监听地址", "web.host", cfg)
        CfgText("监听端口", "web.port", cfg, numeric = true)
    }
}


@Composable
internal fun GuestSection(api: AuraApi, onNote: (String) -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var username by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("social") }
    var note by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var resetFor by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Panel("客人") {
        LoadJson("guests-$tick", loader = { withContext(Dispatchers.IO) { runCatching { SettingsApi.guests(api) }.getOrNull() } }) { j ->
            val list = j.optJSONArray("guests") ?: JSONArray()
            if (list.length() == 0) Text("还没有客人", fontSize = 12.sp)
            for (k in 0 until list.length()) {
                val g = list.optJSONObject(k) ?: continue
                val subject = g.optString("subject")
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${g.optString("displayName").ifBlank { g.optString("username") }}（${g.optString("username")}）", fontSize = 13.sp)
                        Text(
                            (if (g.optString("level") == "full") "完全权限" else "只聊天日记") + g.optString("note").let { if (it.isBlank()) "" else " · $it" },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        resetFor = subject
                        newPw = ""
                    }) { Text("重置密码", fontSize = 11.sp) }
                    TextButton(onClick = { confirm = subject }) { Text("删除", fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(username, { username = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(display, { display = it }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            PickRow("权限", level, listOf("social" to "只聊天日记", "full" to "完全权限")) { level = it }
            TextButton(onClick = {
                val u = username.trim()
                if (u.isNotEmpty()) {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { SettingsApi.addGuest(api, u, display.trim().ifBlank { u }, "", level) }.getOrNull()
                        }
                        note = msgOf(r) + (r?.optString("password")?.takeIf { it.isNotBlank() }?.let { " · 初始密码 $it" } ?: "")
                        onNote(note)
                        username = ""
                        display = ""
                        tick++
                    }
                }
            }) { Text("创建", fontSize = 13.sp) }
            Hint(note)
        }
    }

    if (resetFor.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { resetFor = "" },
            title = { Text("重置密码") },
            text = {
                OutlinedTextField(
                    newPw,
                    { newPw = it },
                    label = { Text("新密码（至少 6 位）") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                TextButton(enabled = newPw.length >= 6, onClick = {
                    val subject = resetFor
                    resetFor = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) {
                            runCatching { SettingsApi.patchGuest(api, subject, JSONObject().put("password", newPw)) }.getOrNull()
                        }
                        onNote(msgOf(r))
                        tick++
                    }
                }) { Text("存下") }
            },
            dismissButton = { TextButton(onClick = { resetFor = "" }) { Text("算了") } },
        )
    }
    if (confirm.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { confirm = "" },
            title = { Text("删掉这个客人？") },
            text = { Text("删掉之后对方就登不上来了。") },
            confirmButton = {
                TextButton(onClick = {
                    val subject = confirm
                    confirm = ""
                    scope.launch {
                        withContext(Dispatchers.IO) { runCatching { SettingsApi.deleteGuest(api, subject) } }
                        onNote("删掉了")
                        tick++
                    }
                }) { Text("删掉") }
            },
            dismissButton = { TextButton(onClick = { confirm = "" }) { Text("算了") } },
        )
    }
}


private fun roleLabel(role: String): String = when (role) {
    "owner" -> "主人（owner）"
    "guest" -> "客人（guest）"
    "" -> "—"
    else -> role
}

@Composable
internal fun AccountSection(api: AuraApi, prefs: Prefs, me: JSONObject?, onLogout: () -> Unit) {
    var changing by remember { mutableStateOf(false) }
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Panel("账号") {
        InfoRow("用户名", me?.optString("name").orEmpty().ifBlank { prefs.user })
        InfoRow("角色", roleLabel(me?.optString("role").orEmpty().ifBlank { prefs.role }))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { changing = !changing }) { Text(if (changing) "取消改密码" else "修改密码") }
            TextButton(onClick = {
                api.logout()
                prefs.clearLogin()
                onLogout()
            }) { Text("退出登录", color = MaterialTheme.colorScheme.error) }
        }
        if (changing) {
            OutlinedTextField(
                oldPw,
                { oldPw = it },
                label = { Text("当前密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            OutlinedTextField(
                newPw,
                { newPw = it },
                label = { Text("新密码（至少 6 位）") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            TextButton(enabled = newPw.length >= 6, onClick = {
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { SettingsApi.changePassword(api, oldPw, newPw) }.getOrNull() }
                    note = if (r?.optBoolean("ok") == true) "密码改好了" else r?.optString("reason").orEmpty().ifBlank { "改密码失败" }
                }
            }) { Text("保存新密码", modifier = Modifier.padding(top = 2.dp)) }
            Hint(note)
        }
    }
}


@Composable
internal fun CacheSection(bytes: Long, onClear: () -> Unit) {
    Panel("本地缓存") {
        val mb = String.format("%.1f", bytes / 1024.0 / 1024.0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("图片/语音缓存：$mb MB（上限 300 MB，超了自动删最旧的）", fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, contentDescription = "清空缓存") }
        }
    }
}
