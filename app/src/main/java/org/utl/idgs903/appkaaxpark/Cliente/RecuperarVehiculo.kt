package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.MainActivity
import org.utl.idgs903.appkaaxpark.R

/**
 * Pantalla final del flujo de pago.
 *
 * Casos de entrada:
 * ─ EXTRA_VEHICULO_EN_CAMINO = true  →  navega aquí DetallePago después de
 *   confirmar el pago (tarjeta o caja). Muestra "en camino" sin botón.
 *
 * ─ Sin extra (navegación directa o back)  →  verifica la estancia real:
 *   · Sin estancia o FINALIZADA  →  muestra "no tienes estancia activa".
 *   · ACTIVA y sin pagar         →  redirige a DetallePago.
 *   · ACTIVA y ya pagada         →  muestra "en camino" (debería ser raro).
 */
class RecuperarVehiculo : BaseActivity() {

    companion object {
        const val EXTRA_VEHICULO_EN_CAMINO = "vehiculo_en_camino"
    }

    private lateinit var txtPregunta: TextView
    private lateinit var txtDescripcion: TextView
    private lateinit var btnSolicitar: LinearLayout

    override fun getLayoutId(): Int = R.layout.activity_recuperar_vehiculo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageTitle("Retirar Vehículo")

        txtPregunta   = findViewById(R.id.txtPregunta)
        txtDescripcion = findViewById(R.id.txtDescripcion)
        btnSolicitar  = findViewById(R.id.btnSolicitar)

        // Si viene del flujo de pago (tarjeta o caja confirmada) → éxito inmediato
        if (intent.getBooleanExtra(EXTRA_VEHICULO_EN_CAMINO, false)) {
            mostrarEnCamino()
        }
    }

    override fun onStart() {
        super.onStart()
        // Solo verificar el estado real si NO venimos del flujo de pago
        if (!intent.getBooleanExtra(EXTRA_VEHICULO_EN_CAMINO, false)) {
            verificarEstado()
        }
    }

    private fun mostrarEnCamino() {
        txtPregunta.text = "¡Tu vehículo está\nen camino!"
        txtDescripcion.text = "En unos minutos estará listo\nen la puerta de salida.\n\n¡Gracias por usar K'áaxPark!"
        btnSolicitar.visibility = View.GONE
    }

    private fun mostrarSinEstancia() {
        txtPregunta.text = "No tienes una estancia\nactiva en este momento"
        txtDescripcion.text = "Escanea el código QR de entrada\npara registrar tu vehículo."
        btnSolicitar.visibility = View.GONE
    }

    /**
     * Verifica la estancia actual cuando se navega aquí directamente
     * (sin el flag EXTRA_VEHICULO_EN_CAMINO).
     */
    private fun verificarEstado() {
        if (!repository.isAuthenticated()) { redirectToLogin(); return }
        val session = sessionManager.getSession() ?: run { redirectToLogin(); return }

        repository.fetchClientStayDetails(session.userDocId) { result ->
            result.onSuccess { details ->
                when {
                    details == null -> mostrarSinEstancia()
                    details.stay.isPaid -> mostrarEnCamino()
                    else -> {
                        // Todavía no ha pagado — redirigir al flujo de pago
                        startActivity(Intent(this, DetallePago::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        })
                    }
                }
            }
            result.onFailure {
                Toast.makeText(this, "No se pudo verificar tu estancia.", Toast.LENGTH_LONG).show()
                mostrarSinEstancia()
            }
        }
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}