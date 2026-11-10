package it.halimeo.app

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * Handles update notification actions without opening MainActivity or requiring a
 * signed-in HaliMeo account. DownloadManager owns the transfer so it survives
 * process death; this receiver only starts it and verifies the completed APK.
 */
internal class UpdateNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_DOWNLOAD -> startDownload(context, intent)
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> onDownloadComplete(
                context,
                intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            )
        }
    }

    private fun startDownload(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return

        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, -1L)
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()
        val apkUrl = intent.getStringExtra(EXTRA_APK_URL).orEmpty()
        val apkName = intent.getStringExtra(EXTRA_APK_NAME).orEmpty()
        val sha256 = intent.getStringExtra(EXTRA_SHA256).orEmpty()
        if (versionCode <= BuildConfig.VERSION_CODE.toLong() ||
            !versionName.matches(Regex("[A-Za-z0-9._-]{1,40}")) ||
            !apkUrl.startsWith("https://github.com/") ||
            !apkName.matches(Regex("HaliMeo-[A-Za-z0-9._-]+\\.apk", RegexOption.IGNORE_CASE)) ||
            !sha256.matches(Regex("[a-fA-F0-9]{64}"))
        ) {
            postMessage(context, "Download non avviato", "I dati della release non hanno superato i controlli di sicurezza.")
            return
        }

        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val previousId = prefs.getLong(PREF_DOWNLOAD_ID, -1L)
        if (previousId >= 0L) {
            val previous = queryDownload(context, previousId)
            if (previous == DownloadManager.STATUS_PENDING || previous == DownloadManager.STATUS_RUNNING || previous == DownloadManager.STATUS_PAUSED) {
                postMessage(context, "Download già in corso", "HaliMeo sta già scaricando un aggiornamento.")
                return
            }
        }

        try {
            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle("Aggiornamento HaliMeo $versionName")
                .setDescription("Download APK in background")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    apkName
                )
            val id = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            prefs.edit()
                .putLong(PREF_DOWNLOAD_ID, id)
                .putLong(PREF_VERSION_CODE, versionCode)
                .putString(PREF_VERSION_NAME, versionName)
                .putString(PREF_APK_NAME, apkName)
                .putString(PREF_SHA256, sha256.lowercase())
                .apply()
            NotificationManagerCompat.from(context).notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Download HaliMeo avviato")
                    .setContentText("Puoi continuare a usare il telefono: il download prosegue in background.")
                    .setProgress(0, 0, true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
            )
        } catch (error: Exception) {
            postMessage(context, "Download non riuscito", error.localizedMessage ?: "Riprova tra poco.")
        }
    }

    private fun onDownloadComplete(context: Context, completedId: Long) {
        if (completedId < 0L) return
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (prefs.getLong(PREF_DOWNLOAD_ID, -1L) != completedId) return

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val status = queryDownload(context, completedId)
        if (status != DownloadManager.STATUS_SUCCESSFUL) {
            prefs.edit().remove(PREF_DOWNLOAD_ID).apply()
            postMessage(context, "Download HaliMeo non riuscito", "Tocca per aprire HaliMeo e riprovare.")
            return
        }

        val apkName = prefs.getString(PREF_APK_NAME, "").orEmpty()
        val expectedHash = prefs.getString(PREF_SHA256, "").orEmpty()
        val apk = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            apkName
        )
        if (!apkName.matches(Regex("HaliMeo-[A-Za-z0-9._-]+\\.apk", RegexOption.IGNORE_CASE)) ||
            !expectedHash.matches(Regex("[a-fA-F0-9]{64}")) ||
            !apk.isFile || !sha256(apk).equals(expectedHash, ignoreCase = true)
        ) {
            apk.delete()
            prefs.edit().remove(PREF_DOWNLOAD_ID).apply()
            postMessage(context, "Verifica aggiornamento fallita", "Il file scaricato non corrisponde alla firma SHA-256 della release. Non verrà installato.")
            return
        }

        val versionName = prefs.getString(PREF_VERSION_NAME, "nuova versione").orEmpty()
        val installIntent = createInstallIntent(context, apk)
        val pending = android.app.PendingIntent.getActivity(
            context,
            NOTIFICATION_ID + 1,
            installIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        ensureChannel(context)
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("HaliMeo $versionName è pronto")
                .setContentText("Download completato e file verificato. Tocca Installa per aggiornare.")
                .setContentIntent(pending)
                .addAction(R.drawable.ic_launcher_foreground, "Installa", pending)
                .setAutoCancel(false)
                .setOngoing(false)
                .build()
        )
    }

    private fun createInstallIntent(context: Context, apk: File): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            return Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apk
        )
        return Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

    private fun queryDownload(context: Context, id: Long): Int? {
        val cursor = (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager)
            .query(DownloadManager.Query().setFilterById(id)) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun postMessage(context: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let {
            android.app.PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
        }
        NotificationManagerCompat.from(context).notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Aggiornamenti HaliMeo", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "Notifiche per scaricare e installare gli aggiornamenti di HaliMeo" }
            )
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "it.halimeo.app.action.DOWNLOAD_UPDATE"
        const val EXTRA_VERSION_CODE = "version_code"
        const val EXTRA_VERSION_NAME = "version_name"
        const val EXTRA_APK_URL = "apk_url"
        const val EXTRA_APK_NAME = "apk_name"
        const val EXTRA_SHA256 = "sha256"

        private const val CHANNEL_ID = "halimeo_updates"
        private const val NOTIFICATION_ID = 7301
        private const val PREFERENCES = "halimeo_preferences"
        private const val PREF_DOWNLOAD_ID = "update_download_id"
        private const val PREF_VERSION_CODE = "update_download_version_code"
        private const val PREF_VERSION_NAME = "update_download_version_name"
        private const val PREF_APK_NAME = "update_download_apk_name"
        private const val PREF_SHA256 = "update_download_sha256"
    }
}
