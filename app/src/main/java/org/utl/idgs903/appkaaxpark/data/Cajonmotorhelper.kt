package org.utl.idgs903.appkaaxpark.data

/**
 * Une los 3 pasos necesarios para mover los motores de un cajón:
 *   1) obtener qué secuencia tiene vinculada (ingreso o salida) en Firestore
 *   2) traer los pasos de esa secuencia (colección 'secuencias')
 *   3) mandar los pasos al ESP32 por MQTT
 *
 * El cliente MQTT que se use (local o en línea) lo decide [AppConfig].
 * Para cambiar de versión solo hay que editar UNA línea en AppConfig.kt.
 */
object CajonMotorHelper {

    fun activarIngreso(
        repository: FirebaseRepository,
        cajonId: String,
        callback: (Result<Unit>) -> Unit = {}
    ) = ejecutar(repository, cajonId, esIngreso = true, callback)

    fun activarSalida(
        repository: FirebaseRepository,
        cajonId: String,
        callback: (Result<Unit>) -> Unit = {}
    ) = ejecutar(repository, cajonId, esIngreso = false, callback)

    private fun ejecutar(
        repository: FirebaseRepository,
        cajonId: String,
        esIngreso: Boolean,
        callback: (Result<Unit>) -> Unit
    ) {
        repository.fetchSecuenciaIdsDeCajon(cajonId) { idsResult ->
            idsResult.onFailure { callback(Result.failure(it)) }
            idsResult.onSuccess { (ingresoId, salidaId) ->
                val secuenciaId = if (esIngreso) ingresoId else salidaId
                if (secuenciaId.isNullOrBlank()) {
                    val tipo = if (esIngreso) "ingreso" else "salida"
                    callback(Result.failure(Exception("Este cajón no tiene secuencia de $tipo asignada.")))
                    return@onSuccess
                }

                repository.fetchPasosDeSecuencia(secuenciaId) { pasosResult ->
                    pasosResult.onFailure { callback(Result.failure(it)) }
                    pasosResult.onSuccess { pasos ->
                        if (pasos.isEmpty()) {
                            callback(Result.failure(Exception("La secuencia asignada no tiene pasos.")))
                            return@onSuccess
                        }

                        // ── Seleccionar cliente según el modo configurado ──────
                        when (AppConfig.modoConexion) {
                            ModoConexion.LOCAL  -> MotorMqttClient.obtener()
                                .ejecutarPasos(pasos, callback)
                            ModoConexion.ONLINE -> MotorMqttClientOnline.obtener()
                                .ejecutarPasos(pasos, callback)
                        }
                    }
                }
            }
        }
    }

    /** Mensaje legible para el usuario cuando el ESP32 rechaza o no responde. */
    fun mensajeAmigable(error: Throwable): String =
        when (error.message) {
            "ocupado"               -> "El robot está ocupado con otra acción, espera un momento."
            "sin respuesta del robot" -> "El robot no respondió. Verifica que esté encendido y conectado."
            else                    -> error.message ?: "No se pudo mover el mecanismo."
        }
}