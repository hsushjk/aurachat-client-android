package dev.aurachat.client.net

import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * 只对用户确认过的「主机:端口」放宽，其余仍走系统默认信任链；放宽时把指纹显示给用户核对。
 */
object Tls {
    private val accepted = ConcurrentHashMap<String, String>()

    fun fingerprintOf(vararg certs: X509Certificate?): String {
        val c = certs.firstOrNull() ?: return ""
        val md = MessageDigest.getInstance("SHA-256").digest(c.encoded)
        return md.joinToString(":") { "%02X".format(it) }
    }

    fun accept(key: String, fingerprint: String) {
        accepted[key] = fingerprint
    }

    fun revoke(key: String) {
        accepted.remove(key)
    }

    fun isAccepted(key: String) = accepted.containsKey(key)

    /**
     * 用 trustAll 的临时 client 抓证书链，拿不到（非 https / 连不上）返回 null。
     */
    fun captureFingerprint(url: String): String? = try {
        var chain: Array<X509Certificate>? = null
        val capturing = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) = Unit
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {
                @Suppress("UNCHECKED_CAST")
                chain = c as Array<X509Certificate>?
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(capturing), java.security.SecureRandom())
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .sslSocketFactory(ctx.socketFactory, capturing)
            .hostnameVerifier { _, _ -> true }
            .build()
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { it.body?.close() }
        fingerprintOf(*(chain ?: emptyArray())).takeIf { it.isNotBlank() }
    } catch (e: Throwable) {
        null
    }

    val trustAll: TrustManager = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}

fun buildClient(insecure: Boolean): OkHttpClient {
    val b = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // 0 = 不超时，SSE 长连接一直挂着
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
    if (insecure) {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(Tls.trustAll), java.security.SecureRandom())
        b.sslSocketFactory(ctx.socketFactory, Tls.trustAll as X509TrustManager)
        b.hostnameVerifier { _, _ -> true }
    }
    return b.build()
}
