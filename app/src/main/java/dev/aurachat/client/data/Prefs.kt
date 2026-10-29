package dev.aurachat.client.data

import android.content.Context
import androidx.core.content.edit

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("aurachat", Context.MODE_PRIVATE)

    var base: String
        get() = sp.getString("base", "") ?: ""
        set(v) = sp.edit { putString("base", v) }

    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit { putString("token", v) }

    var user: String
        get() = sp.getString("user", "") ?: ""
        set(v) = sp.edit { putString("user", v) }

    var name: String
        get() = sp.getString("name", "") ?: ""
        set(v) = sp.edit { putString("name", v) }

    var role: String
        get() = sp.getString("role", "") ?: ""
        set(v) = sp.edit { putString("role", v) }

    // 本机设置，不跟服务端
    var theme: String
        get() = sp.getString("theme", "follow") ?: "follow"
        set(v) = sp.edit { putString("theme", v) }

    // 草稿落盘，切页/杀进程后还在
    var draft: String
        get() = sp.getString("draft", "") ?: ""
        set(v) = sp.edit { putString("draft", v) }

    // 毫秒时间戳，未读数按它算
    var lastRead: Long
        get() = sp.getLong("lastRead", 0L)
        set(v) = sp.edit { putLong("lastRead", v) }

    fun clearLogin() = sp.edit { remove("token"); remove("user"); remove("name"); remove("role") }

    // 只填 ip 时补成 http://ip:2332（局域网默认端口）
    fun normalizeBase(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://") && !s.startsWith("https://")) {
            val hostPort = s.substringBefore('/')
            val withPort = if (hostPort.contains(':') && hostPort.substringAfterLast(':').all { it.isDigit() }) hostPort else "$hostPort:2332"
            s = "http://$withPort" + s.removePrefix(hostPort)
        }
        return s.trimEnd('/')
    }
}
