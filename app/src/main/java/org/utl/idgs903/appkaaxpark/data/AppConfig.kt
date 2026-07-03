package org.utl.idgs903.appkaaxpark.data

/**
 * Interruptor central de modo de conexión.
 * Para cambiar entre versión local y en línea, solo edita [modoConexion].
 *
 * LOCAL  → Mosquitto en tu red local (demo presencial, sin internet)
 * ONLINE → HiveMQ Cloud con TLS (demo remoto, requiere internet)
 */
enum class ModoConexion { LOCAL, ONLINE }

object AppConfig {
    // ── Cambia esta línea para alternar entre versiones ──────────────────────
    //val modoConexion: ModoConexion = ModoConexion.LOCAL
    val modoConexion: ModoConexion = ModoConexion.ONLINE
}