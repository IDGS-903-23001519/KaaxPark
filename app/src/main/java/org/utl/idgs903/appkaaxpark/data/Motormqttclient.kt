package org.utl.idgs903.appkaaxpark.data

import android.os.Handler
import android.os.Looper
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente MQTT hacia el broker local (el mismo Mosquitto que usa la página
 * web). Singleton: una sola conexión para toda la app, igual que hicimos en
 * Angular con el servicio providedIn:'root'.
 *
 * Por ahora apunta al broker local — cuando hagas la versión "en línea" con
 * HiveMQ Cloud, solo cambias HOST/PORT/USER/PASS (y agregas TLS), el resto
 * del código (ejecutarPasos, los topics) no cambia.
 */
class MotorMqttClient private constructor() {

    private var client: Mqtt3AsyncClient? = null
    private val pendientes = ConcurrentHashMap<String, (Result<Unit>) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        // Ajusta a la IP de tu PC donde corre Mosquitto, y a tus credenciales reales.
        private const val HOST = "192.168.137.1"
        private const val PORT = 1883
        private const val USER = "motorctl"
        private const val PASS = "1234"

        private const val TOPIC_REQUEST = "robot1/rpc/request"
        private const val TOPIC_RESPONSE = "robot1/rpc/response"

        @Volatile
        private var instancia: MotorMqttClient? = null

        fun obtener(): MotorMqttClient =
            instancia ?: synchronized(this) {
                instancia ?: MotorMqttClient().also {
                    it.conectar()
                    instancia = it
                }
            }
    }

    private fun conectar() {
        val nuevoClient = MqttClient.builder()
            .useMqttVersion3()
            .identifier("android-${UUID.randomUUID()}")
            .serverHost(HOST)
            .serverPort(PORT)
            .buildAsync()

        client = nuevoClient

        nuevoClient.connectWith()
            .simpleAuth()
            .username(USER)
            .password(PASS.toByteArray())
            .applySimpleAuth()
            .send()
            .whenComplete { _, throwable ->
                if (throwable == null) suscribirRespuestas()
                // Si falla, el siguiente ejecutarPasos() lo reporta como
                // "sin conexión" — no hace falta reintentar aquí a mano.
            }
    }

    private fun suscribirRespuestas() {
        client?.subscribeWith()
            ?.topicFilter(TOPIC_RESPONSE)
            ?.qos(MqttQos.AT_MOST_ONCE)
            ?.callback { publish ->
                val payload = String(publish.payloadAsBytes)
                try {
                    val json = JSONObject(payload)
                    val id = json.optString("id")
                    val callback = pendientes.remove(id) ?: return@callback
                    val resultado =
                        if (json.optBoolean("ok", true)) Result.success(Unit)
                        else Result.failure(Exception(json.optString("error", "error")))
                    mainHandler.post { callback(resultado) }
                } catch (_: Exception) {
                    // JSON inesperado, se ignora — el timeout del que llamó se encargará.
                }
            }
            ?.send()
    }

    /**
     * Manda un arreglo de pasos directo al ESP32 (misma acción "ejecutar_pasos"
     * que usa la página). pasos: lista de mapas, por ejemplo:
     *   listOf(mapOf("tipo" to "P1", "valor" to 500, "velocidad" to 1000))
     */
    fun ejecutarPasos(pasos: List<Map<String, Any>>, callback: (Result<Unit>) -> Unit) {
        val c = client
        if (c == null || !c.state.isConnected) {
            mainHandler.post { callback(Result.failure(Exception("Sin conexión al broker"))) }
            return
        }

        val id = UUID.randomUUID().toString().take(8)

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

        // Igual que en la página: timeout si el ESP32 nunca responde.
        mainHandler.postDelayed({
            pendientes.remove(id)?.invoke(Result.failure(Exception("sin respuesta del robot")))
        }, 5000L)

        pendientes[id] = callback

        c.publishWith()
            .topic(TOPIC_REQUEST)
            .payload(body.toString().toByteArray())
            .send()
    }
}