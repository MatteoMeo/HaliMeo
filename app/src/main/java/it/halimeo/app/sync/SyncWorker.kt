package it.halimeo.app.sync
import android.content.Context
import androidx.work.*
import it.halimeo.app.data.DiaryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p) {
    override suspend fun doWork()=withContext(Dispatchers.IO) {
        try { DiaryRepository(applicationContext).syncNow(); Result.success() }
        catch (_:Exception) { if(runAttemptCount<5) Result.retry() else Result.failure() }
    }
}
object SyncScheduler {
    fun schedule(c:Context) {
        val work=PeriodicWorkRequestBuilder<SyncWorker>(15,java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("halimeo-sync",ExistingPeriodicWorkPolicy.UPDATE,work)
    }
}
