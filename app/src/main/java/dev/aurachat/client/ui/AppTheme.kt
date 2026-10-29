package dev.aurachat.client.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.aurachat.client.net.decodeBytes

enum class ThemeMode(val key: String, val label: String) {
    Follow("follow", "跟随系统"),
    Light("light", "浅色"),
    Dark("dark", "深色"),
    Acrylic("acrylic", "亚克力"),
    Frosted("frosted", "毛玻璃");

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: Follow
    }
}

/**
 * 背景图只存本机（filesDir/backdrop.jpg）；亚克力/毛玻璃拿它垫底，没有就用渐变。
 * 退出登录只清内存，不动本地文件。
 */
object Backdrop {
    var bmp by mutableStateOf<Bitmap?>(null)
        private set
    @Volatile private var loading = false

    fun load(ctx: Context) {
        if (bmp != null || loading) return
        loading = true
        Thread {
            runCatching {
                val f = java.io.File(ctx.filesDir, "backdrop.jpg")
                val b = f.takeIf { it.exists() && it.length() > 0 }?.readBytes()?.let { decodeBytes(it, 1440) }
                if (b != null) bmp = b
            }
            loading = false
        }.apply { isDaemon = true }.start()
    }

    fun reload(ctx: Context) {
        clear()
        load(ctx)
    }

    fun clear() {
        bmp = null
        loading = false
    }
}

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.Follow -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Acrylic, ThemeMode.Frosted -> true
    }
    val translucent = mode == ThemeMode.Acrylic || mode == ThemeMode.Frosted
    val scheme = when {
        !dark -> lightColorScheme()
        translucent -> darkColorScheme().copy(
            background = Color.Transparent,
            surface = if (mode == ThemeMode.Frosted) Color(0xDB11141A) else Color(0xE611141A),
            surfaceVariant = if (mode == ThemeMode.Frosted) Color(0xB3222730) else Color(0xCC222730),
            primaryContainer = Color(0xFF3A4A6B),
        )
        else -> darkColorScheme()
    }
    MaterialTheme(colorScheme = scheme) {
        // content() 只能在这一个调用点：结构一变下面所有 remember 都被重置，切主题会跳回聊天页
        val blurRadius: Dp = if (mode == ThemeMode.Frosted) 28.dp else 10.dp
        val image = Backdrop.bmp
        Box(Modifier.fillMaxSize()) {
            if (!translucent) {
                Box(Modifier.fillMaxSize().background(scheme.background))
            } else {
                if (image != null) {
                    Image(
                        bitmap = image.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(blurRadius),
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1B2436), Color(0xFF2A1F33), Color(0xFF101318)),
                            ),
                        ),
                    )
                }
                // 压一层暗色蒙版，深浅背景图都能看清字
                Box(Modifier.fillMaxSize().background(Color(if (mode == ThemeMode.Frosted) 0x66070A0E else 0x99070A0E)))
            }
            content()
        }
    }
}
