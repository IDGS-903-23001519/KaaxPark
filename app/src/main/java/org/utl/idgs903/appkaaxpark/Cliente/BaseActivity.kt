package org.utl.idgs903.appkaaxpark.Cliente

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import org.utl.idgs903.appkaaxpark.R

abstract class BaseActivity : AppCompatActivity() {

    abstract fun getLayoutId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_layout_base)
        val contenedor = findViewById<FrameLayout>(R.id.contenedor_paginas)
        LayoutInflater.from(this).inflate(getLayoutId(), contenedor, true)
        configurarMenuNavegacion()
        marcarPantallaActiva()
    }

    private fun configurarMenuNavegacion() {
        val btnMenuEstancia = findViewById<LinearLayout>(R.id.btnMenuEstancia)
        val btnMenuHistorial = findViewById<LinearLayout>(R.id.btnMenuHistorial)
        val btnMenuQR = findViewById<CardView>(R.id.btnMenuQR)
        val btnMenuVehiculo = findViewById<LinearLayout>(R.id.btnMenuVehiculo)
        val btnMenuPago = findViewById<LinearLayout>(R.id.btnMenuPago)

        btnMenuEstancia?.setOnClickListener { viajarA(EstanciaVehiculo::class.java) }
        btnMenuHistorial?.setOnClickListener { viajarA(HistorialVisitas::class.java) }
        btnMenuQR?.setOnClickListener { viajarA(Codigoqr::class.java) }
        btnMenuVehiculo?.setOnClickListener { viajarA(RecuperarVehiculo::class.java) }
        btnMenuPago?.setOnClickListener { viajarA(DetallePago::class.java) }
    }

    private fun viajarA(destino: Class<*>) {
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
}