package dev.aurachat.client.net

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 服务端 toMono16k 只认 PCM16 WAV，MediaRecorder 的 m4a 用不了；只好自己采 PCM 拼头。
 */
class Recorder(private val ctx: Context) {
    companion object {
        private const val RATE = 16000
    }

    private var rec: AudioRecord? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val out = ByteArrayOutputStream()
    private var startedAt = 0L

    fun start(): Boolean {
        cancel()
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(min * 2, 4096),
            )
        }.getOrNull() ?: return false
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            return false
        }
        return runCatching {
            out.reset()
            r.startRecording()
            rec = r
            running = true
            startedAt = System.currentTimeMillis()
            thread = Thread {
                val buf = ByteArray(3200)
                while (running) {
                    val n = runCatching { r.read(buf, 0, buf.size) }.getOrDefault(-1)
                    if (n > 0) synchronized(out) { out.write(buf, 0, n) }
                }
            }.apply { isDaemon = true; start() }
            true
        }.getOrElse {
            runCatching { r.release() }
            false
        }
    }

    fun stopAndRead(): Pair<String, Int>? {
        val r = rec ?: return null
        val dur = ((System.currentTimeMillis() - startedAt) / 1000.0).toInt().coerceIn(1, 60)
        running = false
        runCatching { thread?.join(600) }
        runCatching {
            r.stop()
            r.release()
        }
        rec = null
        thread = null
        val pcm = synchronized(out) { out.toByteArray() }
        out.reset()
        if (pcm.size < RATE) return null // 不到半秒，当没说话
        return Base64.encodeToString(wav(pcm), Base64.NO_WRAP) to dur
    }

    fun cancel() {
        running = false
        runCatching { rec?.stop() }
        runCatching { rec?.release() }
        rec = null
        thread = null
        synchronized(out) { out.reset() }
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val out = ByteArray(44 + pcm.size)
        fun le32(v: Int, at: Int) {
            out[at] = (v and 0xFF).toByte()
            out[at + 1] = ((v shr 8) and 0xFF).toByte()
            out[at + 2] = ((v shr 16) and 0xFF).toByte()
            out[at + 3] = ((v shr 24) and 0xFF).toByte()
        }
        fun le16(v: Int, at: Int) {
            out[at] = (v and 0xFF).toByte()
            out[at + 1] = ((v shr 8) and 0xFF).toByte()
        }
        "RIFF".toByteArray().copyInto(out, 0)
        le32(36 + pcm.size, 4)
        "WAVE".toByteArray().copyInto(out, 8)
        "fmt ".toByteArray().copyInto(out, 12)
        le32(16, 16)
        le16(1, 20)          // PCM
        le16(1, 22)          // 单声道
        le32(RATE, 24)
        le32(RATE * 2, 28)   // 字节率
        le16(2, 32)          // 块对齐
        le16(16, 34)         // 位深
        "data".toByteArray().copyInto(out, 36)
        le32(pcm.size, 40)
        pcm.copyInto(out, 44)
        return out
    }
}

object VoicePlayer {
    private var player: MediaPlayer? = null
    @Volatile private var progressOn = false

    /** 老协议：消息里内联的 base64 语音，播完把临时文件删掉。 */
    fun play(ctx: Context, b64: String, onDone: () -> Unit = {}, onProgress: (Int, Int) -> Unit = { _, _ -> }): Boolean {
        val bytes = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull() ?: return false
        val f = File(ctx.cacheDir, "play-${System.currentTimeMillis()}.bin")
        runCatching { f.writeBytes(bytes) }.getOrElse { return false }
        return start(f, onDone, clean = true, onProgress)
    }

    /** 新协议：media/ 缓存里的文件，播完留着下次直接用。 */
    fun playFile(f: File, onDone: () -> Unit = {}, onProgress: (Int, Int) -> Unit = { _, _ -> }): Boolean =
        start(f, onDone, clean = false, onProgress)

    private fun start(f: File, onDone: () -> Unit, clean: Boolean, onProgress: (Int, Int) -> Unit): Boolean {
        stop()
        val p = MediaPlayer()
        return runCatching {
            p.setDataSource(f.absolutePath)
            p.setOnCompletionListener {
                stop()
                if (clean) f.delete()
                onProgress(0, 0)
                onDone()
            }
            p.prepare()
            p.start()
            player = p
            progressOn = true
            // 后台轮询当前位置，按 ~100ms 抛一次进度；播放结束/被 stop 时自动退出
            Thread {
                while (progressOn) {
                    runCatching {
                        val cur = p.currentPosition
                        val total = p.duration
                        onProgress(cur, total)
                    }
                    Thread.sleep(100)
                }
            }.apply { isDaemon = true; start() }
            true
        }.getOrElse {
            runCatching { p.release() }
            if (clean) f.delete()
            onDone()
            false
        }
    }

    fun stop() {
        progressOn = false
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }
}
