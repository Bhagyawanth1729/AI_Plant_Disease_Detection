package com.example.plant_disease_detection.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

object ModelDownloadManager {

    private const val LEAF_MODEL_FILE = "leaf_detector2.onnx"
    private const val DISEASE_MODEL_FILE = "densenet_disease_detector.onnx"
    private const val MAX_DOWNLOAD_ATTEMPTS = 3

    private const val LEAF_MODEL_URL =
        "https://huggingface.co/Bhagyawanth1729/App/resolve/main/leaf_detector2.onnx"
    private const val DISEASE_MODEL_URL =
        "https://huggingface.co/Bhagyawanth1729/App/resolve/main/densenet_disease_detector.onnx"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun downloadLeafModel(context: Context): String = withContext(Dispatchers.IO) {
        requireNetwork(context)
        downloadModel(LEAF_MODEL_URL, cacheFile(context, LEAF_MODEL_FILE)).absolutePath
    }

    suspend fun downloadDiseaseModel(context: Context): String = withContext(Dispatchers.IO) {
        requireNetwork(context)
        downloadModel(DISEASE_MODEL_URL, cacheFile(context, DISEASE_MODEL_FILE)).absolutePath
    }

    private fun requireNetwork(context: Context) {
        if (!isNetworkAvailable(context)) {
            throw IOException(
                "No internet connection. Connect to Wi-Fi or mobile data to load models from cloud."
            )
        }
    }

    fun deleteLeafCache(context: Context) = deleteCachedModel(context, LEAF_MODEL_FILE)

    fun deleteDiseaseCache(context: Context) = deleteCachedModel(context, DISEASE_MODEL_FILE)

    fun deleteCachedModel(context: Context, fileName: String) {
        cacheFile(context, fileName).delete()
        File(cacheFile(context, fileName).parentFile, "$fileName.tmp").delete()
    }

    fun cleanupAllCachedModels(context: Context) {
        File(context.cacheDir, "remote_models").deleteRecursively()
        File(context.filesDir, "models").deleteRecursively()
    }

    private fun cacheFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "remote_models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, fileName)
    }

    private fun downloadModel(url: String, destFile: File) {
        var lastError: Exception? = null
        repeat(MAX_DOWNLOAD_ATTEMPTS) { attempt ->
            val tmpFile = File(destFile.parentFile, "${destFile.name}.tmp")
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "PlantDiseaseDetection/1.0")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download failed (HTTP ${response.code}): $url")
                    }
                    val body = response.body ?: throw IOException("Empty response body: $url")
                    body.byteStream().use { input ->
                        tmpFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                if (destFile.exists()) {
                    destFile.delete()
                }
                if (!tmpFile.renameTo(destFile)) {
                    tmpFile.copyTo(destFile, overwrite = true)
                    tmpFile.delete()
                }
                if (destFile.length() == 0L) {
                    destFile.delete()
                    throw IOException("Downloaded file is empty: ${destFile.name}")
                }
                return
            } catch (e: Exception) {
                if (tmpFile.exists()) {
                    tmpFile.delete()
                }
                lastError = e
                val retryable = e is SocketTimeoutException ||
                    (e is IOException && e.message?.contains("timeout", ignoreCase = true) == true)
                if (!retryable || attempt == MAX_DOWNLOAD_ATTEMPTS - 1) {
                    throw e
                }
            }
        }
        throw lastError ?: IOException("Download failed: $url")
    }
}
