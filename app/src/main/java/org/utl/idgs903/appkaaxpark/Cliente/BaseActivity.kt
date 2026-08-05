package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SessionManager
import org.utl.idgs903.appkaaxpark.global.InfoUsuario

abstract class BaseActivity : AppCompatActivity() {

    protected lateinit var repository: FirebaseRepository
    protected lateinit var sessionManager: SessionManager

    private var lastBackPressedTime = 0L
    protected open fun shouldExitOnBackPress(): Boolean = true
    abstract fun getLayoutId(): Int


    protected var navegacionBloqueada: Boolean = false
        set(value) {
            field = value
            actualizarEstadoMenuNavegacion()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = FirebaseRepository()
        sessionManager = SessionManager(this)

        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_layout_base)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_base)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val contenedor = findViewById<FrameLayout>(R.id.contenedor_paginas)
        LayoutInflater.from(this).inflate(getLayoutId(), contenedor, true)
        configurarMenuNavegacion()
        marcarPantallaActiva()
        actualizarEstadoMenuNavegacion()
    }

    override fun onStart() {
        super.onStart()
        verificarBloqueoNavegacion()
    }

    private fun verificarBloqueoNavegacion() {
        val session = sessionManager.getSession() ?: return
        repository.fetchClientStayDetails(session.userDocId) { result ->
            result.onSuccess { details ->
                // Si NO hay estancia activa, la navegación está bloqueada (debe escanear QR)
                navegacionBloqueada = details == null
            }
        }
    }

    private fun configurarMenuNavegacion() {
        val btnMenuEstancia = findViewById<LinearLayout>(R.id.btnMenuEstancia)
        val btnMenuHistorial = findViewById<LinearLayout>(R.id.btnMenuHistorial)
        val btnMenuQR = findViewById<CardView>(R.id.btnMenuQR)
        val btnMenuVehiculo = findViewById<LinearLayout>(R.id.btnMenuVehiculo)
        val btnMenuPago = findViewById<LinearLayout>(R.id.btnMenuPago)
        val btnInfoUsuario = findViewById<ImageView>(R.id.btnPerfil)
        val btnVehiculos = findViewById<CardView>(R.id.btnVehiculos)

        btnMenuEstancia?.setOnClickListener { viajarA(EstanciaVehiculo::class.java) }
        btnMenuHistorial?.setOnClickListener { viajarA(HistorialVisitas::class.java) }
        btnMenuQR?.setOnClickListener { viajarA(Codigoqr::class.java) }
        btnMenuVehiculo?.setOnClickListener { viajarA(RecuperarVehiculo::class.java) }
        btnMenuPago?.setOnClickListener { viajarA(DetallePago::class.java) }
        btnInfoUsuario?.setOnClickListener { viajarA(InfoUsuario::class.java) }
        btnVehiculos?.setOnClickListener { viajarA(MisVehiculos::class.java) }
    }

    private fun actualizarEstadoMenuNavegacion() {
        val habilitado = !navegacionBloqueada
        val idsBloqueables = listOf(
            R.id.btnMenuEstancia,
            R.id.btnMenuVehiculo,
            R.id.btnMenuPago
        )

        idsBloqueables.forEach { id ->
            findViewById<View>(id)?.let { vista ->
                vista.isEnabled = habilitado
                vista.alpha = if (habilitado) 1f else 0.4f
            }
        }

        findViewById<View>(R.id.btnMenuHistorial)?.apply {
            isEnabled = true
            alpha = 1f
        }

        // El acceso al perfil (y a cerrar sesión desde ahí) nunca se bloquea.
        findViewById<View>(R.id.btnPerfil)?.apply {
            isEnabled = true
            alpha = 1f
        }

        // El botón QR se bloquea si ya hay una estancia activa (navegación desbloqueada)
        findViewById<View>(R.id.btnMenuQR)?.apply {
            isEnabled = navegacionBloqueada
            alpha = if (navegacionBloqueada) 1f else 0.4f
        }
    }
    private fun viajarA(destino: Class<*>) {
        val esHistorial = destino == HistorialVisitas::class.java
        val esPerfil = destino == InfoUsuario::class.java
        val esQR = destino == Codigoqr::class.java

        if (navegacionBloqueada && !esHistorial && !esPerfil && !esQR) {
            Toast.makeText(
                this,
                "Escanea el codigo QR de entrada para continuar.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (this.javaClass != destino) {
            val intent = Intent(this, destino).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    private fun marcarPantallaActiva() {
        val colorDorado = android.graphics.Color.parseColor("#D4A017")

        when (this) {
            is EstanciaVehiculo -> {
                findViewById<TextView>(R.id.txtNavEstancia)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgEstancia)?.setImageResource(R.drawable.icon_estancia_active)
            }
            is HistorialVisitas -> {
                findViewById<TextView>(R.id.txtNavHistorial)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgHistorial)?.setImageResource(R.drawable.icon_historial_active)
            }
            is RecuperarVehiculo -> {
                findViewById<TextView>(R.id.txtNavVehiculo)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgVehiculo)?.setImageResource(R.drawable.icon_vehiculo_active)
            }
            is DetallePago -> {
                findViewById<TextView>(R.id.txtNavPago)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgPago)?.setImageResource(R.drawable.icon_pago_active)
            }
        }
    }

    override fun onBackPressed() {
        if (navegacionBloqueada) {
            Toast.makeText(
                this,
                "Debes escanear el código QR de entrada para continuar.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!shouldExitOnBackPress()) {
            super.onBackPressed()
            return
        }

        if (System.currentTimeMillis() - lastBackPressedTime < 2000) {
            finishAffinity()
        } else {
            lastBackPressedTime = System.currentTimeMillis()
            Toast.makeText(
                this,
                "Presiona nuevamente para salir",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}