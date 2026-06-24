package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.MainActivity
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.ActiveStay
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SessionManager
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class DetallePago : BaseActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var sessionManager: SessionManager

    private lateinit var txtTiempoTotalValor: TextView
    private lateinit var txtTarifaValor: TextView
    private lateinit var txtSubtotalValor: TextView
    private lateinit var txtIvaValor: TextView
    private lateinit var txtTotalValor: TextView
    private lateinit var txtAviso: TextView
    private lateinit var btnPagar: LinearLayout
    private lateinit var txtBtnPagar: TextView

    private var estanciaActual: ActiveStay? = null
    private var subtotalCalculado: Double = 0.0
    private var ivaCalculado: Double = 0.0
    private var totalCalculado: Double = 0.0

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            actualizarMontos()
            timerHandler.postDelayed(this, 1000L)
        }
    }

    companion object {
        private const val TARIFA_POR_MINUTO = 5.0
        private const val IVA_PORCENTAJE = 0.18
    }

    override fun getLayoutId(): Int = R.layout.activity_detalle_pago

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = FirebaseRepository()
        sessionManager = SessionManager(this)

        txtTiempoTotalValor = findViewById(R.id.txtTiempoTotalValor)
        txtTarifaValor = findViewById(R.id.txtTarifaValor)
        txtSubtotalValor = findViewById(R.id.txtSubtotalValor)
        txtIvaValor = findViewById(R.id.txtIvaValor)
        txtTotalValor = findViewById(R.id.txtTotalValor)
        txtAviso = findViewById(R.id.txtAviso)
        btnPagar = findViewById(R.id.btnPagar)
        txtBtnPagar = findViewById(R.id.txtBtnPagar)

        txtTarifaValor.text = String.format(Locale.getDefault(), "$%.2f/minuto", TARIFA_POR_MINUTO)
    }

    override fun onStart() {
        super.onStart()
        cargarEstanciaActual()
    }

    override fun onStop() {
        super.onStop()
        timerHandler.removeCallbacks(timerRunnable)
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
                    mostrarYaPagado()
                } else {
                    mostrarPendientePago()
                    timerHandler.removeCallbacks(timerRunnable)
                    timerHandler.post(timerRunnable)
                }
            }
            result.onFailure {
                Toast.makeText(this, "No se pudo cargar tu estancia.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun actualizarMontos() {
        val stay = estanciaActual ?: return
        val elapsedMillis = max(0L, System.currentTimeMillis() - stay.entryTimestamp.toDate().time)
        val elapsedSeconds = elapsedMillis / 1000L
        val elapsedMinutes = max(1L, ceil(elapsedSeconds / 60.0).toLong())

        val subtotal = elapsedMinutes * TARIFA_POR_MINUTO
        val iva = subtotal * IVA_PORCENTAJE
        val total = subtotal + iva

        subtotalCalculado = subtotal
        ivaCalculado = iva
        totalCalculado = total

        txtTiempoTotalValor.text = formatElapsedTime(elapsedMillis)
        txtSubtotalValor.text = String.format(Locale.getDefault(), "$%.2f", subtotal)
        txtIvaValor.text = String.format(Locale.getDefault(), "$%.2f", iva)
        txtTotalValor.text = String.format(Locale.getDefault(), "$%.2f", total)
    }

    private fun formatElapsedTime(elapsedMillis: Long): String {
        val totalSeconds = elapsedMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun mostrarPendientePago() {
        txtAviso.text = "Realiza tu pago para poder recuperar tu vehículo."
        txtBtnPagar.text = "Pagar y recuperar"
        btnPagar.alpha = 1f
        btnPagar.isEnabled = true
        btnPagar.setOnClickListener {
            abrirSeleccionMetodoPago()
        }
    }

    private fun mostrarYaPagado() {
        timerHandler.removeCallbacks(timerRunnable)
        actualizarMontos()
        txtAviso.text = "Ya pagaste tu estancia. Puedes solicitar tu vehículo."
        txtBtnPagar.text = "Ir a recuperar vehículo"
        btnPagar.alpha = 1f
        btnPagar.isEnabled = true
        btnPagar.setOnClickListener {
            startActivity(Intent(this, RecuperarVehiculo::class.java))
        }
    }

    private fun mostrarSinEstancia() {
        timerHandler.removeCallbacks(timerRunnable)
        txtTiempoTotalValor.text = "--:--:--"
        txtSubtotalValor.text = "$0.00"
        txtIvaValor.text = "$0.00"
        txtTotalValor.text = "$0.00"
        txtAviso.text = "No tienes una estancia activa."
        txtBtnPagar.text = "Pagar y recuperar"
        btnPagar.alpha = 0.4f
        btnPagar.isEnabled = false
    }

    private fun abrirSeleccionMetodoPago() {
        val stay = estanciaActual ?: return
        actualizarMontos() // congelamos los montos justo antes de enviarlos a pagar

        val intent = Intent(this, SeleccionarMetodoPago::class.java).apply {
            putExtra(SeleccionarMetodoPago.EXTRA_ESTANCIA_ID, stay.documentId)
            putExtra(SeleccionarMetodoPago.EXTRA_SUBTOTAL, subtotalCalculado)
            putExtra(SeleccionarMetodoPago.EXTRA_IVA, ivaCalculado)
            putExtra(SeleccionarMetodoPago.EXTRA_MONTO_TOTAL, totalCalculado)
        }
        startActivity(intent)
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}