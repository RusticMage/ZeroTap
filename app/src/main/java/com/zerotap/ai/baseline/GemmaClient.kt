package com.zerotap.ai.baseline

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * GEMMA 4 = the incident reasoning layer.
 * Rules: Gemma only receives NUMBERS (no audio, no coordinates). It can nudge the risk score
 * but can never cancel the 15-second countdown or send an alert by itself.
 * No key / no internet / error => returns UNCERTAIN and the normal on-device engine carries on.
 */

data class IncidentFeatures(
    val suspectedType: String,
    val userActivity: String,
    val currentMotion: String?,
    val speedKmh: Double?,
    val impactPeakMs2: Double?,
    val gyroPeakRads: Double?,
    val audioDbAboveNoiseFloor: Double?,
    val onDeviceRiskPercent: Int,
    val baselineScore: Double,
    val baselineZAccel: Double,
    val baselineZGyro: Double,
    val baselineZAudio: Double,
    val independentSignalsUnusual: Int,
    val secondsUnusualInARow: Int,
    val secondsSinceImpact: Long?,
    val stillSinceImpactSeconds: Long?,
    val movementResumedAfterImpact: Boolean?,
    val unexpectedStop: Boolean?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("suspected_type", suspectedType)
        put("user_activity", userActivity)
        currentMotion?.let { put("current_motion", it) }
        speedKmh?.let { put("speed_kmh", it) }
        impactPeakMs2?.let { put("peak_acceleration_m_s2", it) }
        gyroPeakRads?.let { put("peak_rotation_rad_s", it) }
        audioDbAboveNoiseFloor?.let { put("audio_db_above_noise_floor", it) }
        put("on_device_risk_percent", onDeviceRiskPercent)
        put("baseline_anomaly_score_0_to_1", baselineScore)
        put("baseline_z_accel", baselineZAccel)
        put("baseline_z_gyro", baselineZGyro)
        put("baseline_z_audio", baselineZAudio)
        put("independent_signals_unusual", independentSignalsUnusual)
        put("seconds_unusual_in_a_row", secondsUnusualInARow)
        secondsSinceImpact?.let { put("seconds_since_impact", it) }
        stillSinceImpactSeconds?.let { put("still_for_seconds_since_impact", it) }
        movementResumedAfterImpact?.let { put("movement_resumed_after_impact", it) }
        unexpectedStop?.let { put("unexpected_stop", it) }
    }
}

data class GemmaVerdict(
    val label: String,        // EMERGENCY | HARMLESS | UNCERTAIN
    val confidence: Double,   // 0..1
    val reason: String,
    val source: String,       // "gemma" or "fallback"
    val receivedAtMs: Long = System.currentTimeMillis()
)

class GemmaClient(
    private val apiKey: String,
    /** Other valid id: "gemma-4-31b-it" (bigger, slower). If you get 404, copy the exact id from Google AI Studio. */
    private val model: String = "gemma-4-26b-a4b-it"
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    private val systemPrompt = """
You are the incident-reasoning layer of ZeroTap, an on-phone safety app that detects possible
emergencies (vehicle accidents, personal-safety incidents) from phone sensor summaries.
You receive ONLY numeric features, never audio or location.
Decide whether the pattern looks like a REAL emergency or a HARMLESS event that looks dangerous
(phone dropped, phone shaken, running, bus/train movement, sudden braking, speed bump, pothole,
car horn or traffic noise).
Guidance:
- A drop usually has a short disturbance, then movement resumes or the phone is picked up.
- A crash usually has sustained vehicle speed, a large sudden change, then prolonged stillness.
- One unusual signal alone is weak evidence. Several agreeing signals are strong evidence.
- A wrongly cancelled real emergency is far worse than a false alarm. If unsure, answer UNCERTAIN.
Reply with ONLY one JSON object, no markdown, in exactly this shape:
{"label":"EMERGENCY"|"HARMLESS"|"UNCERTAIN","confidence":0.0-1.0,"reason":"max 20 words"}
""".trim()

    /** Blocking network call. Never throws. Call it from a background thread (Dispatchers.IO). */
    fun classify(features: IncidentFeatures): GemmaVerdict {
        if (!isConfigured) return fallback("no API key")
        val userText = "Sensor summary:\n" + features.toJson().toString(2)
        return try {
            var resp = post(buildBody(userText, useSystemField = true))
            // Some setups reject systemInstruction (HTTP 400): retry with it merged into the prompt.
            if (resp.first == 400) resp = post(buildBody(systemPrompt + "\n\n" + userText, useSystemField = false))
            if (resp.first !in 200..299) fallback("HTTP ${resp.first}") else parseVerdict(resp.second)
        } catch (e: Exception) {
            fallback(e.javaClass.simpleName)
        }
    }

    private fun fallback(why: String) =
        GemmaVerdict("UNCERTAIN", 0.0, "Gemma unavailable ($why); on-device rules only", "fallback")

    private fun buildBody(text: String, useSystemField: Boolean): String {
        val body = JSONObject()
        if (useSystemField) {
            body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
        }
        body.put("contents", JSONArray().put(
            JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))
        ))
        body.put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 600))
        return body.toString()
    }

    private fun post(json: String): Pair<Int, String> {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 4000
            conn.readTimeout = 9000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } } ?: ""
            return code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun parseVerdict(raw: String): GemmaVerdict {
        val root = JSONObject(raw)
        val parts = root.optJSONArray("candidates")?.optJSONObject(0)
            ?.optJSONObject("content")?.optJSONArray("parts") ?: return fallback("empty response")
        // Gemma 4 can return "thinking" parts (thought=true). Ignore them, keep the answer text.
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val p = parts.optJSONObject(i) ?: continue
            if (p.optBoolean("thought", false)) continue
            sb.append(p.optString("text", ""))
        }
        val text = sb.toString()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return fallback("no JSON in answer")
        val obj = JSONObject(text.substring(start, end + 1))
        val label = obj.optString("label", "UNCERTAIN").uppercase()
        if (label != "EMERGENCY" && label != "HARMLESS" && label != "UNCERTAIN") return fallback("bad label")
        val conf = obj.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
        return GemmaVerdict(label, conf, obj.optString("reason", ""), "gemma")
    }
}

/** How Gemma's opinion changes the score. It can only nudge. */
object GemmaFusion {
    fun adjustPercent(percent: Int, v: GemmaVerdict): Int = when {
        v.source != "gemma" -> percent
        v.label == "EMERGENCY" && v.confidence >= 0.6 -> (percent + 15).coerceAtMost(100)
        v.label == "HARMLESS" && v.confidence >= 0.8 -> (percent * 0.7).toInt()   // down-weight only, never cancel
        else -> percent
    }
}
