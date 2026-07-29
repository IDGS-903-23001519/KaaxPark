package org.utl.idgs903.appkaaxpark.data.ai

import org.json.JSONObject
import org.utl.idgs903.appkaaxpark.data.AppConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class RemoteAiAnswerClient {

    fun ask(
        question: String,
        context: ParkingAiContext,
        callback: (Result<AiAssistantReply>) -> Unit
    ) {
        Thread {
            try {
                val url = buildUrl()
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = AppConfig.aiRequestTimeoutMs
                    readTimeout = AppConfig.aiRequestTimeoutMs
                    doInput = true
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }

                val requestBody = AiPromptBuilder.buildRequestBody(question, context).toString()
                OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseText = readResponse(connection, responseCode)
                if (responseCode !in 200..299) {
                    throw IllegalStateException(
                        extractErrorMessage(responseText).ifBlank {
                            "El backend de IA respondio con codigo $responseCode."
                        }
                    )
                }

                val answer = extractAnswer(responseText)
                if (answer.isBlank()) {
                    throw IllegalStateException("El backend de IA no devolvio una respuesta valida.")
                }

                callback(
                    Result.success(
                        AiAssistantReply(
                            answer = answer,
                            mode = AiAnswerMode.REMOTE,
                            sourceLabel = "Backend IA"
                        )
                    )
                )
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }.start()
    }

    private fun buildUrl(): URL {
        val base = AppConfig.aiBackendBaseUrl.trim()
        require(base.isNotBlank()) { "La URL del backend IA no esta configurada." }
        val path = AppConfig.aiBackendPath.trim()
        val normalizedBase = base.trimEnd('/')
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return URL("$normalizedBase$normalizedPath")
    }

    private fun readResponse(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) {
            return ""
        }

        return BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }

    private fun extractAnswer(rawResponse: String): String {
        val text = rawResponse.trim()
        if (text.isBlank()) return ""
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return text
        }

        return try {
            val json = JSONObject(text)
            when {
                json.has("answer") -> json.optString("answer")
                json.has("message") -> json.optString("message")
                json.has("content") -> json.optString("content")
                json.has("data") && json.opt("data") is JSONObject -> {
                    val data = json.optJSONObject("data") ?: JSONObject()
                    when {
                        data.has("answer") -> data.optString("answer")
                        data.has("message") -> data.optString("message")
                        data.has("content") -> data.optString("content")
                        else -> text
                    }
                }
                else -> text
            }
        } catch (_: Exception) {
            text
        }
    }

    private fun extractErrorMessage(rawResponse: String): String {
        val text = rawResponse.trim()
        if (text.isBlank()) return ""
        return try {
            val json = JSONObject(text)
            when {
                json.has("error") -> json.optString("error")
                json.has("message") -> json.optString("message")
                json.has("detail") -> json.optString("detail")
                else -> text
            }
        } catch (_: Exception) {
            text
        }
    }
}
