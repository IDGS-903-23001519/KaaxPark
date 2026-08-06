package org.utl.idgs903.appkaaxpark.data.ai

import org.utl.idgs903.appkaaxpark.data.CajonInfo
import org.utl.idgs903.appkaaxpark.data.EstanciaResumen
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.ParkingStats
import org.utl.idgs903.appkaaxpark.data.ReportPeriod
import org.utl.idgs903.appkaaxpark.data.SustentabilidadInfo
import org.utl.idgs903.appkaaxpark.data.VehicleInfo
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

class ParkingAiContextProvider(
    private val repository: FirebaseRepository = FirebaseRepository(),
    private val userDocId: String? = null,
    private val rol: RolUsuario = RolUsuario.CLIENTE
) {

    companion object {
        private const val CACHE_TTL_MS = 60_000L
    }

    private var cachedContext: ParkingAiContext? = null
    private var cachedAt: Long = 0L

    fun load(callback: (Result<ParkingAiContext>) -> Unit) {
        val now = System.currentTimeMillis()
        if (cachedContext != null && (now - cachedAt) < CACHE_TTL_MS) {
            callback(Result.success(cachedContext!!))
            return
        }

        fetchEssentialData { essentialResult ->
            essentialResult.onSuccess { context ->
                cachedContext = context
                cachedAt = now
                callback(Result.success(context))
            }
            essentialResult.onFailure { error ->
                cachedContext?.let { cached ->
                    callback(Result.success(cached))
                } ?: callback(Result.failure(error))
            }
        }
    }

    private fun fetchEssentialData(callback: (Result<ParkingAiContext>) -> Unit) {
        var cajones: List<CajonInfo> = emptyList()
        var estancias: List<EstanciaResumen> = emptyList()
        var sustentabilidad: SustentabilidadInfo? = null
        var tarifaPorHora = 60.0
        var totalClientes = 0
        var totalVehiculos = 0
        var totalAdministradores = 0
        var userVisits: List<UserVisitDetail> = emptyList()
        var estanciaActualMinutos: Int? = null
        var vehiculoActual: String? = null
        var misVehiculos: List<VehicleInfo> = emptyList()

        val esAdmin = rol == RolUsuario.ADMIN
        var remaining = 4 + (if (esAdmin) 3 else 0) + (if (!userDocId.isNullOrBlank()) 3 else 0)
        var partialData = false

        fun complete() {
            remaining -= 1
            if (remaining == 0) {
                // Generar reporte diario para calcular visitas por día
                val visitasPorDiaMap = estancias
                    .filter { it.fechaSalida != null }
                    .groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it.fechaEntrada.toDate()) }
                    .mapValues { (_, items) -> items.size.toLong() }

                callback(
                    Result.success(
                        buildContext(
                            cajones = cajones,
                            estancias = estancias,
                            sustentabilidad = sustentabilidad,
                            tarifaPorHora = tarifaPorHora,
                            totalClientes = totalClientes,
                            totalVehiculos = totalVehiculos,
                            visitasPorDia = visitasPorDiaMap,
                            estanciaActualMinutos = estanciaActualMinutos,
                            vehiculoActual = vehiculoActual,
                            userVisits = userVisits,
                            misVehiculos = misVehiculos,
                            totalAdministradores = totalAdministradores,
                            partialData = partialData
                        )
                    )
                )
            }
        }

        repository.fetchCajones { result ->
            result.onSuccess { cajones = it }
            result.onFailure { partialData = true }
            complete()
        }

        repository.fetchEstancias { result ->
            result.onSuccess { estancias = it }
            result.onFailure { partialData = true }
            complete()
        }

        repository.fetchSustentabilidad { result ->
            result.onSuccess { sustentabilidad = it }
            result.onFailure { partialData = true }
            complete()
        }

        repository.fetchTarifaPorHora { result ->
            result.onSuccess { tarifaPorHora = it }
            result.onFailure { partialData = true }
            complete()
        }

        if (esAdmin) {
            repository.fetchClientCount { result ->
                result.onSuccess { totalClientes = it }
                result.onFailure { partialData = true }
                complete()
            }

            repository.fetchVehicleCount { result ->
                result.onSuccess { totalVehiculos = it }
                result.onFailure { partialData = true }
                complete()
            }

            repository.fetchAdminCount { result ->
                result.onSuccess { totalAdministradores = it }
                result.onFailure { partialData = true }
                complete()
            }
        }

        if (!userDocId.isNullOrBlank()) {
            repository.fetchUserVisitHistoryWithDetails(userDocId) { result ->
                result.onSuccess { visits ->
                    userVisits = visits
                }
                result.onFailure { partialData = true }
                complete()
            }

            repository.fetchCurrentStayForUser(userDocId) { result ->
                result.onSuccess { data ->
                    if (data != null) {
                        estanciaActualMinutos = data["duracionActualMin"] as? Int
                        val marca = data["vehiculoMarca"] as? String ?: ""
                        val modelo = data["vehiculoModelo"] as? String ?: ""
                        val placa = data["vehiculoPlaca"] as? String ?: ""
                        vehiculoActual = listOf(marca, modelo, "($placa)")
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }
                }
                result.onFailure { partialData = true }
                complete()
            }

            repository.fetchVehiclesByUserId(userDocId) { result ->
                result.onSuccess { vehicles ->
                    misVehiculos = vehicles
                }
                result.onFailure { partialData = true }
                complete()
            }
        }
    }

    private fun buildContext(
        cajones: List<CajonInfo>,
        estancias: List<EstanciaResumen>,
        sustentabilidad: SustentabilidadInfo?,
        tarifaPorHora: Double,
        totalClientes: Int,
        totalVehiculos: Int,
        visitasPorDia: Map<String, Long>,
        estanciaActualMinutos: Int?,
        vehiculoActual: String?,
        userVisits: List<UserVisitDetail>,
        misVehiculos: List<VehicleInfo>,
        totalAdministradores: Int,
        partialData: Boolean
    ): ParkingAiContext {
        val totalCajones = cajones.size
        val cajonesLibres = cajones.count { it.isLibre }
        val cajonesOcupados = totalCajones - cajonesLibres
        val ocupacionPorcentaje = if (totalCajones > 0) {
            (cajonesOcupados * 100f / totalCajones).roundToInt()
        } else {
            0
        }

        val rangoHoy = ParkingStats.rangeFor(ReportPeriod.DIA)
        val entradasHoy = ParkingStats.countEntradas(estancias, rangoHoy)
        val salidasHoy = ParkingStats.countSalidas(estancias, rangoHoy)
        val duracionPromedioHoyMin = ParkingStats.averageStayDurationMillis(estancias, rangoHoy)
            ?.let { (it / 60000.0).roundToInt() }
        val estanciasActivas = estancias.count { it.estatus.equals("ACTIVA", ignoreCase = true) }

        val cajonesPorNivel = cajones
            .groupBy { it.nivel }
            .map { (nivel, items) ->
                NivelParkingResumen(
                    nivel = nivel,
                    total = items.size,
                    libres = items.count { it.isLibre },
                    ocupados = items.count { !it.isLibre }
                )
            }
            .sortedBy { it.nivel }

        return ParkingAiContext(
            generatedAtMillis = System.currentTimeMillis(),
            dataStatus = if (partialData) AiDataStatus.PARTIAL else AiDataStatus.READY,
            totalCajones = totalCajones,
            cajonesLibres = cajonesLibres,
            cajonesOcupados = cajonesOcupados,
            ocupacionPorcentaje = ocupacionPorcentaje,
            estanciasActivas = estanciasActivas,
            entradasHoy = entradasHoy,
            salidasHoy = salidasHoy,
            duracionPromedioHoyMin = duracionPromedioHoyMin,
            tarifaPorHora = tarifaPorHora,
            sustentabilidad = sustentabilidad,
            cajonesPorNivel = cajonesPorNivel,
            totalClientes = totalClientes,
            totalVehiculos = totalVehiculos,
            visitasPorDia = visitasPorDia,
            estanciaActualMinutos = estanciaActualMinutos,
            vehiculoActual = vehiculoActual,
            userVisits = userVisits,
            misVehiculos = misVehiculos,
            totalAdministradores = totalAdministradores
        )
    }
}