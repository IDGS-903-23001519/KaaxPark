package org.utl.idgs903.appkaaxpark.Cliente

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.utl.idgs903.appkaaxpark.R

class Codigoqr : BaseActivity() {

    override fun getLayoutId(): Int = R.layout.activity_codigoqr

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(this, options)

        val btnIniciarEscaneo = findViewById<LinearLayout>(R.id.btnIniciarEscaneo)
        btnIniciarEscaneo.setOnClickListener {
            scanner.startScan()
                .addOnSuccessListener { barcode ->
                    val valorQr = barcode.rawValue

                    if (!valorQr.isNullOrEmpty()) {
                        Toast.makeText(this, "Código detectado: $valorQr", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Escaneo cancelado o error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}