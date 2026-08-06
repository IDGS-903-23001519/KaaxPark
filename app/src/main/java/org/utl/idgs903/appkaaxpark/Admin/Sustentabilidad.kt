package org.utl.idgs903.appkaaxpark.Admin

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SustentabilidadInfo
import kotlin.math.roundToInt

data class RegistroCaptacionSolar(
    val fecha: String,
    val aporteSolar: Int,
    val nivel: String,
    val condicion: String
)

class Sustentabilidad : BaseAdminActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var viewNivelTanque: android.view.View
    private lateinit var numNivelTanque: TextView
    private lateinit var numAguaCaptada: TextView
    private lateinit var numAguaRiego: TextView
    private lateinit var txtNivelSolarDestacado: TextView
    private lateinit var numPorcentajeSolar: TextView
    private lateinit var txtLeyendaNivelSolar: TextView
    private lateinit var chartAporteSolar: BarChart
    private lateinit var viewIndicadorBomba: android.view.View
    private lateinit var txtEstadoBomba: TextView
    private lateinit var layoutAlertasTanque: LinearLayout
    private lateinit var layoutTablaHistoricoSolar: LinearLayout

    override fun getLayoutId(): Int = R.layout.activity_sustentabilidad

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageTitle("Sustentabilidad")

        repository = FirebaseRepository()
        viewNivelTanque = findViewById(R.id.viewNivelTanque)
        numNivelTanque = findViewById(R.id.numNivelTanque)
        numAguaCaptada = findViewById(R.id.numAguaCaptada)
        numAguaRiego = findViewById(R.id.numAguaRiego)
        txtNivelSolarDestacado = findViewById(R.id.txtNivelSolarDestacado)
        numPorcentajeSolar = findViewById(R.id.numPorcentajeSolar)
        txtLeyendaNivelSolar = findViewById(R.id.txtLeyendaNivelSolar)
        chartAporteSolar = findViewById(R.id.chartAporteSolar)
        viewIndicadorBomba = findViewById(R.id.viewIndicadorBomba)
        txtEstadoBomba = findViewById(R.id.txtEstadoBomba)
        layoutAlertasTanque = findViewById(R.id.layoutAlertasTanque)
        layoutTablaHistoricoSolar = findViewById(R.id.layoutTablaHistoricoSolar)
    }

    override fun onStart() {
        super.onStart()
        cargarSustentabilidad()
    }

    private fun cargarSustentabilidad() {
        repository.fetchSustentabilidad { result ->
            result.onSuccess { info ->
                if (info == null) {
                    Toast.makeText(this, "No hay datos de sustentabilidad registrados.", Toast.LENGTH_LONG).show()
                } else {
                    renderSustentabilidad(info)
                }
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No fue posible cargar los datos de sustentabilidad.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderSustentabilidad(info: SustentabilidadInfo) {
        val nivelTanque = info.nivelTanquePorcentaje.roundToInt().coerceIn(0, 100)
        numNivelTanque.text = "$nivelTanque%"
        renderNivelTanque(nivelTanque)

        numAguaCaptada.text = "${formatearNumero(info.aguaCaptadaLitros)} L"
        numAguaRiego.text = "${formatearNumero(info.aguaUsadaRiegoLitros)} L"

        // Configurar el registro del día de hoy con 0% de aporte solar y nivel BAJA
        val porcentajeHoy = 0.0
        val nivelSolarHoy = calcularNivelSolar(porcentajeHoy)
        val colorNivelHoy = obtenerColorNivel(nivelSolarHoy)

        txtNivelSolarDestacado.text = nivelSolarHoy
        txtNivelSolarDestacado.setTextColor(colorNivelHoy)

        numPorcentajeSolar.text = "${porcentajeHoy.roundToInt()}%"
        numPorcentajeSolar.setTextColor(colorNivelHoy)

        txtLeyendaNivelSolar.text = "Se está recibiendo energía: $nivelSolarHoy"

        renderEstadoBomba(info.bombaAguaEncendida)
        renderAlertas(info.alertas)
        renderGraficaAporteSolar()
        renderTablaHistoricoSolar()
    }

    private fun calcularNivelSolar(porcentajeSolar: Double): String {
        return when {
            porcentajeSolar >= 70.0 -> "ALTA"
            porcentajeSolar >= 40.0 -> "MEDIA"
            else -> "BAJA"
        }
    }

    private fun obtenerColorNivel(nivel: String): Int {
        return when (nivel) {
            "ALTA" -> Color.parseColor("#2ECC71")
            "MEDIA" -> Color.parseColor("#F39C12")
            else -> Color.parseColor("#E74C3C")
        }
    }

    private fun renderNivelTanque(nivelPorcentaje: Int) {
        viewNivelTanque.post {
            val contenedor = viewNivelTanque.parent as ViewGroup
            val alturaDisponible = contenedor.height - contenedor.paddingTop - contenedor.paddingBottom
            val params = viewNivelTanque.layoutParams
            params.height = (alturaDisponible * nivelPorcentaje / 100f).roundToInt()
            viewNivelTanque.layoutParams = params
        }
    }

    private fun renderEstadoBomba(encendida: Boolean) {
        val colorHex = if (encendida) "#2ECC71" else "#888888"
        txtEstadoBomba.text = if (encendida) "Bomba encendida" else "Bomba apagada"
        txtEstadoBomba.setTextColor(Color.parseColor(colorHex))

        val drawable = viewIndicadorBomba.background.mutate() as? android.graphics.drawable.GradientDrawable
        drawable?.setColor(Color.parseColor(colorHex))
    }

    private fun renderAlertas(alertas: Map<String, Any?>) {
        layoutAlertasTanque.removeAllViews()

        // Alerta obligatoria de recepción solar baja para el día de hoy
        val alertaSolarHoy = "Recepción solar baja: Sin energía solar directa recibida hoy (0%)."
        layoutAlertasTanque.addView(crearChip(alertaSolarHoy, esPositivo = false))

        val alertasActivas = alertas.filter { (_, valor) ->
            when (valor) {
                is Boolean -> valor
                is Number -> valor.toDouble() > 0
                is String -> valor.isNotBlank()
                else -> false
            }
        }

        alertasActivas.forEach { (clave, valor) ->
            val texto = if (valor is Boolean) humanizarClave(clave) else "${humanizarClave(clave)}: $valor"
            layoutAlertasTanque.addView(crearChip(texto, esPositivo = false))
        }
    }

    private fun renderGraficaAporteSolar() {
        val dias = listOf("Hace 4d", "Hace 3d", "Hace 2d", "Ayer", "Hoy")
        val valores = listOf(42f, 52f, 70f, 78f, 0f)

        val entries = valores.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value)
        }

        val dataSet = BarDataSet(entries, "Aporte Solar (%)").apply {
            colors = listOf(
                Color.parseColor("#F39C12"), // 42% MEDIA
                Color.parseColor("#F39C12"), // 52% MEDIA
                Color.parseColor("#2ECC71"), // 70% ALTA
                Color.parseColor("#2ECC71"), // 78% ALTA
                Color.parseColor("#E74C3C")  // 0% BAJA
            )
            valueTextColor = Color.parseColor("#FFFFFF")
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return "${value.toInt()}%"
                }
            }
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.45f
        }

        chartAporteSolar.apply {
            data = barData
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(false)

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                textColor = Color.parseColor("#A0A0A0")
                textSize = 11f
                valueFormatter = IndexAxisValueFormatter(dias)
                granularity = 1f
            }

            axisLeft.apply {
                textColor = Color.parseColor("#A0A0A0")
                textSize = 10f
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#22FFFFFF")
            }

            axisRight.isEnabled = false
            animateY(800)
            invalidate()
        }
    }

    private fun renderTablaHistoricoSolar() {
        layoutTablaHistoricoSolar.removeAllViews()

        val registros = listOf(
            RegistroCaptacionSolar("Hoy", 0, "BAJA", "Zona Sombra"),
            RegistroCaptacionSolar("Ayer", 78, "ALTA", "Sol Directo"),
            RegistroCaptacionSolar("Hace 2 días", 70, "ALTA", "Sol Directo"),
            RegistroCaptacionSolar("Hace 3 días", 52, "MEDIA", "Sol Directo"),
            RegistroCaptacionSolar("Hace 4 días", 42, "MEDIA", "Sol Directo")
        )

        registros.forEachIndexed { index, reg ->
            val fila = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 12, 0, 12)
            }

            val tvFecha = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
                text = reg.fecha
                setTextColor(Color.parseColor("#FFFFFF"))
                textSize = 12f
            }

            val tvAporte = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                text = "${reg.aporteSolar}%"
                setTextColor(Color.parseColor("#FFFFFF"))
                textSize = 12f
                gravity = Gravity.CENTER
            }

            val colorNivel = obtenerColorNivel(reg.nivel)
            val tvNivel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
                text = reg.nivel
                setTextColor(colorNivel)
                textSize = 12f
                gravity = Gravity.CENTER
            }

            val tvCondicion = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f)
                text = reg.condicion
                setTextColor(Color.parseColor(if (reg.condicion == "Sol Directo") "#2ECC71" else "#A0A0A0"))
                textSize = 12f
                gravity = Gravity.END
            }

            fila.addView(tvFecha)
            fila.addView(tvAporte)
            fila.addView(tvNivel)
            fila.addView(tvCondicion)

            layoutTablaHistoricoSolar.addView(fila)

            if (index < registros.size - 1) {
                val separador = android.view.View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    )
                    setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                }
                layoutTablaHistoricoSolar.addView(separador)
            }
        }
    }

    private fun crearChip(texto: String, esPositivo: Boolean): TextView {
        val chip = TextView(this)
        chip.text = texto
        chip.textSize = 12f
        chip.setTextColor(Color.parseColor(if (esPositivo) "#2ECC71" else "#D4A017"))
        chip.setBackgroundResource(if (esPositivo) R.drawable.bg_chip_ok else R.drawable.bg_chip_alerta)
        chip.setPadding(28, 14, 28, 14)

        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        params.bottomMargin = 16
        chip.layoutParams = params

        return chip
    }

    private fun humanizarClave(clave: String): String {
        val conEspacios = clave.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        return conEspacios.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun formatearNumero(valor: Double): String {
        return if (valor == valor.toLong().toDouble()) {
            valor.toLong().toString()
        } else {
            String.format("%.1f", valor)
        }
    }
}


