package org.utl.idgs903.appkaaxpark.Cliente

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.R

class EstanciaVehiculo : BaseActivity() {

    override fun getLayoutId(): Int = R.layout.activity_estancia_vehiculo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btnActualizar = findViewById<LinearLayout>(R.id.btnActualizar)
        btnActualizar?.setOnClickListener {
            Toast.makeText(this, "Actualizando estado de estancia...", Toast.LENGTH_SHORT).show()
        }
    }
}