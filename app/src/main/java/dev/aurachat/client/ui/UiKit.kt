package dev.aurachat.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


fun deepGet(root: JSONObject, path: String): Any? {
    var cur: Any? = root
    for (p in path.split('.')) {
        val o = cur as? JSONObject ?: return null
        if (!o.has(p)) return null
        cur = o.opt(p)
        if (cur == null || cur == JSONObject.NULL) return null
    }
    return cur
}

/** PATCH 是深合并，所以只发改过的那几项。 */
fun deepPut(target: JSONObject, path: String, value: Any) {
    val parts = path.split('.')
    var cur = target
    for (i in 0 until parts.size - 1) {
        val next = cur.optJSONObject(parts[i]) ?: JSONObject().also { cur.put(parts[i], it) }
        cur = next
    }
    cur.put(parts.last(), value)
}

/**
 * doc 是界面上的值，patch 只攒改动，保存时发 patch。
 */
@Stable
class CfgState(root: JSONObject) {
    var doc by mutableStateOf(root)
        private set
    var patch by mutableStateOf(JSONObject())
        private set
    var dirty by mutableStateOf(0)
        private set

    fun reload(root: JSONObject) {
        doc = root
        patch = JSONObject()
        dirty = 0
    }

    fun str(path: String, d: String = ""): String {
        @Suppress("UNUSED_EXPRESSION") dirty
        val v = deepGet(doc, path) ?: return d
        return if (v is String) v else v.toString()
    }

    fun int(path: String, d: Int = 0): Int {
        @Suppress("UNUSED_EXPRESSION") dirty
        return (deepGet(doc, path) as? Number)?.toInt() ?: d
    }

    fun dbl(path: String, d: Double = 0.0): Double {
        @Suppress("UNUSED_EXPRESSION") dirty
        return (deepGet(doc, path) as? Number)?.toDouble() ?: d
    }

    fun bool(path: String, d: Boolean = false): Boolean {
        @Suppress("UNUSED_EXPRESSION") dirty
        return deepGet(doc, path) as? Boolean ?: d
    }

    fun arr(path: String): JSONArray {
        @Suppress("UNUSED_EXPRESSION") dirty
        return deepGet(doc, path) as? JSONArray ?: JSONArray()
    }

    fun set(path: String, v: Any) {
        deepPut(doc, path, v)
        deepPut(patch, path, v)
        dirty++
    }
}


@Composable
fun PageHeader(title: String, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun Panel(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

@Composable
fun Hint(t: String) {
    if (t.isNotBlank()) {
        Text(
            t,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun EmptyText(t: String) {
    Text(t, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
fun Note(t: String) {
    if (t.isNotBlank()) {
        Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun Toggle(label: String, on: Boolean, hint: String = "", onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp)
            Hint(hint)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, fontSize = 13.sp)
    }
}

/** 本地留一份文本，回写配置时不会跳字。 */
@Composable
fun CfgText(
    label: String,
    path: String,
    cfg: CfgState,
    hint: String = "",
    numeric: Boolean = false,
    decimals: Boolean = false,
    multiline: Boolean = false,
    secret: Boolean = false,
    placeholder: String = "",
) {
    var txt by remember(path) { mutableStateOf(cfg.str(path)) }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        OutlinedTextField(
            value = txt,
            onValueChange = { v ->
                txt = v
                if (numeric) {
                    if (decimals) v.toDoubleOrNull()?.let { cfg.set(path, it) }
                    else v.toIntOrNull()?.let { cfg.set(path, it) }
                } else {
                    cfg.set(path, v)
                }
            },
            label = { Text(label) },
            placeholder = if (placeholder.isBlank()) null else ({ Text(placeholder) }),
            singleLine = !multiline,
            minLines = if (multiline) 3 else 1,
            visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (numeric && decimals) KeyboardType.Decimal else if (numeric) KeyboardType.Number else KeyboardType.Text,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Hint(hint)
    }
}

/** 用弹窗实现，material3 的 ExposedDropdown 接口不稳。 */
@Composable
fun PickRow(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val cur = options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "—" }
    Row(
        Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(cur, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Text("  ▾", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column {
                    for ((k, v) in options) {
                        Row(
                            Modifier.fillMaxWidth().clickable { onChange(k); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == k, onClick = { onChange(k); open = false })
                            Text(v, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("算了") } },
        )
    }
}

@Composable
fun SliderRow(label: String, value: Float, min: Float = 0f, max: Float = 1f, steps: Int = 0, hint: String = "", fmt: (Float) -> String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(fmt(value), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = min..max, steps = steps)
        Hint(hint)
    }
}

@Composable
fun RowScope.MiniBtn(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(enabled = enabled, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
        Text(text, fontSize = 12.sp)
    }
}

@Composable
fun Mono(text: String) {
    Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

@Composable
fun KeyValueLines(j: JSONObject?) {
    if (j == null) return
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)).padding(8.dp)) {
        val keys = j.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(100.dp))
                Text(j.opt(k)?.toString().orEmpty(), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun LoadBox(error: String?, loaded: Boolean, content: @Composable () -> Unit) {
    when {
        error != null -> Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        !loaded -> LoadingRow()
        else -> content()
    }
}

/**
 * key 变了就重拉（拿 tick 当 key = 刷新），拉不到显示一行红字。
 */
@Composable
fun <T : Any> LoadJson(key: String, loader: suspend () -> T?, content: @Composable (T) -> Unit) {
    var data by remember(key) { mutableStateOf<T?>(null) }
    var err by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key) {
        val r = runCatching { loader() }
        data = r.getOrNull()
        err = if (data == null) "拉不到数据：${r.exceptionOrNull()?.message ?: "网络不对"}" else null
    }
    when {
        err != null -> Text(err!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        data == null -> LoadingRow()
        else -> content(data!!)
    }
}


private val hm = SimpleDateFormat("HH:mm", Locale.CHINA)
private val mdhm = SimpleDateFormat("M月d日 HH:mm", Locale.CHINA)
private val ymdhm = SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA)

fun fmtWhen(t: Long): String {
    if (t <= 0) return ""
    val d = Calendar.getInstance().apply { timeInMillis = t }
    val now = Calendar.getInstance()
    val sameYear = d.get(Calendar.YEAR) == now.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - d.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "今天 " + hm.format(Date(t))
        sameYear && dayDiff == 1 -> "昨天 " + hm.format(Date(t))
        sameYear -> mdhm.format(Date(t))
        else -> ymdhm.format(Date(t))
    }
}

fun fmtClock(t: Long): String = if (t <= 0) "" else hm.format(Date(t))

fun fmtStamp(t: Long): String = if (t <= 0) "" else mdhm.format(Date(t))

fun pct(v: Double): String = String.format(Locale.CHINA, "%d%%", (v * 100).toInt())

@Composable
fun TabStrip(tabs: List<String>, index: Int, onPick: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { i, t ->
            Box(
                Modifier
                    .weight(1f)
                    .background(
                        if (i == index) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .clickable { onPick(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    t,
                    fontSize = 12.sp,
                    color = if (i == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
