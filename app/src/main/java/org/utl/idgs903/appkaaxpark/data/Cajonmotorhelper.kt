package org.utl.idgs903.appkaaxpark.data

/**
 * Une los 3 pasos necesarios para mover los motores de un cajón en
 * particular:
 *   1) saber qué secuencia tiene vinculada (ingreso o salida)
 *   2) traer sus pasos desde Firestore (colección 'secuencias',
 *      la misma que administra la página web)
 *   3) mandarlos al ESP32 por MQTT
 *
 * Pensado para llamarse desde Codigoqr (ingreso) y RecuperarVehiculo
 * (salida) sin repetir esta lógica en cada uno.
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
                        MotorMqttClient.obtener().ejecutarPasos(pasos, callback)
                    }
                }
            }
        }
    }

    /** Mismo mensaje "ocupado" que ya usa la página web cuando el robot rechaza por estar en curso otra acción. */
    fun mensajeAmigable(error: Throwable): String =
        if (error.message == "ocupado") "El robot está ocupado con otra acción, espera un momento."
        else error.message ?: "No se pudo mover el mecanismo."
}