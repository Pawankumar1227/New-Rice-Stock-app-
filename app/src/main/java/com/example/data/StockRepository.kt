package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class SyncStatusState {
    object Idle : SyncStatusState()
    data class Progress(val message: String) : SyncStatusState()
    data class Success(val message: String) : SyncStatusState()
    data class Error(val message: String) : SyncStatusState()
}

class StockRepository(private val context: Context, private val dao: StockEntryDao) {

    private val sharedPrefs = context.getSharedPreferences("shri_ram_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "StockRepository"
        const val DEFAULT_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbzP3_zCAOr-hmpJodQxWndcDLOcZRy0mCR6zH3HxvbOlFOaEx2g1WGyts6kirXAIxPU/exec"
        const val DEFAULT_IMGBB_KEY = "00c76d0382411712f64b1165c52d06f1"
    }

    // Shared preferences setting options
    fun getScriptUrl(): String = sharedPrefs.getString("pref_script_url", DEFAULT_SCRIPT_URL) ?: DEFAULT_SCRIPT_URL
    fun getImgbbKey(): String = sharedPrefs.getString("pref_imgbb_key", DEFAULT_IMGBB_KEY) ?: DEFAULT_IMGBB_KEY

    fun saveApiSettings(scriptUrl: String, imgbbKey: String) {
        sharedPrefs.edit()
            .putString("pref_script_url", scriptUrl.trim())
            .putString("pref_imgbb_key", imgbbKey.trim())
            .apply()
    }

    val allEntries: Flow<List<StockEntry>> = dao.getAllEntriesFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Saves a chosen photo URI into application internal filesDir as a persistent JPEG file.
     * Returns the absolute path string.
     */
    suspend fun saveImageToInternalStorage(uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not open photo input stream")
        val dir = File(context.filesDir, "pending_photos").apply { mkdirs() }
        val file = File(dir, "img_${System.currentTimeMillis()}_${UUID.randomUUID().getLeastSignificantBits()}.jpg")
        
        file.outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    }

    suspend fun saveEntry(entry: StockEntry): Long = withContext(Dispatchers.IO) {
        dao.insertEntry(entry)
    }

    suspend fun deleteEntry(entry: StockEntry) = withContext(Dispatchers.IO) {
        // Clean up files first
        entry.toPhotoPathList().forEach { path ->
            try {
                val f = File(path)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete file on entry deletion: $path", e)
            }
        }
        dao.deleteEntry(entry)
    }

    suspend fun deleteEntryById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteEntryById(id)
    }

    /**
     * Attempts to upload all PENDING or FAILED local stock entries to google sheets.
     */
    suspend fun syncPendingEntries(onProgress: (SyncStatusState) -> Unit) = withContext(Dispatchers.IO) {
        val pending = dao.getPendingEntries()
        if (pending.isEmpty()) {
            onProgress(SyncStatusState.Success("All records are already synced!"))
            return@withContext
        }

        onProgress(SyncStatusState.Progress("Found ${pending.size} pending entries to sync..."))
        
        var successCount = 0
        var errorOccurred = false
        var lastErrorMessage = ""

        val imgbbApiKey = getImgbbKey()
        val googleScriptUrl = getScriptUrl()

        for (entry in pending) {
            try {
                onProgress(SyncStatusState.Progress("Uploading entry ${entry.id} (status: ${entry.status})..."))
                dao.updateSyncStatus(entry.id, "SYNCING", null)

                // 1. Upload photos to ImgBB
                val localPaths = entry.toPhotoPathList()
                val uploadedUrls = mutableListOf<String>()

                for ((index, path) in localPaths.withIndex()) {
                    onProgress(SyncStatusState.Progress("Uploading photo ${index + 1}/${localPaths.size} to ImgBB..."))
                    val file = File(path)
                    if (!file.exists()) {
                        Log.w(TAG, "Local file not found, skipping photo: $path")
                        continue
                    }

                    val url = uploadFileToImgBB(file, imgbbApiKey)
                    uploadedUrls.add(url)
                }

                // Update intermediate progress with URLs
                val uploadedUrlsString = uploadedUrls.joinToString(",")
                val currentUploadedEntry = entry.copy(uploadedPhotoUrls = uploadedUrlsString)
                dao.insertEntry(currentUploadedEntry)

                // 2. Submit data to Google Sheets SCRIPT_URL
                onProgress(SyncStatusState.Progress("Submitting entry to Google Sheet..."))
                submitToGoogleSheets(currentUploadedEntry, uploadedUrls, googleScriptUrl)

                // 3. Complete sync
                dao.updateSyncStatus(entry.id, "SUCCESS", null)
                successCount++

                // 4. Delete local photo files to save space
                localPaths.forEach { path ->
                    try {
                        val f = File(path)
                        if (f.exists()) f.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error cleaning up synced photo: $path", e)
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed syncing entry id: ${entry.id}", e)
                errorOccurred = true
                lastErrorMessage = e.message ?: "Unknown sync error"
                dao.updateSyncStatus(entry.id, "FAILED", lastErrorMessage)
                // Stop processing subsequent entries to avoid loops of failures
                break
            }
        }

        if (errorOccurred) {
            onProgress(SyncStatusState.Error("Sync stopped due to error: $lastErrorMessage. (Synced $successCount entries successfully)"))
        } else {
            onProgress(SyncStatusState.Success("Successfully synced $successCount entries to Google Sheets!"))
        }
    }

    /**
     * Upload physical file to ImgBB using standard POST request.
     */
    private fun uploadFileToImgBB(file: File, apiKey: String): String {
        val fileBytes = file.readBytes()
        val base64Image = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("key", apiKey)
            .addFormDataPart("image", base64Image)
            .build()

        val request = Request.Builder()
            .url("https://api.imgbb.com/1/upload")
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("ImgBB HTTP error code: ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("ImgBB response body null")
            val json = JSONObject(body)
            if (json.getBoolean("success")) {
                return json.getJSONObject("data").getString("url")
            } else {
                throw Exception(json.optString("message", "ImgBB upload failed"))
            }
        }
    }

    /**
     * Sends the completed entry data to Google App Script SCRIPT_URL.
     */
    private fun submitToGoogleSheets(entry: StockEntry, photoUrls: List<String>, scriptUrl: String) {
        val jsonPayload = JSONObject().apply {
            put("riceType", entry.riceType)
            put("riceType1", entry.riceType1)
            put("riceType2", entry.riceType2)
            put("riceType3", entry.riceType3)
            put("riceType4", entry.riceType4)
            put("daraStatus", entry.daraStatus)
            put("partyName", entry.partyName)
            put("status", entry.status)
            put("station", entry.station)
            put("weight", entry.weight)
            put("bags", entry.bags)
            put("location", entry.location)
            
            // Add photo URLs matching JS format: photo1, photo2, etc.
            photoUrls.forEachIndexed { i, url ->
                put("photo${i + 1}", url)
            }
        }

        val jsonString = jsonPayload.toString()
        Log.d(TAG, "Submitting payload to Sheets: $jsonString")

        val mediaType = "text/plain; charset=utf-8".toMediaType() // Matches JS content type: text/plain
        val requestBody = jsonString.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(scriptUrl)
            .post(requestBody)
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            // Since JS fetch uses "no-cors" mode, GAS Webapps normally redirects to a download page,
            // producing 200 or 302/301 redirects. OkHttp follows redirects. If successful, response code should be 200/OK.
            if (!response.isSuccessful && response.code != 302 && response.code != 301) {
                throw Exception("Google Script status error: ${response.code}")
            }
        }
    }
}
