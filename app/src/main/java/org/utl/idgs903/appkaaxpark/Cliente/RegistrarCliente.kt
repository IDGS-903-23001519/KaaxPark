package org.utl.idgs903.appkaaxpark.Cliente

import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository

class RegistrarCliente : AppCompatActivity() {

    // Campos Personales
    private lateinit var txtNombre: EditText
    private lateinit var txtTelefono: EditText
    private lateinit var txtCorreo: EditText
    private lateinit var txtPassword: EditText

    // Campos Vehículo
    private lateinit var txtMarca: EditText
    private lateinit var txtModelo: EditText
    private lateinit var txtColor: EditText
    private lateinit var txtPlaca: EditText

    private lateinit var imgOjo: ImageView
    private lateinit var btnRegistrar: Button
    private lateinit var txtLoginRegresar: TextView

    private var passwordVisible = false

    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registrar_cliente)

        txtNombre = findViewById(R.id.txtNombreReg)
        txtTelefono = findViewById(R.id.txtTelefonoReg)
        txtCorreo = findViewById(R.id.txtCorreoReg)
        txtPassword = findViewById(R.id.txtPasswordReg)

        txtMarca = findViewById(R.id.txtMarcaReg)
        txtModelo = findViewById(R.id.txtModeloReg)
        txtColor = findViewById(R.id.txtColorReg)
        txtPlaca = findViewById(R.id.txtPlacaReg)

        imgOjo = findViewById(R.id.imgOjoReg)
        btnRegistrar = findViewById(R.id.btnRegistrar)
        txtLoginRegresar = findViewById(R.id.txtLoginRegresar)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        imgOjo.setOnClickListener {
            passwordVisible = !passwordVisible
            val selection = txtPassword.selectionEnd
            txtPassword.inputType = if (passwordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            txtPassword.setSelection(selection.coerceAtLeast(0))
        }

        btnRegistrar.setOnClickListener {
            validarRegistro()
        }

        txtLoginRegresar.setOnClickListener {
            finish()
        }
    }

    private fun validarRegistro() {
        val nombre = txtNombre.text.toString().trim()
        val telefono = txtTelefono.text.toString().trim()
        val correo = txtCorreo.text.toString().trim()
        val password = txtPassword.text.toString()
        val marca = txtMarca.text.toString().trim()
        val modelo = txtModelo.text.toString().trim()
        val color = txtColor.text.toString().trim()
        val placa = txtPlaca.text.toString().trim()

        txtNombre.error = null
        txtTelefono.error = null
        txtCorreo.error = null
        txtPassword.error = null
        txtMarca.error = null
        txtModelo.error = null
        txtColor.error = null
        txtPlaca.error = null

        var hasErrors = false

        if (nombre.isBlank()) {
            txtNombre.error = "Ingresa tu nombre."
            hasErrors = true
        }
        if (telefono.isBlank() || telefono.length < 10) {
            txtTelefono.error = "Ingresa un número de teléfono válido (10 dígitos)."
            hasErrors = true
        }
        if (correo.isBlank()) {
            txtCorreo.error = "Ingresa tu correo."
            hasErrors = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            txtCorreo.error = "Ingresa un correo válido."
            hasErrors = true
        }
        if (password.isBlank() || password.length < 6) {
            txtPassword.error = "La contraseña debe tener mínimo 6 caracteres."
            hasErrors = true
        }
        if (marca.isBlank()) {
            txtMarca.error = "Ingresa la marca."
            hasErrors = true
        }
        if (modelo.isBlank()) {
            txtModelo.error = "Ingresa el modelo."
            hasErrors = true
        }
        if (color.isBlank()) {
            txtColor.error = "Ingresa el color."
            hasErrors = true
        }
        if (placa.isBlank()) {
            txtPlaca.error = "Ingresa las placas del vehículo."
            hasErrors = true
        }

        if (hasErrors) return

        registrarEnFirebase(nombre, telefono, correo, password, marca, modelo, color, placa)
    }

    private fun registrarEnFirebase(
        nombre: String,
        telefono: String,
        correo: String,
        password: String,
        marca: String,
        modelo: String,
        color: String,
        placa: String
    ) {
        btnRegistrar.isEnabled = false
        Toast.makeText(this, "Registrando en K'áaxPark...", Toast.LENGTH_SHORT).show()

        repository.registerClient(
            nombre = nombre,
            telefono = telefono,
            correo = correo,
            password = password,
            marca = marca,
            modelo = modelo,
            color = color,
            placa = placa
        ) { result ->
            btnRegistrar.isEnabled = true

            result.onSuccess {
                Toast.makeText(
                    this,
                    "Cliente registrado correctamente. Ya puedes iniciar sesión.",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            }

            result.onFailure { error ->
                mostrarErrorRegistro(error)
            }
        }
    }

    private fun mostrarErrorRegistro(error: Throwable) {
        when (error) {
            is FirebaseAuthUserCollisionException -> {
                txtCorreo.error = "Ese correo ya está registrado."
                Toast.makeText(this, "Ese correo ya está en uso.", Toast.LENGTH_LONG).show()
            }
            is FirebaseAuthWeakPasswordException -> {
                txtPassword.error = "La contraseña es muy débil."
                Toast.makeText(this, "Usa una contraseña más segura.", Toast.LENGTH_LONG).show()
            }
            is FirebaseAuthInvalidCredentialsException -> {
                txtCorreo.error = "El formato del correo no es válido."
                Toast.makeText(this, "Revisa el correo ingresado.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(
                    this,
                    error.message ?: "No se pudo completar el registro.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}