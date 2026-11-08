package it.halimeo.app

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

private const val UPDATE_REPOSITORY = "MatteoMeo/HaliMeo"
private const val UPDATE_NOTIFICATION_CHANNEL = "halimeo_updates"
private const val UPDATE_NOTIFICATION_ID = 7301
private const val PREF_DOWNLOAD_ID = "update_download_id"

internal data class HaliMeoRelease(val name: String, val versionCode: Long, val versionName: String, val changelog: String, val apkUrl: String, val apkName: String, val sha256: String?)

internal object HaliMeoUpdateState {
    var release by mutableStateOf<HaliMeoRelease?>(null)
    var checkedAt by mutableLongStateOf(0L)
}

private fun ensureUpdateNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(UPDATE_NOTIFICATION_CHANNEL, "Aggiornamenti HaliMeo", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Nuove versioni di HaliMeo" }
        )
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

private fun notifyUpdateAvailable(context: Context, item: HaliMeoRelease?) {
    if (item == null || item.versionCode <= BuildConfig.VERSION_CODE.toLong() || !notificationsAllowed(context)) return
    ensureUpdateNotificationChannel(context)
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val pending = launch?.let { android.app.PendingIntent.getActivity(context, UPDATE_NOTIFICATION_ID, it, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE) }
    val notification = NotificationCompat.Builder(context, UPDATE_NOTIFICATION_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Aggiornamento HaliMeo disponibile")
        .setContentText("È pronta la versione ${item.versionName}. Tocca per aprire l'updater.")
        .setAutoCancel(true)
        .setContentIntent(pending)
        .build()
    runCatching { NotificationManagerCompat.from(context).notify(UPDATE_NOTIFICATION_ID, notification) }
}

internal suspend fun checkForUpdatesOnLaunch(context: Context) {
    HaliMeoUpdateState.release = null
    HaliMeoUpdateState.checkedAt = 0L
    val prefs = context.getSharedPreferences("halimeo_preferences", Context.MODE_PRIVATE)
    if (!prefs.getBoolean("auto_check_updates", true)) return
    val channel = prefs.getString("update_channel", "alpha") ?: "alpha"
    try {
        val latest = fetchLatestRelease(channel)
        HaliMeoUpdateState.release = latest
        HaliMeoUpdateState.checkedAt = System.currentTimeMillis()
        notifyUpdateAvailable(context, latest)
    } catch (_: Exception) { }
}

private fun updateApkFile(context: Context, name: String) =
    File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, name)

/**
 * Remove only APKs downloaded by HaliMeo's own updater after their version is
 * already installed. Keep any newer pending update and leave unrelated files/cache
 * untouched. Android removes this app-specific directory on uninstall.
 */
internal fun cleanupInstalledUpdateApks(context: Context) {
    val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
    val releaseApk = Regex("""HaliMeo-.*-build(\d+)-debug\.apk""", RegexOption.IGNORE_CASE)
    val installedCode = BuildConfig.VERSION_CODE.toLong()
    runCatching {
        directory.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach
            val buildCode = releaseApk.matchEntire(file.name)
                ?.groupValues?.getOrNull(1)?.toLongOrNull()
            if (buildCode != null && buildCode <= installedCode) {
                file.delete()
            }
        }
    }
}

private fun digestMatches(file: File, expected: String?): Boolean {
    if (!file.isFile || file.length() <= 0L) return false
    if (expected.isNullOrBlank()) return true
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }.equals(expected, true)
}

private data class DownloadSnapshot(val status: Int, val downloaded: Long, val total: Long, val reason: Int)

private fun queryDownload(manager: DownloadManager, id: Long): DownloadSnapshot? {
    val cursor = manager.query(DownloadManager.Query().setFilterById(id)) ?: return null
    cursor.use {
        if (!it.moveToFirst()) return null
        return DownloadSnapshot(
            it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
            it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
            it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
            it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        )
    }
}

@Composable
internal fun UpdateCenterCard(activity: androidx.activity.ComponentActivity) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("halimeo_preferences", Context.MODE_PRIVATE) }
    val manager = remember(context) { context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    var channel by remember { mutableStateOf(prefs.getString("update_channel", "alpha") ?: "alpha") }
    var release by remember { mutableStateOf(HaliMeoUpdateState.release) }
    var status by remember { mutableStateOf("Controllo gli aggiornamenti all'avvio.") }
    var autoCheck by remember { mutableStateOf(prefs.getBoolean("auto_check_updates", true)) }
    var checking by remember { mutableStateOf(false) }
    var downloadId by remember { mutableLongStateOf(prefs.getLong(PREF_DOWNLOAD_ID, -1L)) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadFailed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) notifyUpdateAvailable(context, release)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed(context)) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
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

    LaunchedEffect(release?.apkName, downloadId) {
        val item = release ?: return@LaunchedEffect
        val localFile = updateApkFile(context, item.apkName)
        if (withContext(Dispatchers.IO) { digestMatches(localFile, item.sha256) }) {
            downloadedFile = localFile
            downloading = false
            downloadProgress = 1f
            status = "APK già scaricato. Puoi installarlo."
        } else if (downloadId >= 0L) {
            while (true) {
                val snapshot = withContext(Dispatchers.IO) { queryDownload(manager, downloadId) }
                if (snapshot == null) { downloading = false; break }
                when (snapshot.status) {
                    DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                        downloading = true
                        downloadFailed = false
                        if (snapshot.total > 0L) downloadProgress = (snapshot.downloaded.toFloat() / snapshot.total).coerceIn(0f, 1f)
                        status = if (snapshot.status == DownloadManager.STATUS_PAUSED) "Download in pausa: attendo la connessione…" else "Download in background…"
                    }
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        downloading = false
                        if (withContext(Dispatchers.IO) { digestMatches(localFile, item.sha256) }) {
                            downloadedFile = localFile
                            downloadProgress = 1f
                            status = "APK scaricato e verificato. Premi Installa."
                        } else {
                            downloadFailed = true
                            status = "Verifica SHA-256 non superata. Scarica di nuovo."
                            manager.remove(downloadId)
                            prefs.edit().remove(PREF_DOWNLOAD_ID).apply()
                            downloadId = -1L
                        }
                        break
                    }
                    DownloadManager.STATUS_FAILED -> {
                        downloading = false
                        downloadFailed = true
                        status = "Download non riuscito (codice ${snapshot.reason}). Puoi riprovare."
                        prefs.edit().remove(PREF_DOWNLOAD_ID).apply()
                        downloadId = -1L
                        break
                    }
                }
                delay(650)
            }
        }
    }

    fun checkUpdates() {
        if (checking) return
        checking = true
        release = null
        status = "Controllo le release su GitHub…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchLatestRelease(channel) }
                release = result
                HaliMeoUpdateState.release = result
                HaliMeoUpdateState.checkedAt = System.currentTimeMillis()
                notifyUpdateAvailable(context, result)
                status = when {
                    result == null && channel == "stable" -> "Non è ancora stata pubblicata una versione stabile."
                    result == null -> "Nessuna release Alpha disponibile."
                    result.versionCode <= BuildConfig.VERSION_CODE.toLong() -> "Hai già installato l'ultima versione di questo canale."
                    else -> "È disponibile un aggiornamento."
                }
            } catch (error: Exception) { status = "Controllo non riuscito: ${error.message ?: "verifica la connessione"}" }
            finally { checking = false }
        }
    }

    fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${BuildConfig.APPLICATION_ID}")))
                status = "Consenti a HaliMeo di installare APK e poi premi Installa. Il download è già pronto."
            } else {
                activity.startActivity(Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                })
                status = "Conferma l'installazione nella finestra di Android."
            }
        } catch (error: Exception) { status = "Impossibile avviare l'installazione: ${error.message ?: "errore imprevisto"}" }
    }

    fun startDownload(item: HaliMeoRelease) {
        if (downloading) return
        scope.launch {
            try {
                val request = DownloadManager.Request(Uri.parse(item.apkUrl))
                    .setTitle("Aggiornamento HaliMeo ${item.versionName}")
                    .setDescription("Download APK in background")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                    .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, item.apkName)
                val id = withContext(Dispatchers.IO) { manager.enqueue(request) }
                prefs.edit().putLong(PREF_DOWNLOAD_ID, id).apply()
                downloadId = id
                downloadProgress = 0f
                downloadedFile = null
                downloadFailed = false
                downloading = true
                status = "Download avviato. Continuerà anche cambiando pagina o chiudendo l'updater."
            } catch (error: Exception) {
                downloading = false
                status = "Impossibile avviare il download: ${error.message ?: "errore imprevisto"}"
            }
        }
    }

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.62f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)) {
                Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp).size(19.dp))
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
            Switch(checked = autoCheck, onCheckedChange = { autoCheck = it; prefs.edit().putBoolean("auto_check_updates", it).apply() })
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f), RoundedCornerShape(22.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f), RoundedCornerShape(22.dp)).padding(4.dp), space = 3.dp) {
            listOf("alpha" to "Alpha", "stable" to "Stabile").forEachIndexed { index, option ->
                SegmentedButton(
                    selected = channel == option.first,
                    onClick = { channel = option.first; prefs.edit().putString("update_channel", channel).apply(); release = null; downloadedFile = null; status = if (channel == "stable") "Canale Stabile selezionato." else "Canale Alpha selezionato." },
                    shape = SegmentedButtonDefaults.itemShape(index, 2, RoundedCornerShape(18.dp)),
                    colors = SegmentedButtonDefaults.colors(activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), activeContentColor = MaterialTheme.colorScheme.onSurface, activeBorderColor = Color.Transparent, inactiveContainerColor = Color.Transparent, inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant, inactiveBorderColor = Color.Transparent)
                ) { Text(option.second) }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
            Text(status, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        release?.let { item ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (item.versionCode > BuildConfig.VERSION_CODE.toLong()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Aggiornamento disponibile", Modifier.padding(horizontal = 12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("v${item.versionName}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    val ready = downloadedFile?.exists() == true
                    Button(onClick = { if (ready) installApk(downloadedFile!!) else if (!downloading) startDownload(item) }, enabled = ready || !downloading, modifier = Modifier.fillMaxWidth()) {
                        if (downloading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(if (ready) Icons.Default.DownloadDone else Icons.Default.Download, null)
                        Text(if (ready) "Installa" else if (downloading) "Download in corso…" else "Scarica e installa", Modifier.padding(start = 8.dp))
                    }
                    if (downloading) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Download in background", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (downloadProgress > 0f) "${(downloadProgress * 100).toInt()}%" else "In preparazione", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        LinearProgressIndicator(progress = { downloadProgress }, Modifier.fillMaxWidth().height(7.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    }
                    if (downloadFailed) Text("Il download può essere ripetuto.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        OutlinedButton(onClick = { checkUpdates() }, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
            if (checking) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Refresh, null)
            Text("Controlla aggiornamenti", Modifier.padding(start = 8.dp))
        }
        Text("Il download usa il servizio Android e continua in background anche cambiando pagina. Android chiederà sempre conferma prima di installare.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private suspend fun fetchLatestRelease(channel: String): HaliMeoRelease? {
    val endpoint = "https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=100"
    val json = withContext(Dispatchers.IO) {
        var response: String? = null
        var lastError: Exception? = null
        for (attempt in 1..2) {
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000; readTimeout = 18_000; instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "HaliMeo-Android-Updater")
                setRequestProperty("Cache-Control", "no-cache")
            }
            try {
                if (connection.responseCode !in 200..299) error("GitHub ha risposto ${connection.responseCode}")
                response = connection.inputStream.bufferedReader().use { it.readText() }
                break
            } catch (error: Exception) { lastError = error; if (attempt < 2) Thread.sleep(600L) }
            finally { connection.disconnect() }
        }
        response ?: throw (lastError ?: java.io.IOException("Risposta GitHub non disponibile"))
    }
    val releases = JSONArray(json)
    val candidates = mutableListOf<HaliMeoRelease>()
    for (i in 0 until releases.length()) {
        val item = releases.getJSONObject(i)
        if (item.optBoolean("draft", false)) continue
        val pre = item.optBoolean("prerelease", false)
        val tag = item.optString("tag_name")
        val matches = if (channel == "alpha") pre && tag.startsWith("alpha-build-") else !pre && tag.startsWith("stable-build-")
        if (!matches) continue
        val assets = item.optJSONArray("assets") ?: continue
        for (j in 0 until assets.length()) {
            val asset = assets.getJSONObject(j)
            val name = asset.optString("name")
            if (!name.endsWith(".apk", true)) continue
            val build = Regex("build(\\d+)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: continue
            val version = Regex("(\\d+\\.\\d+\\.\\d+)").find(name)?.groupValues?.getOrNull(1) ?: item.optString("name")
            val body = item.optString("body", "").replace(Regex("(?m)^#{1,6}\\s*"), "").replace("*", "").trim()
            candidates += HaliMeoRelease(item.optString("name", "HaliMeo $version"), build, version, body, asset.optString("browser_download_url"), name, asset.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"))
        }
    }
    return candidates.maxByOrNull { it.versionCode }
}
