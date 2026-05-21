package com.example.plant_disease_detection

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plant_disease_detection.databinding.ActivityDetectionBinding
import com.example.plant_disease_detection.utils.ScanHistoryEntry
import com.example.plant_disease_detection.utils.ScanHistoryManager
import com.example.plant_disease_detection.utils.UserManager
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class DetectionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDetectionBinding

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()

    private val minConfidencePercentForWarning = 40f
    private var latestDetectedDisease: String? = null
    private var currentImagePath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetectionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        if (imagePath != null) {
            currentImagePath = imagePath
            showSelectedImage(imagePath)
            startDetection(imagePath)
        } else {
            binding.detectionStatus.text = "No image provided"
        }

        binding.askAiButton.setOnClickListener {
            val disease = latestDetectedDisease ?: return@setOnClickListener
            val intent = Intent(this, ChatbotActivity::class.java).apply {
                putExtra(ChatbotActivity.EXTRA_DETECTED_DISEASE, disease)
            }
            startActivity(intent)
        }
    }

    private fun showSelectedImage(imagePath: String) {
        val bitmap = when {
            imagePath.startsWith("content://") || imagePath.startsWith("android.resource://") -> {
                contentResolver.openInputStream(Uri.parse(imagePath))?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
            else -> BitmapFactory.decodeFile(imagePath)
        }

        if (bitmap != null) {
            binding.capturedImage.setImageBitmap(bitmap)
        } else {
            binding.detectionStatus.text = "Failed to load image"
        }
    }

    private fun startDetection(imagePath: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.detectionStatus.text = "Sending image to model API..."
        binding.resultText.visibility = View.GONE
        binding.askAiButton.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val prediction = withContext(Dispatchers.IO) {
                    uploadImageForPrediction(imagePath)
                }
                showPredictionResult(prediction)
            } catch (e: Exception) {
                showDetectionError(e)
            }
        }
    }

    private fun uploadImageForPrediction(imagePath: String): PredictionResponse {
        val baseUrl = BuildConfig.MODEL_API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            throw IOException("MODEL_API_BASE_URL is missing. Add your Render URL in local.properties.")
        }

        val imagePart = createImagePart(imagePath)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(imagePart)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/predict")
            .post(requestBody)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = runCatching {
                    gson.fromJson(body, PredictionResponse::class.java).detail
                }.getOrNull()
                throw IOException(error ?: "API request failed with HTTP ${response.code}")
            }
            return gson.fromJson(body, PredictionResponse::class.java)
                ?: throw IOException("API returned an empty response.")
        }
    }

    private fun createImagePart(imagePath: String): MultipartBody.Part {
        val uri = Uri.parse(imagePath)
        val mediaType = getMediaType(uri)

        return if (imagePath.startsWith("content://") || imagePath.startsWith("android.resource://")) {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Unable to read selected image.")
            MultipartBody.Part.createFormData(
                "file",
                getFileName(uri),
                bytes.toRequestBody(mediaType)
            )
        } else {
            val imageFile = File(imagePath)
            if (!imageFile.exists()) {
                throw IOException("Image file does not exist.")
            }
            MultipartBody.Part.createFormData(
                "file",
                imageFile.name,
                imageFile.asRequestBody(mediaType)
            )
        }
    }

    private fun getMediaType(uri: Uri) =
        when {
            uri.scheme == "content" -> contentResolver.getType(uri)
            else -> MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(File(uri.path.orEmpty()).extension)
        }?.toMediaTypeOrNull() ?: "image/jpeg".toMediaTypeOrNull()

    private fun getFileName(uri: Uri): String {
        val extension = MimeTypeMap.getSingleton()
            .getExtensionFromMimeType(contentResolver.getType(uri))
            ?: "jpg"
        return "plant_leaf.$extension"
    }

    private fun showPredictionResult(prediction: PredictionResponse) {
        binding.progressBar.visibility = View.GONE

        if (prediction.leafDetected == false) {
            binding.detectionStatus.text = "No leaf detected"
            binding.resultText.text = "No Leaf Detected - Please upload a clear leaf image."
            binding.resultText.visibility = View.VISIBLE
            latestDetectedDisease = null
            recordScanHistory("No leaf detected", 0f)
            return
        }

        val label = prediction.label ?: "Unknown"
        val diseaseName = prediction.disease ?: label.replace('_', ' ')
        val confidence = prediction.confidence ?: 0f
        val treatment = prediction.treatment
            ?: "No treatment information available for this disease."
        val confidenceNote =
            if (confidence < minConfidencePercentForWarning) {
                "\n\nLow confidence result. Please capture a clearer leaf image."
            } else {
                ""
            }

        binding.detectionStatus.text = "Analysis Complete"
        binding.resultText.text =
            "Detection Result: $diseaseName\n\nTreatment Suggestion:\n$treatment\n\n(Confidence: ${"%.2f".format(confidence)}%)$confidenceNote"
        binding.resultText.visibility = View.VISIBLE

        latestDetectedDisease = label
        binding.askAiButton.visibility = if (label == "Unknown") View.GONE else View.VISIBLE
        recordScanHistory(label, confidence)
    }

    private fun showDetectionError(e: Exception) {
        binding.progressBar.visibility = View.GONE
        val message = e.message ?: "Unknown error"
        Toast.makeText(this, "Detection failed: $message", Toast.LENGTH_LONG).show()
        binding.detectionStatus.text = "Detection failed"
        binding.resultText.text = "Error during detection: $message"
        binding.resultText.visibility = View.VISIBLE
        latestDetectedDisease = null
        binding.askAiButton.visibility = View.GONE
    }

    private fun recordScanHistory(disease: String, confidencePercent: Float) {
        val path = currentImagePath ?: return
        val userKey = UserManager.getCurrentUserEmail() ?: "guest"
        val source = intent.getStringExtra(EXTRA_SOURCE_LABEL)
        ScanHistoryManager.addEntry(
            userKey,
            ScanHistoryEntry(
                imageUri = path,
                disease = disease,
                confidencePercent = confidencePercent,
                sourceLabel = source
            )
        )
    }

    data class PredictionResponse(
        @SerializedName("leaf_detected")
        val leafDetected: Boolean? = null,
        val label: String? = null,
        val disease: String? = null,
        val confidence: Float? = null,
        val treatment: String? = null,
        val result: String? = null,
        val detail: String? = null
    )

    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_SOURCE_LABEL = "source_label"
    }
}
