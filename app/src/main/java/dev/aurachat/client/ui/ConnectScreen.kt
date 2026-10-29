package dev.aurachat.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.aurachat.client.data.Prefs
import dev.aurachat.client.net.AuraApi
import dev.aurachat.client.net.Tls

@Composable
fun ConnectScreen(
    prefs: Prefs,
    onConnected: (AuraApi) -> Unit,
) {
    var base by remember { mutableStateOf(prefs.base) }
    var user by remember { mutableStateOf(prefs.user) }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    // 待确认的自签证书：Pair(主机:端口, 指纹)
    var cert by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun attempt() {
        val b = prefs.normalizeBase(base)
        if (b.isEmpty() || user.isBlank() || pass.isBlank()) {
            msg = "地址、用户名、密码都要填。"
            return
        }
        busy = true
        msg = ""
        Thread {
            val api = AuraApi(b)
            val r = runCatching { api.login(user.trim(), pass) }
            val err = r.exceptionOrNull()
            val j = r.getOrNull()
            val ok = j?.optBoolean("ok") == true
            prefs.base = b
            if (ok) {
                api.token = j!!.optString("token")
                prefs.token = api.token
                prefs.user = j.optString("subject")
                prefs.name = j.optString("name", j.optString("subject"))
                prefs.role = j.optString("role")
            }
            // https 登录失败多是自签证书，抓指纹给用户确认
            val fp = if (!ok && b.startsWith("https://")) Tls.captureFingerprint(api.url("api/state")) else null
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                busy = false
                when {
                    ok -> onConnected(api)
                    fp != null -> cert = b.substringAfter("://").trimEnd('/') to fp
                    else -> msg = err?.let { "连不上：${it.message}" }
                        ?: (j?.optString("reason").takeUnless { it.isNullOrBlank() } ?: "登录没成功，检查用户名密码。")
                }
            }
        }.start()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("AuraChat", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = base,
            onValueChange = { base = it },
            label = { Text("地址") },
            placeholder = { Text("服务器地址") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        if (msg.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Button(
            enabled = !busy,
            onClick = { attempt() },
        ) { Text(if (busy) "连接中…" else "连接") }
    }

    cert?.let { (key, fp) ->
        AlertDialog(
            onDismissRequest = { cert = null },
            title = { Text("信任这个证书吗？") },
            text = {
                Text("服务器用的是自签证书，系统不认。指纹（SHA-256）：\n\n$fp\n\n如果和你在别处看到的指纹一致，就点「信任并连接」。")
            },
            confirmButton = {
                TextButton(onClick = {
                    Tls.accept(key, fp)
                    cert = null
                    attempt()
                }) { Text("信任并连接") }
            },
            dismissButton = {
                TextButton(onClick = { cert = null }) { Text("不信任") }
            },
        )
    }
}
