package it.halimeo.app.data

import android.content.Context
import android.net.Uri
import android.graphics.BitmapFactory
import it.halimeo.app.network.ApiClient
import it.halimeo.app.sync.SyncScheduler
import it.halimeo.app.network.SyncRequest
import it.halimeo.app.network.SocialFeedResponse
import it.halimeo.app.network.SocialComment
import it.halimeo.app.network.SocialFeedEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import it.halimeo.app.network.SocialCommentRequest
import it.halimeo.app.network.SocialCommentResponse
import it.halimeo.app.network.SocialAlbum
import it.halimeo.app.network.SocialAlbumsResponse
import it.halimeo.app.network.AlbumRequest
import it.halimeo.app.network.AlbumEntryRequest
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

class DiaryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = HaliMeoDatabase.get(appContext).diaryDao()
    private val session = SessionStore(appContext)
    private val photoDir = File(appContext.filesDir, "diary_photos").apply { mkdirs() }
    private val profilePhotoCache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val profilePhotoLoadMutex = Mutex()
    val entries: Flow<List<DiaryEntry>> = dao.observeAll()

    private fun importPhoto(uri: String, id: String): String {
        val source = Uri.parse(uri)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Impossibile leggere la foto selezionata" }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Il formato della foto non è supportato"
        }

        // Normalize every selected image to a reasonably sized JPEG before upload.
        // Full-resolution phone photos can exceed the server's upload limit.
        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 2560) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        val bitmap = appContext.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Impossibile leggere la foto selezionata" }
            BitmapFactory.decodeStream(input, null, options)
        } ?: throw IllegalArgumentException("Impossibile decodificare la foto selezionata")

        val destination = File(photoDir, "$id-${System.currentTimeMillis()}.jpg")
        try {
            destination.outputStream().use { output ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)) {
                    "Impossibile preparare la foto per la sincronizzazione"
                }
            }
        } finally {
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
        val accountId = session.userId()
        val old = id?.let { dao.get(it) }
        if (old != null && (old.ownerId.isBlank() || old.ownerId != accountId)) {
            throw IllegalStateException("Solo il creatore di questo ricordo può modificarlo.")
        }
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
            ownerId=old?.ownerId?.takeIf { it.isNotBlank() } ?: accountId,
            photoUpdatedAt=if (photoChanged || removedPhoto) now else old?.photoUpdatedAt ?: 0L,
            photoDeletedAt=if (removedPhoto) now else if (photoChanged) null else old?.photoDeletedAt,
            photoSyncState=if (photoChanged || removedPhoto) "PENDING" else old?.photoSyncState ?: "SYNCED"
        ))
    }

    suspend fun delete(id:String) {
        val old=dao.get(id) ?: return
        val accountId = session.userId()
        if (old.ownerId.isBlank() || old.ownerId != accountId) {
            throw IllegalStateException("Solo il creatore di questo ricordo può eliminarlo.")
        }
        val now=System.currentTimeMillis()
        dao.upsert(old.copy(updatedAt=now,deletedAt=now,syncState="PENDING"))
    }

    suspend fun hasPendingChanges(): Boolean {
        val accountId = session.userId()
        dao.assignLegacySocialActions(accountId)
        return dao.pending().any { it.ownerId.isBlank() || it.ownerId == accountId } ||
            dao.pendingPhotos().any { it.ownerId.isBlank() || it.ownerId == accountId } ||
            dao.pendingSocialActions(accountId).isNotEmpty()
    }

    suspend fun syncNow() {
        val token = session.token() ?: return
        val accountId = session.userId()
        val auth = "Bearer $token"
        val api = ApiClient.api

        // Ensure every photo mutation has its parent entry on the server first.
        // A photo can remain pending after its text row was marked SYNCED (for example
        // after a transient upload failure or a server database reset).
        val pending = dao.pending().map { entry ->
            if (entry.ownerId.isBlank()) entry.copy(ownerId = accountId) else entry
        }.filter { it.ownerId == accountId }
        if (pending.isNotEmpty()) dao.upsertAll(pending)
        val pendingPhotoEntries = dao.pendingPhotos().map { entry ->
            if (entry.ownerId.isBlank()) entry.copy(ownerId = accountId) else entry
        }.filter { it.ownerId == accountId && it.deletedAt == null }
        if (pendingPhotoEntries.isNotEmpty()) dao.upsertAll(pendingPhotoEntries)
        val metadataToSync = (pending + pendingPhotoEntries).distinctBy { it.id }
        val response = api.sync(auth, SyncRequest(metadataToSync.map { it.toDto() }))
        val remoteById = response.changes.associateBy { it.id }
        for (dto in response.changes) {
            val local = dao.get(dto.id)
            val localTextIsNewer = local != null && local.ownerId == accountId &&
                dto.ownerId == accountId && local.updatedAt > dto.updatedAt
            val remotePhotoIsNewer = dto.photoUpdatedAt > (local?.photoUpdatedAt ?: 0L)
            val serverPhotoUri = if (dto.photoUpdatedAt > 0L && dto.photoDeletedAt == null) {
                remotePhotoUri(dto.id, dto.photoUpdatedAt)
            } else null

            // Previously downloaded server photos are migrated to a lightweight remote
            // reference. Pending local uploads are deliberately kept on-device until sent.
            if (localTextIsNewer && !remotePhotoIsNewer && local != null) {
                val hasPendingLocalDeletion = local.photoUri == null && local.photoDeletedAt != null
                val hasNoPendingLocalFile = local.photoUri.isNullOrBlank() ||
                    local.photoUri.startsWith(REMOTE_PHOTO_PREFIX)
                if (!hasPendingLocalDeletion &&
                    (local.photoSyncState == "SYNCED" || hasNoPendingLocalFile) &&
                    (local.photoUri != serverPhotoUri || local.photoSyncState != "SYNCED")
                ) {
                    deleteManagedPhoto(local.photoUri)
                    dao.upsert(local.copy(
                        ownerId = dto.ownerId,
                        photoUri = serverPhotoUri,
                        photoUpdatedAt = maxOf(dto.photoUpdatedAt, local.photoUpdatedAt),
                        photoDeletedAt = dto.photoDeletedAt ?: local.photoDeletedAt,
                        photoSyncState = "SYNCED"
                    ))
                }
                continue
            }

            val keepPendingLocalPhoto = local != null &&
                local.ownerId == accountId && dto.ownerId == accountId &&
                !remotePhotoIsNewer &&
                local.photoSyncState != "SYNCED" &&
                !local.photoUri.isNullOrBlank() &&
                !local.photoUri.startsWith(REMOTE_PHOTO_PREFIX)
            val merged = if (localTextIsNewer && local != null) local.copy(
                ownerId = dto.ownerId,
                photoUri = if (keepPendingLocalPhoto) local.photoUri else serverPhotoUri,
                photoUpdatedAt = maxOf(dto.photoUpdatedAt, local.photoUpdatedAt),
                photoDeletedAt = if (remotePhotoIsNewer) dto.photoDeletedAt else local.photoDeletedAt,
                photoSyncState = if (keepPendingLocalPhoto) local.photoSyncState else "SYNCED"
            ) else dto.toEntity().copy(
                photoUri = if (keepPendingLocalPhoto) local?.photoUri else serverPhotoUri,
                photoUpdatedAt = maxOf(dto.photoUpdatedAt, local?.photoUpdatedAt ?: 0L),
                photoDeletedAt = if (remotePhotoIsNewer) dto.photoDeletedAt else local?.photoDeletedAt,
                photoSyncState = if (keepPendingLocalPhoto) local?.photoSyncState ?: "PENDING" else "SYNCED"
            )
            if (!keepPendingLocalPhoto) deleteManagedPhoto(local?.photoUri)
            dao.upsert(merged)
        }
        pending.forEach { entry ->
            val remote = remoteById[entry.id]
            if (remote != null && remote.updatedAt >= entry.updatedAt) dao.markSynced(entry.id, entry.updatedAt)
        }

        // Upload or propagate a photo deletion only after the sync response confirms
        // that the parent entry is present remotely. Avoid opaque 404s from /photo.
        for (entry in dao.pendingPhotos().filter { it.ownerId == accountId }) {
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
            if (result["ignored"] == true) {
                val confirmed = (result["photoUpdatedAt"] as? Number)?.toLong()
                if (confirmed != null && confirmed >= entry.photoUpdatedAt) {
                    dao.markPhotoUploaded(entry.id, entry.photoUpdatedAt, confirmed, remotePhotoUri(entry.id, confirmed))
                    deleteManagedPhoto(localUri)
                } else {
                    dao.markPhotoSynced(entry.id, entry.photoUpdatedAt)
                }
            } else {
                val confirmed = (result["photoUpdatedAt"] as? Number)?.toLong() ?: entry.photoUpdatedAt
                dao.markPhotoUploaded(entry.id, entry.photoUpdatedAt, confirmed, remotePhotoUri(entry.id, confirmed))
                deleteManagedPhoto(localUri)
            }
        }

        // Remote photos are not prefetched or persisted. Their image bytes are requested
        // only when a visible diary item needs them, and kept in the in-memory bitmap cache.
        for (dto in response.changes) {
            val local = dao.get(dto.id) ?: continue
            if (dto.photoDeletedAt != null && dto.photoDeletedAt >= local.photoUpdatedAt) {
                deleteManagedPhoto(local.photoUri)
                dao.upsert(local.copy(photoUri = null, photoUpdatedAt = dto.photoUpdatedAt,
                    photoDeletedAt = dto.photoDeletedAt, photoSyncState = "SYNCED"))
            }
        }

        // Replay queued likes and comments only after their diary entry is confirmed on the server.
        dao.assignLegacySocialActions(accountId)
        val socialActions = dao.pendingSocialActions(accountId)
        for (action in socialActions) {
            val entry = dao.get(action.entryId)
            if (entry == null || entry.deletedAt != null || entry.syncState != "SYNCED") continue
            val sent = runCatching {
                when (action.type) {
                    "LIKE" -> api.likeEntry(auth, action.entryId)
                    "UNLIKE" -> api.unlikeEntry(auth, action.entryId)
                    "COMMENT" -> api.addComment(auth, action.entryId, SocialCommentRequest(action.payload, action.id))
                    "DELETE_COMMENT" -> api.deleteComment(auth, action.payload)
                    else -> emptyMap<String, Any>()
                }
            }.isSuccess
            if (sent) dao.deleteSocialAction(action.id)
        }
    }

    private suspend fun authorizedHeader(): String {
        val token = session.token() ?: throw IllegalStateException("Accedi per usare le funzioni social.")
        return "Bearer $token"
    }

    /**
     * Fetches a single image only when its diary item is actually displayed.
     * The response is consumed in memory and is never written to the photo directory.
     */
    suspend fun loadRemotePhotoBytes(entryId: String, maxDimension: Int): ByteArray? = try {
        ApiClient.api.downloadPhoto(authorizedHeader(), entryId, maxDimension).use { response ->
            response.byteStream().use { input -> input.readBytes() }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    suspend fun loadSocialFeed(): SocialFeedResponse {
        val response = ApiClient.api.socialFeed(authorizedHeader())
        val actions = dao.pendingSocialActions(session.userId()).groupBy { it.entryId }
        val userId = session.userId()
        val authorName = session.name().ifBlank { "Tu" }
        return response.copy(entries = response.entries.map { entry ->
            val pending = actions[entry.id].orEmpty()
            val likeAction = pending.lastOrNull { it.type == "LIKE" || it.type == "UNLIKE" }
            val deletedCommentIds = pending.filter { it.type == "DELETE_COMMENT" }.map { it.payload }.toSet()
            val comments = pending.filter { it.type == "COMMENT" }.map { action ->
                SocialComment(action.id, action.payload, userId, authorName, action.createdAt, action.createdAt)
            }
            val uniquePendingComments = comments.filterNot { pendingComment -> entry.comments.any { it.id == pendingComment.id } }
            val visibleComments = entry.comments.filterNot { it.id in deletedCommentIds } + uniquePendingComments
            val visibleCommentCount = (entry.commentCount - deletedCommentIds.size + uniquePendingComments.size).coerceAtLeast(0)
            when (likeAction?.type) {
                "LIKE" -> entry.copy(likeCount = if (entry.likedByMe) entry.likeCount else entry.likeCount + 1, likedByMe = true, commentCount = visibleCommentCount, comments = visibleComments)
                "UNLIKE" -> entry.copy(likeCount = (entry.likeCount - if (entry.likedByMe) 1 else 0).coerceAtLeast(0), likedByMe = false, commentCount = visibleCommentCount, comments = visibleComments)
                else -> entry.copy(commentCount = visibleCommentCount, comments = visibleComments)
            }
        })
    }

    suspend fun setEntryLiked(entryId: String, liked: Boolean): Map<String, Any> {
        val auth = authorizedHeader()
        val accountId = session.userId()
        dao.clearPendingLikeActions(entryId, accountId)
        return runCatching {
            if (liked) ApiClient.api.likeEntry(auth, entryId) else ApiClient.api.unlikeEntry(auth, entryId)
        }.getOrElse {
            dao.upsertSocialAction(PendingSocialAction(UUID.randomUUID().toString(), entryId, if (liked) "LIKE" else "UNLIKE", createdAt = System.currentTimeMillis(), accountId = accountId))
            SyncScheduler.requestImmediate(appContext)
            mapOf("queued" to true)
        }
    }

    suspend fun addEntryComment(entryId: String, body: String): SocialCommentResponse {
        require(body.isNotBlank()) { "Scrivi un commento prima di inviarlo." }
        val auth = authorizedHeader()
        val cleanBody = body.trim()
        val clientId = UUID.randomUUID().toString()
        return runCatching {
            ApiClient.api.addComment(auth, entryId, SocialCommentRequest(cleanBody, clientId))
        }.getOrElse {
            val now = System.currentTimeMillis()
            dao.upsertSocialAction(PendingSocialAction(clientId, entryId, "COMMENT", cleanBody, now, session.userId()))
            SyncScheduler.requestImmediate(appContext)
            SocialCommentResponse(clientId, entryId, cleanBody, session.userId(), session.name().ifBlank { "Tu" }, now, now)
        }
    }

    suspend fun loadEntryComments(entryId: String): List<SocialComment> {
        val auth = authorizedHeader()
        val remote = runCatching { ApiClient.api.comments(auth, entryId)["comments"].orEmpty() }.getOrDefault(emptyList())
        val actions = dao.pendingSocialActions(session.userId()).filter { it.entryId == entryId }
        val deletedIds = actions.filter { it.type == "DELETE_COMMENT" }.map { it.payload }.toSet()
        val pending = actions.filter { it.type == "COMMENT" }.map { action ->
            SocialComment(action.id, action.payload, session.userId(), session.name().ifBlank { "Tu" }, action.createdAt, action.createdAt)
        }
        return remote.filterNot { it.id in deletedIds } + pending.filterNot { local -> remote.any { it.id == local.id } }
    }

    suspend fun loadLocalSocialFeed(): List<SocialFeedEntry> {
        val userId = session.userId()
        val authorName = session.name().ifBlank { "Tu" }
        val actions = dao.pendingSocialActions(userId).groupBy { it.entryId }
        return dao.observeAll().first().map { entry ->
            val pending = actions[entry.id].orEmpty()
            val likeAction = pending.lastOrNull { it.type == "LIKE" || it.type == "UNLIKE" }
            val comments = pending.filter { it.type == "COMMENT" }.map { action ->
                SocialComment(action.id, action.payload, userId, authorName, action.createdAt, action.createdAt)
            }
            val deletedCount = pending.count { it.type == "DELETE_COMMENT" }
            SocialFeedEntry(
                id = entry.id, title = entry.title, body = entry.body, category = entry.category,
                memoryDate = entry.memoryDate, createdAt = entry.createdAt, updatedAt = entry.updatedAt,
                authorId = userId, authorName = authorName,
                likeCount = if (likeAction?.type == "LIKE") 1 else 0,
                likedByMe = likeAction?.type == "LIKE",
                commentCount = (comments.size - deletedCount).coerceAtLeast(0),
                comments = comments
            )
        }
    }

    suspend fun deleteEntryComment(entryId: String, commentId: String): Map<String, Boolean> {
        val accountId = session.userId()
        val pending = dao.pendingComments(entryId, accountId).firstOrNull { it.id == commentId }
        if (pending != null) {
            dao.deleteSocialAction(commentId)
            return mapOf("deleted" to true)
        }
        return runCatching { ApiClient.api.deleteComment(authorizedHeader(), commentId) }.getOrElse {
            dao.upsertSocialAction(PendingSocialAction(UUID.randomUUID().toString(), entryId, "DELETE_COMMENT", commentId, System.currentTimeMillis(), accountId))
            SyncScheduler.requestImmediate(appContext)
            mapOf("deleted" to true)
        }
    }

    suspend fun uploadProfilePhoto(uri: String) {
        val token = session.token() ?: throw IllegalStateException("Accedi per aggiornare la foto profilo.")
        val parsed = Uri.parse(uri)
        val sourceBytes = appContext.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Impossibile leggere la foto selezionata.")
        require(sourceBytes.isNotEmpty() && sourceBytes.size <= 20 * 1024 * 1024) {
            "La foto selezionata è troppo grande."
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Il formato della foto profilo non è supportato."
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > 1024) {
            sampleSize *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            sourceBytes, 0, sourceBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize; inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888 }
        ) ?: throw IllegalArgumentException("Impossibile decodificare la foto profilo.")
        val normalized = java.io.ByteArrayOutputStream().use { output ->
            try {
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, output)) {
                    "Impossibile preparare la foto profilo."
                }
                output.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
        require(normalized.size <= 5 * 1024 * 1024) { "La foto profilo preparata supera 5 MB." }
        val part = MultipartBody.Part.createFormData(
            "photo", "profile-${System.currentTimeMillis()}.jpg",
            normalized.toRequestBody("image/jpeg".toMediaType())
        )
        ApiClient.api.uploadProfilePhoto("Bearer $token", part)
        profilePhotoCache.remove(session.userId())
    }

    suspend fun loadProfilePhotoBytes(userId: String): ByteArray? =
        profilePhotoLoadMutex.withLock {
            profilePhotoCache[userId]?.let { return@withLock it }
            try {
                val token = session.token() ?: return@withLock null
                val bytes = ApiClient.api.downloadProfilePhoto("Bearer $token", userId).use { response ->
                    response.byteStream().use { it.readBytes() }
                }
                if (bytes.isNotEmpty()) profilePhotoCache[userId] = bytes
                bytes
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    suspend fun currentUserId() = session.userId()

    suspend fun loadAlbums(): SocialAlbumsResponse = ApiClient.api.albums(authorizedHeader())

    suspend fun loadAlbum(albumId: String) = ApiClient.api.album(authorizedHeader(), albumId)

    suspend fun createAlbum(title: String, description: String = ""): SocialAlbum =
        ApiClient.api.createAlbum(authorizedHeader(), AlbumRequest(title, description))

    suspend fun addEntryToAlbum(albumId: String, entryId: String) =
        ApiClient.api.addAlbumEntry(authorizedHeader(), albumId, AlbumEntryRequest(entryId))

    suspend fun removeEntryFromAlbum(albumId: String, entryId: String) =
        ApiClient.api.removeAlbumEntry(authorizedHeader(), albumId, entryId)

    suspend fun allForBackup()=dao.allForBackup()
}
