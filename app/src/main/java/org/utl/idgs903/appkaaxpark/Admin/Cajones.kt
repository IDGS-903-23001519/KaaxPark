package org.utl.idgs903.appkaaxpark.Admin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.CajonInfo
import org.utl.idgs903.appkaaxpark.data.ClientStayDetails
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import java.text.SimpleDateFormat
import java.util.Locale

class Cajones : BaseAdminActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var txtBuscarCajon: EditText
    private lateinit var layoutListaCajones: LinearLayout
    private lateinit var lblSinCajones: TextView
    private lateinit var txtTotalCajones: TextView
    private lateinit var txtOcupadosCajones: TextView
    private lateinit var txtOcupadosPorcentaje: TextView
    private lateinit var txtDisponiblesCajones: TextView
    private lateinit var txtDisponiblesPorcentaje: TextView

    private var cajones: List<CajonInfo> = emptyList()

    override fun getLayoutId(): Int = R.layout.activity_cajones

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageTitle("Cajones")

        repository = FirebaseRepository()
        txtBuscarCajon = findViewById(R.id.txtBuscarCajon)
        layoutListaCajones = findViewById(R.id.layoutListaCajones)
        lblSinCajones = findViewById(R.id.lblSinCajones)
        txtTotalCajones = findViewById(R.id.txtTotalCajones)
        txtOcupadosCajones = findViewById(R.id.txtOcupadosCajones)
        txtOcupadosPorcentaje = findViewById(R.id.txtOcupadosPorcentaje)
        txtDisponiblesCajones = findViewById(R.id.txtDisponiblesCajones)
        txtDisponiblesPorcentaje = findViewById(R.id.txtDisponiblesPorcentaje)

        txtBuscarCajon.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                renderLista(filtrarCajones(s?.toString().orEmpty()))
            }
        })
    }

    override fun onStart() {
        super.onStart()
        cargarCajones()
    }

    private fun cargarCajones() {
        repository.fetchCajones { result ->
            result.onSuccess { lista ->
                cajones = lista
                actualizarContadores(lista)
                renderLista(filtrarCajones(txtBuscarCajon.text.toString()))
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No fue posible cargar los cajones.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun filtrarCajones(query: String): List<CajonInfo> {
        if (query.isBlank()) {
            return cajones
        }
        return cajones.filter { it.codigo.contains(query.trim(), ignoreCase = true) }
    }

    private fun actualizarContadores(lista: List<CajonInfo>) {
        val total = lista.size
        val disponibles = lista.count { it.isLibre }
        val ocupados = total - disponibles

        txtTotalCajones.text = total.toString()
        txtOcupadosCajones.text = ocupados.toString()
        txtDisponiblesCajones.text = disponibles.toString()

        val porcentajeOcupados = if (total > 0) ocupados * 100 / total else 0
        val porcentajeDisponibles = if (total > 0) disponibles * 100 / total else 0
        txtOcupadosPorcentaje.text = "$porcentajeOcupados%"
        txtDisponiblesPorcentaje.text = "$porcentajeDisponibles%"
    }

    private fun renderLista(lista: List<CajonInfo>) {
        layoutListaCajones.removeAllViews()
        lblSinCajones.visibility = if (lista.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        lista.forEach { cajon ->
            layoutListaCajones.addView(crearItemCajon(cajon))
        }
    }

    private fun crearItemCajon(cajon: CajonInfo): android.view.View {
        val view = LayoutInflater.from(this).inflate(R.layout.item_cajon, layoutListaCajones, false)

        view.findViewById<TextView>(R.id.txtItemCodigoCajon).text = cajon.codigo
        view.findViewById<TextView>(R.id.txtItemUbicacionCajon).text =
            "Piso ${cajon.nivel} - C${cajon.numeroCajon}"

        val txtEstado = view.findViewById<TextView>(R.id.txtItemEstado)
        val colorHex = if (cajon.isLibre) "#3DDC84" else "#D4A017"
        txtEstado.text = if (cajon.isLibre) "Disponible" else cajon.estado
        txtEstado.setTextColor(Color.parseColor(colorHex))

        val indicador = view.findViewById<android.view.View>(R.id.viewIndicadorColor)
        val indicadorDrawable = indicador.background.mutate() as? GradientDrawable
        indicadorDrawable?.setColor(Color.parseColor(colorHex))

        if (!cajon.isLibre) {
            view.setOnClickListener { mostrarOcupacionCajon(cajon) }
        }

        return view
    }

    private fun mostrarOcupacionCajon(cajon: CajonInfo) {
        repository.fetchCajonOccupancy(cajon.documentId) { result ->
            result.onSuccess { detalle ->
                if (detalle == null) {
                    Toast.makeText(
                        this,
                        "El cajón ${cajon.codigo} no tiene una estancia activa registrada.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    mostrarDialogoOcupacion(cajon, detalle)
                }
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No fue posible consultar la ocupación del cajón.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarDialogoOcupacion(cajon: CajonInfo, detalle: ClientStayDetails) {
        val formatoFecha = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val horaEntrada = formatoFecha.format(detalle.stay.entryTimestamp.toDate())

        val vista = LayoutInflater.from(this).inflate(R.layout.dialog_ocupacion_cajon, null)
        vista.findViewById<TextView>(R.id.dlgTxtCajonCodigo).text = cajon.codigo
        vista.findViewById<TextView>(R.id.dlgTxtVehiculo).text = detalle.vehicle.brand
        vista.findViewById<TextView>(R.id.dlgTxtPlaca).text = detalle.vehicle.plate
        vista.findViewById<TextView>(R.id.dlgTxtColor).text = detalle.vehicle.color
        vista.findViewById<TextView>(R.id.dlgTxtEntrada).text = horaEntrada

        val dialog = AlertDialog.Builder(this)
            .setView(vista)
            .create()
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

        vista.findViewById<TextView>(R.id.dlgBtnCerrar).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
