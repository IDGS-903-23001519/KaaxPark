package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import java.util.Locale

class SeleccionarMetodoPago : AppCompatActivity() {

    private lateinit var repository: FirebaseRepository

    private lateinit var btnRegresar: LinearLayout
    private lateinit var txtTotalAPagar: TextView
    private lateinit var cardEfectivo: LinearLayout
    private lateinit var cardTarjeta: LinearLayout
    private lateinit var contenedorDatosTarjeta: LinearLayout
    private lateinit var txtNumeroTarjeta: EditText
    private lateinit var txtNombreTarjeta: EditText
    private lateinit var txtFechaTarjeta: EditText
    private lateinit var txtCvvTarjeta: EditText
    private lateinit var btnConfirmarPago: LinearLayout
    private lateinit var txtBtnConfirmar: TextView

    private var metodoSeleccionado: String? = null
    private var estanciaId: String = ""
    private var subtotal: Double = 0.0
    private var iva: Double = 0.0
    private var montoTotal: Double = 0.0
    private var procesandoPago = false

    companion object {
        const val EXTRA_ESTANCIA_ID = "extra_estancia_id"
        const val EXTRA_SUBTOTAL = "extra_subtotal"
        const val EXTRA_IVA = "extra_iva"
        const val EXTRA_MONTO_TOTAL = "extra_monto_total"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_seleccionar_metodo_pago)

        repository = FirebaseRepository()

        estanciaId = intent.getStringExtra(EXTRA_ESTANCIA_ID).orEmpty()
        subtotal = intent.getDoubleExtra(EXTRA_SUBTOTAL, 0.0)
        iva = intent.getDoubleExtra(EXTRA_IVA, 0.0)
        montoTotal = intent.getDoubleExtra(EXTRA_MONTO_TOTAL, 0.0)

        if (estanciaId.isBlank()) {
            Toast.makeText(this, "No se encontró la estancia a pagar.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        btnRegresar = findViewById(R.id.btnRegresar)
        txtTotalAPagar = findViewById(R.id.txtTotalAPagar)
        cardEfectivo = findViewById(R.id.cardEfectivo)
        cardTarjeta = findViewById(R.id.cardTarjeta)
        contenedorDatosTarjeta = findViewById(R.id.contenedorDatosTarjeta)
        txtNumeroTarjeta = findViewById(R.id.txtNumeroTarjeta)
        txtNombreTarjeta = findViewById(R.id.txtNombreTarjeta)
        txtFechaTarjeta = findViewById(R.id.txtFechaTarjeta)
        txtCvvTarjeta = findViewById(R.id.txtCvvTarjeta)
        btnConfirmarPago = findViewById(R.id.btnConfirmarPago)
        txtBtnConfirmar = findViewById(R.id.txtBtnConfirmar)

        txtTotalAPagar.text = String.format(Locale.getDefault(), "Total a pagar: $%.2f", montoTotal)

        btnRegresar.setOnClickListener {
            regresarADetallePago()
        }

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                actualizarEstadoBoton()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        listOf(txtNumeroTarjeta, txtNombreTarjeta, txtFechaTarjeta, txtCvvTarjeta).forEach {
            it.addTextChangedListener(watcher)
        }

        actualizarEstadoBoton()

        cardEfectivo.setOnClickListener { seleccionarMetodo("EFECTIVO") }
        cardTarjeta.setOnClickListener { seleccionarMetodo("TARJETA") }

        btnConfirmarPago.setOnClickListener {
            confirmarPago()
        }
    }

    private fun regresarADetallePago() {
        if (procesandoPago) return
        // No usamos flags especiales: DetallePago ya está debajo en la pila
        // de actividades (se llegó aquí con un startActivity normal desde ahí),
        // así que solo basta con cerrar esta pantalla para volver a esa.
        finish()
    }

    private fun seleccionarMetodo(metodo: String) {
        metodoSeleccionado = metodo
        contenedorDatosTarjeta.visibility = if (metodo == "TARJETA") View.VISIBLE else View.GONE

        cardEfectivo.alpha = if (metodo == "EFECTIVO") 1f else 0.5f
        cardTarjeta.alpha = if (metodo == "TARJETA") 1f else 0.5f

        actualizarEstadoBoton()
    }

    private fun actualizarEstadoBoton() {
        val habilitado = when (metodoSeleccionado) {
            "EFECTIVO" -> true
            "TARJETA" -> datosTarjetaCompletos()
            else -> false
        }
        btnConfirmarPago.isEnabled = habilitado && !procesandoPago
        btnConfirmarPago.alpha = if (habilitado && !procesandoPago) 1f else 0.4f
    }

    private fun datosTarjetaCompletos(): Boolean {
        val numero = txtNumeroTarjeta.text.toString().trim()
        val nombre = txtNombreTarjeta.text.toString().trim()
        val fecha = txtFechaTarjeta.text.toString().trim()
        val cvv = txtCvvTarjeta.text.toString().trim()
        return numero.length in 13..19 && nombre.isNotBlank() && fecha.isNotBlank() && cvv.length in 3..4
    }

    private fun confirmarPago() {
        val metodo = metodoSeleccionado ?: return
        if (metodo == "TARJETA" && !datosTarjetaCompletos()) {
            Toast.makeText(this, "Revisa los datos de la tarjeta.", Toast.LENGTH_SHORT).show()
            return
        }

        procesandoPago = true
        txtBtnConfirmar.text = "Procesando..."
        btnConfirmarPago.isEnabled = false

        // Aquí solo se simula la validación de la tarjeta; no se procesa un cargo real.
        repository.registerPayment(estanciaId, metodo, subtotal, iva, montoTotal) { result ->
            procesandoPago = false
            result.onSuccess {
                Toast.makeText(this, "Pago registrado correctamente.", Toast.LENGTH_LONG).show()
                irARecuperarVehiculo()
            }
            result.onFailure { error ->
                txtBtnConfirmar.text = "Confirmar pago"
                actualizarEstadoBoton()
                Toast.makeText(
                    this,
                    error.message ?: "No se pudo registrar el pago.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun irARecuperarVehiculo() {
        val intent = Intent(this, RecuperarVehiculo::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}