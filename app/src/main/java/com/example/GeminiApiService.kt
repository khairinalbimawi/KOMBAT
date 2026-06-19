package com.example

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Serializable
data class GenerateContentRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val tools: List<JsonObject>? = null,
    val systemInstruction: Content? = null
)

@Serializable
data class Content(
    val parts: List<Part>
)

@Serializable
data class Part(
    val text: String? = null
)

@Serializable
data class GenerationConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null
)

@Serializable
data class GenerateContentResponse(
    val candidates: List<Candidate>
)

@Serializable
data class Candidate(
    val content: Content
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val service: GeminiApiService by lazy {
        val json = Json { ignoreUnknownKeys = true }
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        retrofit.create(GeminiApiService::class.java)
    }
}

suspend fun generateGeminiResponse(prompt: String): String = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
         return@withContext "Error: API Key belum diatur di AI Studio Secrets."
    }
    
    val request = GenerateContentRequest(
        contents = listOf(Content(
            parts = listOf(Part(text = prompt))
        )),
        systemInstruction = Content(
            parts = listOf(Part(text = """
                Kamu adalah KOMBAT AI, asisten cerdas untuk aplikasi KOMBAT (Sistem Jurnal dan Absensi Pertanian).
                
                Bantulah pengguna (pekerja atau pengawas lahan) dalam menjalankan tugas sehari-hari. 
                Jawablah dengan ringkas, profesional, dan membantu dalam bahasa Indonesia.
                
                Konteks dan Aturan Aplikasi KOMBAT:
                
                1. Kebijakan Absensi:
                   - Absensi Masuk dan Keluar wajib disertai foto lokasi (diambil real-time lewat kamera) dan tag koordinat GPS.
                   - Pemalsuan lokasi atau mematikan GPS tidak diperbolehkan dan akan diblokir oleh sistem.
                   - Keterlambatan akan tercatat secara otomatis.
                
                2. Format Penulisan Jurnal Harian:
                   - Bantu pengguna menyusun rekapan aktivitas pertanian jika mereka bertanya.
                   - Format Jurnal yang baik meliputi: [Waktu], [Jenis Kegiatan: misal Pemupukan/Panen/Penyemprotan], [Area/Blok Lahan], [Deskripsi Pekerjaan & Hasil], dan [Cuaca/Kendala].
                   - Rekomendasikan agar mencatat pemakaian material seperti volume air, pupuk, atau bibit yang digunakan.
                
                Selalu berikan respons yang relevan, akurat, dan merujuk pada aturan ini ketika ada pertanyaan terkait aplikasi atau prosedur pertanian.
            """.trimIndent()))
        )
    )
    try {
        val response = RetrofitClient.service.generateContent(apiKey, request)
        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Maaf, saya tidak mengerti."
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}
