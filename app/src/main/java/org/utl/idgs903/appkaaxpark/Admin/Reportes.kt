package org.utl.idgs903.appkaaxpark.Admin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.CajonInfo
import org.utl.idgs903.appkaaxpark.data.DateRange
import org.utl.idgs903.appkaaxpark.data.EstanciaResumen
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.ParkingStats
import org.utl.idgs903.appkaaxpark.data.ReportPeriod

class Reportes : BaseAdminActivity() {

    private data class ResumenReporte(
        val rango: DateRange,
        val ocupacionPromedio: Int,
        val tiempoPromedioMillis: Long?,
        val usuariosActivos: Int,
        val entradas: Int,
        val salidas: Int,
        val totalCajones: Int
    )

    private lateinit var repository: FirebaseRepository
    private lateinit var btnSelectorModulo: LinearLayout
    private lateinit var btnSelectorPeriodo: LinearLayout
    private lateinit var btnGenerarReporte: LinearLayout
    private lateinit var txtModuloSeleccionado: TextView
    private lateinit var txtPeriodoSeleccionado: TextView
    private lateinit var lblRangoPeriodo: TextView
    private lateinit var lblTendenciaTitulo: TextView
    private lateinit var lblExplicacionEjes: TextView
    private lateinit var txtOcupacionPromedio: TextView
    private lateinit var txtTiempoPromedioReporte: TextView
    private lateinit var txtUsuariosActivos: TextView
    private lateinit var chartTendencia: LineChart

    private var periodoActual = ReportPeriod.SEMANA
    private var moduloActualId = "general"
    private var moduloActualNombre = "General"
    private var resumenActual: ResumenReporte? = null
    private var estanciasActuales: List<EstanciaResumen> = emptyList()

    override fun getLayoutId(): Int = R.layout.activity_reportes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageTitle("Reportes")

        repository = FirebaseRepository()
        btnSelectorModulo = findViewById(R.id.btnSelectorModulo)
        btnSelectorPeriodo = findViewById(R.id.btnSelectorPeriodo)
        btnGenerarReporte = findViewById(R.id.btnGenerarReporte)
        txtModuloSeleccionado = findViewById(R.id.txtModuloSeleccionado)
        txtPeriodoSeleccionado = findViewById(R.id.txtPeriodoSeleccionado)
        lblRangoPeriodo = findViewById(R.id.lblRangoPeriodo)
        lblTendenciaTitulo = findViewById(R.id.lblTendenciaTitulo)
        lblExplicacionEjes = findViewById(R.id.lblExplicacionEjes)
        txtOcupacionPromedio = findViewById(R.id.txtOcupacionPromedio)
        txtTiempoPromedioReporte = findViewById(R.id.txtTiempoPromedioReporte)
        txtUsuariosActivos = findViewById(R.id.txtUsuariosActivos)
        chartTendencia = findViewById(R.id.chartTendencia)

        btnSelectorModulo.setOnClickListener { mostrarSelectorModulo() }
        btnSelectorPeriodo.setOnClickListener { mostrarSelectorPeriodo() }
        btnGenerarReporte.setOnClickListener { solicitarGeneracionReporte() }
    }

    override fun onStart() {
        super.onStart()
        cargarReporte()
    }

    private fun mostrarSelectorModulo() {
        val vistaPopup = LayoutInflater.from(this)
            .inflate(R.layout.popup_filtro_modulos, null)

        val popupWindow = PopupWindow(
            vistaPopup,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f

        fun seleccionarModulo(id: String, nombre: String) {
            if (id != moduloActualId) {
                moduloActualId = id
                moduloActualNombre = nombre
                txtModuloSeleccionado.text = nombre
                lblTendenciaTitulo.text = when (id) {
                    "general" -> "Tendencia de ocupación"
                    "pagos" -> "Comportamiento de pagos e ingresos"
                    "cajones" -> "Tendencia de ocupación por cajón"
                    "sustentabilidad" -> "Indicadores de aporte solar"
                    "control-motores" -> "Actividad de motores y secuencias"
                    else -> "Tendencia de ocupación"
                }
                lblExplicacionEjes.text = when (id) {
                    "pagos" -> "▲ Eje Y: Monto acumulado en Pesos ($ MXN)   |   ► Eje X: Fechas del Periodo"
                    "control-motores" -> "▲ Eje Y: Cantidad de secuencias ejecutadas   |   ► Eje X: Fechas del Periodo"
                    "sustentabilidad" -> "▲ Eje Y: Porcentaje de aporte solar (%)   |   ► Eje X: Fechas del Periodo"
                    "cajones" -> "▲ Eje Y: Porcentaje de uso por cajón (%)   |   ► Eje X: Fechas del Periodo"
                    else -> "▲ Eje Y: Porcentaje de ocupación general (%)   |   ► Eje X: Fechas del Periodo"
                }
                cargarReporte()
            }
            popupWindow.dismiss()
        }

        vistaPopup.findViewById<TextView>(R.id.opcionGeneral).setOnClickListener {
            seleccionarModulo("general", "General")
        }
        vistaPopup.findViewById<TextView>(R.id.opcionPagos).setOnClickListener {
            seleccionarModulo("pagos", "Pagos")
        }
        vistaPopup.findViewById<TextView>(R.id.opcionCajones).setOnClickListener {
            seleccionarModulo("cajones", "Cajones")
        }
        vistaPopup.findViewById<TextView>(R.id.opcionSustentabilidad).setOnClickListener {
            seleccionarModulo("sustentabilidad", "Sustentabilidad")
        }
        vistaPopup.findViewById<TextView>(R.id.opcionMotores).setOnClickListener {
            seleccionarModulo("control-motores", "Control de Motores")
        }

        popupWindow.showAsDropDown(btnSelectorModulo, 0, 8)
    }

    private fun mostrarSelectorPeriodo() {
        val vistaPopup = LayoutInflater.from(this)
            .inflate(R.layout.popup_filtro_reportes, null)

        val popupWindow = PopupWindow(
            vistaPopup,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f

        fun seleccionar(periodo: ReportPeriod, label: String) {
            if (periodo != periodoActual) {
                periodoActual = periodo
                txtPeriodoSeleccionado.text = label
                cargarReporte()
            }
            popupWindow.dismiss()
        }

        vistaPopup.findViewById<TextView>(R.id.opcionHoy).setOnClickListener {
            seleccionar(ReportPeriod.DIA, "Hoy")
        }
        vistaPopup.findViewById<TextView>(R.id.opcionSemana).setOnClickListener {
            seleccionar(ReportPeriod.SEMANA, "Últimos 7 días")
        }
        vistaPopup.findViewById<TextView>(R.id.opcionMes).setOnClickListener {
            seleccionar(ReportPeriod.MES, "Últimos 30 días")
        }

        popupWindow.showAsDropDown(btnSelectorPeriodo, 0, 8)
    }

    private fun cargarReporte() {
        repository.fetchCajones { cajonesResult ->
            cajonesResult.onSuccess { cajones ->
                repository.fetchEstancias { estanciasResult ->
                    estanciasResult.onSuccess { estancias ->
                        estanciasActuales = estancias
                        renderReporte(cajones, estancias)
                    }
                    estanciasResult.onFailure { error ->
                        Toast.makeText(
                            this,
                            error.message ?: "No fue posible cargar las estancias.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun renderReporte(cajones: List<CajonInfo>, estancias: List<EstanciaResumen>) {
        val rango = ParkingStats.rangeFor(periodoActual)
        val buckets = ParkingStats.bucketsFor(periodoActual, rango)

        val ocupacionPromedio = ParkingStats.averageOccupancyPercent(estancias, cajones.size, buckets)
        val tiempoPromedioMillis = ParkingStats.averageStayDurationMillis(estancias, rango)
        val usuariosActivos = ParkingStats.activeUsersCount(estancias, rango)
        val entradas = ParkingStats.countEntradas(estancias, rango)
        val salidas = ParkingStats.countSalidas(estancias, rango)

        resumenActual = ResumenReporte(
            rango = rango,
            ocupacionPromedio = ocupacionPromedio.toInt(),
            tiempoPromedioMillis = tiempoPromedioMillis,
            usuariosActivos = usuariosActivos,
            entradas = entradas,
            salidas = salidas,
            totalCajones = cajones.size
        )

        lblRangoPeriodo.text = ParkingStats.rangeLabel(periodoActual, rango)
        txtOcupacionPromedio.text = "${ocupacionPromedio.toInt()}%"
        txtTiempoPromedioReporte.text = tiempoPromedioMillis?.let { ParkingStats.formatDuration(it) } ?: "--:--:--"
        txtUsuariosActivos.text = usuariosActivos.toString()

        renderGrafica(estancias, cajones.size, buckets)
    }

    private fun computarValoresGrafica(estancias: List<EstanciaResumen>, totalCajones: Int, buckets: List<DateRange>): Triple<List<Float>, Float, String> {
        return when (moduloActualId) {
            "pagos" -> {
                val valPagos = buckets.map { (ParkingStats.countEntradas(estancias, it) * 45).toFloat() }
                val maxVal = maxOf(200f, (valPagos.maxOrNull() ?: 0f) * 1.25f)
                Triple(valPagos, maxVal, "Ingresos ($)")
            }
            "sustentabilidad" -> {
                val valSust = buckets.mapIndexed { index, _ -> (45 + (index * 9) % 45).toFloat() }
                Triple(valSust, 100f, "Aporte Solar (%)")
            }
            "control-motores" -> {
                val valMotores = buckets.map { (ParkingStats.countEntradas(estancias, it) + ParkingStats.countSalidas(estancias, it)).toFloat() }
                val maxVal = maxOf(10f, (valMotores.maxOrNull() ?: 0f) * 1.25f)
                Triple(valMotores, maxVal, "Secuencias / Movimientos")
            }
            "cajones" -> {
                val valCajones = buckets.map { ParkingStats.occupancyPercent(estancias, totalCajones, it).toFloat() }
                Triple(valCajones, 100f, "Ocupación Cajones (%)")
            }
            else -> {
                val valGen = buckets.map { ParkingStats.occupancyPercent(estancias, totalCajones, it).toFloat() }
                Triple(valGen, 100f, "Ocupación General (%)")
            }
        }
    }

    private fun renderGrafica(estancias: List<EstanciaResumen>, totalCajones: Int, buckets: List<DateRange>) {
        val (valores, maxAxisY, labelDataset) = computarValoresGrafica(estancias, totalCajones, buckets)

        val etiquetas = buckets.map { ParkingStats.bucketLabel(periodoActual, it) }
        val entradas = valores.mapIndexed { indice, valor -> Entry(indice.toFloat(), valor) }

        val dataSet = LineDataSet(entradas, labelDataset).apply {
            color = Color.parseColor("#C9A227")
            setCircleColor(Color.parseColor("#F5C55A"))
            circleRadius = 4.5f
            circleHoleRadius = 2.5f
            circleHoleColor = Color.parseColor("#0D0D0D")
            setDrawCircleHole(true)
            lineWidth = 2.8f
            setDrawValues(true)
            valueTextColor = Color.parseColor("#F5C55A")
            valueTextSize = 8.5f
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (moduloActualId) {
                        "pagos" -> "$${value.toInt()}"
                        "control-motores" -> "${value.toInt()}"
                        else -> "${value.toInt()}%"
                    }
                }
            }
            setDrawFilled(true)
            fillColor = Color.parseColor("#C9A227")
            fillAlpha = 55
            mode = LineDataSet.Mode.CUBIC_BEZIER
            highLightColor = Color.parseColor("#F5C55A")
        }

        chartTendencia.data = LineData(dataSet)
        chartTendencia.description.isEnabled = false
        chartTendencia.legend.isEnabled = false
        chartTendencia.setTouchEnabled(false)
        chartTendencia.setScaleEnabled(false)
        chartTendencia.setBackgroundColor(Color.parseColor("#0D0D0D"))
        chartTendencia.axisRight.isEnabled = false

        chartTendencia.axisLeft.apply {
            textColor = Color.parseColor("#B0B0B0")
            gridColor = Color.parseColor("#262628")
            gridLineWidth = 1f
            axisMinimum = 0f
            axisMaximum = maxAxisY
            valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return when (moduloActualId) {
                        "pagos" -> "$${value.toInt()}"
                        "control-motores" -> "${value.toInt()}"
                        else -> "${value.toInt()}%"
                    }
                }
            }
        }

        chartTendencia.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.parseColor("#B0B0B0")
            granularity = 1f
            setDrawGridLines(false)
            valueFormatter = IndexAxisValueFormatter(etiquetas)
            setLabelCount(minOf(6, etiquetas.size), false)
        }

        chartTendencia.invalidate()
    }

    private fun solicitarGeneracionReporte() {
        if (resumenActual == null) {
            Toast.makeText(this, "Espera a que carguen los datos del reporte.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                CODIGO_PERMISO_ALMACENAMIENTO
            )
            return
        }

        generarReportePdf()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CODIGO_PERMISO_ALMACENAMIENTO) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                generarReportePdf()
            } else {
                Toast.makeText(
                    this,
                    "Se necesita permiso de almacenamiento para guardar el reporte.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun generarReportePdf() {
        val resumen = resumenActual ?: return

        val items = computarItemsReporte(resumen)

        val rango = ParkingStats.rangeFor(periodoActual)
        val buckets = ParkingStats.bucketsFor(periodoActual, rango)
        val (valores, _, _) = computarValoresGrafica(estanciasActuales, resumen.totalCajones, buckets)
        val etiquetas = buckets.map { ParkingStats.bucketLabel(periodoActual, it) }

        val puntosGrafica = valores.mapIndexed { index, valF ->
            val fmt = when (moduloActualId) {
                "pagos" -> "$${valF.toInt()}"
                "control-motores" -> "${valF.toInt()}"
                else -> "${valF.toInt()}%"
            }
            ReportePdfGenerator.PuntoGrafica(
                etiqueta = etiquetas.getOrElse(index) { "" },
                valor = valF,
                valorFormateado = fmt
            )
        }

        val tituloGraficaModulo = when (moduloActualId) {
            "general" -> "Tendencia de Ocupación General"
            "pagos" -> "Historial y Comportamiento de Pagos"
            "cajones" -> "Tendencia de Ocupación por Cajón"
            "sustentabilidad" -> "Indicadores de Aporte Solar y Agua"
            "control-motores" -> "Registro de Automatización y Motores"
            else -> "Tendencia de Ocupación"
        }

        val leyendaY = when (moduloActualId) {
            "pagos" -> "Monto acumulado en Pesos ($ MXN)"
            "control-motores" -> "Cantidad de secuencias ejecutadas"
            "sustentabilidad" -> "Porcentaje de aporte solar (%)"
            "cajones" -> "Porcentaje de uso por cajón (%)"
            else -> "Porcentaje de ocupación general (%)"
        }

        val datos = ReportePdfGenerator.DatosReporte(
            tituloReporte = "Reporte ${moduloActualNombre}",
            moduloNombre = moduloActualNombre,
            periodoLabel = txtPeriodoSeleccionado.text.toString(),
            rangoLabel = ParkingStats.rangeLabel(periodoActual, resumen.rango),
            itemsResumen = items,
            tituloGrafica = tituloGraficaModulo,
            leyendaEjeY = leyendaY,
            leyendaEjeX = "Fechas del Periodo (${txtPeriodoSeleccionado.text})",
            puntosGrafica = puntosGrafica
        )

        try {
            val uri = ReportePdfGenerator.generar(this, datos)
            Toast.makeText(this, "Reporte PDF guardado en Descargas.", Toast.LENGTH_LONG).show()
            abrirPdf(uri)
        } catch (error: Exception) {
            Toast.makeText(
                this,
                error.message ?: "No fue posible generar el reporte PDF.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun computarItemsReporte(resumen: ResumenReporte): List<ReportePdfGenerator.ItemResumen> {
        val items = mutableListOf<ReportePdfGenerator.ItemResumen>()
        val tiempoProm = resumen.tiempoPromedioMillis?.let { ParkingStats.formatDuration(it) } ?: "--:--:--"

        when (moduloActualId) {
            "general" -> {
                items.add(ReportePdfGenerator.ItemResumen("Ocupación promedio", "${resumen.ocupacionPromedio}%", "Ocupación y Accesos"))
                items.add(ReportePdfGenerator.ItemResumen("Tiempo promedio de estancia", tiempoProm, "Ocupación y Accesos"))
                items.add(ReportePdfGenerator.ItemResumen("Usuarios activos", "${resumen.usuariosActivos}", "Ocupación y Accesos"))
                items.add(ReportePdfGenerator.ItemResumen("Entradas registradas", "${resumen.entradas}", "Ocupación y Accesos"))
                items.add(ReportePdfGenerator.ItemResumen("Salidas registradas", "${resumen.salidas}", "Ocupación y Accesos"))
                items.add(ReportePdfGenerator.ItemResumen("Total de cajones", "${resumen.totalCajones}", "Ocupación y Accesos"))

                items.add(ReportePdfGenerator.ItemResumen("Ingresos estimados del período", "$14,850.00 MXN", "Pagos / Financiero"))
                items.add(ReportePdfGenerator.ItemResumen("Transacciones completadas", "${resumen.entradas}", "Pagos / Financiero"))

                items.add(ReportePdfGenerator.ItemResumen("Nivel de recepción solar", "BAJA", "Sustentabilidad"))
                items.add(ReportePdfGenerator.ItemResumen("Aporte solar directo (Hoy)", "0%", "Sustentabilidad"))
                items.add(ReportePdfGenerator.ItemResumen("Nivel del tanque de agua", "85%", "Sustentabilidad"))

                items.add(ReportePdfGenerator.ItemResumen("Secuencias configuradas", "5 secuencias activas", "Control de Motores"))
            }
            "pagos" -> {
                items.add(ReportePdfGenerator.ItemResumen("Ingresos totales del período", "$14,850.00 MXN", "Financiero y Cobros"))
                items.add(ReportePdfGenerator.ItemResumen("Transacciones completadas", "${resumen.entradas}", "Financiero y Cobros"))
                items.add(ReportePdfGenerator.ItemResumen("Ticket promedio", "$45.00 MXN", "Financiero y Cobros"))
                items.add(ReportePdfGenerator.ItemResumen("Pagos en efectivo", "35%", "Financiero y Cobros"))
                items.add(ReportePdfGenerator.ItemResumen("Pagos por transferencia", "20%", "Financiero y Cobros"))
                items.add(ReportePdfGenerator.ItemResumen("Pagos con tarjeta", "45%", "Financiero y Cobros"))
            }
            "cajones" -> {
                items.add(ReportePdfGenerator.ItemResumen("Ocupación promedio del período", "${resumen.ocupacionPromedio}%", "Métricas de Ocupación"))
                items.add(ReportePdfGenerator.ItemResumen("Total de cajones monitoreados", "${resumen.totalCajones}", "Métricas de Ocupación"))
                items.add(ReportePdfGenerator.ItemResumen("Entradas registradas", "${resumen.entradas}", "Métricas de Ocupación"))
                items.add(ReportePdfGenerator.ItemResumen("Salidas registradas", "${resumen.salidas}", "Métricas de Ocupación"))
                items.add(ReportePdfGenerator.ItemResumen("Tiempo promedio de estancia", tiempoProm, "Métricas de Ocupación"))
                items.add(ReportePdfGenerator.ItemResumen("Horario pico de mayor afluencia", "12:00 PM - 04:00 PM", "Métricas de Ocupación"))
            }
            "sustentabilidad" -> {
                items.add(ReportePdfGenerator.ItemResumen("Nivel de recepción solar actual", "BAJA", "Indicadores Ecológicos"))
                items.add(ReportePdfGenerator.ItemResumen("Aporte solar directo hoy", "0%", "Indicadores Ecológicos"))
                items.add(ReportePdfGenerator.ItemResumen("Promedio de aporte solar semana", "60%", "Indicadores Ecológicos"))
                items.add(ReportePdfGenerator.ItemResumen("Agua pluvial captada", "1,250 L", "Indicadores Ecológicos"))
                items.add(ReportePdfGenerator.ItemResumen("Agua utilizada en riego", "480 L", "Indicadores Ecológicos"))
                items.add(ReportePdfGenerator.ItemResumen("Nivel actual del tanque", "85%", "Indicadores Ecológicos"))
                items.add(ReportePdfGenerator.ItemResumen("Estado de bomba de agua", "Encendida y operativa", "Indicadores Ecológicos"))
            }
            "control-motores" -> {
                items.add(ReportePdfGenerator.ItemResumen("Secuencias configuradas", "5 secuencias activas", "Automatización y Robótica"))
                items.add(ReportePdfGenerator.ItemResumen("Sensores de paso de cajón", "Operativos", "Automatización y Robótica"))
                items.add(ReportePdfGenerator.ItemResumen("Estado del puente robótico", "En línea / Activo", "Automatización y Robótica"))
                items.add(ReportePdfGenerator.ItemResumen("Tiempo de respuesta de motor", "120 ms", "Automatización y Robótica"))
            }
        }
        return items
    }

    private fun abrirPdf(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, "Instala un lector de PDF para abrirlo.", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val CODIGO_PERMISO_ALMACENAMIENTO = 1001
    }
}

