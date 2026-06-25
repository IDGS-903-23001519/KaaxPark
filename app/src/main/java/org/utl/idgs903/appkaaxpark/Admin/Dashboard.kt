package org.utl.idgs903.appkaaxpark.Admin

import android.graphics.Color
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.CajonInfo
import org.utl.idgs903.appkaaxpark.data.EstanciaResumen
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.ParkingStats
import org.utl.idgs903.appkaaxpark.data.ReportPeriod
import org.utl.idgs903.appkaaxpark.data.SessionManager
import java.util.Calendar
import java.util.Date

class Dashboard : BaseAdminActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var sessionManager: SessionManager

    override fun getLayoutId(): Int = R.layout.activity_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FirebaseRepository()
        sessionManager = SessionManager(this)
    }

    override fun onStart() {
        super.onStart()
        loadAdminGreeting()
        loadEstadisticas()
    }

    private fun loadAdminGreeting() {
        val session = sessionManager.getSession() ?: return
        repository.fetchUserProfileByDocumentId(session.userDocId) { result ->
            result.onSuccess { profile ->
                findViewById<TextView>(R.id.lblBienvenido)?.text = "Bienvenido, ${profile.displayName}"
            }
        }
    }

    private fun loadEstadisticas() {
        repository.fetchCajones { cajonesResult ->
            cajonesResult.onSuccess { cajones ->
                repository.fetchEstancias { estanciasResult ->
                    estanciasResult.onSuccess { estancias ->
                        renderEstadisticas(cajones, estancias)
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
            cajonesResult.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No fue posible cargar los cajones.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderEstadisticas(cajones: List<CajonInfo>, estancias: List<EstanciaResumen>) {
        val ahora = Date()
        val hoy = ParkingStats.rangeFor(ReportPeriod.DIA, ahora)
        val ayerReferencia = addDays(ahora, -1)
        val ayer = ParkingStats.rangeFor(ReportPeriod.DIA, ayerReferencia)
        val semana = ParkingStats.rangeFor(ReportPeriod.SEMANA, ahora)

        val entradasHoy = ParkingStats.countEntradas(estancias, hoy)
        val entradasAyer = ParkingStats.countEntradas(estancias, ayer)
        val salidasHoy = ParkingStats.countSalidas(estancias, hoy)
        val salidasAyer = ParkingStats.countSalidas(estancias, ayer)

        val ocupados = cajones.count { !it.isLibre }
        val ocupacionPorcentaje = if (cajones.isNotEmpty()) ocupados * 100 / cajones.size else 0

        val tiempoPromedioMillis = ParkingStats.averageStayDurationMillis(estancias, semana)

        findViewById<TextView>(R.id.numEntradas)?.text = entradasHoy.toString()
        findViewById<TextView>(R.id.numSalidas)?.text = salidasHoy.toString()
        findViewById<TextView>(R.id.numOcupacion)?.text = "$ocupacionPorcentaje%"
        findViewById<TextView>(R.id.numTiempo)?.text = tiempoPromedioMillis?.let { ParkingStats.formatDuration(it) } ?: "--:--:--"

        bindCambioPorcentual(R.id.txtCambioEntradas, entradasAyer, entradasHoy)
        bindCambioPorcentual(R.id.txtCambioSalidas, salidasAyer, salidasHoy)
    }

    private fun bindCambioPorcentual(viewId: Int, valorAnterior: Int, valorActual: Int) {
        val txt = findViewById<TextView>(viewId) ?: return
        if (valorAnterior == 0) {
            if (valorActual == 0) {
                txt.text = "Sin cambios"
                txt.setTextColor(Color.parseColor("#B0B5B8"))
            } else {
                txt.text = "↑ Nuevo"
                txt.setTextColor(Color.parseColor("#4CAF50"))
            }
            return
        }

        val cambio = (valorActual - valorAnterior) * 100 / valorAnterior
        if (cambio >= 0) {
            txt.text = "↑ $cambio%"
            txt.setTextColor(Color.parseColor("#4CAF50"))
        } else {
            txt.text = "↓ $cambio%"
            txt.setTextColor(Color.parseColor("#F44336"))
        }
    }

    private fun addDays(date: Date, days: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.add(Calendar.DAY_OF_MONTH, days)
        return calendar.time
    }
}
