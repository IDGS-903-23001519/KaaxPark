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
import org.utl.idgs903.appkaaxpark.data.ClientStayDetails
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SessionManager
import org.utl.idgs903.appkaaxpark.data.UserProfile
import kotlin.math.max

class EstanciaVehiculo : BaseActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var txtSubtitulo: TextView
    private lateinit var txtTiempoMarcador: TextView
    private lateinit var txtDetalleMarca: TextView
    private lateinit var txtDetallePlaca: TextView
    private lateinit var txtDetalleColor: TextView

    private val timerHandler = Handler(Looper.getMainLooper())
    private var entryTimeMillis: Long? = null

    private val timerRunnable = object : Runnable {
        override fun run() {
            val startedAt = entryTimeMillis
            if (startedAt == null) {
                txtTiempoMarcador.text = "--:--:--"
                return
            }

            val elapsedMillis = max(0L, System.currentTimeMillis() - startedAt)
            txtTiempoMarcador.text = formatElapsedTime(elapsedMillis)
            timerHandler.postDelayed(this, 1000L)
        }
    }

    override fun getLayoutId(): Int = R.layout.activity_estancia_vehiculo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = FirebaseRepository()
        sessionManager = SessionManager(this)
        txtSubtitulo = findViewById(R.id.txtSubtitulo)
        txtTiempoMarcador = findViewById(R.id.txtTiempoMarcador)
        txtDetalleMarca = findViewById(R.id.txtDetalleMarca)
        txtDetallePlaca = findViewById(R.id.txtDetallePlaca)
        txtDetalleColor = findViewById(R.id.txtDetalleColor)

        val btnActualizar = findViewById<LinearLayout>(R.id.btnActualizar)
        btnActualizar?.setOnClickListener {
            loadClientStay(showRefreshFeedback = true)
        }
    }

    override fun onStart() {
        super.onStart()
        loadClientStay()
    }

    override fun onStop() {
        super.onStop()
        stopTimer()
    }

    private fun loadClientStay(showRefreshFeedback: Boolean = false) {
        if (!repository.isAuthenticated()) {
            sessionManager.clearSession()
            redirectToLogin()
            return
        }

        val session = sessionManager.getSession()
        if (session == null) {
            redirectToLogin()
            return
        }

        repository.fetchUserProfileByDocumentId(session.userDocId) { userResult ->
            userResult.onSuccess { profile ->
                repository.fetchClientStayDetails(profile.documentId) { stayResult ->
                    stayResult.onSuccess { details ->
                        if (details == null) {
                            renderEmptyStay(profile)
                            if (showRefreshFeedback) {
                                Toast.makeText(this, "No tienes una estancia activa.", Toast.LENGTH_SHORT).show()
                            }
                            return@onSuccess
                        }

                        renderStay(profile, details)
                        if (showRefreshFeedback) {
                            Toast.makeText(this, "Informacion actualizada.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    stayResult.onFailure { error ->
                        renderEmptyStay(profile, message = "${profile.displayName}, no se pudo cargar tu estancia.")
                        Toast.makeText(
                            this,
                            error.message ?: "No fue posible consultar tu estancia.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }

            userResult.onFailure {
                repository.signOut()
                sessionManager.clearSession()
                Toast.makeText(
                    this,
                    "Tu sesion ya no es valida. Inicia sesion nuevamente.",
                    Toast.LENGTH_LONG
                ).show()
                redirectToLogin()
            }
        }
    }

    private fun renderStay(profile: UserProfile, details: ClientStayDetails) {
        txtSubtitulo.text = "${profile.displayName}, tu vehiculo esta estacionado"
        txtDetalleMarca.text = details.vehicle.brand
        txtDetallePlaca.text = details.vehicle.plate
        txtDetalleColor.text = details.vehicle.color
        entryTimeMillis = details.stay.entryTimestamp.toDate().time
        startTimer()
    }

    private fun renderEmptyStay(profile: UserProfile, message: String? = null) {
        txtSubtitulo.text = message ?: "${profile.displayName}, no tienes una estancia activa"
        txtDetalleMarca.text = "--"
        txtDetallePlaca.text = "--"
        txtDetalleColor.text = "--"
        entryTimeMillis = null
        stopTimer()
        txtTiempoMarcador.text = "--:--:--"
    }

    private fun startTimer() {
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun formatElapsedTime(elapsedMillis: Long): String {
        val totalSeconds = elapsedMillis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    private fun redirectToLogin() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
