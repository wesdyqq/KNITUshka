package com.qq.knitushka

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.qq.knitushka.data.ScheduleRepository
import com.qq.knitushka.data.SourceFormatException
import java.io.IOException
import java.util.concurrent.TimeUnit

class KnituApplication : Application() {
    val repository by lazy { ScheduleRepository(this) }

    override fun onCreate() {
        super.onCreate()
        val work = PeriodicWorkRequestBuilder<ScheduleSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("schedule-sync", ExistingPeriodicWorkPolicy.KEEP, work)
    }
}

class ScheduleSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as KnituApplication).repository
        val group = repository.selection() ?: return Result.success()
        return try {
            repository.refreshWindow(group)
            Result.success()
        } catch (_: IOException) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        } catch (_: SourceFormatException) {
            Result.failure()
        }
    }
}
