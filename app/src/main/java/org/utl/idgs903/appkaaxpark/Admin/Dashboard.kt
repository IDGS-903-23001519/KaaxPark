package org.utl.idgs903.appkaaxpark.Admin

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity  // solo para las dependencias transitivas de BaseAdminActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.ActividadDashboardItem
import org.utl.idgs903.appkaaxpark.data.CajonInfo
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Dashboard de administrador con datos en tiempo real de Firebase.
 * Funcionalidad equivalente al DashboardComponent de la página web:
 *   - Stat cards: Entradas Hoy, Salidas Hoy, Ocupación, Tiempo Promedio
 *   - Gráfica de ocupación por hora (MPAndroidChart)
 *   - Lista de actividad reciente
 *
 * Si tu clase ya extiende BaseActivity, cambia AppCompatActivity por BaseActivity,
 * agrega `override fun getLayoutId() = R.layout.activity_dashboard` y elimina
 * la línea `setContentView(...)` del onCreate.
 */
class Dashboard : BaseAdminActivity() {

    override fun getLayoutId(): Int = R.layout.activity_dashboard

    private lateinit var repository: FirebaseRepository

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var numEntradas: TextView
    private lateinit var numSalidas: TextView
    private lateinit var numOcupacion: TextView
    private lateinit var numTiempo: TextView
    private lateinit var lineChart: LineChart
    private lateinit var contenedorActividad: LinearLayout

    // ── Estado ────────────────────────────────────────────────────────────────
    private var cajones: List<CajonInfo> = emptyList()
    private var actividadHoy: List<ActividadDashboardItem> = emptyList()
    private var actividadReciente: List<ActividadDashboardItem> = emptyList()

    private var cancelarCajones: (() -> Unit)? = null
    private var cancelarActividadHoy: (() -> Unit)? = null
    private var cancelarActividadReciente: (() -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContentView NO va aquí — BaseAdminActivity ya infla
        // activity_layout_base_admin.xml y mete getLayoutId() en containerDashboard.

        repository = FirebaseRepository()

        numEntradas         = findViewById(R.id.numEntradas)
        numSalidas          = findViewById(R.id.numSalidas)
        numOcupacion        = findViewById(R.id.numOcupacion)
        numTiempo           = findViewById(R.id.numTiempo)
        lineChart           = findViewById(R.id.lineChart)
        contenedorActividad = findViewById(R.id.contenedorActividad)
        findViewById<LinearLayout>(R.id.btnAsistenteIA)?.setOnClickListener {
            viajarA(AsistenteIA::class.java)
        }

        setupLineChart()
        iniciarListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelarCajones?.invoke()
        cancelarActividadHoy?.invoke()
        cancelarActividadReciente?.invoke()
    }

    // ── Listeners en tiempo real ───────────────────────────────────────────────
    private fun iniciarListeners() {
        // 1. Cajones: para ocupación y total
        cancelarCajones = repository.listenCajones { lista ->
            cajones = lista
            actualizarEstadisticas()
            actualizarGrafica()
        }

        // 2. Actividad de hoy: para conteos y gráfica por hora
        // Forzamos Locale.US para asegurar formato yyyy-MM-dd sin importar el idioma del cel.
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        cancelarActividadHoy = repository.listenActividadHoy(hoy) { lista ->
            actividadHoy = lista
            actualizarEstadisticas()
            actualizarGrafica()
        }

        // 3. Actividad reciente (últimas 5): para la lista
        cancelarActividadReciente = repository.listenActividadReciente { lista ->
            actividadReciente = lista
            renderizarActividad()
        }
    }

    // ── Stat cards ────────────────────────────────────────────────────────────
    private fun actualizarEstadisticas() {
        val entradas = actividadHoy.count { it.tipo.equals("entrada", ignoreCase = true) }
        val salidas  = actividadHoy.count { it.tipo.equals("salida", ignoreCase = true) }
        val ocupados = cajones.count { it.estado.equals("Ocupado", ignoreCase = true) }
        val total    = cajones.size
        val pct      = if (total > 0) (ocupados * 100f / total).roundToInt() else 0

        numEntradas.text  = entradas.toString()
        numSalidas.text   = salidas.toString()
        numOcupacion.text = "$pct%"
        numTiempo.text    = calcularTiempoPromedio()
    }

    /** Promedio de estancias COMPLETADAS hoy — igual que el web. */
    private fun calcularTiempoPromedio(): String {
        val duraciones = actividadHoy
            .filter { it.tipo.equals("salida", ignoreCase = true) && it.duracionMin != null }
            .mapNotNull { it.duracionMin }
        if (duraciones.isEmpty()) return "—"
        val avg = duraciones.average().roundToInt()
        return if (avg < 60) "${avg} min"
        else { val h = avg / 60; val m = avg % 60; if (m > 0) "${h}h ${m}m" else "${h}h" }
    }

    // ── Gráfica de ocupación por hora ─────────────────────────────────────────
    private fun setupLineChart() {
        lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(false)
            setScaleEnabled(false)
            legend.isEnabled = false
            axisRight.isEnabled = false
            setNoDataText("Cargando datos…")
            setNoDataTextColor(Color.GRAY)
            setBackgroundColor(Color.TRANSPARENT)
        }
        lineChart.xAxis.apply {
            position  = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            textColor = Color.parseColor("#B0B5B8")
            textSize  = 9f
        }
        lineChart.axisLeft.apply {
            axisMinimum = 0f
            axisMaximum = 100f
            textColor   = Color.parseColor("#B0B5B8")
            textSize    = 9f
            gridColor   = Color.parseColor("#2A2A2A")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "${value.toInt()}%"
            }
        }
    }

    /**
     * Reconstruye la ocupación neta acumulada (entradas - salidas) por hora,
     * mismo algoritmo que el getter ocupacionPorHora del DashboardComponent web.
     */
    private fun actualizarGrafica() {
        val horaActual = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val total    = cajones.size
        val labels   = mutableListOf<String>()
        val entries  = mutableListOf<Entry>()
        var acumulado = 0

        for (h in 0..horaActual) {
            val eventosDeLaHora = actividadHoy.filter {
                it.hora.split(":").firstOrNull()?.toIntOrNull() == h
            }
            for (ev in eventosDeLaHora) {
                if (ev.tipo.equals("entrada", ignoreCase = true)) {
                    acumulado++
                } else if (ev.tipo.equals("salida", ignoreCase = true)) {
                    acumulado = max(0, acumulado - 1)
                }
            }
            labels.add(String.format("%02d:00", h))
            entries.add(
                Entry(h.toFloat(),
                    if (total > 0) (acumulado.toFloat() / total * 100).coerceIn(0f, 100f) else 0f)
            )
        }

        if (entries.isEmpty()) return

        val dataSet = LineDataSet(entries, "Ocupación").apply {
            color              = Color.parseColor("#C9A227")
            setCircleColor(Color.parseColor("#C9A227"))
            circleRadius       = 3f
            lineWidth          = 2f
            setDrawValues(false)
            mode               = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor          = Color.parseColor("#C9A227")
            fillAlpha          = 30
        }

        lineChart.data = LineData(dataSet)
        lineChart.xAxis.apply {
            labelCount = minOf(labels.size, 6)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val idx = value.toInt()
                    return if (idx in labels.indices) labels[idx] else ""
                }
            }
        }
        lineChart.invalidate()
    }

    // ── Lista de actividad reciente ───────────────────────────────────────────
    /** Renderiza la lista programáticamente (máx. 5 items, sin RecyclerView). */
    private fun renderizarActividad() {
        contenedorActividad.removeAllViews()

        if (actividadReciente.isEmpty()) {
            contenedorActividad.addView(TextView(this).apply {
                text      = "Sin actividad registrada aún"
                setTextColor(Color.parseColor("#777777"))
                textSize  = 13f
                gravity   = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(24), 0, dp(24)) }
            })
            return
        }

        actividadReciente.forEachIndexed { index, item ->
            // Fila horizontal
            val row = LinearLayout(this).apply {
                orientation  = LinearLayout.HORIZONTAL
                gravity      = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(14), 0, dp(14))
            }

            // Indicador de color (entrada=verde, salida=rojo)
            row.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(12) }
                setBackgroundColor(
                    if (item.tipo.equals("entrada", ignoreCase = true)) Color.parseColor("#4CAF50")
                    else Color.parseColor("#F44336")
                )
            })

            // Columna tipo + placa
            val info = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            info.addView(TextView(this).apply {
                // Si hay descripción detallada (Nivel, Cajón, Folio), la usamos.
                // Si no, usamos el texto genérico por defecto.
                text = if (item.descripcion.isNotBlank()) item.descripcion
                       else if (item.tipo.equals("entrada", ignoreCase = true)) "Entrada de Vehículo"
                       else "Salida de Vehículo"
                setTextColor(Color.WHITE)
                textSize = 13f
            })
            info.addView(TextView(this).apply {
                text = if (item.placa.isNotBlank()) "Placa: ${item.placa}" else "Placa: —"
                setTextColor(Color.parseColor("#A0A0A0"))
                textSize = 11f
            })
            row.addView(info)

            // Hora (dorado)
            row.addView(TextView(this).apply {
                text     = item.hora
                setTextColor(Color.parseColor("#D4A017"))
                textSize = 12f
            })

            contenedorActividad.addView(row)

            // Divisor (excepto último)
            if (index < actividadReciente.size - 1) {
                contenedorActividad.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(Color.parseColor("#2A2A2A"))
                })
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
