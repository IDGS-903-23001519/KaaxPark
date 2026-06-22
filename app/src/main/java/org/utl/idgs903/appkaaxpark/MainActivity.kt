package org.utl.idgs903.appkaaxpark

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import org.utl.idgs903.appkaaxpark.Admin.Dashboard
import org.utl.idgs903.appkaaxpark.Cliente.EstanciaVehiculo
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

    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            return
        }

        setLoadingState(isLoading = true, loadingLabel = "Restaurando...")
        repository.restoreUserProfile(sessionManager.getSession()) { result ->
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

        setLoadingState(isLoading = true, loadingLabel = "Validando...")
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

    private fun navigateToRoleHome(profile: UserProfile) {
        val destination = when {
            profile.role.equals("ADMIN", ignoreCase = true) -> Dashboard::class.java
            profile.role.equals("CLIENTE", ignoreCase = true) -> EstanciaVehiculo::class.java
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

    private fun setLoadingState(isLoading: Boolean, loadingLabel: String = "Validando...") {
        btnLogin.isEnabled = !isLoading
        txtUsuario.isEnabled = !isLoading
        txtPassword.isEnabled = !isLoading
        imgOjo.isEnabled = !isLoading
        btnLogin.text = if (isLoading) loadingLabel else "Iniciar sesion"
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
