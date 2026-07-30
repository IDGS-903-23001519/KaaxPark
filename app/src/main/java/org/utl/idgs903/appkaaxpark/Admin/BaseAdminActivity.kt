package org.utl.idgs903.appkaaxpark.Admin

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
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

        val btnAsistenteIA = findViewById<View>(R.id.btnAsistenteIA)

        btnMenuDashboard?.setOnClickListener { viajarA(Dashboard::class.java) }
        btnMenuCajones?.setOnClickListener { viajarA(Cajones::class.java) }
        btnMenuReporte?.setOnClickListener { viajarA(Reportes::class.java) }
        btnMenuSustentabilidad?.setOnClickListener { viajarA(Sustentabilidad::class.java) }
        btnInfoUsuario?.setOnClickListener { viajarA(InfoUsuario::class.java) }
        hacerBotonFlotanteDraggable(btnAsistenteIA) { viajarA(AsistenteIA::class.java) }

        btnMenuCentralK?.setOnClickListener {
            viajarA(AsistenteIA::class.java)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun hacerBotonFlotanteDraggable(view: View?, onClick: () -> Unit) {
        if (view == null) return
        var dX = 0f
        var dY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            val parent = v.parent as? View ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY

                    val minX = 0f
                    val maxX = (parent.width - v.width).toFloat()
                    val minY = 0f
                    val maxY = (parent.height - v.height).toFloat()

                    val clampedX = newX.coerceIn(minX, maxX)
                    val clampedY = newY.coerceIn(minY, maxY)

                    if (Math.abs(clampedX - v.x) > 6f || Math.abs(clampedY - v.y) > 6f) {
                        isDragging = true
                    }

                    v.x = clampedX
                    v.y = clampedY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        onClick()
                    }
                    true
                }
                else -> false
            }
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
