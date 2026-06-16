package org.utl.idgs903.appkaaxpark.Cliente

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.R

class DetallePago : BaseActivity() {

    override fun getLayoutId(): Int = R.layout.activity_detalle_pago

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val btnPagar = findViewById<LinearLayout>(R.id.btnPagar)
        btnPagar?.setOnClickListener {
            Toast.makeText(this, "Procesando pago de K'áaxPark...", Toast.LENGTH_SHORT).show()
        }
    }
}