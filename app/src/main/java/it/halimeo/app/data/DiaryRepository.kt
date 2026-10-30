package it.halimeo.app.data

import android.content.Context
import android.net.Uri
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
        val destination = File(photoDir, "$id-${System.currentTimeMillis()}.img")
        appContext.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Impossibile leggere la foto selezionata" }
            destination.outputStream().use { output -> input.copyTo(output) }
        }
        return Uri.fromFile(destination).toString()
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
            old?.photoUri?.let { previous -> runCatching { File(Uri.parse(previous).path ?: "").delete() } }
        }
        dao.upsert(DiaryEntry(
            id=entryId, title=title.trim(), body=body.trim(),
            category=category.trim().ifBlank { "Ricordo" }, memoryDate=date,
            createdAt=old?.createdAt ?: now, updatedAt=now, deletedAt=null,
            syncState="PENDING", photoUri=storedPhoto,
            photoUpdatedAt=if (photoChanged || removedPhoto) now else old?.photoUpdatedAt ?: 0L,
            photoDeletedAt=if (removedPhoto) now else if (photoChanged) null else old?.photoDeletedAt,
            photoSyncState=if (photoChanged || removedPhoto) "PENDING" else old?.photoSyncState ?: "SYNCED"
        ))
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

        // First exchange entry metadata. Photo revisions are independent from text revisions.
        val pending = dao.pending()
        val response = api.sync(auth, SyncRequest(pending.map { it.toDto() }))
        val remoteById = response.changes.associateBy { it.id }
        for (dto in response.changes) {
            val local = dao.get(dto.id)
            if (local != null && local.updatedAt > dto.updatedAt) continue
            val remotePhotoIsNewer = dto.photoUpdatedAt > (local?.photoUpdatedAt ?: 0L)
            val preserveLocalPhoto = local != null && !remotePhotoIsNewer
            dao.upsert(dto.toEntity().copy(
                photoUri = if (preserveLocalPhoto) local?.photoUri else null,
                photoUpdatedAt = maxOf(dto.photoUpdatedAt, local?.photoUpdatedAt ?: 0L),
                photoDeletedAt = if (remotePhotoIsNewer) dto.photoDeletedAt else local?.photoDeletedAt,
                photoSyncState = if (preserveLocalPhoto) local?.photoSyncState ?: "SYNCED" else "PENDING"
            ))
        }
        pending.forEach { entry ->
            val remote = remoteById[entry.id]
            if (remote != null && remote.updatedAt >= entry.updatedAt) dao.markSynced(entry.id, entry.updatedAt)
        }

        // Upload or propagate a photo deletion only after the entry exists on the server.
        for (entry in dao.pendingPhotos()) {
            if (entry.deletedAt != null) continue
            if (entry.photoDeletedAt != null && (entry.photoUri == null || entry.photoUpdatedAt == entry.photoDeletedAt)) {
                api.deletePhoto(auth, entry.id)
                dao.markPhotoSynced(entry.id, entry.photoUpdatedAt)
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
            api.uploadPhoto(auth, entry.id, part)
            dao.markPhotoSynced(entry.id, entry.photoUpdatedAt)
        }

        // Download remote photo revisions after uploads, keeping the file app-private for offline use.
        for (dto in response.changes) {
            val local = dao.get(dto.id) ?: continue
            if (dto.photoUpdatedAt <= local.photoUpdatedAt || dto.photoDeletedAt != null) {
                if (dto.photoDeletedAt != null && dto.photoDeletedAt >= local.photoUpdatedAt) {
                    local.photoUri?.let { runCatching { File(Uri.parse(it).path ?: "").delete() } }
                    dao.upsert(local.copy(photoUri=null, photoUpdatedAt=dto.photoUpdatedAt,
                        photoDeletedAt=dto.photoDeletedAt, photoSyncState="SYNCED"))
                }
                continue
            }
            val downloaded = api.downloadPhoto(auth, dto.id)
            val destination = File(photoDir, "${dto.id}-remote-${dto.photoUpdatedAt}.img")
            downloaded.byteStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
            local.photoUri?.let { runCatching { File(Uri.parse(it).path ?: "").delete() } }
            dao.upsert(local.copy(photoUri=Uri.fromFile(destination).toString(),
                photoUpdatedAt=dto.photoUpdatedAt, photoDeletedAt=null, photoSyncState="SYNCED"))
        }
    }

    suspend fun allForBackup()=dao.allForBackup()
}
