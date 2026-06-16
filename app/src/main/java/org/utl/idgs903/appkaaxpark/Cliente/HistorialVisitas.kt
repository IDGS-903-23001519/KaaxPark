package org.utl.idgs903.appkaaxpark.Cliente

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.R

class HistorialVisitas : BaseActivity() {

    override fun getLayoutId(): Int = R.layout.activity_historial_visitas

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layoutFiltro = findViewById<LinearLayout>(R.id.layoutFiltro)
        layoutFiltro?.setOnClickListener {
            Toast.makeText(this, "Abriendo filtros...", Toast.LENGTH_SHORT).show()
        }
    }
}