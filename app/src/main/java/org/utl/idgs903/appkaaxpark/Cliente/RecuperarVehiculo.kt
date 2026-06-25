package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.MainActivity
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.ActiveStay
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SessionManager

class RecuperarVehiculo : BaseActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var txtPregunta: TextView
    private lateinit var txtDescripcion: TextView
    private lateinit var btnSolicitar: LinearLayout

    private var estanciaActual: ActiveStay? = null
    private var redirigiendoAPago = false
    private var vehiculoEnCamino = false

    override fun getLayoutId(): Int = R.layout.activity_recuperar_vehiculo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = FirebaseRepository()
        sessionManager = SessionManager(this)
        txtPregunta = findViewById(R.id.txtPregunta)
        txtDescripcion = findViewById(R.id.txtDescripcion)
        btnSolicitar = findViewById(R.id.btnSolicitar)

        btnSolicitar.setOnClickListener {
            solicitarVehiculo()
        }
    }

    override fun onStart() {
        super.onStart()
        redirigiendoAPago = false
        cargarEstanciaActual()
    }

    private fun cargarEstanciaActual() {
        if (!repository.isAuthenticated()) {
            redirectToLogin()
            return
        }
        val session = sessionManager.getSession()
        if (session == null) {
            redirectToLogin()
            return
        }

        repository.fetchClientStayDetails(session.userDocId) { result ->
            result.onSuccess { details ->
                if (details == null) {
                    estanciaActual = null
                    mostrarSinEstancia()
                    return@onSuccess
                }

                estanciaActual = details.stay
                if (details.stay.isPaid) {
                    mostrarListoParaSolicitar()
                } else {
                    mostrarRequierePago()
                }
            }
            result.onFailure {
                Toast.makeText(this, "No se pudo verificar tu estancia.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun mostrarListoParaSolicitar() {
        if (vehiculoEnCamino) return
        txtPregunta.text = "¿Listo para recuperar\ntu vehículo?"
        txtDescripcion.text = "Al solicitar tu vehículo, se activará la plataforma para traerlo a la puerta de salida."
        btnSolicitar.isEnabled = true
        btnSolicitar.alpha = 1f
    }

    private fun mostrarRequierePago() {
        btnSolicitar.isEnabled = false
        btnSolicitar.alpha = 0.4f
        txtPregunta.text = "Debes pagar tu estancia\nantes de continuar"
        txtDescripcion.text = "Te estamos redirigiendo al apartado de pago..."

        if (!redirigiendoAPago) {
            redirigiendoAPago = true
            Toast.makeText(this, "Primero debes pagar tu estancia.", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, DetallePago::class.java))
        }
    }

    private fun mostrarSinEstancia() {
        btnSolicitar.isEnabled = false
        btnSolicitar.alpha = 0.4f
        txtPregunta.text = "No tienes una estancia\nactiva en este momento"
        txtDescripcion.text = "Escanea el código QR de entrada para registrar tu vehículo."
    }

    private fun solicitarVehiculo() {
        val stay = estanciaActual ?: return
        if (!stay.isPaid) {
            Toast.makeText(this, "Primero debes pagar tu estancia.", Toast.LENGTH_LONG).show()
            return
        }

        btnSolicitar.isEnabled = false
        repository.finalizeStay(stay.documentId, stay.assignedSpotId) { result ->
            result.onSuccess {
                vehiculoEnCamino = true
                estanciaActual = null
                txtPregunta.text = "¡Está en camino\ntu vehículo!"
                txtDescripcion.text = "En unos minutos tu vehículo estará listo en la puerta de salida."
                Toast.makeText(this, "Está en camino su vehículo.", Toast.LENGTH_LONG).show()
            }
            result.onFailure { error ->
                btnSolicitar.isEnabled = true
                btnSolicitar.alpha = 1f
                Toast.makeText(
                    this,
                    error.message ?: "No se pudo solicitar tu vehículo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}