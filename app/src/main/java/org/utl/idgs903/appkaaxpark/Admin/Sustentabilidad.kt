package org.utl.idgs903.appkaaxpark.Admin

import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SustentabilidadInfo
import kotlin.math.roundToInt

class Sustentabilidad : BaseAdminActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var viewNivelTanque: android.view.View
    private lateinit var numNivelTanque: TextView
    private lateinit var numAguaCaptada: TextView
    private lateinit var numAguaRiego: TextView
    private lateinit var numEnergiaGenerada: TextView
    private lateinit var numPorcentajeSolar: TextView
    private lateinit var viewIndicadorBomba: android.view.View
    private lateinit var txtEstadoBomba: TextView
    private lateinit var layoutAlertasTanque: LinearLayout

    override fun getLayoutId(): Int = R.layout.activity_sustentabilidad

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageTitle("Sustentabilidad")

        repository = FirebaseRepository()
        viewNivelTanque = findViewById(R.id.viewNivelTanque)
        numNivelTanque = findViewById(R.id.numNivelTanque)
        numAguaCaptada = findViewById(R.id.numAguaCaptada)
        numAguaRiego = findViewById(R.id.numAguaRiego)
        numEnergiaGenerada = findViewById(R.id.numEnergiaGenerada)
        numPorcentajeSolar = findViewById(R.id.numPorcentajeSolar)
        viewIndicadorBomba = findViewById(R.id.viewIndicadorBomba)
        txtEstadoBomba = findViewById(R.id.txtEstadoBomba)
        layoutAlertasTanque = findViewById(R.id.layoutAlertasTanque)
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
        val nivel = info.nivelTanquePorcentaje.roundToInt().coerceIn(0, 100)
        numNivelTanque.text = "$nivel%"
        renderNivelTanque(nivel)

        numAguaCaptada.text = "${formatearNumero(info.aguaCaptadaLitros)} L"
        numAguaRiego.text = "${formatearNumero(info.aguaUsadaRiegoLitros)} L"
        numEnergiaGenerada.text = "${formatearNumero(info.energiaGeneradaKwh)} kWh"
        numPorcentajeSolar.text = "${info.porcentajeSolar.roundToInt()}%"

        renderEstadoBomba(info.bombaAguaEncendida)
        renderAlertas(info.alertas)
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

        val alertasActivas = alertas.filter { (_, valor) ->
            when (valor) {
                is Boolean -> valor
                is Number -> valor.toDouble() > 0
                is String -> valor.isNotBlank()
                else -> false
            }
        }

        if (alertasActivas.isEmpty()) {
            layoutAlertasTanque.addView(crearChip("Sin alertas activas", esPositivo = true))
            return
        }

        alertasActivas.forEach { (clave, valor) ->
            val texto = if (valor is Boolean) humanizarClave(clave) else "${humanizarClave(clave)}: $valor"
            layoutAlertasTanque.addView(crearChip(texto, esPositivo = false))
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
