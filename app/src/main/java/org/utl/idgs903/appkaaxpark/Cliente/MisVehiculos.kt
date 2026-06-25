package org.utl.idgs903.appkaaxpark.Cliente

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SessionManager
import org.utl.idgs903.appkaaxpark.data.VehicleInfo

class MisVehiculos : BaseActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var contenedorVehiculos: LinearLayout
    private lateinit var btnAgregarVehiculo: CardView

    private var usuarioId: String = ""
    private var vehiculos: List<VehicleInfo> = emptyList()

    override fun getLayoutId(): Int = R.layout.activity_mis_vehiculos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = FirebaseRepository()
        sessionManager = SessionManager(this)

        contenedorVehiculos = findViewById(R.id.contenedorVehiculos)
        btnAgregarVehiculo = findViewById(R.id.btnAgregarVehiculo)

        usuarioId = sessionManager.getSession()?.userDocId.orEmpty()

        btnAgregarVehiculo.setOnClickListener {
            mostrarDialogoAgregarVehiculo()
        }

        cargarVehiculos()
    }

    private fun cargarVehiculos() {
        if (usuarioId.isBlank()) {
            Toast.makeText(this, "No se pudo identificar tu cuenta.", Toast.LENGTH_LONG).show()
            return
        }

        repository.fetchVehiclesByUserId(usuarioId) { result ->
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
        contenedorVehiculos.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (vehiculos.isEmpty()) {
            val vacio = TextView(this).apply {
                text = "Aún no tienes vehículos registrados."
                setTextColor(Color.parseColor("#AAAAAA"))
                textSize = 14f
                setPadding(0, 32, 0, 32)
            }
            contenedorVehiculos.addView(vacio)
            return
        }

        vehiculos.forEach { vehiculo ->
            val item = inflater.inflate(R.layout.item_vehiculo, contenedorVehiculos, false)

            val txtMarcaModelo = item.findViewById<TextView>(R.id.txtMarcaModelo)
            val txtPlacaColor = item.findViewById<TextView>(R.id.txtPlacaColor)
            val txtEnUso = item.findViewById<TextView>(R.id.txtEnUso)
            val cardItem = item.findViewById<CardView>(R.id.cardVehiculoItem)
            val btnEliminar = item.findViewById<ImageView>(R.id.btnEliminarVehiculo)

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

            cardItem.setOnClickListener {
                if (!vehiculo.isActive) {
                    seleccionarVehiculoActivo(vehiculo)
                }
            }

            // El botón de eliminar solo se muestra en esta pantalla
            // ("Mis vehículos"); en la selección durante el escaneo permanece oculto.
            btnEliminar.visibility = View.VISIBLE
            btnEliminar.setOnClickListener {
                confirmarEliminarVehiculo(vehiculo)
            }

            contenedorVehiculos.addView(item)
        }
    }

    private fun seleccionarVehiculoActivo(vehiculo: VehicleInfo) {
        repository.setActiveVehicle(usuarioId, vehiculo.documentId) { result ->
            result.onSuccess {
                Toast.makeText(
                    this,
                    "${vehiculo.brand} ${vehiculo.model} ahora está en uso.",
                    Toast.LENGTH_SHORT
                ).show()
                cargarVehiculos()
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No se pudo actualizar el vehículo en uso.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmarEliminarVehiculo(vehiculo: VehicleInfo) {
        val nombre = listOf(vehiculo.brand, vehiculo.model)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        AlertDialog.Builder(this)
            .setTitle("Eliminar vehículo")
            .setMessage("¿Seguro que quieres eliminar $nombre (${vehiculo.plate})? Podrás registrar otro vehículo cuando quieras.")
            .setPositiveButton("Eliminar") { _, _ -> eliminarVehiculo(vehiculo) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarVehiculo(vehiculo: VehicleInfo) {
        repository.deactivateVehicle(usuarioId, vehiculo.documentId) { result ->
            result.onSuccess {
                Toast.makeText(this, "Vehículo eliminado.", Toast.LENGTH_SHORT).show()
                cargarVehiculos()
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No se pudo eliminar el vehículo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarDialogoAgregarVehiculo() {
        val vista = LayoutInflater.from(this).inflate(R.layout.dialog_agregar_vehiculo, null)

        val txtMarca = vista.findViewById<EditText>(R.id.txtMarcaDialog)
        val txtModelo = vista.findViewById<EditText>(R.id.txtModeloDialog)
        val txtColor = vista.findViewById<EditText>(R.id.txtColorDialog)
        val txtPlaca = vista.findViewById<EditText>(R.id.txtPlacaDialog)
        val btnCancelar = vista.findViewById<CardView>(R.id.btnCancelarDialog)
        val btnGuardar = vista.findViewById<CardView>(R.id.btnGuardarDialog)

        // Sin título ni botones del propio AlertDialog: todo el diseño
        // (tarjeta, campos y botones) viene del layout para que se vea
        // igual que el resto de la app.
        val dialogo = AlertDialog.Builder(this)
            .setView(vista)
            .create()

        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener { dialogo.dismiss() }

        btnGuardar.setOnClickListener {
            val marca = txtMarca.text.toString().trim()
            val modelo = txtModelo.text.toString().trim()
            val color = txtColor.text.toString().trim()
            val placa = txtPlaca.text.toString().trim()

            if (marca.isBlank() || color.isBlank() || placa.isBlank()) {
                Toast.makeText(this, "Completa marca, color y placa.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            guardarVehiculo(marca, modelo, color, placa)
            dialogo.dismiss()
        }

        dialogo.show()
    }

    private fun guardarVehiculo(marca: String, modelo: String, color: String, placa: String) {
        repository.addVehicle(usuarioId, marca, modelo, color, placa) { result ->
            result.onSuccess {
                Toast.makeText(this, "Vehículo agregado correctamente.", Toast.LENGTH_SHORT).show()
                cargarVehiculos()
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No se pudo agregar el vehículo.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}