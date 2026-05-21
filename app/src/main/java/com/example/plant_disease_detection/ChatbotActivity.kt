package com.example.plant_disease_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.plant_disease_detection.databinding.ActivityChatbotBinding
import com.example.plant_disease_detection.utils.ChatHistoryManager
import com.example.plant_disease_detection.utils.UserManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import com.google.gson.Gson

class ChatbotActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatbotBinding
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    private var detectedDisease: String = "Unknown"
    private lateinit var chatUserKey: String
    private var transcriptForSave: String = ""
    private var pendingImageBitmap: Bitmap? = null

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()?.let { setPendingImage(it) }
            ?: Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) setPendingImage(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatbotBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detectedDisease = intent.getStringExtra(EXTRA_DETECTED_DISEASE) ?: "Unknown"
        chatUserKey = UserManager.getCurrentUserEmail() ?: "guest"
        binding.diseaseLabelText.text = "Detected disease: ${detectedDisease.replace('_', ' ')}"

        val saved = ChatHistoryManager.load(chatUserKey)
        binding.chatMessagesContainer.removeAllViews()
        if (saved.isNotBlank()) {
            transcriptForSave = saved
            restoreTranscript(saved)
        } else {
            val welcome = buildWelcomeTranscript()
            transcriptForSave = "AI: $welcome"
            addMessageToUi("AI", welcome, null, false)
            ChatHistoryManager.save(chatUserKey, transcriptForSave)
        }
        binding.chatScroll.post { binding.chatScroll.fullScroll(View.FOCUS_DOWN) }

        binding.useCameraButton.setOnClickListener { takePictureLauncher.launch(null) }
        binding.uploadImageButton.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        binding.clearPendingImageButton.setOnClickListener { clearPendingImage() }

        binding.sendButton.setOnClickListener {
            val userPrompt = binding.messageInput.text.toString().trim()
            val hasImage = pendingImageBitmap != null
            if (userPrompt.isEmpty() && !hasImage) {
                Toast.makeText(this, "Type a message or attach a leaf photo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            askAiForTreatment(userPrompt)
        }
    }

    private fun setPendingImage(bitmap: Bitmap) {
        pendingImageBitmap = bitmap
        binding.pendingImagePreview.setImageBitmap(bitmap)
        binding.pendingImageCard.visibility = View.VISIBLE
    }

    private fun clearPendingImage() {
        pendingImageBitmap = null
        binding.pendingImagePreview.setImageDrawable(null)
        binding.pendingImageCard.visibility = View.GONE
    }

    private fun askAiForTreatment(userPrompt: String) {
        if (BuildConfig.AI_API_KEY.isBlank()) {
            Toast.makeText(this, "Set AI_API_KEY in build config first.", Toast.LENGTH_LONG).show()
            return
        }
        if (BuildConfig.AI_BASE_URL.isBlank() || BuildConfig.AI_MODEL.isBlank()) {
            Toast.makeText(this, "Set AI_BASE_URL and AI_MODEL in build config.", Toast.LENGTH_LONG).show()
            return
        }
        if (!isInternetAvailable()) {
            appendChatPersistAndUi(
                "AI",
                "No internet connection. Please check your network and try again.",
                null
            )
            return
        }

        val imageSnapshot = pendingImageBitmap
        val effectivePrompt = userPrompt.ifBlank {
            "Analyze this leaf image. Identify whether it looks healthy or diseased, name likely issues, then give treatment and prevention steps."
        }

        if (!isPlantDiseaseQuery(effectivePrompt, imageSnapshot != null)) {
            appendChatPersistAndUi(
                "AI",
                "Please ask me about plant leaf disease and its treatment only.",
                null
            )
            binding.messageInput.text?.clear()
            return
        }

        appendChatPersistAndUi("You", effectivePrompt, imageSnapshot)
        binding.messageInput.text?.clear()
        clearPendingImage()

        binding.progressBar.visibility = View.VISIBLE
        binding.sendButton.isEnabled = false

        lifecycleScope.launch {
            val responseText = withContext(Dispatchers.IO) {
                runCatching { callAiApiWithRetry(effectivePrompt, imageSnapshot) }
                    .getOrElse { errorMessageForException(it) }
            }

            binding.progressBar.visibility = View.GONE
            binding.sendButton.isEnabled = true
            appendChatPersistAndUi("AI", responseText, null)
        }
    }

    private fun callAiApi(userPrompt: String, imageBitmap: Bitmap?): String {
        val systemText = if (imageBitmap != null) {
            "You are a plant leaf disease assistant. The user sent a photo of a leaf. " +
                "Examine the image carefully. Say if the leaf appears healthy or describe likely disease or damage. " +
                "Give practical treatment and prevention for plants only. If the image is not a plant leaf, say so briefly. " +
                "Answer ONLY about plant health, disease, treatment, and prevention. " +
                "If the user asks something unrelated, reply exactly: " +
                "'Please ask me about plant leaf disease and its treatment only.' " +
                "Do not use asterisks for bold. Be concise."
        } else {
            "You are a plant leaf disease treatment assistant. Disease detection is already done by an on-device model. " +
                "Do not re-diagnose from image unless the user sends one. Answer ONLY about plant leaf diseases, treatment, prevention, and care steps. " +
                "If user asks anything outside plant leaf disease/treatment, reply exactly: " +
                "'Please ask me about plant leaf disease and its treatment only.' " +
                "Keep advice practical and concise."
        }

        val messages = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", systemText)
            })
            add(JsonObject().apply {
                addProperty("role", "user")
                if (imageBitmap != null) {
                    val userContent = JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("type", "text")
                            addProperty(
                                "text",
                                buildString {
                                    append("User message: $userPrompt\n")
                                    if (detectedDisease != "Unknown") {
                                        append("On-device detector hint (may differ from photo): $detectedDisease\n")
                                    }
                                    append("Structure your reply: 1) What you see 2) Likely condition 3) Treatment 4) Prevention.")
                                }
                            )
                        })
                        add(JsonObject().apply {
                            addProperty("type", "image_url")
                            add("image_url", JsonObject().apply {
                                addProperty("url", bitmapToJpegDataUrl(imageBitmap))
                            })
                        })
                    }
                    add("content", userContent)
                } else {
                    addProperty(
                        "content",
                        "Detected disease (from on-device model): $detectedDisease\nUser question: $userPrompt\n" +
                            "Please provide: 1) immediate action, 2) treatment steps, 3) prevention."
                    )
                }
            })
        }

        val model = if (imageBitmap != null && BuildConfig.AI_VISION_MODEL.isNotBlank()) {
            BuildConfig.AI_VISION_MODEL
        } else {
            BuildConfig.AI_MODEL
        }

        val payload = JsonObject().apply {
            addProperty("model", model)
            add("messages", messages)
            addProperty("temperature", 0.4)
        }

        val body = gson.toJson(payload).toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(BuildConfig.AI_BASE_URL)
            .addHeader("Authorization", "Bearer ${BuildConfig.AI_API_KEY}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return "AI error ${response.code}: $responseBody"
            }

            val json = gson.fromJson(responseBody, JsonObject::class.java)
            if (json.has("error")) {
                return json.getAsJsonObject("error").get("message")?.asString
                    ?: "AI returned an unknown error."
            }
            val choices = json.getAsJsonArray("choices")
            if (choices == null || choices.size() == 0) {
                return "No response from AI."
            }
            val message = choices[0].asJsonObject.getAsJsonObject("message")
            val content = extractMessageContent(message?.get("content"))
            return if (content.isNullOrBlank()) {
                "AI returned an empty response. Please try again."
            } else {
                sanitizeAiText(content)
            }
        }
    }

    private fun bitmapToJpegDataUrl(bitmap: Bitmap): String {
        val maxSide = 768
        val scaled = if (bitmap.width <= maxSide && bitmap.height <= maxSide) {
            bitmap
        } else {
            val ratio = minOf(
                maxSide.toFloat() / bitmap.width,
                maxSide.toFloat() / bitmap.height
            )
            val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
            val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, w, h, true)
        }
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, stream)
        if (scaled !== bitmap) scaled.recycle()
        val b64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        return "data:image/jpeg;base64,$b64"
    }

    private fun callAiApiWithRetry(userPrompt: String, imageBitmap: Bitmap?): String {
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                return callAiApi(userPrompt, imageBitmap)
            } catch (e: Exception) {
                lastError = e
                val retriable = e is UnknownHostException || e is SocketTimeoutException || e is IOException
                if (!retriable || attempt == 1) throw e
                Thread.sleep(1200)
            }
        }
        throw lastError ?: IllegalStateException("Unknown AI request error")
    }

    private fun errorMessageForException(error: Throwable): String {
        return when (error) {
            is UnknownHostException ->
                "Network/DNS issue: unable to reach AI server. Check internet or DNS and try again."
            is SocketTimeoutException ->
                "AI server timeout. Please try again in a moment."
            is IOException ->
                "Network error while contacting AI server. Please try again."
            else -> "AI request failed: ${error.message}"
        }
    }

    private fun isInternetAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun extractMessageContent(contentElement: JsonElement?): String? {
        if (contentElement == null || contentElement.isJsonNull) return null
        if (contentElement.isJsonPrimitive) return contentElement.asString.trim()

        if (contentElement.isJsonArray) {
            val text = buildString {
                contentElement.asJsonArray.forEach { part ->
                    val piece = when {
                        part.isJsonPrimitive -> part.asString
                        part.isJsonObject -> {
                            val o = part.asJsonObject
                            when (o.get("type")?.asString) {
                                "text" -> o.get("text")?.asString
                                else -> o.get("text")?.asString
                            }
                        }
                        else -> null
                    }
                    if (!piece.isNullOrBlank()) {
                        if (isNotEmpty()) append('\n')
                        append(piece.trim())
                    }
                }
            }.trim()
            return text.ifBlank { null }
        }

        if (contentElement.isJsonObject) {
            return contentElement.asJsonObject.get("text")?.asString?.trim()
        }
        return null
    }

    private fun sanitizeAiText(text: String): String {
        return text.replace("**", "").trim()
    }

    private fun isPlantDiseaseQuery(prompt: String, hasImage: Boolean): Boolean {
        if (hasImage) return true
        val text = prompt.lowercase()
        val keywords = listOf(
            "plant", "leaf", "disease", "treatment", "fungus", "bacteria", "virus",
            "blight", "spot", "mold", "mites", "healthy", "tomato", "potato", "pepper",
            "spray", "fungicide", "pesticide", "prevention", "care", "analyze", "photo", "image"
        )
        return keywords.any { text.contains(it) }
    }

    private fun buildWelcomeTranscript(): String =
        "AI treatment assistant is ready.\n\nOn-device detected disease: ${detectedDisease.replace('_', ' ')}\n\n" +
            "Attach a leaf photo with Attach photo / Camera, add an optional message, then Send — I will analyze the image like a chat. " +
            "Or ask text-only questions about treatment."

    private fun appendChatPersistAndUi(sender: String, text: String, imageBitmap: Bitmap?) {
        val line = when {
            sender == "You" && imageBitmap != null ->
                "You: [Image] $text"
            else ->
                "$sender: $text"
        }
        transcriptForSave = if (transcriptForSave.isBlank()) line else "$transcriptForSave\n\n$line"
        ChatHistoryManager.save(chatUserKey, transcriptForSave)
        val showUserImageSlot = sender == "You" && imageBitmap != null
        addMessageToUi(sender, text, imageBitmap, showUserImageSlot)
        binding.chatScroll.post { binding.chatScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun addMessageToUi(
        sender: String,
        messageText: String,
        imageBitmap: Bitmap?,
        showUserImageSlot: Boolean
    ) {
        val pad = (8 * resources.displayMetrics.density).toInt()
        val block = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, pad)
        }

        val header = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTextColor(ContextCompat.getColor(this@ChatbotActivity, R.color.bot_text_primary))
            text = if (sender == "You") {
                if (messageText.isNotBlank()) "You:\n$messageText" else "You:"
            } else {
                "$sender:\n$messageText"
            }
        }
        block.addView(header)

        if (showUserImageSlot && imageBitmap != null) {
            val dp = (180 * resources.displayMetrics.density).toInt()
            val iv = ImageView(this).apply {
                setImageBitmap(imageBitmap)
                layoutParams = LinearLayout.LayoutParams(dp, dp)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            block.addView(iv)
        } else if (showUserImageSlot && imageBitmap == null) {
            val ph = TextView(this).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ContextCompat.getColor(this@ChatbotActivity, R.color.bot_text_secondary))
                text = "[Photo from earlier session — not stored]"
            }
            block.addView(ph)
        }

        binding.chatMessagesContainer.addView(block)
    }

    private fun restoreTranscript(saved: String) {
        binding.chatMessagesContainer.removeAllViews()
        val parts = saved.split("\n\n")
        for (part in parts) {
            when {
                part.startsWith("You: [Image]") -> {
                    val body = part.removePrefix("You: [Image]").trim()
                    addMessageToUi("You", body, null, true)
                }
                part.startsWith("You: ") -> {
                    addMessageToUi("You", part.removePrefix("You: ").trim(), null, false)
                }
                part.startsWith("AI: ") -> {
                    addMessageToUi("AI", part.removePrefix("AI: ").trim(), null, false)
                }
            }
        }
    }

    companion object {
        const val EXTRA_DETECTED_DISEASE = "detected_disease"
    }
}

