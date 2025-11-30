package com.example.remotedisplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.SessionDescription

class SignalingClient(private val serverUrl: String) {
    private val client = OkHttpClient()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun sendOffer(sdp: SessionDescription): SessionDescription? {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                json.put("sdp", sdp.description)
                json.put("type", sdp.type.canonicalForm())

                val body = json.toString().toRequestBody(jsonMediaType)
                val request = Request.Builder()
                    .url("$serverUrl/offer")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        val responseJson = JSONObject(responseBody)
                        val type = responseJson.getString("type")
                        val description = responseJson.getString("sdp")
                        return@withContext SessionDescription(
                            SessionDescription.Type.fromCanonicalForm(type),
                            description
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }
}
