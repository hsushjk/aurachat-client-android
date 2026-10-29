package dev.aurachat.client.net

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 全局连接：前台服务持有，界面只订阅，退到后台也能收消息。
 * 分页游标是毫秒，边界会重复，合并时按 id 去重。
 */
object Live {
    val messages = mutableStateListOf<Message>()
    var online by mutableStateOf(false)
    var typing by mutableStateOf(false)
    var botName by mutableStateOf("")

    var hasMore by mutableStateOf(false)
    var loadingOlder by mutableStateOf(false)
    var fileIndex by mutableStateOf(-1)
    var fileTotal by mutableStateOf(0)

    @Volatile var visible = false

    @Volatile var onNew: ((Message) -> Unit)? = null

    private val arrivals = java.util.concurrent.CopyOnWriteArrayList<(Message) -> Unit>()

    fun addArrival(f: (Message) -> Unit) {
        arrivals.add(f)
    }

    fun removeArrival(f: (Message) -> Unit) {
        arrivals.remove(f)
    }

    @Volatile var onState: ((Boolean) -> Unit)? = null

    private val ui = Handler(Looper.getMainLooper())
    private var api: AuraApi? = null
    private var thread: Thread? = null
    @Volatile private var running = false

    fun start(context: Context, api: AuraApi) {
        if (running && this.api === api) return
        stop()
        this.api = api
        running = true
        val app = context.applicationContext
        ObjectHolder.ctx = app
        thread = Thread {
            runCatching {
                val p = api.page("last")
                ui.post {
                    merge(p.messages)
                    hasMore = p.hasMore
                    fileIndex = p.fileIndex
                    fileTotal = p.fileTotal
                }
                api.state().optString("botName").takeIf { it.isNotBlank() }?.let { n -> ui.post { botName = n } }
            }
            connect(api)
        }.apply { isDaemon = true; start() }
        Log.i("aurachat", "Live start")
    }

    private fun connect(api: AuraApi) {
        while (running) {
            api.stream(
                onMessage = { m ->
                    ui.post {
                        if (messages.none { it.id == m.id }) {
                            messages.add(m)
                            if (m.who != "user") {
                                onNew?.invoke(m)
                                arrivals.forEach { runCatching { it(m) } }
                            }
                        }
                    }
                },
                onTyping = { on -> ui.post { typing = on } },
                onOpen = {
                    ui.post { markOnline(true); typing = false }
                    if (running) reload()
                },
                onClosed = { ui.post { markOnline(false); typing = false } },
                // 过期清理：比 oldestAt 早的消息和媒体都没了
                onHistory = { oldest, more ->
                    ui.post {
                        if (oldest > 0) messages.removeAll { it.t < oldest }
                        hasMore = more
                    }
                },
            )
            if (!running) break
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    fun reload() {
        val a = api ?: return
        Thread {
            val p = runCatching { a.page("last") }.getOrNull() ?: return@Thread
            ui.post {
                merge(p.messages)
                hasMore = p.hasMore
                if (p.fileIndex >= 0) {
                    fileIndex = p.fileIndex
                    fileTotal = p.fileTotal
                }
            }
        }.apply { isDaemon = true }.start()
    }

    // before 用列表最早那条的 t 当游标，拉上一片
    fun loadOlder() {
        val a = api ?: return
        if (loadingOlder || !hasMore) return
        val before = messages.firstOrNull()?.t ?: 0L
        loadingOlder = true
        Thread {
            val p = runCatching { a.page("prev", before) }.getOrNull()
            ui.post {
                loadingOlder = false
                if (p == null) return@post
                merge(p.messages)
                hasMore = p.hasMore
                if (p.fileIndex >= 0) {
                    fileIndex = p.fileIndex
                    fileTotal = p.fileTotal
                }
            }
        }.apply { isDaemon = true }.start()
    }

    // 合并而不是替换：拉取期间推来的消息不能被清掉；按 t 重排才不乱
    private fun merge(fresh: List<Message>) {
        val seen = HashSet<String>()
        val merged = ArrayList<Message>(fresh.size + messages.size)
        for (m in fresh) if (seen.add(m.id)) merged.add(m)
        for (m in messages) if (seen.add(m.id)) merged.add(m)
        val sorted = merged.sortedBy { it.t } // Kotlin 的 sortedBy 是稳定排序，同毫秒保持原顺序
        messages.clear()
        messages.addAll(sorted)
    }

    private fun markOnline(v: Boolean) {
        if (online == v) return
        online = v
        onState?.invoke(v)
    }

    fun stop() {
        running = false
        runCatching { api?.stopStream() }
        thread = null
        online = false
        typing = false
    }

    fun clear() {
        stop()
        ui.post {
            messages.clear()
            botName = ""
            hasMore = false
            loadingOlder = false
            fileIndex = -1
            fileTotal = 0
        }
        api = null
    }

    private object ObjectHolder {
        var ctx: Context? = null
    }
}
