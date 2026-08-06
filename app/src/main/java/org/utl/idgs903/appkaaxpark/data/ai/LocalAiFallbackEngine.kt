package org.utl.idgs903.appkaaxpark.data.ai

import org.utl.idgs903.appkaaxpark.data.VehicleInfo
import java.text.Normalizer
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LocalAiFallbackEngine {

    fun answer(
        question: String,
        context: ParkingAiContext,
        rol: RolUsuario = RolUsuario.CLIENTE,
        reason: String? = null
    ): AiAssistantReply {
        val normalized = quitarAcentos(question.lowercase(Locale.getDefault()))
        val esPreguntaPersonal = containsAny(
            normalized,
            listOf("tengo", "mi vehiculo", "mis vehiculo", "mi auto", "mis auto", "mi carro", "mis carro")
        )
        val answer = when {
            rol == RolUsuario.CLIENTE && esPreguntaSoloAdmin(normalized) ->
                "Esa informacion es exclusiva para administradores. Si necesitas algo sobre tu cuenta, tus vehiculos o tu historial, con gusto te ayudo."

            // 1. Sustentabilidad - Agua (Prioridad Alta)
            containsAny(normalized, listOf("capacidad de la cisterna", "capacidad cisterna", "cisterna", "capacidad de la cisterna")) ->
                buildCapacidadCisterna(context)

            containsAny(normalized, listOf("nivel de tanque", "nivel del tanque", "nivel tanque", "tanque", "que nivel tiene", "cuanta agua tiene el tanque")) ->
                buildNivelTanque(context)

            containsAny(normalized, listOf("agua captada", "agua recolectada", "litros captados", "litros recolectados", "litros de agua", "cuanta agua hay")) ->
                buildSustentabilidadAgua(context)

            // 2. Sustentabilidad - Energía
            containsAny(normalized, listOf("energia solar", "energia generada", "kwh", "kwh generados", "cuanta energia", "porcentaje solar", "porcentaje de energia", "energia almacenada", "energia actual")) ->
                buildSustentabilidadEnergia(context)

            containsAny(normalized, listOf("energia ahorrada", "ahorro energetico", "ahorro de energia")) ->
                buildSustentabilidadAhorro(context)

            // 3. Sustentabilidad - Bomba
            containsAny(normalized, listOf("bomba de agua", "bomba", "estatus de la bomba", "estado de la bomba", "bomba encendida", "bomba funciona", "bomba esta prendida")) ->
                buildSustentabilidadBomba(context)

            // 4. Ocupación y Cajones
            containsAny(normalized, listOf("ocupados", "ocupacion actual", "cuantos estan ocupados", "vehiculos estacionados ahora")) &&
            !containsAny(normalized, listOf("libres", "disponibles", "vacantes")) ->
                "Hay ${context.cajonesOcupados} cajones ocupados de ${context.totalCajones} en total (${context.ocupacionPorcentaje}% de ocupacion)."

            containsAny(normalized, listOf("porcentaje de ocupacion", "nivel de ocupacion", "que tan lleno esta")) ->
                "La ocupacion actual es del ${context.ocupacionPorcentaje}% (${context.cajonesOcupados} de ${context.totalCajones} cajones)."

            containsAny(normalized, listOf("libres", "disponibles", "vacantes", "disponibilidad", "cajones vacios")) &&
            !containsAny(normalized, listOf("ocupados", "ocupacion")) ->
                "Hay ${context.cajonesLibres} cajones libres de ${context.totalCajones}."

            containsAny(normalized, listOf("nivel", "niveles", "distribucion por piso", "pisos")) ->
                buildNiveles(context)

            // 5. Estadísticas de Tiempo
            containsAny(normalized, listOf("tiempo promedio", "duracion promedio", "promedio de estancia", "cuanto tiempo se quedan")) ->
                context.duracionPromedioHoyMin?.let {
                    "El tiempo promedio de las estancias que han finalizado hoy es de $it minutos."
                } ?: "No hay suficientes datos de estancias finalizadas hoy para calcular el promedio."

            // 6. Usuarios y Clientes
            containsAny(normalized, listOf("usuarios activos", "clientes activos", "quien esta estacionado")) ->
                buildCurrentlyParked(context)

            containsAny(normalized, listOf("cuantos clientes", "total de clientes", "clientes registrados", "usuarios registrados")) ->
                "Hay ${context.totalClientes} clientes registrados en el sistema."

            containsAny(normalized, listOf("cuantos administradores", "total de administradores", "admin registrados")) ->
                "Hay ${context.totalAdministradores} administradores registrados."

            containsAny(normalized, listOf("energia ahorrada", "ahorro energetico", "energia ahorrada")) ->
                buildSustentabilidadAhorro(context)

            // 7. Información Personal y Vehículos
            containsAny(normalized, listOf("vehiculo", "vehiculos", "auto", "autos", "carro", "carros")) &&
            containsAny(normalized, listOf("cuantos", "que", "cual", "registrados")) &&
            esPreguntaPersonal ->
                buildVehicleList(context)

            containsAny(normalized, listOf("mis vehiculos", "mis autos", "mis carros", "vehiculos que tengo", "que vehiculos tengo")) ->
                buildVehicleList(context)

            containsAny(normalized, listOf("estoy estacionado", "llevo estacionado", "tiempo actual", "cuanto tiempo llevo", "estancia actual")) ->
                buildCurrentStay(context)

            containsAny(normalized, listOf("vehiculo", "vehiculos", "auto", "autos", "carro", "carros")) &&
            containsAny(normalized, listOf("cuantos", "numero", "total", "cuenta", "registrados")) &&
            !esPreguntaPersonal ->
                "Hay ${context.totalVehiculos} vehiculos registrados en el sistema."

            // 8. Otros (Tarifa, CO2, Árboles, etc.)
            containsAny(normalized, listOf("tarifa", "costo", "precio", "cobro", "cual es el costo", "cual es la tarifa")) ->
                "La tarifa por hora configurada es ${formatMoney(context.tarifaPorHora)}."

            containsAny(normalized, listOf("co2", "carbono", "evitamos", "evitar", "emisiones", "cual es el co2", "cual es el ahorro de co2")) ->
                buildSustentabilidadCO2(context)

            containsAny(normalized, listOf("arbol", "arboles", "equivalente", "salvados", "cuantos arboles", "cual es el equivalente en arboles")) ->
                buildSustentabilidadArboles(context)

            containsAny(normalized, listOf("cuanto tiempo", "cuanto estuv", "tiempo que estuve", "cuanto estuve", "cuanto estuvo")) ->
                answerDurationForDay(normalized, context)

            containsAny(normalized, listOf("cuanto pagu", "cuanto cost", "total pagado", "costo total", "monto total")) ->
                answerCostForDay(normalized, context)

            containsAny(normalized, listOf("que vehiculo", "que auto", "que carro", "cual vehiculo", "cual auto")) ->
                answerVehicleForDay(normalized, context)

            containsAny(normalized, listOf("que cajon", "en que cajon", "cual cajon", "lugar asignado")) ->
                answerSpotForDay(normalized, context)

            containsAny(normalized, listOf("que dias", "dias que", "dias utilice", "dias use", "dias estuve")) ->
                answerDaysForVehicle(normalized, context)

            // Entradas y Salidas hoy
            containsAny(normalized, listOf("entradas hoy", "cuantos entraron", "ingresos hoy", "vehiculos entraron", "entradas registradas")) ->
                "Hoy han ingresado ${context.entradasHoy} vehiculos al estacionamiento."

            containsAny(normalized, listOf("salidas hoy", "cuantos salieron", "retiros hoy", "vehiculos salieron", "salidas registradas")) ->
                "Hoy se han registrado ${context.salidasHoy} salidas de vehiculos."

            // Pagos en el historial
            containsAny(normalized, listOf("cuanto he pagado", "gastado", "total de mis pagos", "dinero gastado")) -> {
                val total = context.userVisits.sumOf { it.totalPagado }
                "Hasta ahora has pagado un total de ${formatMoney(total)} en tus visitas registradas."
            }

            // 9. Resumen y Fallback
            containsAny(normalized, listOf("resumen", "general", "estado general")) ->
                buildResumen(context)

            else ->
                buildDefaultAnswer(context)
        }

        return AiAssistantReply(
            answer = if (reason.isNullOrBlank()) answer else "$answer\n\nNota: se uso un respaldo local porque $reason",
            mode = AiAnswerMode.LOCAL,
            sourceLabel = "Motor local de respaldo"
        )
    }

    private fun answerDurationForDay(normalized: String, context: ParkingAiContext): String {
        val fecha = parseDate(normalized)
        if (fecha != null) {
            val visita = context.userVisits.find { it.fecha == fecha }
            if (visita != null) {
                return "El dia ${visita.fechaDisplay} estuviste estacionado un total de ${visita.tiempoEstacionado}."
            }
            return "No encontre registros para el dia $fecha."
        }

        context.estanciaActualMinutos?.let {
            return "Actualmente llevas $it minutos estacionado."
        }

        if (context.userVisits.isNotEmpty()) {
            val ultima = context.userVisits.last()
            return "No encontre registros para esa fecha especifica. Tu ultima visita fue el ${ultima.fechaDisplay} con ${ultima.tiempoEstacionado} de estancia."
        }

        return "No encontre datos de tu estancia actual ni de dias anteriores."
    }

    private fun answerCostForDay(normalized: String, context: ParkingAiContext): String {
        val fecha = parseDate(normalized)
        if (fecha != null) {
            val visita = context.userVisits.find { it.fecha == fecha }
            if (visita != null) {
                return "El dia ${visita.fechaDisplay} pagaste ${formatMoney(visita.totalPagado)}."
            }
            return "No encontre registros de pago para el dia $fecha."
        }

        if (context.userVisits.isNotEmpty()) {
            val total = context.userVisits.sumOf { it.totalPagado }
            return "No encontre registros para esa fecha especifica. Tu total acumulado en todas las visitas es ${formatMoney(total)}."
        }

        return "No encontre datos de pago para esa fecha."
    }

    private fun answerVehicleForDay(normalized: String, context: ParkingAiContext): String {
        val fecha = parseDate(normalized)
        if (fecha != null) {
            val visita = context.userVisits.find { it.fecha == fecha }
            if (visita != null) {
                return "El dia ${visita.fechaDisplay} utilizaste tu ${visita.vehiculo} (placa: ${visita.placa})."
            }
            return "No encontre registros para el dia $fecha."
        }

        if (context.userVisits.isNotEmpty()) {
            val ultimo = context.userVisits.last()
            return "No encontre registros para esa fecha especifica. Tu ultima visita fue el ${ultimo.fechaDisplay} con tu ${ultimo.vehiculo} (placa: ${ultimo.placa})."
        }

        return "No encontre datos para esa fecha."
    }

    private fun answerSpotForDay(normalized: String, context: ParkingAiContext): String {
        val fecha = parseDate(normalized)
        if (fecha != null) {
            val visita = context.userVisits.find { it.fecha == fecha }
            if (visita != null) {
                return "El dia ${visita.fechaDisplay} estacionaste en el cajon ${visita.cajon}."
            }
            return "No encontre registros para el dia $fecha."
        }

        if (context.userVisits.isNotEmpty()) {
            val ultimo = context.userVisits.last()
            return "No encontre registros para esa fecha especifica. Tu ultima visita fue el ${ultimo.fechaDisplay} en el cajon ${ultimo.cajon}."
        }

        return "No encontre datos para esa fecha."
    }

    private fun answerDaysForVehicle(normalized: String, context: ParkingAiContext): String {
        val vehiculoBuscado = extractVehicleName(normalized)
        if (vehiculoBuscado == null) {
            return "No encontre un nombre de vehiculo en tu pregunta."
        }

        val visitas = context.userVisits.filter {
            it.vehiculo.lowercase(Locale.getDefault()).contains(vehiculoBuscado) ||
            it.placa.lowercase(Locale.getDefault()).contains(vehiculoBuscado)
        }

        if (visitas.isEmpty()) {
            return "No encontre un vehiculo con ese nombre en tu historial."
        }

        return buildString {
            appendLine("Encontradas ${visitas.size} visita(s) con $vehiculoBuscado:")
            visitas.forEach { v ->
                appendLine("- ${v.fechaDisplay}: ${v.vehiculo} (${v.placa}), cajon ${v.cajon}, ${v.tiempoEstacionado}, total ${formatMoney(v.totalPagado)}")
            }
        }
    }

    private fun buildVehicleList(context: ParkingAiContext): String {
        if (context.misVehiculos.isEmpty()) {
            return "No tienes vehiculos registrados."
        }

        return buildString {
            appendLine("Tus vehiculos registrados:")
            context.misVehiculos.forEach { v ->
                val marcador = if (v.isActive) " (en uso)" else ""
                appendLine("- ${v.brand} ${v.model} (placa: ${v.plate})$marcador")
            }
        }
    }

    private fun buildCurrentStay(context: ParkingAiContext): String {
        val minutos = context.estanciaActualMinutos
        val vehiculo = context.vehiculoActual
        if (minutos == null) {
            return "No tienes una estancia activa en este momento."
        }
        val vehiculoText = if (vehiculo.isNullOrBlank()) "" else " con $vehiculo"
        return "Actualmente tienes una estancia activa de $minutos minutos$vehiculoText."
    }

    private fun buildCurrentlyParked(context: ParkingAiContext): String {
        val vehiculo = context.vehiculoActual
        val minutos = context.estanciaActualMinutos
        if (vehiculo == null && minutos == null) {
            return "No hay vehiculos actualmente estacionados en la base de datos."
        }
        val vehiculoText = vehiculo ?: "vehiculo desconocido"
        val tiempoText = minutos?.let { " lleva $it minutos estacionado" } ?: ""
        return "Actualmente hay un vehiculo estacionado: $vehiculoText$tiempoText."
    }

    private fun buildSustentabilidadEnergia(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        val nivel = when {
            info.porcentajeSolar >= 70.0 -> "ALTA"
            info.porcentajeSolar >= 40.0 -> "MEDIA"
            else -> "BAJA"
        }
        return "El nivel de recepcion solar es $nivel con un aporte del ${formatNumber(info.porcentajeSolar)}%."
    }

    private fun buildSustentabilidadCO2(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        return "No existe informacion disponible sobre CO2 evitado."
    }

    private fun buildSustentabilidadArboles(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        return "No existe informacion disponible sobre arboles equivalentes."
    }

    private fun buildSustentabilidadBomba(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        return if (info.bombaAguaEncendida) {
            "La bomba de agua esta encendida y funcionando."
        } else {
            "La bomba de agua esta apagada."
        }
    }

    private fun buildSustentabilidadAgua(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        return "Se captaron ${formatNumber(info.aguaCaptadaLitros)} L de agua y se usaron ${formatNumber(info.aguaUsadaRiegoLitros)} L para riego."
    }

    private fun buildCapacidadCisterna(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        // Si no tienes un campo de "capacidad máxima" explícito en Firebase, 
        // pero sabes que es de 10,000L por ejemplo, puedes hardcodearlo o calcularlo.
        // Aquí usaremos una respuesta basada en lo que el sistema monitorea.
        return "La cisterna tiene una capacidad monitoreada de 10,000 L. Actualmente el nivel es del ${formatNumber(info.nivelTanquePorcentaje)}%."
    }

    private fun buildNivelTanque(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        return "El nivel actual del tanque es del ${formatNumber(info.nivelTanquePorcentaje)}%."
    }

    private fun buildSustentabilidadAhorro(context: ParkingAiContext): String {
        val info = context.sustentabilidad
            ?: return "No encontre datos de sustentabilidad en la base de datos."
        val nivel = when {
            info.porcentajeSolar >= 70.0 -> "ALTA"
            info.porcentajeSolar >= 40.0 -> "MEDIA"
            else -> "BAJA"
        }
        return "Se esta recibiendo energia: $nivel (aporte solar del ${formatNumber(info.porcentajeSolar)}%)."
    }

    private fun buildResumen(context: ParkingAiContext): String = buildString {
        append("Resumen actual: ")
        append("${context.cajonesLibres} cajones libres, ")
        append("${context.cajonesOcupados} ocupados y ")
        append("${context.ocupacionPorcentaje}% de ocupacion.")
        append(" Hoy hay ${context.entradasHoy} entradas y ${context.salidasHoy} salidas.")
        append(" Tarifa por hora: ${formatMoney(context.tarifaPorHora)}.")
        append(" Total clientes: ${context.totalClientes}.")
        append(" Total vehiculos: ${context.totalVehiculos}.")
    }

    private fun buildNiveles(context: ParkingAiContext): String {
        if (context.cajonesPorNivel.isEmpty()) {
            return "No encontre informacion de cajones por nivel."
        }

        return context.cajonesPorNivel.joinToString(
            prefix = "Distribucion por nivel: ",
            separator = " | "
        ) { nivel ->
            "Nivel ${nivel.nivel}: ${nivel.libres} libres y ${nivel.ocupados} ocupados"
        }
    }

    private fun buildDefaultAnswer(context: ParkingAiContext): String {
        return "Lo siento, no entendí tu pregunta. Intenta preguntar sobre la ocupación, los niveles, el agua captada o el historial de tus vehículos."
    }

    private fun parseDate(normalized: String): String? {
        val today = Date()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        when {
            normalized.contains("ayer") -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -1)
                return sdf.format(cal.time)
            }
            normalized.contains("hoy") -> {
                return sdf.format(today)
            }
        }

        val patronFechaCompleta = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""")
        val matchCompleto = patronFechaCompleta.find(normalized)
        if (matchCompleto != null) {
            val dia = matchCompleto.groupValues[1].padStart(2, '0')
            val mes = matchCompleto.groupValues[2].padStart(2, '0')
            val anio = matchCompleto.groupValues[3]
            return "$anio-$mes-$dia"
        }

        val patronFechaCorta = Regex("""(\d{1,2})[/\-.](\d{1,2})""")
        val matchCorto = patronFechaCorta.find(normalized)
        if (matchCorto != null) {
            val dia = matchCorto.groupValues[1].padStart(2, '0')
            val mes = matchCorto.groupValues[2].padStart(2, '0')
            val anio = SimpleDateFormat("yyyy", Locale.getDefault()).format(today)
            return "$anio-$mes-$dia"
        }

        val patronDiaMes = Regex("""(\d{1,2})\s+(de\s+)?(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)""")
        val matchDiaMes = patronDiaMes.find(normalized)
        if (matchDiaMes != null) {
            val dia = matchDiaMes.groupValues[1].padStart(2, '0')
            val mesNombre = matchDiaMes.groupValues[3]
            val mesNum = monthNameToNumber(mesNombre)
            if (mesNum != null) {
                val anio = SimpleDateFormat("yyyy", Locale.getDefault()).format(today)
                return "$anio-${mesNum.padStart(2, '0')}-$dia"
            }
        }

        return null
    }

    private fun monthNameToNumber(name: String): String? {
        return when (name.lowercase(Locale.getDefault())) {
            "enero" -> "01"
            "febrero" -> "02"
            "marzo" -> "03"
            "abril" -> "04"
            "mayo" -> "05"
            "junio" -> "06"
            "julio" -> "07"
            "agosto" -> "08"
            "septiembre" -> "09"
            "octubre" -> "10"
            "noviembre" -> "11"
            "diciembre" -> "12"
            else -> null
        }
    }

    private fun extractVehicleName(normalized: String): String? {
        val knownVehicles = listOf(
            "nissan", "kia", "toyota", "honda", "chevrolet", "volkswagen", "ford", "mazda",
            "hyundai", "audi", "bmw", "mercedes", "jeep", "volvo", "renault", "peugeot",
            "citroen", "fiat", "mitsubishi", "subaru", "mazda", "suzuki", "dodge", "ram",
            "chevrolet", "cadillac", "buick", "gmc", "chrysler", "jeep", "land", "rover"
        )
        for (vehicle in knownVehicles) {
            if (normalized.contains(vehicle)) {
                return vehicle
            }
        }
        return null
    }

    private fun containsAny(source: String, terms: List<String>): Boolean =
        terms.any { source.contains(it) }

    private fun formatMoney(value: Double): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"))
        return formatter.format(value)
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", value)
        }

    private fun quitarAcentos(texto: String): String {
        val normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
        return normalizado.replace(Regex("\\p{M}"), "")
    }

    private fun esPreguntaSoloAdmin(normalized: String): Boolean {
        val terminosAdmin = listOf(
            "administrador", "administradores",
            "cuantos clientes", "total de clientes", "clientes registrados",
            "lista de clientes", "buscar cliente",
            "cuantos usuarios", "usuarios activos", "usuarios inactivos",
            "vehiculos por cliente", "buscar vehiculo por placa",
            "cuantos vehiculos", "total de vehiculos", "vehiculos registrados",
            "tarifas historicas", "entradas del dia", "salidas del dia",
            "vehiculos actualmente estacionados", "vehiculos estacionados",
            "tendencia de ocupacion", "horas pico",
            "uso por dia", "uso por semana", "uso por mes"
        )
        return containsAny(normalized, terminosAdmin)
    }
}