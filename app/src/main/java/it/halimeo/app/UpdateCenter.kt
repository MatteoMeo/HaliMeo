package it.halimeo.app
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val UPDATE_REPOSITORY = "MatteoMeo/HaliMeo"
private data class HaliMeoRelease(val name: String, val versionCode: Long, val versionName: String, val changelog: String, val apkUrl: String, val apkName: String, val sha256: String?)

@Composable
internal fun UpdateCenterCard(activity: androidx.activity.ComponentActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("halimeo_preferences", Context.MODE_PRIVATE) }
    var channel by remember { mutableStateOf(prefs.getString("update_channel", "alpha") ?: "alpha") }
    var release by remember { mutableStateOf<HaliMeoRelease?>(null) }
    var status by remember { mutableStateOf("Controlla manualmente se è disponibile una nuova versione.") }
    var busy by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    fun checkUpdates() {
        if (busy) return
        busy = true; release = null; status = "Controllo le release su GitHub…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchLatestRelease(channel) }
                release = result
                status = when {
                    result == null && channel == "stable" -> "Non è ancora stata pubblicata una versione stabile."
                    result == null -> "Nessuna release Alpha disponibile."
                    result.versionCode <= BuildConfig.VERSION_CODE.toLong() -> "Hai già installato l'ultima versione di questo canale."
                    else -> "È disponibile un aggiornamento."
                }
            } catch (error: Exception) { status = "Controllo non riuscito: ${error.message ?: "verifica la connessione"}" }
            finally { busy = false }
        }
    }

    fun downloadAndInstall(item: HaliMeoRelease) {
        if (busy) return
        busy = true; downloading = true; progress = 0f; status = "Download in corso…"
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) {
                    val target = File(context.cacheDir, "updates/${item.apkName}")
                    target.parentFile?.mkdirs()
                    val connection = (URL(item.apkUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
                    }
                    try {
                        connection.connect()
                        if (connection.responseCode !in 200..299) error("Download GitHub non riuscito (${connection.responseCode})")
                        val total = connection.contentLengthLong
                        connection.inputStream.use { input ->
                            target.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024); var copied = 0L
                                while (true) {
                                    val count = input.read(buffer); if (count < 0) break
                                    output.write(buffer, 0, count); copied += count
                                    if (total > 0) progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                                }
                            }
                        }
                    } finally { connection.disconnect() }
                    if (!item.sha256.isNullOrBlank()) {
                        val actual = MessageDigest.getInstance("SHA-256").digest(target.readBytes()).joinToString("") { "%02x".format(it) }
                        if (actual != item.sha256) { target.delete(); error("Verifica di sicurezza non superata: file diverso dalla release.") }
                    }
                    target
                }
                downloading = false
                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${BuildConfig.APPLICATION_ID}")))
                    status = "Consenti a HaliMeo di installare aggiornamenti e poi premi di nuovo Installa."
                } else {
                    activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    status = "Installazione avviata: conferma nella finestra di Android."
                }
            } catch (error: Exception) { downloading = false; status = "Aggiornamento non riuscito: ${error.message ?: "errore imprevisto"}" }
            finally { busy = false }
        }
    }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Text("AGGIORNAMENTI", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text("Scegli quale versione ricevere", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("alpha" to "Alpha", "stable" to "Stabile").forEachIndexed { index, option ->
                    SegmentedButton(selected = channel == option.first, onClick = {
                        channel = option.first; prefs.edit().putString("update_channel", channel).apply()
                        release = null; status = if (channel == "stable") "Canale Stabile selezionato." else "Canale Alpha selezionato."
                    }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(option.second) }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (busy && downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            release?.let { item ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("v${item.versionName} · Build ${item.versionCode}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (item.changelog.isNotBlank()) Text(item.changelog.take(1800), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (item.versionCode > BuildConfig.VERSION_CODE.toLong()) Button(onClick = { downloadAndInstall(item) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null); Text("Scarica e installa", Modifier.padding(start = 8.dp))
                    }
                }
            }
            OutlinedButton(onClick = { checkUpdates() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy && !downloading) CircularProgressIndicator() else Icon(Icons.Default.Refresh, contentDescription = null)
                Text("Controlla aggiornamenti", Modifier.padding(start = 8.dp))
            }
            Text("Il controllo è manuale. L'installazione richiede sempre la conferma di Android.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private suspend fun fetchLatestRelease(channel: String): HaliMeoRelease? {
    val endpoint = "https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=100"
    val json = withContext(Dispatchers.IO) {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "HaliMeo-Updater")
        }
        try {
            if (connection.responseCode !in 200..299) error("GitHub ha risposto ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
    val releases = JSONArray(json); val candidates = mutableListOf<HaliMeoRelease>()
    for (i in 0 until releases.length()) {
        val item = releases.getJSONObject(i)
        if (item.optBoolean("draft", false)) continue
        val pre = item.optBoolean("prerelease", false); val tag = item.optString("tag_name")
        val matches = if (channel == "alpha") pre && tag.startsWith("alpha-build-") else !pre && tag.startsWith("stable-build-")
        if (!matches) continue
        val assets = item.optJSONArray("assets") ?: continue
        for (j in 0 until assets.length()) {
            val asset = assets.getJSONObject(j); val name = asset.optString("name")
            if (!name.endsWith(".apk", true)) continue
            val build = Regex("build(\\d+)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: continue
            val version = Regex("(\\d+\\.\\d+\\.\\d+)").find(name)?.groupValues?.getOrNull(1) ?: item.optString("name")
            val body = item.optString("body", "").replace(Regex("(?m)^#{1,6}\\s*"), "").replace("*", "").trim()
            candidates += HaliMeoRelease(item.optString("name", "HaliMeo $version"), build, version, body, asset.optString("browser_download_url"), name, asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"))
        }
    }
    return candidates.maxByOrNull { it.versionCode }
}
