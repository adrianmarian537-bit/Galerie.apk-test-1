package com.example.data

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

// --- Gemini API Request Models (with Moshi annotations) ---

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class ResponseFormat(
    @Json(name = "type") val type: String = "APPLICATION_JSON"
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    @Json(name = "responseMimeType") val responseMimeType: String = "application/json",
    @Json(name = "temperature") val temperature: Double = 0.2
)

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "generationConfig") val generationConfig: GenerationConfig = GenerationConfig()
)

// --- Gemini API Response Models ---

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

// --- Structured Classification Response Mapping ---
@JsonClass(generateAdapter = true)
data class SmartTagsResponse(
    @Json(name = "faces") val faces: List<String>?,
    @Json(name = "objects") val objects: List<String>?,
    @Json(name = "category") val category: String?
)

interface GeminiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiService = retrofit.create(GeminiService::class.java)

    // Helper to extract JSON from raw response text
    fun parseSmartResponse(rawJson: String): SmartTagsResponse? {
        return try {
            val adapter = moshi.adapter(SmartTagsResponse::class.java)
            adapter.fromJson(rawJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun scanAndClassifyMedia(imageUrl: String, title: String, location: String): SmartTagsResponse? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return null
        }

        val prompt = """
            You are an expert Samsung photo gallery AI auto-classifier.
            Analyze this image of a user in their gallery:
            Image URL: $imageUrl
            Title provided by user: $title
            Location: $location

            Please identify:
            1. any faces/names of people (like "George", "Andreea", "Alice", "Elena" or general categories if names aren't clear, e.g., "Copil" or "Băiat").
            2. primary visual objects or scenes (e.g., "copac", "floare", "pisică", "mașină", "mare", "oraș", "mâncare"). Use Romanian language keywords.
            3. the most suitable category for grouping ("Natură", "Oameni", "Animale", "Călătorii", "Mâncare", "Mașini", "Vacanță", "Altele").

            Return strictly a JSON object with the following structure. No enclosing markdown codeblocks, just the JSON:
            {
              "faces": ["name1", "name2"],
              "objects": ["object1", "object2", "object3"],
              "category": "ChosenCategory"
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt))))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val textResponse = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            // Clean markdown blocks if any
            val cleanJson = textResponse.replace("```json", "").replace("```", "").trim()
            parseSmartResponse(cleanJson)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
