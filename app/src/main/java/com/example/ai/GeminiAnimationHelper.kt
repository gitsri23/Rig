package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.RigJointData
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class JointResponse(val name: String, val x: Float, val y: Float)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class LipSyncResponse(val timeMs: Long, val viseme: String)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class FramePoseResponse(val frameIndex: Int, val joints: List<JointResponse>)

object GeminiAnimationHelper {
    private const val TAG = "GeminiAnimHelper"
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    // Helper to convert Bitmap to Base64
    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    suspend fun autoRigCharacter(base64Image: String): List<RigJointData> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return@withContext emptyList()
        }

        val systemPrompt = "You are an AI character rigger. You detect body part locations on a 2D character canvas (width 1.0, height 1.0) and return standard joint coordinates as a JSON array of objects with keys 'name', 'x', and 'y'."
        val prompt = """
            Analyze the provided image of a 2D character.
            Detect and output the optimal coordinates for these 15 skeletal joints in normalized 0.0 to 1.0 coordinates:
            'Head', 'Neck', 'Torso', 'L_Shoulder', 'L_Elbow', 'L_Hand', 'R_Shoulder', 'R_Elbow', 'R_Hand', 'L_Hip', 'L_Knee', 'L_Foot', 'R_Hip', 'R_Knee', 'R_Foot'.
            Return ONLY a valid JSON array of these joints, for example:
            [
              {"name": "Head", "x": 0.5, "y": 0.15},
              {"name": "Neck", "x": 0.5, "y": 0.25},
              {"name": "Torso", "x": 0.5, "y": 0.5},
              ...
            ]
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val listType = Types.newParameterizedType(List::class.java, JointResponse::class.java)
                val adapter = moshi.adapter<List<JointResponse>>(listType)
                val parsed = adapter.fromJson(jsonText)
                return@withContext parsed?.map { RigJointData(it.name, it.x, it.y) } ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in autoRigCharacter: ${e.message}", e)
        }
        return@withContext emptyList()
    }

    suspend fun generateLipSync(script: String, totalDurationMs: Long): List<LipSyncResponse> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return@withContext emptyList()
        }

        val systemPrompt = "You are an expert lipsync animator. You take a script and total duration, and output keyframes mapping timestamps (ms) to phonetic mouth shapes (Rest, Wide, Open, Round, Close)."
        val prompt = """
            Generate a series of lipsync keyframes for this speech script: "$script"
            Total duration: $totalDurationMs milliseconds.
            Mouth shapes available:
            - 'Rest' (idle/closed)
            - 'Wide' (e, i sounds)
            - 'Open' (a, o, space sounds)
            - 'Round' (u, w, o sounds)
            - 'Close' (b, m, p sounds)

            Output should be a JSON array of objects with keys 'timeMs' (Long) and 'viseme' (String), ordered chronologically from 0 to $totalDurationMs:
            [
              {"timeMs": 0, "viseme": "Rest"},
              {"timeMs": 150, "viseme": "Open"},
              {"timeMs": 400, "viseme": "Close"},
              ...
            ]
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.4f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val listType = Types.newParameterizedType(List::class.java, LipSyncResponse::class.java)
                val adapter = moshi.adapter<List<LipSyncResponse>>(listType)
                return@withContext adapter.fromJson(jsonText) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generateLipSync: ${e.message}", e)
        }
        return@withContext emptyList()
    }

    suspend fun generatePoseLoop(actionName: String): List<List<RigJointData>> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API key is not configured!")
            return@withContext emptyList()
        }

        val systemPrompt = "You are an expert 2D animator. You generate fluid skeletal frame pose coordinates (0.0 to 1.0) for standard bone structures."
        val prompt = """
            Create a fluid 6-frame loop for the action: "$actionName"
            Output the relative (x, y) coordinates for our 15 standard joints:
            'Head', 'Neck', 'Torso', 'L_Shoulder', 'L_Elbow', 'L_Hand', 'R_Shoulder', 'R_Elbow', 'R_Hand', 'L_Hip', 'L_Knee', 'L_Foot', 'R_Hip', 'R_Knee', 'R_Foot'.

            Generate exactly 6 frames. The output must be a JSON array containing exactly 6 elements. Each element is a frame object containing 'frameIndex' (0..5) and a list of 'joints':
            [
              {
                "frameIndex": 0,
                "joints": [
                  {"name": "Head", "x": 0.5, "y": 0.15},
                  {"name": "Neck", "x": 0.5, "y": 0.25},
                  ...
                ]
              },
              ...
            ]
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.5f
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemPrompt)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val listType = Types.newParameterizedType(List::class.java, FramePoseResponse::class.java)
                val adapter = moshi.adapter<List<FramePoseResponse>>(listType)
                val frames = adapter.fromJson(jsonText)
                if (frames != null) {
                    return@withContext frames.sortedBy { it.frameIndex }.map { frame ->
                        frame.joints.map { RigJointData(it.name, it.x, it.y) }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in generatePoseLoop: ${e.message}", e)
        }
        return@withContext emptyList()
    }
}
