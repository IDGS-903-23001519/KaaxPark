package org.utl.idgs903.appkaaxpark.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

class MotorMqttClientOnline private constructor() {

    private var client: Mqtt3AsyncClient? = null
    private val pendientes = ConcurrentHashMap<String, (Result<Unit>) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "MotorMqttOnline"

        private const val HOST = "f7c0eee45b1e495da57c64fa8aeb95e8.s1.eu.hivemq.cloud"
        private const val PORT = 8883
        private const val USER = "puentelocal"
        private const val PASS = "Kaaxpark5"

        private const val TOPIC_REQUEST  = "robot1/rpc/request"
        private const val TOPIC_RESPONSE = "robot1/rpc/response"

        @Volatile
        private var instancia: MotorMqttClientOnline? = null

        fun obtener(): MotorMqttClientOnline =
            instancia ?: synchronized(this) {
                instancia ?: MotorMqttClientOnline().also {
                    it.conectar()
                    instancia = it
                }
            }
    }

    private fun conectar() {
        try {
            // Fix SSL para Android: sslWithDefaultConfig() en Android puede usar
            // un truststore vacío (JVM) y el handshake TLS falla. Establecemos
            // el SSLContext por defecto ANTES de construir el cliente para que
            // use el truststore del sistema Android (que incluye Let's Encrypt).
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
            SSLContext.setDefault(sslContext)

            val nuevoClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier("android-kaax-${UUID.randomUUID()}")
                .serverHost(HOST)
                .serverPort(PORT)
                .sslWithDefaultConfig()
                .buildAsync()

            client = nuevoClient

            Log.i(TAG, "Intentando conectar a $HOST:$PORT con usuario '$USER'…")

            nuevoClient.connectWith()
                .simpleAuth()
                .username(USER)
                .password(PASS.toByteArray())
                .applySimpleAuth()
                .send()
                .thenAccept {
                    Log.i(TAG, "✓ Conectado a HiveMQ Cloud")
                    suscribirRespuestas()
                }
                .exceptionally { err ->
                    // Mira este mensaje en Logcat para ver exactamente qué falló
                    Log.e(TAG, "✗ Error al conectar HiveMQ: ${err.message}", err)
                    lastError = err.message ?: "Error desconocido al conectar"
                    null
                }
        } catch (e: Exception) {
            Log.e(TAG, "✗ Excepción al construir cliente MQTT: ${e.message}", e)
            lastError = e.message ?: "Error de inicialización"
        }
    }

    private var lastError: String? = null

    private fun suscribirRespuestas() {
        client?.subscribeWith()
            ?.topicFilter(TOPIC_RESPONSE)
            ?.qos(MqttQos.AT_MOST_ONCE)
            ?.callback { publish ->
                val payload = String(publish.payloadAsBytes)
                Log.d(TAG, "← Respuesta recibida: $payload")
                try {
                    val json = JSONObject(payload)
                    val id = json.optString("id")
                    val callback = pendientes.remove(id) ?: return@callback
                    val resultado =
                        if (json.optBoolean("ok", true)) Result.success(Unit)
                        else Result.failure(Exception(json.optString("error", "error")))
                    mainHandler.post { callback(resultado) }
                } catch (_: Exception) { }
            }
            ?.send()
            ?.thenAccept { Log.i(TAG, "✓ Suscrito a $TOPIC_RESPONSE") }
            ?.exceptionally { err ->
                Log.e(TAG, "✗ Error al suscribir: ${err.message}")
                null
            }
    }

    fun ejecutarPasos(pasos: List<Map<String, Any>>, callback: (Result<Unit>) -> Unit) {
        val c = client
        if (c == null || !c.state.isConnected) {
            Log.w(TAG, "ejecutarPasos() sin conexión activa. Intentando reconectar...")
            conectar() // Intentar reconectar si se perdió la conexión
            
            mainHandler.postDelayed({
                val retryC = client
                if (retryClientConnected(retryC)) {
                    enviarPasosMqtt(retryC!!, pasos, callback)
                } else {
                    val detail = if (lastError != null) ": $lastError" else ""
                    callback(Result.failure(Exception("Sin conexión al broker HiveMQ$detail")))
                }
            }, 3000L) // Aumentamos tiempo de espera de reconexión
            return
        }

        enviarPasosMqtt(c, pasos, callback)
    }

    private fun retryClientConnected(c: Mqtt3AsyncClient?): Boolean {
        return c != null && c.state.isConnected
    }

    private fun enviarPasosMqtt(c: Mqtt3AsyncClient, pasos: List<Map<String, Any>>, callback: (Result<Unit>) -> Unit) {
        val id = UUID.randomUUID().toString().take(8)
        Log.d(TAG, "→ Publicando ejecutar_pasos id=$id (${pasos.size} pasos)")

        val pasosArray = JSONArray()
        pasos.forEach { paso ->
            val obj = JSONObject()
            paso.forEach { (k, v) -> obj.put(k, v) }
            pasosArray.put(obj)
        }
        val body = JSONObject().apply {
            put("id", id)
            put("accion", "ejecutar_pasos")
            put("pasos", pasosArray)
        }

        mainHandler.postDelayed({
            pendientes.remove(id)?.invoke(Result.failure(Exception("sin respuesta del robot")))
        }, 8000L) // Aumentamos a 8s por latencia de nube

        pendientes[id] = callback

        c.publishWith()
            .topic(TOPIC_REQUEST)
            .payload(body.toString().toByteArray())
            .send()
            .thenAccept { Log.d(TAG, "→ Publicado en $TOPIC_REQUEST") }
            .exceptionally { err ->
                Log.e(TAG, "✗ Error al publicar: ${err.message}")
                mainHandler.post { callback(Result.failure(err)) }
                null
            }
    }
}