package com.mynotes.sync

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

/**
 * Handles communication with Microsoft Graph API for OneDrive storage.
 * Aligns with offline-first strategy: local DB is source of truth,
 * this client pushes/pulls state to/from cloud.
 */
class OneDriveClient @Inject constructor() {

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Uploads or updates a note in a specified OneDrive folder.
     * Uses PUT for idempotent upsert behavior.
     */
    suspend fun syncNote(
        accessToken: String,
        parentFolderId: String,
        fileName: String,
        content: ByteArray
    ): Result<OneDriveItem> = resultRunCatching {
        Timber.d("OneDriveClient: Syncing note '$fileName'")

        val url = "https://graph.microsoft.com/v1.0/me/drive/items/$parentFolderId:/$fileName:/content"

        val response = client.put(url) {
            header("Authorization", "Bearer $accessToken")
            contentType(ContentType.Application.OctetStream)
            setBody(content)
        }

        if (response.status.isSuccess()) {
            val item = response.body<OneDriveItem>()
            Timber.d("OneDriveClient: Successfully synced '$fileName' (id: ${item.id})")
            item
        } else {
            throw RuntimeException("OneDrive sync failed: ${response.status}")
        }
    }

    /**
     * Fetches the content of a note from OneDrive.
     */
    suspend fun fetchNote(
        accessToken: String,
        itemId: String
    ): Result<ByteArray> = resultRunCatching {
        Timber.d("OneDriveClient: Fetching note '$itemId'")

        val url = "https://graph.microsoft.com/v1.0/me/drive/items/$itemId/content"

        val response = client.get(url) {
            header("Authorization", "Bearer $accessToken")
        }

        if (response.status.isSuccess()) {
            val content = response.body<ByteArray>()
            Timber.d("OneDriveClient: Fetched note '$itemId' (${content.size} bytes)")
            content
        } else {
            throw RuntimeException("Failed to fetch note: ${response.status}")
        }
    }

    /**
     * Lists children of a specific folder in OneDrive.
     */
    suspend fun listFolderItems(
        accessToken: String,
        folderId: String
    ): Result<List<OneDriveItem>> = resultRunCatching {
        Timber.d("OneDriveClient: Listing items in folder '$folderId'")

        val url = "https://graph.microsoft.com/v1.0/me/drive/items/$folderId/children"

        val response = client.get(url) {
            header("Authorization", "Bearer $accessToken")
        }

        if (response.status.isSuccess()) {
            val itemsResponse = response.body<OneDriveItemList>()
            Timber.d("OneDriveClient: Found ${itemsResponse.value.size} items in folder '$folderId'")
            itemsResponse.value
        } else {
            throw RuntimeException("Failed to list folder items: ${response.status}")
        }
    }

    /**
     * Deletes a note from OneDrive.
     */
    suspend fun deleteNote(
        accessToken: String,
        itemId: String
    ): Result<Unit> = resultRunCatching {
        Timber.d("OneDriveClient: Deleting note '$itemId'")

        val url = "https://graph.microsoft.com/v1.0/me/drive/items/$itemId"

        val response = client.delete(url) {
            header("Authorization", "Bearer $accessToken")
        }

        if (response.status.isSuccess()) {
            Timber.d("OneDriveClient: Successfully deleted note '$itemId'")
        } else {
            throw RuntimeException("Failed to delete note: ${response.status}")
        }
    }
}

/**
 * DTOs for Microsoft Graph API responses.
 */
@Serializable
data class OneDriveItemList(
    val value: List<OneDriveItem>
)

@Serializable
data class OneDriveItem(
    val id: String,
    val name: String,
    val size: Long?,
    val lastModifiedDateTime: String?
)

/**
 * Helper extension to wrap suspend functions in kotlinx Result.
 */
suspend inline fun <T> resultRunCatching(crossinline block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: Exception) {
        Timber.e(e, "OneDriveClient: Operation failed")
        Result.failure(e)
    }
}
