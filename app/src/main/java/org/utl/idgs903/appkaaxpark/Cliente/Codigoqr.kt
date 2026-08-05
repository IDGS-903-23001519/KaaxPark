package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.CajonMotorHelper
import org.utl.idgs903.appkaaxpark.data.EstanciaActivaExistenteException
import org.utl.idgs903.appkaaxpark.data.SinCajonesDisponiblesException
import org.utl.idgs903.appkaaxpark.data.VehicleInfo
import org.utl.idgs903.appkaaxpark.data.VehiculoNoEncontradoException

class Codigoqr : BaseActivity() {

    private lateinit var txtResultadoAsignacion: TextView
    private lateinit var contenedorVehiculosQR: LinearLayout
    private lateinit var contenedorEscaneo: LinearLayout
    private lateinit var contenedorSeleccionVehiculo: LinearLayout
    private lateinit var btnIniciarEscaneo: LinearLayout
    private lateinit var txtVolverEscaneo: TextView

    private var procesandoAsignacion = false
    private var vehiculos: List<VehicleInfo> = emptyList()

    // Ya no se guarda un código de cajón: el QR es uno solo, fijo en la
    // entrada del estacionamiento. Esta bandera solo confirma que se
    // escaneó ese QR antes de permitir elegir vehículo.
    private var codigoEntradaValidado = false

    private enum class EstadoPantalla { ESCANEO, SELECCION_VEHICULO, ESTANCIA_ACTIVA }

    private val scanner by lazy {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()
        GmsBarcodeScanning.getClient(this, options)
    }

    companion object {
        // El texto que debe traer el código QR físico colocado en la
        // entrada del estacionamiento (uno solo para todo el lugar, ya
        // no uno distinto por cajón). Debe coincidir EXACTO con el QR
        // impreso — si cambias este valor, hay que reimprimir el QR.
        private const val CODIGO_QR_ENTRADA = "KAAXPARK_INGRESO"
    }

    override fun getLayoutId(): Int = R.layout.activity_codigoqr

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        txtResultadoAsignacion = findViewById(R.id.txtResultadoAsignacion)
        contenedorVehiculosQR = findViewById(R.id.contenedorVehiculosQR)
        contenedorEscaneo = findViewById(R.id.contenedorEscaneo)
        contenedorSeleccionVehiculo = findViewById(R.id.contenedorSeleccionVehiculo)
        btnIniciarEscaneo = findViewById(R.id.btnIniciarEscaneo)
        txtVolverEscaneo = findViewById(R.id.txtVolverEscaneo)

        btnIniciarEscaneo.setOnClickListener { iniciarEscaneo() }
        txtVolverEscaneo.setOnClickListener { volverAEscanear() }

        mostrarEstado(EstadoPantalla.ESCANEO)
    }

    override fun onStart() {
        super.onStart()
        cargarVehiculos()
    }

    // --- Control de las distintas pantallas (escanear / elegir vehículo / ya estacionado) ---

    private fun mostrarEstado(estado: EstadoPantalla) {
        contenedorEscaneo.visibility =
            if (estado == EstadoPantalla.ESCANEO) View.VISIBLE else View.GONE
        contenedorSeleccionVehiculo.visibility =
            if (estado == EstadoPantalla.SELECCION_VEHICULO) View.VISIBLE else View.GONE
    }

    private fun volverAEscanear() {
        codigoEntradaValidado = false
        txtResultadoAsignacion.text = ""
        mostrarEstado(EstadoPantalla.ESCANEO)
    }

    // --- Carga y dibujo de la lista de vehículos seleccionables ---

    private fun cargarVehiculos() {
        val session = sessionManager.getSession() ?: return

        repository.fetchVehiclesByUserId(session.userDocId) { result ->
            result.onSuccess { lista ->
                vehiculos = lista
                renderizarVehiculos()
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No se pudieron cargar tus vehículos.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun renderizarVehiculos() {
        contenedorVehiculosQR.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (vehiculos.isEmpty()) {
            val vacio = TextView(this).apply {
                text = "No tienes vehículos registrados."
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 14f
                setPadding(0, 16, 0, 4)
            }
            val agregar = TextView(this).apply {
                text = "+ Agregar vehículo"
                setTextColor(Color.parseColor("#D4A017"))
                textSize = 14f
                setPadding(0, 4, 0, 16)
                setOnClickListener {
                    startActivity(Intent(this@Codigoqr, MisVehiculos::class.java))
                }
            }
            contenedorVehiculosQR.addView(vacio)
            contenedorVehiculosQR.addView(agregar)
            return
        }

        val habilitado = !procesandoAsignacion

        vehiculos.forEach { vehiculo ->
            val item = inflater.inflate(R.layout.item_vehiculo, contenedorVehiculosQR, false)

            val txtMarcaModelo = item.findViewById<TextView>(R.id.txtMarcaModelo)
            val txtPlacaColor = item.findViewById<TextView>(R.id.txtPlacaColor)
            val txtEnUso = item.findViewById<TextView>(R.id.txtEnUso)
            val cardItem = item.findViewById<CardView>(R.id.cardVehiculoItem)

            txtMarcaModelo.text = listOf(vehiculo.brand, vehiculo.model)
                .filter { it.isNotBlank() }
                .joinToString(" ")
            txtPlacaColor.text = "${vehiculo.plate} · ${vehiculo.color}"

            if (vehiculo.isActive) {
                txtEnUso.visibility = View.VISIBLE
                cardItem.setCardBackgroundColor(Color.parseColor("#1F1A0D"))
            } else {
                txtEnUso.visibility = View.GONE
                cardItem.setCardBackgroundColor(Color.parseColor("#1A1A1A"))
            }

            // El botón de eliminar (visible en "Mis vehículos") se mantiene
            // oculto aquí: en esta pantalla solo se elige el vehículo.

            cardItem.isEnabled = habilitado
            cardItem.alpha = if (habilitado) 1f else 0.4f
            cardItem.setOnClickListener { seleccionarVehiculoYAsignar(vehiculo) }

            contenedorVehiculosQR.addView(item)
        }
    }

    // --- Paso 1: escaneo del código QR de la entrada ---

    private fun iniciarEscaneo() {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val valorQr = barcode.rawValue?.trim()
                if (valorQr.isNullOrEmpty()) {
                    Toast.makeText(this, "No se pudo leer el código QR.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                if (!valorQr.equals(CODIGO_QR_ENTRADA, ignoreCase = true)) {
                    Toast.makeText(
                        this,
                        "Ese código QR no corresponde a la entrada del estacionamiento.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }
                codigoEntradaValidado = true
                txtResultadoAsignacion.text = ""
                mostrarEstado(EstadoPantalla.SELECCION_VEHICULO)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Escaneo cancelado o error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // --- Paso 2: elegir con qué vehículo se entra (el cajón se asigna al azar) ---

    private fun seleccionarVehiculoYAsignar(vehiculo: VehicleInfo) {
        if (procesandoAsignacion) return

        if (!codigoEntradaValidado) {
            // Salvaguarda: nunca debería pasar porque el contenedor de
            // selección solo es visible después de un escaneo válido.
            Toast.makeText(this, "Primero escanea el código QR.", Toast.LENGTH_SHORT).show()
            mostrarEstado(EstadoPantalla.ESCANEO)
            return
        }

        val session = sessionManager.getSession()
        if (session == null) {
            Toast.makeText(this, "Tu sesión no es válida. Inicia sesión nuevamente.", Toast.LENGTH_LONG).show()
            return
        }

        procesandoAsignacion = true
        renderizarVehiculos()
        txtResultadoAsignacion.text = "Buscando un cajón disponible..."

        repository.setActiveVehicle(session.userDocId, vehiculo.documentId) { resultActivo ->
            if (resultActivo.isFailure) {
                procesandoAsignacion = false
                renderizarVehiculos()
                Toast.makeText(
                    this,
                    resultActivo.exceptionOrNull()?.message ?: "No se pudo seleccionar el vehículo.",
                    Toast.LENGTH_LONG
                ).show()
                return@setActiveVehicle
            }

            repository.assignRandomParkingSpot(session.userDocId, vehiculo.documentId) { result ->
                procesandoAsignacion = false

                result.onSuccess { lugarAsignado ->
                    mostrarLugarAsignado(lugarAsignado)
                    navegacionBloqueada = false
                    Toast.makeText(
                        this,
                        "¡Listo! Tu vehículo quedó registrado en el lugar $lugarAsignado.",
                        Toast.LENGTH_LONG
                    ).show()

                    // Activa el mecanismo físico del cajón asignado. No bloquea
                    // la navegación: el cajón ya quedó asignado en Firebase de
                    // cualquier forma; si el robot no responde, solo avisamos.
                    val contexto = applicationContext
                    CajonMotorHelper.activarIngreso(repository, lugarAsignado) { motorResult ->
                        motorResult.onFailure { error ->
                            Toast.makeText(
                                contexto,
                                "Aviso: ${CajonMotorHelper.mensajeAmigable(error)} Avisa a un encargado.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    irAEstancia()
                }

                result.onFailure { error ->
                    renderizarVehiculos()
                    mostrarErrorAsignacion(error)
                }
            }
        }
    }

    // --- Estado de la estancia (si ya entró antes, no se vuelve a mostrar el escaneo) ---

    private fun verificarEstanciaExistente() {
        if (!repository.isAuthenticated()) return
        val session = sessionManager.getSession() ?: return

        repository.fetchClientStayDetails(session.userDocId) { result ->
            result.onSuccess { details ->
                if (details != null) {
                    mostrarLugarAsignado(details.stay.assignedSpotId)
                    navegacionBloqueada = false
                    mostrarEstado(EstadoPantalla.ESTANCIA_ACTIVA)
                }
            }
        }
    }

    private fun irAEstancia() {
        val intent = Intent(this, EstanciaVehiculo::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    private fun mostrarLugarAsignado(lugar: String) {
        txtResultadoAsignacion.text = "Tu vehículo fue asignado al lugar: $lugar"
    }

    private fun mostrarErrorAsignacion(error: Throwable) {
        val mensaje = when (error) {
            is SinCajonesDisponiblesException -> "No hay cajones disponibles en este momento. Intenta más tarde."
            is VehiculoNoEncontradoException -> "No se encontró un vehículo registrado en tu cuenta."
            is EstanciaActivaExistenteException -> {
                verificarEstanciaExistente()
                "Ya tienes una estancia activa."
            }
            else -> error.message ?: "No se pudo registrar tu ingreso."
        }
        txtResultadoAsignacion.text = mensaje
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }
}