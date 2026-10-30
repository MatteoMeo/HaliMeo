package it.halimeo.app.data

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import it.halimeo.app.network.ApiClient
import it.halimeo.app.network.SyncRequest
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.UUID

class DiaryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = HaliMeoDatabase.get(appContext).diaryDao()
    private val session = SessionStore(appContext)
    private val photoDir = File(appContext.filesDir, "diary_photos").apply { mkdirs() }
    val entries: Flow<List<DiaryEntry>> = dao.observeAll()

    private fun importPhoto(uri: String, id: String): String {
        val source = Uri.parse(uri)
        val mime = appContext.contentResolver.getType(source)?.lowercase()
        val extension = when (mime) {
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            else -> ".jpg"
        }
        val destination = File(photoDir, "$id-${System.currentTimeMillis()}$extension")
        if (mime in setOf("image/jpeg", "image/jpg", "image/png", "image/webp")) {
            appContext.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Impossibile leggere la foto selezionata" }
                destination.outputStream().use { output -> input.copyTo(output) }
            }
        } else {
            val bitmap = appContext.contentResolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Impossibile leggere la foto selezionata" }
                BitmapFactory.decodeStream(input)
            } ?: throw IllegalArgumentException("Il formato della foto non è supportato")
            destination.outputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, output)
            }
            bitmap.recycle()
        }
        return Uri.fromFile(destination).toString()
    }

    private fun deleteManagedPhoto(uri: String?) {
        if (uri.isNullOrBlank()) return
        val parsed = Uri.parse(uri)
        val path = parsed.path ?: return
        if (parsed.scheme == "file" && File(path).parentFile?.canonicalFile == photoDir.canonicalFile) {
            File(path).delete()
        }
    }

    suspend fun save(title:String, body:String, category:String, date:String, id:String?=null, photoUri:String?=null) {
        val now = System.currentTimeMillis()
        val old = id?.let { dao.get(it) }
        val entryId = old?.id ?: UUID.randomUUID().toString()
        val photoChanged = photoUri != null && photoUri != old?.photoUri
        val removedPhoto = photoUri == "" && old?.photoUri != null
        val storedPhoto = when {
            photoUri == "" -> null
            photoChanged -> importPhoto(photoUri!!, entryId)
            else -> old?.photoUri
        }
        if (photoChanged || removedPhoto) {
            deleteManagedPhoto(old?.photoUri)
        }
        dao.upsert(DiaryEntry(
            id=entryId, title=title.trim(), body=body.trim(),
            category=category.trim().ifBlank { "Ricordo" }, memoryDate=date,
            createdAt=old?.createdAt ?: now, updatedAt=now, deletedAt=null,
            syncState="PENDING", photoUri=storedPhoto,
            photoUpdatedAt=if (photoChanged || removedPhoto) now else old?.photoUpdatedAt ?: 0L,
            photoDeletedAt=if (removedPhoto) now else if (photoChanged) null else old?.photoDeletedAt,
            photoSyncState=if (photoChanged || removedPhoto) "PENDING" else old?.photoSyncState ?: "SYNCED",
            isFavorite=old?.isFavorite ?: false
        ))
    }

    suspend fun toggleFavorite(id: String) {
        dao.toggleFavorite(id)
    }

    suspend fun setFavorite(id: String, favorite: Boolean) {
        dao.setFavorite(id, favorite)
    }

    suspend fun delete(id:String) {
        val old=dao.get(id) ?: return
        val now=System.currentTimeMillis()
        dao.upsert(old.copy(updatedAt=now,deletedAt=now,syncState="PENDING"))
    }

    suspend fun syncNow() {
        val token = session.token() ?: return
        val auth = "Bearer $token"
        val api = ApiClient.api

        // Ensure every photo mutation has its parent entry on the server first.
        // A photo can remain pending after its text row was marked SYNCED (for example
        // after a transient upload failure or a server database reset).
        val pending = dao.pending()
        val pendingPhotoEntries = dao.pendingPhotos().filter { it.deletedAt == null }
        val metadataToSync = (pending + pendingPhotoEntries).distinctBy { it.id }
        val response = api.sync(auth, SyncRequest(metadataToSync.map { it.toDto() }))
        val remoteById = response.changes.associateBy { it.id }
        val photosToDownload = mutableSetOf<String>()
        for (dto in response.changes) {
            val local = dao.get(dto.id)
            val localTextIsNewer = local != null && local.updatedAt > dto.updatedAt
            val remotePhotoIsNewer = dto.photoUpdatedAt > (local?.photoUpdatedAt ?: 0L)
            if (localTextIsNewer && !remotePhotoIsNewer) continue
            val preserveLocalPhoto = local != null && !remotePhotoIsNewer
            if (remotePhotoIsNewer && dto.photoDeletedAt == null) photosToDownload += dto.id
            val merged = if (localTextIsNewer && local != null) local.copy(
                photoUri = if (preserveLocalPhoto) local.photoUri else null,
                photoUpdatedAt = maxOf(dto.photoUpdatedAt, local.photoUpdatedAt),
                photoDeletedAt = if (remotePhotoIsNewer) dto.photoDeletedAt else local.photoDeletedAt,
                photoSyncState = if (preserveLocalPhoto) local.photoSyncState else "PENDING"
            ) else dto.toEntity().copy(
                photoUri = if (preserveLocalPhoto) local?.photoUri else null,
                photoUpdatedAt = maxOf(dto.photoUpdatedAt, local?.photoUpdatedAt ?: 0L),
                photoDeletedAt = if (remotePhotoIsNewer) dto.photoDeletedAt else local?.photoDeletedAt,
                photoSyncState = if (preserveLocalPhoto) local?.photoSyncState ?: "SYNCED" else if (dto.photoUpdatedAt == 0L || dto.photoDeletedAt != null) "SYNCED" else "PENDING",
                isFavorite = local?.isFavorite ?: false
            )
            dao.upsert(merged)
        }
        pending.forEach { entry ->
            val remote = remoteById[entry.id]
            if (remote != null && remote.updatedAt >= entry.updatedAt) dao.markSynced(entry.id, entry.updatedAt)
        }

        // Upload or propagate a photo deletion only after the sync response confirms
        // that the parent entry is present remotely. Avoid opaque 404s from /photo.
        for (entry in dao.pendingPhotos()) {
            if (entry.deletedAt != null) continue
            if (!remoteById.containsKey(entry.id)) {
                throw IllegalStateException("Il ricordo non è stato confermato dal server: foto non caricata.")
            }
            if (entry.photoDeletedAt != null && (entry.photoUri == null || entry.photoUpdatedAt == entry.photoDeletedAt)) {
                val result = api.deletePhoto(auth, entry.id, entry.photoUpdatedAt)
                if (result["ignored"] == true) dao.markPhotoSynced(entry.id, entry.photoUpdatedAt)
                else {
                    val confirmed = (result["photoDeletedAt"] as? Number)?.toLong() ?: entry.photoUpdatedAt
                    dao.confirmPhotoRevision(entry.id, entry.photoUpdatedAt, confirmed, confirmed)
                }
                continue
            }
            val localUri = entry.photoUri ?: continue
            val file = File(Uri.parse(localUri).path ?: "")
            if (!file.isFile) continue // Keep pending; the next WorkManager retry can recover.
            val mime = when (file.extension.lowercase()) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                else -> "application/octet-stream"
            }
            val body = file.asRequestBody(mime.toMediaType())
            val part = MultipartBody.Part.createFormData("photo", file.name, body)
            val result = api.uploadPhoto(auth, entry.id, entry.photoUpdatedAt, part)
            if (result["ignored"] == true) dao.markPhotoSynced(entry.id, entry.photoUpdatedAt)
            else {
                val confirmed = (result["photoUpdatedAt"] as? Number)?.toLong() ?: entry.photoUpdatedAt
                dao.confirmPhotoRevision(entry.id, entry.photoUpdatedAt, confirmed, null)
            }
        }

        // Download remote photo revisions after uploads, keeping the file app-private for offline use.
        for (dto in response.changes) {
            val local = dao.get(dto.id) ?: continue
            if ((dto.id !in photosToDownload && dto.photoUpdatedAt <= local.photoUpdatedAt) || dto.photoDeletedAt != null) {
                if (dto.photoDeletedAt != null && dto.photoDeletedAt >= local.photoUpdatedAt) {
                    deleteManagedPhoto(local.photoUri)
                    dao.upsert(local.copy(photoUri=null, photoUpdatedAt=dto.photoUpdatedAt,
                        photoDeletedAt=dto.photoDeletedAt, photoSyncState="SYNCED"))
                }
                continue
            }
            val downloaded = api.downloadPhoto(auth, dto.id)
            val destination = File(photoDir, "${dto.id}-remote-${dto.photoUpdatedAt}.img")
            downloaded.byteStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            deleteManagedPhoto(local.photoUri)
            dao.upsert(local.copy(photoUri=Uri.fromFile(destination).toString(),
                photoUpdatedAt=dto.photoUpdatedAt, photoDeletedAt=null, photoSyncState="SYNCED"))
        }
    }

    suspend fun allForBackup()=dao.allForBackup()
}
