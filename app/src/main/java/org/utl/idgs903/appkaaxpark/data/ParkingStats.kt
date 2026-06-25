package org.utl.idgs903.appkaaxpark.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class ReportPeriod {
    DIA, SEMANA, MES
}

data class DateRange(val start: Date, val end: Date)

object ParkingStats {

    private val LOCALE_ES = Locale("es", "MX")

    fun rangeFor(period: ReportPeriod, reference: Date = Date()): DateRange {
        val todayStart = startOfDay(reference)
        val todayEnd = addDays(todayStart, 1)
        return when (period) {
            ReportPeriod.DIA -> DateRange(todayStart, todayEnd)
            ReportPeriod.SEMANA -> DateRange(addDays(todayStart, -6), todayEnd)
            ReportPeriod.MES -> DateRange(addDays(todayStart, -29), todayEnd)
        }
    }

    fun bucketsFor(period: ReportPeriod, range: DateRange): List<DateRange> {
        val buckets = mutableListOf<DateRange>()
        var cursor = range.start
        val step = if (period == ReportPeriod.DIA) Calendar.HOUR_OF_DAY else Calendar.DAY_OF_MONTH
        while (cursor.before(range.end)) {
            val next = addCalendarField(cursor, step, 1)
            buckets.add(DateRange(cursor, next))
            cursor = next
        }
        return buckets
    }

    fun bucketLabel(period: ReportPeriod, bucket: DateRange): String {
        val formato = when (period) {
            ReportPeriod.DIA -> SimpleDateFormat("HH:00", LOCALE_ES)
            ReportPeriod.SEMANA -> SimpleDateFormat("EEE d", LOCALE_ES)
            ReportPeriod.MES -> SimpleDateFormat("d MMM", LOCALE_ES)
        }
        return formato.format(bucket.start)
    }

    fun rangeLabel(period: ReportPeriod, range: DateRange): String {
        val ultimoDia = addDays(range.end, -1)
        return when (period) {
            ReportPeriod.DIA -> SimpleDateFormat("d MMM yyyy", LOCALE_ES).format(range.start)
            else -> {
                val inicio = SimpleDateFormat("d MMM", LOCALE_ES).format(range.start)
                val fin = SimpleDateFormat("d MMM yyyy", LOCALE_ES).format(ultimoDia)
                "$inicio - $fin"
            }
        }
    }

    fun countEntradas(estancias: List<EstanciaResumen>, range: DateRange): Int =
        estancias.count { dentroDeRango(it.fechaEntrada.toDate(), range) }

    fun countSalidas(estancias: List<EstanciaResumen>, range: DateRange): Int =
        estancias.count { estancia ->
            estancia.fechaSalida?.toDate()?.let { dentroDeRango(it, range) } ?: false
        }

    fun occupancyPercent(estancias: List<EstanciaResumen>, totalCajones: Int, range: DateRange): Double {
        if (totalCajones <= 0) return 0.0
        val ocupados = estancias.filter { seSuperponeConRango(it, range) }
            .map { it.cajonId }
            .distinct()
            .size
        return ocupados * 100.0 / totalCajones
    }

    fun averageOccupancyPercent(estancias: List<EstanciaResumen>, totalCajones: Int, buckets: List<DateRange>): Double {
        if (buckets.isEmpty()) return 0.0
        return buckets.map { occupancyPercent(estancias, totalCajones, it) }.average()
    }

    fun averageStayDurationMillis(estancias: List<EstanciaResumen>, range: DateRange): Long? {
        val duraciones = estancias.mapNotNull { estancia ->
            val salida = estancia.fechaSalida?.toDate()
            if (salida != null && dentroDeRango(salida, range)) {
                salida.time - estancia.fechaEntrada.toDate().time
            } else {
                null
            }
        }
        if (duraciones.isEmpty()) return null
        return duraciones.average().toLong()
    }

    fun activeUsersCount(estancias: List<EstanciaResumen>, range: DateRange): Int =
        estancias.filter { seSuperponeConRango(it, range) }
            .map { it.usuarioId }
            .distinct()
            .size

    fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val horas = totalSeconds / 3600
        val minutos = (totalSeconds % 3600) / 60
        val segundos = totalSeconds % 60
        return String.format("%02d:%02d:%02d", horas, minutos, segundos)
    }

    private fun seSuperponeConRango(estancia: EstanciaResumen, range: DateRange): Boolean {
        val entrada = estancia.fechaEntrada.toDate()
        val salida = estancia.fechaSalida?.toDate()
        val terminaDespuesDelInicio = salida == null || salida.after(range.start)
        return entrada.before(range.end) && terminaDespuesDelInicio
    }

    private fun dentroDeRango(fecha: Date, range: DateRange): Boolean =
        !fecha.before(range.start) && fecha.before(range.end)

    private fun startOfDay(date: Date): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.time
    }

    private fun addDays(date: Date, days: Int): Date = addCalendarField(date, Calendar.DAY_OF_MONTH, days)

    private fun addCalendarField(date: Date, field: Int, amount: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(field, amount)
        return calendar.time
    }
}
