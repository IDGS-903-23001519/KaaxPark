package org.utl.idgs903.appkaaxpark.Admin

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
import androidx.core.view.WindowCompat
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.global.InfoUsuario

abstract class BaseAdminActivity : AppCompatActivity() {

    abstract fun getLayoutId(): Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_layout_base_admin)
        val contenedor = findViewById<FrameLayout>(R.id.containerDashboard)
        LayoutInflater.from(this).inflate(getLayoutId(), contenedor, true)

        configurarMenuNavegacionAdmin()
        marcarPantallaActivaAdmin()
    }

    private fun configurarMenuNavegacionAdmin() {
        val btnMenuDashboard = findViewById<LinearLayout>(R.id.btnMenuDashboard)
        val btnMenuCajones = findViewById<LinearLayout>(R.id.btnMenuCajones)
        val btnMenuReporte = findViewById<LinearLayout>(R.id.btnMenuReporte)
        val btnMenuSustentabilidad = findViewById<LinearLayout>(R.id.btnMenuSustentabilidad)
        val btnMenuCentralK = findViewById<CardView>(R.id.btnMenuCentralK)
        val btnInfoUsuario = findViewById<ImageView>(R.id.btnPerfil)

        btnMenuDashboard?.setOnClickListener { viajarA(Dashboard::class.java) }
        btnMenuCajones?.setOnClickListener { viajarA(Cajones::class.java) }
        btnMenuReporte?.setOnClickListener { viajarA(Reportes::class.java) }
        btnMenuSustentabilidad?.setOnClickListener { viajarA(Sustentabilidad::class.java) }
        btnInfoUsuario?.setOnClickListener { viajarA(InfoUsuario::class.java) }

        btnMenuCentralK?.setOnClickListener {
            viajarA(AsistenteIA::class.java)
        }
    }

    protected fun viajarA(destino: Class<*>) {
        if (this.javaClass != destino) {
            val intent = Intent(this, destino).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
    }

    private fun marcarPantallaActivaAdmin() {
        val colorDorado = android.graphics.Color.parseColor("#D4A017")

        when (this) {
            is Dashboard -> {
                findViewById<TextView>(R.id.txtNavDashboard)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgNavDashboard)?.setImageResource(R.drawable.icon_dashboard_active)
            }

            is Cajones -> {
                findViewById<TextView>(R.id.txtNavCajones)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgNavCajones)?.setImageResource(R.drawable.icon_cajones_active)
            }

            is Reportes -> {
                findViewById<TextView>(R.id.txtNavReporte)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgNavReporte)?.setImageResource(R.drawable.icon_reporte_active)
            }

            is Sustentabilidad -> {
                findViewById<TextView>(R.id.txtNavSustentabilidad)?.setTextColor(colorDorado)
                findViewById<ImageView>(R.id.imgNavSustentabilidad)?.setImageResource(R.drawable.icon_sustentabilidad_active)
            }

            is GestionUsuarios -> {
                findViewById<ImageView>(R.id.btnPerfil)?.setColorFilter(colorDorado)
            }
        }
    }
}
