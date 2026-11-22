package com.example.data.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MochiApplication

class LibraryScanWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? MochiApplication ?: return Result.failure()
        return runCatching {
            app.container.libraryRepository.rescanAllFolders()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}
