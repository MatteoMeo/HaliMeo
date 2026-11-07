package it.halimeo.app
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
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
internal data class HaliMeoRelease(val name: String, val versionCode: Long, val versionName: String, val changelog: String, val apkUrl: String, val apkName: String, val sha256: String?)

internal object HaliMeoUpdateState {
    var release by mutableStateOf<HaliMeoRelease?>(null)
    var checkedAt by mutableLongStateOf(0L)
}

/**
 * Performs the optional release check after launch. Network failures are deliberately
 * non-fatal: the local diary must remain usable when GitHub or the server is offline.
 */
internal suspend fun checkForUpdatesOnLaunch(context: Context) {
    // Clear stale process state first, so an old release cannot trigger a false prompt
    // while the fresh request is still running.
    HaliMeoUpdateState.release = null
    HaliMeoUpdateState.checkedAt = 0L
    val prefs = context.getSharedPreferences("halimeo_preferences", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("auto_check_updates", true)) return

    val channel = prefs.getString("update_channel", "alpha") ?: "alpha"
    try {
        val latest = fetchLatestRelease(channel)
        HaliMeoUpdateState.release = latest
        HaliMeoUpdateState.checkedAt = System.currentTimeMillis()
    } catch (_: Exception) {
        // An unavailable network must never block app startup or offline diary access.
    }
}

@Composable
internal fun UpdateCenterCard(activity: androidx.activity.ComponentActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("halimeo_preferences", Context.MODE_PRIVATE) }
    var channel by remember { mutableStateOf(prefs.getString("update_channel", "alpha") ?: "alpha") }
    var release by remember { mutableStateOf(HaliMeoUpdateState.release) }
    var status by remember { mutableStateOf(if (HaliMeoUpdateState.checkedAt > 0L) "Controllo automatico completato." else "Controllo gli aggiornamenti all'avvio.") }
    var autoCheck by remember { mutableStateOf(prefs.getBoolean("auto_check_updates", true)) }
    var busy by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }
    val changelogScrollState = rememberScrollState()
    val changelogNestedScroll = remember(changelogScrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val atTop = changelogScrollState.value == 0 && available.y > 0f
                val atBottom = changelogScrollState.value >= changelogScrollState.maxValue && available.y < 0f
                return if (atTop || atBottom) Offset(0f, available.y) else Offset.Zero
            }
        }
    }
    val scope = rememberCoroutineScope()
    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val installedFile = pendingInstallFile
        if (result.resultCode == Activity.RESULT_OK) {
            installedFile?.delete()
            installedFile?.parentFile?.listFiles()?.filter { it.name.endsWith(".part", true) }?.forEach { it.delete() }
            status = "Aggiornamento installato. Il file APK temporaneo è stato eliminato."
        } else {
            status = "Installazione non completata: APK conservato, puoi riprovare senza scaricarlo di nuovo."
        }
        pendingInstallFile = null
    }
    LaunchedEffect(HaliMeoUpdateState.checkedAt) {
        if (HaliMeoUpdateState.checkedAt > 0L) {
            release = HaliMeoUpdateState.release
            status = when {
                release == null && channel == "stable" -> "Non è ancora stata pubblicata una versione stabile."
                release == null -> "Nessuna release Alpha disponibile."
                release!!.versionCode <= BuildConfig.VERSION_CODE.toLong() -> "Hai già installato l'ultima versione di questo canale."
                else -> "È disponibile un aggiornamento."
            }
        }
    }

    fun checkUpdates() {
        if (busy) return
        busy = true; release = null; status = "Controllo le release su GitHub…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchLatestRelease(channel) }
                release = result
                HaliMeoUpdateState.release = result
                HaliMeoUpdateState.checkedAt = System.currentTimeMillis()
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
                    val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                    val target = File(directory, item.apkName)
                    val partial = File(directory, "${item.apkName}.part")
                    directory.listFiles()?.filter { it.isFile && it != target && (it.name.endsWith(".apk", true) || it.name.endsWith(".part", true)) }?.forEach { it.delete() }

                    fun matchesExpectedDigest(file: File): Boolean {
                        if (!file.isFile || file.length() <= 0L) return false
                        val expected = item.sha256?.takeIf { it.isNotBlank() } ?: return true
                        val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                        return actual.equals(expected, ignoreCase = true)
                    }

                    // Reuse the complete APK after an installation cancellation/failure.
                    if (!matchesExpectedDigest(target)) {
                        target.delete()
                        partial.delete()
                        val connection = (URL(item.apkUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 20_000; readTimeout = 60_000; instanceFollowRedirects = true
                        }
                        try {
                            connection.connect()
                            if (connection.responseCode !in 200..299) error("Download GitHub non riuscito (${connection.responseCode})")
                            val total = connection.contentLengthLong
                            connection.inputStream.use { input ->
                                partial.outputStream().use { output ->
                                    val buffer = ByteArray(64 * 1024); var copied = 0L
                                    while (true) {
                                        val count = input.read(buffer); if (count < 0) break
                                        output.write(buffer, 0, count); copied += count
                                        if (total > 0) progress = (copied.toFloat() / total).coerceIn(0f, 1f)
                                    }
                                    output.flush()
                                    if (total > 0 && copied != total) error("Download incompleto: riprova.")
                                }
                            }
                        } catch (error: Exception) {
                            partial.delete()
                            throw error
                        } finally { connection.disconnect() }

                        if (!matchesExpectedDigest(partial)) {
                            partial.delete()
                            error("Verifica di sicurezza non superata: file diverso dalla release.")
                        }
                        if (!partial.renameTo(target)) {
                            partial.copyTo(target, overwrite = true)
                            partial.delete()
                        }
                    }
                    target
                }
                downloading = false
                val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${BuildConfig.APPLICATION_ID}")))
                    status = "Consenti a HaliMeo di installare aggiornamenti e poi premi di nuovo Installa. Il file è già pronto."
                } else {
                    pendingInstallFile = apk
                    installLauncher.launch(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        data = uri
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        putExtra(Intent.EXTRA_RETURN_RESULT, true)
                    })
                    status = "Installazione avviata: conferma nella finestra di Android."
                }
            } catch (error: Exception) { downloading = false; status = "Aggiornamento non riuscito: ${error.message ?: "errore imprevisto"}" }
            finally { busy = false }
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                Icon(Icons.Default.Download, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(19.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("AGGIORNAMENTI", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Canale di rilascio e nuove versioni", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Controllo automatico all'avvio", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                Text("Verifica le nuove versioni ogni volta che apri HaliMeo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = autoCheck,
                onCheckedChange = {
                    autoCheck = it
                    prefs.edit().putBoolean("auto_check_updates", it).apply()
                }
            )
        }
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.52f), RoundedCornerShape(18.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(3.dp),
            space = 3.dp
        ) {
            listOf("alpha" to "Alpha", "stable" to "Stabile").forEachIndexed { index, option ->
                SegmentedButton(
                    selected = channel == option.first,
                    onClick = {
                        channel = option.first; prefs.edit().putString("update_channel", channel).apply()
                        release = null; status = if (channel == "stable") "Canale Stabile selezionato." else "Canale Alpha selezionato."
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, 2, RoundedCornerShape(15.dp)),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.34f else 0.18f),
                        activeContentColor = MaterialTheme.colorScheme.onSurface,
                        activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        inactiveBorderColor = Color.Transparent
                    )
                ) { Text(option.second) }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ) {
            Text(status, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        release?.let { item ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (item.versionCode > BuildConfig.VERSION_CODE.toLong()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Aggiornamento disponibile", modifier = Modifier.padding(horizontal = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("v${item.versionName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                if (item.changelog.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).nestedScroll(changelogNestedScroll),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                    ) {
                        Column(Modifier.verticalScroll(changelogScrollState).padding(12.dp)) {
                            Text(item.changelog.take(5000), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                if (item.versionCode > BuildConfig.VERSION_CODE.toLong()) {
                    Button(onClick = { downloadAndInstall(item) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Download, contentDescription = null); Text("Scarica e installa", Modifier.padding(start = 8.dp))
                    }
                    if (busy && downloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Download in corso", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (progress > 0f) "${(progress * 100).toInt()}%" else "In preparazione", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Surface(Modifier.fillMaxWidth().height(9.dp), shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Box {
                                    Box(
                                        Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                                            .background(
                                                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), MaterialTheme.colorScheme.primary)),
                                                RoundedCornerShape(50)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { checkUpdates() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            if (busy && !downloading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, contentDescription = null)
            Text("Controlla aggiornamenti", Modifier.padding(start = 8.dp))
        }
        Text("Puoi controllare anche manualmente. Android chiederà sempre conferma prima di installare; i download completati restano disponibili in caso di errore e vengono eliminati dopo l'installazione riuscita.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun fetchLatestRelease(channel: String): HaliMeoRelease? {
    val endpoint = "https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=100"
    val json = withContext(Dispatchers.IO) {
        var response: String? = null
        var lastError: Exception? = null
        for (attempt in 1..2) {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 18_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "HaliMeo-Android-Updater")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    error("GitHub ha risposto ${connection.responseCode}")
                }
                response = connection.inputStream.bufferedReader().use { it.readText() }
                break
            } catch (error: Exception) {
                lastError = error
                if (attempt < 2) Thread.sleep(600L)
            } finally {
                connection.disconnect()
            }
        }
        response ?: throw (lastError ?: java.io.IOException("Risposta GitHub non disponibile"))
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
