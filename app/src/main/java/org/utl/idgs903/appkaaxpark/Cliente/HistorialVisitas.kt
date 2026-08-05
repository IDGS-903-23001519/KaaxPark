package org.utl.idgs903.appkaaxpark.Cliente

import android.app.Dialog
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.MainActivity
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.VisitHistoryItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HistorialVisitas : BaseActivity() {

    private lateinit var contenedorHistorial: LinearLayout
    private lateinit var txtSinRegistros: TextView
    private lateinit var layoutFiltro: LinearLayout
    private lateinit var txtFiltroLabel: TextView

    private var historialCompleto: List<VisitHistoryItem> = emptyList()
    private var filtroActual: FiltroHistorial = FiltroHistorial.TODOS

    private enum class FiltroHistorial(val etiqueta: String) {
        TODOS("Todos los registros"),
        HOY("Hoy"),
        AYER("Ayer"),
        SEMANA("Esta semana"),
        MES("Este mes")
    }

    override fun getLayoutId(): Int = R.layout.activity_historial_visitas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        contenedorHistorial = findViewById(R.id.contenedorHistorial)
        txtSinRegistros = findViewById(R.id.txtSinRegistros)
        layoutFiltro = findViewById(R.id.layoutFiltro)
        txtFiltroLabel = findViewById(R.id.txtFiltroLabel)

        layoutFiltro.setOnClickListener {
            mostrarMenuFiltro()
        }
    }

    override fun onStart() {
        super.onStart()
        cargarHistorial()
    }

    private fun cargarHistorial() {
        if (!repository.isAuthenticated()) {
            redirectToLogin()
            return
        }
        val session = sessionManager.getSession()
        if (session == null) {
            redirectToLogin()
            return
        }

        repository.fetchVisitHistory(session.userDocId) { result ->
            result.onSuccess { visitas ->
                historialCompleto = visitas
                renderHistorial(aplicarFiltro(historialCompleto, filtroActual))
            }
            result.onFailure { error ->
                Toast.makeText(
                    this,
                    error.message ?: "No se pudo cargar tu historial.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun mostrarMenuFiltro() {
        val vistaPopup = LayoutInflater.from(this)
            .inflate(R.layout.popup_filtro_historial, null)

        val popupWindow = PopupWindow(
            vistaPopup,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        popupWindow.elevation = 8f

        fun seleccionar(filtro: FiltroHistorial) {
            filtroActual = filtro
            txtFiltroLabel.text = filtro.etiqueta
            renderHistorial(aplicarFiltro(historialCompleto, filtroActual))
            popupWindow.dismiss()
        }

        vistaPopup.findViewById<TextView>(R.id.opcionTodos).setOnClickListener {
            seleccionar(FiltroHistorial.TODOS)
        }
        vistaPopup.findViewById<TextView>(R.id.opcionHoy).setOnClickListener {
            seleccionar(FiltroHistorial.HOY)
        }
        vistaPopup.findViewById<TextView>(R.id.opcionAyer).setOnClickListener {
            seleccionar(FiltroHistorial.AYER)
        }
        vistaPopup.findViewById<TextView>(R.id.opcionSemana).setOnClickListener {
            seleccionar(FiltroHistorial.SEMANA)
        }
        vistaPopup.findViewById<TextView>(R.id.opcionMes).setOnClickListener {
            seleccionar(FiltroHistorial.MES)
        }

        popupWindow.showAsDropDown(layoutFiltro, 0, 8)
    }

    private fun aplicarFiltro(
        lista: List<VisitHistoryItem>,
        filtro: FiltroHistorial
    ): List<VisitHistoryItem> {
        if (filtro == FiltroHistorial.TODOS) return lista

        return lista.filter { visita ->
            val fechaVisita = Calendar.getInstance().apply {
                time = visita.fechaEntrada.toDate()
            }

            when (filtro) {
                FiltroHistorial.HOY -> esMismoDia(fechaVisita, Calendar.getInstance())
                FiltroHistorial.AYER -> {
                    val ayer = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    esMismoDia(fechaVisita, ayer)
                }
                FiltroHistorial.SEMANA -> fechaVisita.timeInMillis >= inicioDeSemana().timeInMillis
                FiltroHistorial.MES -> fechaVisita.timeInMillis >= inicioDeMes().timeInMillis
                else -> true
            }
        }
    }

    private fun esMismoDia(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun inicioDeSemana(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun inicioDeMes(): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun renderHistorial(visitas: List<VisitHistoryItem>) {
        contenedorHistorial.removeAllViews()

        if (visitas.isEmpty()) {
            txtSinRegistros.visibility = View.VISIBLE
            return
        }
        txtSinRegistros.visibility = View.GONE

        val formatoFecha = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val formatoHora = SimpleDateFormat("hh:mm a", Locale.getDefault())

        visitas.forEach { visita ->
            val vista = LayoutInflater.from(this)
                .inflate(R.layout.item_historial_visita, contenedorHistorial, false)

            val fechaEntrada = visita.fechaEntrada.toDate()

            vista.findViewById<TextView>(R.id.txtItemFecha).text = formatoFecha.format(fechaEntrada)
            vista.findViewById<TextView>(R.id.txtItemHora).text = formatoHora.format(fechaEntrada)
            vista.findViewById<TextView>(R.id.txtItemCajon).text = "Cajón: ${visita.cajonId}"
            vista.findViewById<TextView>(R.id.txtItemMonto).text =
                String.format(Locale.getDefault(), "$%.2f", visita.montoTotal)

            vista.setOnClickListener {
                mostrarDetalleVisita(visita)
            }

            contenedorHistorial.addView(vista)
        }
    }

    private fun mostrarDetalleVisita(visita: VisitHistoryItem) {
        val vistaDialogo = LayoutInflater.from(this)
            .inflate(R.layout.dialog_detalle_visita, null)

        val formatoFechaHora = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())

        vistaDialogo.findViewById<TextView>(R.id.txtDetalleCajon).text = visita.cajonId
        vistaDialogo.findViewById<TextView>(R.id.txtDetalleFechaEntrada).text =
            formatoFechaHora.format(visita.fechaEntrada.toDate())

        val fechaSalida = visita.fechaSalida
        vistaDialogo.findViewById<TextView>(R.id.txtDetalleFechaSalida).text =
            fechaSalida?.let { formatoFechaHora.format(it.toDate()) } ?: "--"

        vistaDialogo.findViewById<TextView>(R.id.txtDetalleTiempoTotal).text =
            if (fechaSalida != null) {
                calcularTiempoTotal(visita.fechaEntrada.toDate().time, fechaSalida.toDate().time)
            } else {
                "--"
            }

        vistaDialogo.findViewById<TextView>(R.id.txtDetalleMetodoPago).text =
            visita.metodoPago.ifBlank { "N/D" }
        vistaDialogo.findViewById<TextView>(R.id.txtDetalleSubtotal).text =
            String.format(Locale.getDefault(), "$%.2f", visita.subtotal)
        vistaDialogo.findViewById<TextView>(R.id.txtDetalleIva).text =
            String.format(Locale.getDefault(), "$%.2f", visita.iva)
        vistaDialogo.findViewById<TextView>(R.id.txtDetalleTotal).text =
            String.format(Locale.getDefault(), "$%.2f", visita.montoTotal)

        val dialog = Dialog(this)
        dialog.setContentView(vistaDialogo)
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        vistaDialogo.findViewById<LinearLayout>(R.id.btnCerrarDetalle).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()

        val anchoDeseado = (resources.displayMetrics.widthPixels * 0.88).toInt()
        dialog.window?.setLayout(anchoDeseado, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun calcularTiempoTotal(entradaMillis: Long, salidaMillis: Long): String {
        val totalSeconds = ((salidaMillis - entradaMillis) / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}