package org.utl.idgs903.appkaaxpark.Cliente

import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
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
import androidx.core.view.WindowCompat
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

    private lateinit var txtErrorTelefono: TextView
    private lateinit var txtErrorCorreo: TextView
    private lateinit var txtErrorPlaca: TextView

    private lateinit var imgOjo: ImageView
    private lateinit var btnRegistrar: Button
    private lateinit var txtLoginRegresar: TextView

    private var passwordVisible = false

    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ajustar la ventana para que el contenido se desplace cuando aparezca el teclado
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        setContentView(R.layout.activity_registrar_cliente)

        txtNombre = findViewById(R.id.txtNombreReg)
        txtTelefono = findViewById(R.id.txtTelefonoReg)
        txtCorreo = findViewById(R.id.txtCorreoReg)
        txtPassword = findViewById(R.id.txtPasswordReg)

        txtMarca = findViewById(R.id.txtMarcaReg)
        txtModelo = findViewById(R.id.txtModeloReg)
        txtColor = findViewById(R.id.txtColorReg)
        txtPlaca = findViewById(R.id.txtPlacaReg)

        txtErrorTelefono = findViewById(R.id.txtErrorTelefonoReg)
        txtErrorCorreo = findViewById(R.id.txtErrorCorreoReg)
        txtErrorPlaca = findViewById(R.id.txtErrorPlacaReg)

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

        txtErrorTelefono.visibility = View.GONE
        txtErrorCorreo.visibility = View.GONE
        txtErrorPlaca.visibility = View.GONE

        var hasErrors = false

        if (nombre.isBlank()) {
            txtNombre.error = "Ingresa tu nombre completo."
            hasErrors = true
        } else if (nombre.length < 3) {
            txtNombre.error = "El nombre es demasiado corto."
            hasErrors = true
        }

        if (telefono.isBlank() || telefono.length < 10) {
            txtErrorTelefono.text = "Ingresa un número de teléfono de 10 dígitos."
            txtErrorTelefono.visibility = View.VISIBLE
            hasErrors = true
        } else if (!Regex("^[0-9]+$").matches(telefono)) {
            txtErrorTelefono.text = "El teléfono solo debe contener números."
            txtErrorTelefono.visibility = View.VISIBLE
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
            txtMarca.error = "Ingresa la marca del vehículo."
            hasErrors = true
        }

        if (color.isBlank()) {
            txtColor.error = "Ingresa el color del vehículo."
            hasErrors = true
        }

        if (placa.isBlank()) {
            txtPlaca.error = "Ingresa las placas del vehículo."
            hasErrors = true
        } else if (!validarFormatoPlaca(placa)) {
            txtPlaca.error = "Formato de placa inválido (6-10 caracteres)."
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
        // Ocultar teclado de forma segura
        val view = this.currentFocus ?: btnRegistrar
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)

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
        val msg = error.message ?: ""
        
        // Limpiar errores previos
        txtPlaca.error = null
        txtCorreo.error = null
        txtPassword.error = null
        txtErrorTelefono.visibility = View.GONE
        txtErrorCorreo.visibility = View.GONE
        txtErrorPlaca.visibility = View.GONE

        when {
            msg.contains("placa ya está registrada", ignoreCase = true) -> {
                txtErrorPlaca.text = msg
                txtErrorPlaca.visibility = View.VISIBLE
                txtPlaca.requestFocus()
            }
            msg.contains("ya está registrado", ignoreCase = true) -> {
                txtErrorCorreo.text = "Este correo ya está registrado."
                txtErrorCorreo.visibility = View.VISIBLE
                txtCorreo.requestFocus()
            }
            error is FirebaseAuthUserCollisionException -> {
                txtErrorCorreo.text = "Este correo ya está registrado."
                txtErrorCorreo.visibility = View.VISIBLE
                txtCorreo.requestFocus()
            }
            error is FirebaseAuthWeakPasswordException -> {
                txtPassword.error = "La contraseña es muy débil."
                txtPassword.requestFocus()
            }
            error is FirebaseAuthInvalidCredentialsException -> {
                txtCorreo.error = "El formato del correo no es válido."
                txtCorreo.requestFocus()
            }
            msg.contains("PERMISSION_DENIED", ignoreCase = true) -> {
                Toast.makeText(this, "Error de permisos. Revisa tus reglas de Firebase.", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(this, msg.ifBlank { "Error desconocido." }, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validarFormatoPlaca(placa: String): Boolean {
        // Regex para placas: Alfanumérico (A-Z, 0-9), opcional guion, entre 6 y 10 caracteres
        val regex = Regex("^[A-Z0-9-]{6,10}$")
        return regex.matches(placa.uppercase())
    }
}
