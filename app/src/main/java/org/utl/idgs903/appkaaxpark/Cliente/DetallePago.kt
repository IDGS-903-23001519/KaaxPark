package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.MainActivity
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.ActiveStay
import org.utl.idgs903.appkaaxpark.data.CajonMotorHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class DetallePago : BaseActivity() {

    // ── Views ─────────────────────────────────────────────────────────────────
    private lateinit var txtTiempoTotalValor: TextView
    private lateinit var txtTarifaValor: TextView
    private lateinit var txtTotalValor: TextView
    private lateinit var txtAviso: TextView
    private lateinit var contenedorOpciones: LinearLayout
    private lateinit var btnPagarTarjeta: LinearLayout
    private lateinit var btnPagarCaja: LinearLayout
    private lateinit var contenedorEspera: LinearLayout
    private lateinit var txtEsperaEstado: TextView

    // ── Estado ────────────────────────────────────────────────────────────────
    private var estanciaActual: ActiveStay? = null
    private var vehiclePlaca: String = ""
    private var tarifaPorHora: Double = 60.0
    private var montoCalculado: Double = 0.0
    private var elapsedMillis: Long = 0L
    private var cancelarListenerPago: (() -> Unit)? = null
    private var procesando = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            actualizarMontos()
            timerHandler.postDelayed(this, 1000L)
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun getLayoutId(): Int = R.layout.activity_detalle_pago

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        txtTiempoTotalValor = findViewById(R.id.txtTiempoTotalValor)
        txtTarifaValor      = findViewById(R.id.txtTarifaValor)
        txtTotalValor       = findViewById(R.id.txtTotalValor)
        txtAviso            = findViewById(R.id.txtAviso)
        contenedorOpciones  = findViewById(R.id.contenedorOpciones)
        btnPagarTarjeta     = findViewById(R.id.btnPagarTarjeta)
        btnPagarCaja        = findViewById(R.id.btnPagarCaja)
        contenedorEspera    = findViewById(R.id.contenedorEspera)
        txtEsperaEstado     = findViewById(R.id.txtEsperaEstado)

        btnPagarTarjeta.setOnClickListener { if (!procesando) pagarConTarjeta() }
        btnPagarCaja.setOnClickListener { if (!procesando) pagarEnCaja() }

        // Leer tarifa desde Firebase (misma que usa la página web de administración)
        repository.fetchTarifaPorHora { result ->
            tarifaPorHora = result.getOrDefault(60.0)
            txtTarifaValor.text = String.format(Locale.getDefault(), "$%.0f/hora", tarifaPorHora)
        }
    }

    override fun onStart() {
        super.onStart()
        cargarEstanciaActual()
    }

    override fun onStop() {
        super.onStop()
        timerHandler.removeCallbacks(timerRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelarListenerPago?.invoke()
    }

    // ── Carga de estancia ─────────────────────────────────────────────────────
    private fun cargarEstanciaActual() {
        if (!repository.isAuthenticated()) { redirectToLogin(); return }
        val session = sessionManager.getSession() ?: run { redirectToLogin(); return }

        repository.fetchClientStayDetails(session.userDocId) { result ->
            result.onSuccess { details ->
                when {
                    details == null -> mostrarSinEstancia()
                    details.stay.isPaid -> mostrarYaPagado()
                    else -> {
                        estanciaActual = details.stay
                        vehiclePlaca   = details.vehicle.plate
                        timerHandler.post(timerRunnable)
                        mostrarPendientePago()
                    }
                }
            }
            result.onFailure {
                Toast.makeText(this, "No se pudo cargar tu estancia.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Cálculo de montos (igual que la página web: por hora, sin IVA) ────────
    private fun actualizarMontos() {
        val stay = estanciaActual ?: return
        elapsedMillis = max(0L, System.currentTimeMillis() - stay.entryTimestamp.toDate().time)

        // Fórmula idéntica a PagosComponent.montoCalculado del web:
        // ceil(max(1, ceil(minutos / 60))) * tarifaPorHora
        val minutos = ceil(elapsedMillis / 60_000.0).toLong()
        val horas   = max(1L, ceil(minutos / 60.0).toLong())
        montoCalculado = horas * tarifaPorHora

        txtTiempoTotalValor.text = formatElapsedTime(elapsedMillis)
        txtTotalValor.text = String.format(Locale.getDefault(), "$%.0f MXN", montoCalculado)
    }

    // ── Estados de pantalla ───────────────────────────────────────────────────
    private fun mostrarPendientePago() {
        txtAviso.text = "Elige cómo quieres pagar para recuperar tu vehículo."
        contenedorOpciones.visibility = View.VISIBLE
        contenedorEspera.visibility   = View.GONE
    }

    private fun mostrarYaPagado() {
        timerHandler.removeCallbacks(timerRunnable)
        txtAviso.text = "Tu pago ya fue registrado. Tu vehículo está en camino."
        contenedorOpciones.visibility = View.GONE
        contenedorEspera.visibility   = View.GONE
    }

    private fun mostrarSinEstancia() {
        txtTiempoTotalValor.text = "--:--:--"
        txtTotalValor.text       = "$0 MXN"
        txtAviso.text            = "No tienes una estancia activa en este momento."
        contenedorOpciones.visibility = View.GONE
        contenedorEspera.visibility   = View.GONE
    }

    // ── Flujo 1: Pagar con tarjeta (simulado — todo automático) ───────────────
    // 1. Marca la estancia como PAGADA en Firestore
    // 2. Crea el registro de pago (visible al admin en la web)
    // 3. Finaliza la estancia (cajón a Libre)
    // 4. Activa la secuencia de salida del motor
    // 5. Navega a RecuperarVehiculo con flag "en camino"
    private fun pagarConTarjeta() {
        val stay = estanciaActual ?: return
        procesando = true
        setBotonesHabilitados(false)
        actualizarMontos() // congelar monto final

        val ahora       = Date()
        val horaSalida  = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora)
        val horaEntrada = SimpleDateFormat("HH:mm", Locale.getDefault()).format(stay.entryTimestamp.toDate())
        val folio       = generarFolio()
        val durMin      = max(1L, ceil(elapsedMillis / 60_000.0).toLong())
        val cajonDesc   = cajonDescripcionFromId(stay.assignedSpotId)

        // 1. Marcar estancia como PAGADA
        repository.registerPayment(stay.documentId, "Tarjeta", montoCalculado, 0.0, montoCalculado) { regResult ->
            if (regResult.isFailure) { handleError("No se pudo procesar el pago."); return@registerPayment }

            // 2. Crear registro en colección 'pagos' (el admin lo verá en la web)
            repository.addPagoMovil(
                folio, stay.assignedSpotId, cajonDesc, vehiclePlaca,
                horaEntrada, horaSalida, durMin, montoCalculado,
                "Tarjeta", "Completado", stay.documentId
            ) { _ ->

                // 3. Finalizar estancia: cajón a Libre, estancia a FINALIZADA
                repository.finalizeStay(stay.documentId, stay.assignedSpotId) { finalResult ->
                    if (finalResult.isFailure) { handleError("Error al finalizar la estancia."); return@finalizeStay }

                    // 4. Activar secuencia de salida del motor para este cajón
                    val ctx = applicationContext
                    CajonMotorHelper.activarSalida(repository, stay.assignedSpotId) { motorResult ->
                        motorResult.onFailure { error ->
                            Toast.makeText(
                                ctx,
                                "Aviso: ${CajonMotorHelper.mensajeAmigable(error)} Avisa a un encargado.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    // 5. Ir a pantalla de éxito
                    irAExito()
                }
            }
        }
    }

    // ── Flujo 2: Pagar en caja (el admin confirma desde la web) ──────────────
    // 1. Crea un pago con estado 'PendienteCaja' en Firestore
    // 2. Muestra pantalla de espera con spinner
    // 3. Escucha en tiempo real ese documento
    // 4. Cuando el admin confirma en la web:
    //    - La web cambia estado a 'Completado', finaliza estancia y activa motor
    //    - El listener detecta el cambio y navega a éxito
    private fun pagarEnCaja() {
        val stay = estanciaActual ?: return
        procesando = true
        setBotonesHabilitados(false)
        actualizarMontos()

        val ahora       = Date()
        val horaSalida  = SimpleDateFormat("HH:mm", Locale.getDefault()).format(ahora)
        val horaEntrada = SimpleDateFormat("HH:mm", Locale.getDefault()).format(stay.entryTimestamp.toDate())
        val folio       = generarFolio()
        val durMin      = max(1L, ceil(elapsedMillis / 60_000.0).toLong())
        val cajonDesc   = cajonDescripcionFromId(stay.assignedSpotId)

        // Mostrar pantalla de espera inmediatamente
        contenedorOpciones.visibility = View.GONE
        contenedorEspera.visibility   = View.VISIBLE
        txtEsperaEstado.text = "Solicitud enviada al cajero.\nEsperando confirmación del cobro en ventanilla…"

        repository.addPagoMovil(
            folio, stay.assignedSpotId, cajonDesc, vehiclePlaca,
            horaEntrada, horaSalida, durMin, montoCalculado,
            "Efectivo", "PendienteCaja", stay.documentId
        ) { pagoResult ->
            pagoResult.onFailure {
                // Si falla, volver a mostrar las opciones de pago
                contenedorOpciones.visibility = View.VISIBLE
                contenedorEspera.visibility   = View.GONE
                handleError("No se pudo enviar la solicitud al cajero. Intenta de nuevo.")
                return@addPagoMovil
            }

            val pagoId = pagoResult.getOrNull() ?: return@addPagoMovil

            // Escuchar en tiempo real el estado del pago en Firestore
            cancelarListenerPago = repository.listenPagoEstado(pagoId) { estado ->
                when (estado) {
                    "Completado" -> {
                        // El admin confirmó el cobro en la página web.
                        // La web ya finalizó la estancia y activó el motor.
                        cancelarListenerPago?.invoke()
                        txtEsperaEstado.text = "¡Pago confirmado!\nTu vehículo está en camino."
                        timerHandler.postDelayed({ irAExito() }, 2000L)
                    }
                    "Rechazado" -> {
                        // El admin rechazó (estado reservado para uso futuro)
                        cancelarListenerPago?.invoke()
                        contenedorOpciones.visibility = View.VISIBLE
                        contenedorEspera.visibility   = View.GONE
                        procesando = false
                        setBotonesHabilitados(true)
                        Toast.makeText(
                            this,
                            "El pago no pudo confirmarse. Acércate a la ventanilla.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun irAExito() {
        val intent = Intent(this, RecuperarVehiculo::class.java).apply {
            putExtra(RecuperarVehiculo.EXTRA_VEHICULO_EN_CAMINO, true)
        }
        startActivity(intent)
        finish()
    }

    private fun handleError(msg: String) {
        procesando = false
        setBotonesHabilitados(true)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun setBotonesHabilitados(enabled: Boolean) {
        btnPagarTarjeta.isEnabled = enabled
        btnPagarTarjeta.alpha     = if (enabled) 1f else 0.4f
        btnPagarCaja.isEnabled    = enabled
        btnPagarCaja.alpha        = if (enabled) 1f else 0.4f
    }

    /** "n1c2" → "Nivel 1 · Cajón 2" */
    private fun cajonDescripcionFromId(id: String): String {
        return try {
            val nivel = id.substringAfter("n").substringBefore("c").toInt()
            val num   = id.substringAfterLast("c").toInt()
            "Nivel $nivel · Cajón $num"
        } catch (_: Exception) { id }
    }

    private fun generarFolio(): String {
        val fecha = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val rand  = (1000..9999).random()
        return "KP-$fecha-$rand"
    }

    private fun formatElapsedTime(millis: Long): String {
        val total = millis / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s)
    }

    private fun redirectToLogin() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}