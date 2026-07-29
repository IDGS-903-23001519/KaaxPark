package org.utl.idgs903.appkaaxpark.data.ai

import org.json.JSONArray
import org.json.JSONObject
import org.utl.idgs903.appkaaxpark.data.CajonInfo
import org.utl.idgs903.appkaaxpark.data.SustentabilidadInfo
import org.utl.idgs903.appkaaxpark.data.VehicleInfo
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserVisitDetail(
    val fecha: String,
    val fechaDisplay: String,
    val vehiculo: String,
    val placa: String,
    val cajon: String,
    val entrada: String,
    val salida: String?,
    val tiempoEstacionado: String,
    val tarifaPorHora: Double,
    val totalPagado: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fecha", fecha)
        put("fechaDisplay", fechaDisplay)
        put("vehiculo", vehiculo)
        put("placa", placa)
        put("cajon", cajon)
        put("entrada", entrada)
        put("salida", salida ?: JSONObject.NULL)
        put("tiempoEstacionado", tiempoEstacionado)
        put("tarifaPorHora", tarifaPorHora)
        put("totalPagado", totalPagado)
    }
}

enum class AiAnswerMode {
    LOCAL,
    REMOTE
}

enum class AiDataStatus {
    READY,
    PARTIAL
}

enum class RolUsuario {
    CLIENTE,
    ADMIN
}

data class NivelParkingResumen(
    val nivel: Int,
    val total: Int,
    val libres: Int,
    val ocupados: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("nivel", nivel)
        put("total", total)
        put("libres", libres)
        put("ocupados", ocupados)
    }
}

data class ParkingAiContext(
    val generatedAtMillis: Long,
    val dataStatus: AiDataStatus,
    val totalCajones: Int,
    val cajonesLibres: Int,
    val cajonesOcupados: Int,
    val ocupacionPorcentaje: Int,
    val estanciasActivas: Int,
    val entradasHoy: Int,
    val salidasHoy: Int,
    val duracionPromedioHoyMin: Int?,
    val tarifaPorHora: Double,
    val sustentabilidad: SustentabilidadInfo?,
    val cajonesPorNivel: List<NivelParkingResumen>,
    val totalClientes: Int,
    val totalVehiculos: Int,
    val visitasPorDia: Map<String, Long>,
    val estanciaActualMinutos: Int?,
    val vehiculoActual: String?,
    val userVisits: List<UserVisitDetail>,
    val misVehiculos: List<VehicleInfo>,
    val totalAdministradores: Int
) {
    val generatedAtLabel: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(generatedAtMillis))

    fun toSummaryText(): String = buildString {
        appendLine("Generado: $generatedAtLabel")
        appendLine("Estado de datos: ${dataStatus.name}")
        appendLine("Cajones totales: $totalCajones")
        appendLine("Cajones libres: $cajonesLibres")
        appendLine("Cajones ocupados: $cajonesOcupados")
        appendLine("Ocupacion actual: $ocupacionPorcentaje%")
        appendLine("Estancias activas: $estanciasActivas")
        appendLine("Entradas hoy: $entradasHoy")
        appendLine("Salidas hoy: $salidasHoy")
        appendLine("Duracion promedio hoy: ${duracionPromedioHoyMin?.let { "$it min" } ?: "sin datos"}")
        appendLine("Tarifa por hora: ${formatMoney(tarifaPorHora)}")
        appendLine("Total clientes registrados: $totalClientes")
        appendLine("Total vehiculos registrados: $totalVehiculos")
        if (visitasPorDia.isNotEmpty()) {
            appendLine("Visitas por dia:")
            visitasPorDia.forEach { (fecha, duracion) ->
                appendLine("- $fecha: $duracion min")
            }
        }
        estanciaActualMinutos?.let {
            appendLine("Estancia actual: $it min")
        }
        vehiculoActual?.let {
            appendLine("Vehiculo en uso: $it")
        }
        if (userVisits.isNotEmpty()) {
            appendLine("Visitas recientes:")
            userVisits.forEach { v ->
                appendLine("- ${v.fechaDisplay}: ${v.vehiculo} (${v.placa}) en ${v.cajon}, ${v.tiempoEstacionado}, total ${formatMoney(v.totalPagado)}")
            }
        }
        if (misVehiculos.isNotEmpty()) {
            appendLine("Mis vehiculos:")
            misVehiculos.forEach { v ->
                appendLine("- ${v.brand} ${v.model} (placa: ${v.plate})")
            }
        }
        if (totalAdministradores > 0) {
            appendLine("Total administradores: $totalAdministradores")
        }
        sustentabilidad?.let { info ->
            appendLine("Agua captada: ${formatNumber(info.aguaCaptadaLitros)} L")
            appendLine("Agua usada en riego: ${formatNumber(info.aguaUsadaRiegoLitros)} L")
            appendLine("Energia generada: ${formatNumber(info.energiaGeneradaKwh)} kWh")
            appendLine("Nivel del tanque: ${formatNumber(info.nivelTanquePorcentaje)}%")
            appendLine("Porcentaje solar: ${formatNumber(info.porcentajeSolar)}%")
        }
        appendLine("Cajones por nivel:")
        cajonesPorNivel.forEach { nivel ->
            appendLine("- Nivel ${nivel.nivel}: ${nivel.libres} libres, ${nivel.ocupados} ocupados de ${nivel.total}")
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("generatedAt", generatedAtLabel)
        put("dataStatus", dataStatus.name)
        put("totalCajones", totalCajones)
        put("cajonesLibres", cajonesLibres)
        put("cajonesOcupados", cajonesOcupados)
        put("ocupacionPorcentaje", ocupacionPorcentaje)
        put("estanciasActivas", estanciasActivas)
        put("entradasHoy", entradasHoy)
        put("salidasHoy", salidasHoy)
        put(
            "duracionPromedioHoyMin",
            duracionPromedioHoyMin?.let { it } ?: JSONObject.NULL
        )
        put("tarifaPorHora", tarifaPorHora)
        put("totalClientes", totalClientes)
        put("totalVehiculos", totalVehiculos)
        put("visitasPorDia", JSONObject().apply {
            visitasPorDia.forEach { (fecha, duracion) ->
                put(fecha, duracion)
            }
        })
        put(
            "estanciaActualMinutos",
            estanciaActualMinutos?.let { it } ?: JSONObject.NULL
        )
        put("vehiculoActual", vehiculoActual ?: JSONObject.NULL)
        put("userVisits", JSONArray().apply {
            userVisits.forEach { put(it.toJson()) }
        })
        put("misVehiculos", JSONArray().apply {
            misVehiculos.forEach { v ->
                put(JSONObject().apply {
                    put("brand", v.brand)
                    put("model", v.model)
                    put("plate", v.plate)
                    put("color", v.color)
                    put("isActive", v.isActive)
                })
            }
        })
        put("totalAdministradores", totalAdministradores)
        put("cajonesPorNivel", JSONArray().apply {
            cajonesPorNivel.forEach { put(it.toJson()) }
        })
        put("sustentabilidad", sustentabilidad?.toJson() ?: JSONObject.NULL)
    }

    companion object {
        private fun formatMoney(value: Double): String {
            val formato = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
            return formato.format(value)
        }

        private fun formatNumber(value: Double): String {
            return if (value % 1.0 == 0.0) {
                value.toLong().toString()
            } else {
                String.format(Locale.getDefault(), "%.2f", value)
            }
        }
    }
}

data class AiAssistantReply(
    val answer: String,
    val mode: AiAnswerMode,
    val sourceLabel: String,
    val generatedAtMillis: Long = System.currentTimeMillis()
)

fun SustentabilidadInfo.toJson(): JSONObject = JSONObject().apply {
    put("aguaCaptadaLitros", aguaCaptadaLitros)
    put("aguaUsadaRiegoLitros", aguaUsadaRiegoLitros)
    put("energiaGeneradaKwh", energiaGeneradaKwh)
    put("nivelTanquePorcentaje", nivelTanquePorcentaje)
    put("porcentajeSolar", porcentajeSolar)
    put("bombaAguaEncendida", bombaAguaEncendida)
    put("alertas", mapToJsonObject(alertas))
}

fun CajonInfo.toSummaryJson(): JSONObject = JSONObject().apply {
    put("id", documentId)
    put("nivel", nivel)
    put("numeroCajon", numeroCajon)
    put("estado", estado)
}

@Suppress("UNCHECKED_CAST")
private fun mapToJsonObject(map: Map<String, Any?>): JSONObject = JSONObject().apply {
    map.forEach { (key, value) ->
        when (value) {
            null -> put(key, JSONObject.NULL)
            is Map<*, *> -> put(key, mapToJsonObject(value as Map<String, Any?>))
            is List<*> -> put(key, JSONArray().apply {
                value.forEach { item ->
                    when (item) {
                        null -> put(JSONObject.NULL)
                        is Map<*, *> -> put(mapToJsonObject(item as Map<String, Any?>))
                        else -> put(item)
                    }
                }
            })
            else -> put(key, value)
        }
    }
}
