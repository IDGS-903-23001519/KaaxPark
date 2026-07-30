package org.utl.idgs903.appkaaxpark

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import org.utl.idgs903.appkaaxpark.Admin.Dashboard
import org.utl.idgs903.appkaaxpark.Cliente.Codigoqr
import org.utl.idgs903.appkaaxpark.Cliente.RegistrarCliente
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.InactiveUserException
import org.utl.idgs903.appkaaxpark.data.SessionManager
import org.utl.idgs903.appkaaxpark.data.UnsupportedRoleException
import org.utl.idgs903.appkaaxpark.data.UserProfile
import org.utl.idgs903.appkaaxpark.data.UserProfileNotFoundException

class MainActivity : AppCompatActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var txtUsuario: EditText
    private lateinit var txtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var imgOjo: ImageView
    private lateinit var progressLogin: ProgressBar
    private lateinit var btnBiometricoContainer: LinearLayout

    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView)?.apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        AppCompatDelegate.setDefaultNightMode(
            AppCompatDelegate.MODE_NIGHT_YES
        )
        setContentView(R.layout.activity_main)

        repository = FirebaseRepository()
        sessionManager = SessionManager(this)
        txtUsuario = findViewById(R.id.txtUsuario)
        txtPassword = findViewById(R.id.txtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        imgOjo = findViewById(R.id.imgOjo)
        progressLogin = findViewById(R.id.progressLogin)
        btnBiometricoContainer = findViewById(R.id.btnBiometricoContainer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
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

        btnLogin.setOnClickListener {
            signIn()
        }

        val txtOlvidastePassword = findViewById<TextView>(R.id.txtOlvidastePassword)
        txtOlvidastePassword?.setOnClickListener {
            mostrarDialogoRecuperarPassword()
        }

        val txtRegistro = findViewById<TextView>(R.id.txtRegistro)
        txtRegistro.setOnClickListener {
            val intent = Intent(this, RegistrarCliente::class.java)
            startActivity(intent)
        }

        // Limpiar errores en tiempo real al escribir
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                txtUsuario.error = null
                txtPassword.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        txtUsuario.addTextChangedListener(textWatcher)
        txtPassword.addTextChangedListener(textWatcher)

        // Configurar autenticación biométrica (Huella / FaceID)
        configurarBiometria()

        // Animación suave de entrada
        val cardLogin = findViewById<CardView>(R.id.cardLogin)
        cardLogin?.alpha = 0f
        cardLogin?.translationY = 40f
        cardLogin?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(500)?.start()
    }

    private fun configurarBiometria() {
        val cachedSession = sessionManager.getSession()
        if (isBiometricAvailable() && cachedSession != null && cachedSession.role.isNotBlank()) {
            btnBiometricoContainer.visibility = View.VISIBLE
            btnBiometricoContainer.setOnClickListener {
                solicitarAutenticacionBiometrica()
            }
        } else {
            btnBiometricoContainer.visibility = View.GONE
        }
    }

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        val result = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun solicitarAutenticacionBiometrica() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    val session = sessionManager.getSession()
                    if (session != null) {
                        val profile = UserProfile(
                            documentId = session.userDocId,
                            email = session.email,
                            name = "",
                            username = "",
                            phone = "",
                            role = session.role,
                            active = true
                        )
                        navigateToRoleHome(profile)
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Sesión no encontrada. Inicia sesión con contraseña.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(
                            this@MainActivity,
                            "Error biométrico: $errString",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Autenticación Biométrica")
            .setSubtitle("Usa tu huella digital o rostro para acceder a K'áax Park")
            .setNegativeButtonText("Usar Contraseña")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    override fun onStart() {
        super.onStart()
        restoreSessionIfPossible()
    }

    private fun restoreSessionIfPossible() {
        if (!repository.isAuthenticated()) {
            if (sessionManager.getSession() != null) {
                sessionManager.clearSession()
            }
            configurarBiometria()
            return
        }

        val cachedSession = sessionManager.getSession()
        if (cachedSession != null && cachedSession.role.isNotBlank()) {
            val cachedProfile = UserProfile(
                documentId = cachedSession.userDocId,
                email = cachedSession.email,
                name = "",
                username = "",
                phone = "",
                role = cachedSession.role,
                active = true
            )
            navigateToRoleHome(cachedProfile)
            return
        }

        setLoadingState(isLoading = true)
        repository.restoreUserProfile(null) { result ->
            setLoadingState(isLoading = false)
            result.onSuccess { profile ->
                sessionManager.saveSession(repository.getCurrentUserUid().orEmpty(), profile)
                navigateToRoleHome(profile)
            }
            result.onFailure {
                repository.signOut()
                sessionManager.clearSession()
                Toast.makeText(
                    this,
                    "No se pudo restaurar tu sesion. Inicia sesion nuevamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun signIn() {
        val email = txtUsuario.text.toString().trim()
        val password = txtPassword.text.toString()

        txtUsuario.error = null
        txtPassword.error = null

        var hasErrors = false
        if (email.isBlank()) {
            txtUsuario.error = "Ingresa tu correo."
            hasErrors = true
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            txtUsuario.error = "Ingresa un correo valido."
            hasErrors = true
        }

        if (password.isBlank()) {
            txtPassword.error = "Ingresa tu contrasena."
            hasErrors = true
        }

        if (hasErrors) {
            return
        }

        setLoadingState(isLoading = true)
        repository.signIn(email, password) { result ->
            setLoadingState(isLoading = false)
            result.onSuccess { profile ->
                sessionManager.saveSession(repository.getCurrentUserUid().orEmpty(), profile)
                navigateToRoleHome(profile)
            }
            result.onFailure { error ->
                sessionManager.clearSession()
                showLoginError(error)
            }
        }
    }

    private fun mostrarDialogoRecuperarPassword() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_recuperar_password, null)
        val txtEmailRecuperacion = dialogView.findViewById<EditText>(R.id.txtEmailRecuperacion)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarRecuperacion)
        val btnEnviar = dialogView.findViewById<Button>(R.id.btnEnviarRecuperacion)

        val currentEmail = txtUsuario.text.toString().trim()
        if (currentEmail.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
            txtEmailRecuperacion.setText(currentEmail)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancelar.setOnClickListener {
            dialog.dismiss()
        }

        btnEnviar.setOnClickListener {
            val emailRecuperacion = txtEmailRecuperacion.text.toString().trim()
            if (emailRecuperacion.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(emailRecuperacion).matches()) {
                txtEmailRecuperacion.error = "Ingresa un correo válido"
                return@setOnClickListener
            }

            btnEnviar.isEnabled = false
            btnEnviar.text = "Enviando..."
            repository.sendPasswordResetEmail(emailRecuperacion) { result ->
                runOnUiThread {
                    dialog.dismiss()
                    result.onSuccess {
                        Toast.makeText(
                            this,
                            "Correo de recuperación enviado. Revisa tu bandeja de entrada.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    result.onFailure { error ->
                        Toast.makeText(
                            this,
                            "Error al enviar correo: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun navigateToRoleHome(profile: UserProfile) {
        val destination = when {
            profile.role.equals("ADMIN", ignoreCase = true) -> Dashboard::class.java
            profile.role.equals("CLIENTE", ignoreCase = true) -> Codigoqr::class.java
            else -> {
                repository.signOut()
                sessionManager.clearSession()
                Toast.makeText(this, "El rol del usuario no es valido.", Toast.LENGTH_LONG).show()
                return
            }
        }

        val intent = Intent(this, destination).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun setLoadingState(isLoading: Boolean) {
        btnLogin.isEnabled = !isLoading
        txtUsuario.isEnabled = !isLoading
        txtPassword.isEnabled = !isLoading
        imgOjo.isEnabled = !isLoading
        btnBiometricoContainer.isEnabled = !isLoading

        if (isLoading) {
            btnLogin.text = ""
            progressLogin.visibility = View.VISIBLE
        } else {
            btnLogin.text = "Iniciar Sesión"
            progressLogin.visibility = View.GONE
        }
    }

    private fun showLoginError(error: Throwable) {
        repository.signOut()
        val message = when (error) {
            is FirebaseAuthInvalidCredentialsException -> "Correo o contrasena incorrectos."
            is FirebaseAuthInvalidUserException -> "No existe una cuenta registrada con ese correo."
            is FirebaseTooManyRequestsException -> "Se bloquearon temporalmente los intentos. Intenta mas tarde."
            is FirebaseNetworkException -> "No se pudo conectar con Firebase. Revisa tu internet."
            is UserProfileNotFoundException -> "El usuario existe en Authentication, pero no tiene perfil en Firestore."
            is InactiveUserException -> "Tu usuario esta inactivo. Consulta con un administrador."
            is UnsupportedRoleException -> error.message ?: "El rol del usuario no esta soportado."
            else -> error.message ?: "No fue posible iniciar sesion."
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
