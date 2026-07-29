package org.utl.idgs903.appkaaxpark.data

/**
 * Interruptor central de modo de conexion.
 * Para cambiar entre version local y en linea, solo edita [modoConexion].
 *
 * LOCAL  -> Mosquitto en tu red local (demo presencial, sin internet)
 * ONLINE -> HiveMQ Cloud con TLS (demo remoto, requiere internet)
 */
enum class ModoConexion { LOCAL, ONLINE }

object AppConfig {
    // Cambia esta linea para alternar entre versiones.
    //val modoConexion: ModoConexion = ModoConexion.LOCAL
    val modoConexion: ModoConexion = ModoConexion.ONLINE

    // Configuracion del asistente IA.
    // Si esto queda deshabilitado, la app usa un motor local de respaldo
    // que responde con datos reales de Firebase.
    val aiBackendEnabled: Boolean = false
    val aiBackendBaseUrl: String = "https://tu-backend-ejemplo.com"
    val aiBackendPath: String = "/api/kaaxpark/assistant"
    val aiRequestTimeoutMs: Int = 15_000
}
