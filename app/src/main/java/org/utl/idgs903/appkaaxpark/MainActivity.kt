package org.utl.idgs903.appkaaxpark

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
// 1. IMPORTAMOS LAS DOS RUTAS PARA TENERLAS LISTAS
import org.utl.idgs903.appkaaxpark.Cliente.Codigoqr
import org.utl.idgs903.appkaaxpark.Admin.Dashboard

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnLogin = findViewById<Button>(R.id.btnLogin)

        btnLogin.setOnClickListener {
            // Codigoqr::class.java
            // Dashboard::class.java
            val intent = Intent(this, Codigoqr::class.java)
            startActivity(intent)
        }
    }
}