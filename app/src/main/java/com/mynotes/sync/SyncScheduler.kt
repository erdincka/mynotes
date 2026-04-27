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
        val accessToken = settingsRepository.onedriveAccessToken.first()
        val folderId = settingsRepository.onedriveFolderId.first()

        if (accessToken.isNullOrEmpty() || folderId.isNullOrEmpty()) {
            WorkManager.getInstance(context).cancelUniqueWork("onedrive_sync")
            return
        }

        val data = Data.Builder()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_ONEDRIVE_FOLDER_ID, folderId)
            .build()

        if (immediate) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "onedrive_sync_immediate",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "onedrive_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
