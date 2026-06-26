package org.utl.idgs903.appkaaxpark.global

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.utl.idgs903.appkaaxpark.MainActivity
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SessionManager

class InfoUsuario : AppCompatActivity() {

    private val repository = FirebaseRepository()
    private lateinit var sessionManager: SessionManager

    private lateinit var txtNombre: TextView
    private lateinit var txtRol: TextView
    private lateinit var txtEstado: TextView
    private lateinit var txtEmail: TextView
    private lateinit var lblCampoSecundario: TextView
    private lateinit var txtCampoSecundario: TextView
    private lateinit var txtMarcaModelo: TextView
    private lateinit var txtPlaca: TextView
    private lateinit var txtColor: TextView

    private lateinit var cardVehiculo: CardView

    private lateinit var btnCerrarSesion: Button


    override fun onCreate(savedInstanceState: Bundle?) {



        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_info_usuario)

        sessionManager = SessionManager(this)

        txtNombre = findViewById(R.id.txtNombre)
        txtEstado = findViewById(R.id.txtEstado)
        txtEmail = findViewById(R.id.txtEmail)
        lblCampoSecundario = findViewById(R.id.lblCampoSecundario)
        txtCampoSecundario = findViewById(R.id.txtCampoSecundario)

        txtMarcaModelo = findViewById(R.id.txtMarcaModelo)
        txtPlaca = findViewById(R.id.txtPlaca)
        txtColor = findViewById(R.id.txtColor)

        txtRol = findViewById(R.id.txtRol)

        cardVehiculo = findViewById(R.id.cardVehiculo)

        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        btnCerrarSesion.setOnClickListener {

            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Deseas cerrar tu sesión?")
                .setPositiveButton("Sí") { _, _ ->

                    repository.signOut()
                    sessionManager.clearSession()

                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags =
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK

                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        cargarUsuario()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun cargarUsuario() {

        repository.restoreUserProfile(sessionManager.getSession()) { result ->

            runOnUiThread {

                result.onSuccess { usuario ->

                    txtNombre.text = usuario.name
                    txtRol.text = usuario.role
                    txtEstado.text = usuario.status
                    txtEmail.text = usuario.email

                    if (usuario.role.equals("ADMIN", true)) {
                        lblCampoSecundario.text = "Usuario"
                        txtCampoSecundario.text = usuario.username
                    } else {
                        lblCampoSecundario.text = "Teléfono"
                        txtCampoSecundario.text = usuario.phone
                    }

                    if (usuario.role.equals("CLIENTE", true)) {

                        repository.fetchClientStayDetails(usuario.documentId) { vehicleResult ->

                            runOnUiThread {

                                vehicleResult.onSuccess { details ->

                                    if (details != null) {

                                        txtMarcaModelo.text =
                                            details.vehicle.brand

                                        txtPlaca.text =
                                            details.vehicle.plate

                                        txtColor.text =
                                            details.vehicle.color

                                        cardVehiculo.visibility = View.VISIBLE

                                    } else {

                                        cardVehiculo.visibility = View.GONE
                                    }
                                }

                                vehicleResult.onFailure {
                                    cardVehiculo.visibility = View.GONE
                                }
                            }
                        }

                    } else {

                        cardVehiculo.visibility = View.GONE
                    }
                }

                result.onFailure {

                    Toast.makeText(
                        this,
                        it.message,
                        Toast.LENGTH_LONG
                    ).show()

                    finish()
                }
            }
        }
    }
}