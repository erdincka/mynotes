package com.mynotes.sync

import android.content.Context
import androidx.work.*
import com.mynotes.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    suspend fun scheduleSync(immediate: Boolean = false) {
        val folderId = settingsRepository.onedriveFolderId.first()
        val isConnected = settingsRepository.onedriveAccessToken.first() != null

        if (!isConnected || folderId.isNullOrEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork("onedrive_sync")
            WorkManager.getInstance(context).cancelUniqueWork("onedrive_sync_immediate")
            return
        }

        // Token is no longer passed as WorkManager data; SyncWorker calls MSAL directly.
        val data = Data.Builder()
            .putString(KEY_ONEDRIVE_FOLDER_ID, folderId)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        if (immediate) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "onedrive_sync_immediate",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(data)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "onedrive_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
