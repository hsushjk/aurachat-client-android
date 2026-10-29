package dev.aurachat.client

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.aurachat.client.data.Prefs
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.Live

/**
 * 常驻前台服务攥着 SSE，退到后台也收消息；来消息弹横幅。
 */
class LiveService : Service() {
    companion object {
        const val CH_ALIVE = "aurachat-alive"
        const val CH_MSG = "aurachat-msg"
        const val ID_ALIVE = 1
        const val ID_MSG = 2

        fun start(ctx: Context) {
            val i = Intent(ctx, LiveService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun ensureChannels(ctx: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CH_ALIVE, "保持连接", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "让聊天在后台也能收到消息"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CH_MSG, "新消息", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ta 发来消息时弹出"
                },
            )
        }

        fun notifyMessage(ctx: Context, who: String, body: String) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            ensureChannels(ctx)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val n = Notification.Builder(ctx, CH_MSG)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(who.ifBlank { "AuraChat" })
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(ID_MSG, n)
        }

        fun clearMessages(ctx: Context) {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_MSG)
        }
    }

    private var api: AuraApi? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels(this)
        startForeground(ID_ALIVE, aliveNotification("正在连接…"))
        val prefs = Prefs(this)
        if (prefs.token.isNotBlank()) {
            val a = AuraApi(prefs.base, prefs.token).also { it.rebuild() }
            api = a
            Live.onNew = { m ->
                if (!Live.visible) {
                    val body = when (m.kind) {
                        "image" -> "[图片]"
                        "sticker" -> "[表情]"
                        "voice" -> if (m.dur > 0) "[语音 ${m.dur}s]" else "[语音]"
                        else -> m.text
                    }
                    notifyMessage(this, Live.botName.ifBlank { "AuraChat" }, body)
                }
                setOnline(Live.online)
            }
            Live.onState = { on -> setOnline(on) }
            Live.start(this, a)
            setOnline(Live.online)
        }
    }

    private fun setOnline(on: Boolean) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.notify(ID_ALIVE, aliveNotification(if (on) "已连接 · 实时收消息" else "正在重连…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        Live.onNew = null
        Live.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun aliveNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CH_ALIVE)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle("AuraChat 在后台待着")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
