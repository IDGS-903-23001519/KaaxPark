package org.utl.idgs903.appkaaxpark.Cliente

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.R

class RecuperarVehiculo : BaseActivity() {

    override fun getLayoutId(): Int = R.layout.activity_recuperar_vehiculo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btnSolicitar = findViewById<LinearLayout>(R.id.btnSolicitar)
        btnSolicitar?.setOnClickListener {
            Toast.makeText(this, "Solicitud enviada. Tu vehículo viene en camino.", Toast.LENGTH_LONG).show()
        }
    }
}