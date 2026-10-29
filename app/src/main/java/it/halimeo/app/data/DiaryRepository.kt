package it.halimeo.app.data

import android.content.Context
import it.halimeo.app.network.ApiClient
import it.halimeo.app.network.SyncRequest
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class DiaryRepository(context: Context) {
    private val dao = HaliMeoDatabase.get(context).diaryDao()
    private val session = SessionStore(context)
    val entries: Flow<List<DiaryEntry>> = dao.observeAll()

    suspend fun save(title:String, body:String, category:String, date:String, id:String?=null, photoUri:String?=null) {
        val now=System.currentTimeMillis()
        val old=id?.let { dao.get(it) }
        dao.upsert(DiaryEntry(old?.id ?: UUID.randomUUID().toString(), title.trim(), body.trim(), category.trim().ifBlank{"Ricordo"}, date, old?.createdAt ?: now, now, null, "PENDING", photoUri?.takeIf { it.isNotBlank() } ?: old?.photoUri))
    }
    suspend fun delete(id:String) {
        val old=dao.get(id) ?: return
        val now=System.currentTimeMillis()
        dao.upsert(old.copy(updatedAt=now,deletedAt=now,syncState="PENDING"))
    }
    suspend fun syncNow() {
        val token=session.token() ?: return
        val pending=dao.pending()
        val response=ApiClient.api.sync("Bearer $token", SyncRequest(pending.map { it.toDto() }))
        val local=pending.associateBy { it.id }
        val remoteById = response.changes.associateBy { it.id }
        dao.upsertAll(response.changes.mapNotNull { dto ->
            val localEntry=local[dto.id]
            if(localEntry!=null && localEntry.updatedAt>dto.updatedAt) null else dto.toEntity()
        })
        // Never mark a local edit as synced unless the server confirms an equal/newer version.
        pending.forEach { localEntry ->
            val remote=remoteById[localEntry.id]
            if(remote!=null && remote.updatedAt>=localEntry.updatedAt) {
                dao.markSynced(localEntry.id, localEntry.updatedAt)
            }
        }
    }
    suspend fun allForBackup()=dao.allForBackup()
}
