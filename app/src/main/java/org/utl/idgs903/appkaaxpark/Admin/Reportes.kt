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
    private lateinit var btnSelectorPeriodo: LinearLayout
    private lateinit var btnGenerarReporte: LinearLayout
    private lateinit var txtPeriodoSeleccionado: TextView
    private lateinit var lblRangoPeriodo: TextView
    private lateinit var txtOcupacionPromedio: TextView
    private lateinit var txtTiempoPromedioReporte: TextView
    private lateinit var txtUsuariosActivos: TextView
    private lateinit var chartTendencia: LineChart

    private var periodoActual = ReportPeriod.SEMANA
    private var resumenActual: ResumenReporte? = null

    override fun getLayoutId(): Int = R.layout.activity_reportes

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageTitle("Reportes")

        repository = FirebaseRepository()
        btnSelectorPeriodo = findViewById(R.id.btnSelectorPeriodo)
        btnGenerarReporte = findViewById(R.id.btnGenerarReporte)
        txtPeriodoSeleccionado = findViewById(R.id.txtPeriodoSeleccionado)
        lblRangoPeriodo = findViewById(R.id.lblRangoPeriodo)
        txtOcupacionPromedio = findViewById(R.id.txtOcupacionPromedio)
        txtTiempoPromedioReporte = findViewById(R.id.txtTiempoPromedioReporte)
        txtUsuariosActivos = findViewById(R.id.txtUsuariosActivos)
        chartTendencia = findViewById(R.id.chartTendencia)

        btnSelectorPeriodo.setOnClickListener { mostrarSelectorPeriodo() }
        btnGenerarReporte.setOnClickListener { solicitarGeneracionReporte() }
    }

    override fun onStart() {
        super.onStart()
        cargarReporte()
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

    private fun renderGrafica(estancias: List<EstanciaResumen>, totalCajones: Int, buckets: List<DateRange>) {
        val valores = buckets.map { ParkingStats.occupancyPercent(estancias, totalCajones, it).toFloat() }
        val etiquetas = buckets.map { ParkingStats.bucketLabel(periodoActual, it) }
        val entradas = valores.mapIndexed { indice, valor -> Entry(indice.toFloat(), valor) }

        val dataSet = LineDataSet(entradas, "Ocupación").apply {
            color = Color.parseColor("#D4A017")
            setCircleColor(Color.parseColor("#D4A017"))
            circleRadius = 3f
            lineWidth = 2f
            setDrawValues(false)
            setDrawFilled(true)
            fillColor = Color.parseColor("#D4A017")
            fillAlpha = 40
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawCircleHole(false)
        }

        chartTendencia.data = LineData(dataSet)
        chartTendencia.description.isEnabled = false
        chartTendencia.legend.isEnabled = false
        chartTendencia.setTouchEnabled(false)
        chartTendencia.setScaleEnabled(false)
        chartTendencia.setBackgroundColor(Color.parseColor("#0D0D0D"))
        chartTendencia.axisRight.isEnabled = false

        chartTendencia.axisLeft.apply {
            textColor = Color.parseColor("#A0A0A0")
            gridColor = Color.parseColor("#222222")
            axisMinimum = 0f
            axisMaximum = 100f
        }

        chartTendencia.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = Color.parseColor("#A0A0A0")
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

        val bitmapGrafica = try {
            if (chartTendencia.data != null) chartTendencia.chartBitmap else null
        } catch (error: Exception) {
            null
        }

        val datos = ReportePdfGenerator.DatosReporte(
            periodoLabel = txtPeriodoSeleccionado.text.toString(),
            rangoLabel = ParkingStats.rangeLabel(periodoActual, resumen.rango),
            ocupacionPromedio = resumen.ocupacionPromedio,
            tiempoPromedio = resumen.tiempoPromedioMillis?.let { ParkingStats.formatDuration(it) } ?: "--:--:--",
            usuariosActivos = resumen.usuariosActivos,
            entradas = resumen.entradas,
            salidas = resumen.salidas,
            totalCajones = resumen.totalCajones,
            graficaBitmap = bitmapGrafica
        )

        try {
            val uri = ReportePdfGenerator.generar(this, datos)
            Toast.makeText(this, "Reporte guardado en Descargas.", Toast.LENGTH_LONG).show()
            abrirPdf(uri)
        } catch (error: Exception) {
            Toast.makeText(
                this,
                error.message ?: "No fue posible generar el reporte.",
                Toast.LENGTH_LONG
            ).show()
        }
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
