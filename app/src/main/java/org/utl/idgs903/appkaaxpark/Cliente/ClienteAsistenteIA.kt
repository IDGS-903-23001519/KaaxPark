package org.utl.idgs903.appkaaxpark.Cliente

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.AppConfig
import org.utl.idgs903.appkaaxpark.data.ai.AiAnswerMode
import org.utl.idgs903.appkaaxpark.data.ai.AiAssistantRepository
import org.utl.idgs903.appkaaxpark.data.ai.ParkingAiAssistantRepository
import org.utl.idgs903.appkaaxpark.data.ai.RolUsuario

class ClienteAsistenteIA : BaseActivity() {

    override fun getLayoutId(): Int = R.layout.activity_cliente_asistente_ia

    private var userDocId: String? = null
    private lateinit var assistantRepository: AiAssistantRepository

    private lateinit var txtPregunta: EditText
    private lateinit var btnEnviar: Button
    private lateinit var txtEstado: TextView
    private lateinit var contenedorMensajes: LinearLayout
    private lateinit var scrollMensajes: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageTitle("Asistente IA")

        // Asegurar que el contenido suba con el teclado
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val session = sessionManager.getSession()
        userDocId = session?.userDocId?.takeIf { it.isNotBlank() }

        val rolUsuario = when (session?.role?.uppercase()) {
            "ADMIN" -> RolUsuario.ADMIN
            else -> RolUsuario.CLIENTE
        }

        assistantRepository = if (userDocId != null) {
            ParkingAiAssistantRepository(userDocId!!, rolUsuario)
        } else {
            ParkingAiAssistantRepository(rol = rolUsuario)
        }

        txtPregunta = findViewById(R.id.txtPreguntaAI)
        btnEnviar = findViewById(R.id.btnEnviarAI)
        txtEstado = findViewById(R.id.txtEstadoAI)
        contenedorMensajes = findViewById(R.id.contenedorMensajesAI)
        scrollMensajes = findViewById(R.id.scrollMensajesAI)
        val cardInputAI = findViewById<View>(R.id.cardInputAI)

        // Ajustar posición justa del campo de texto pegado al teclado (sin espacio sobrante)
        var originalCardBottomY = 0
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { rootView, insets ->
            val imeInset = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom

            if (imeInset <= 0) {
                cardInputAI.translationY = 0f
                scrollMensajes.setPadding(
                    scrollMensajes.paddingLeft,
                    scrollMensajes.paddingTop,
                    scrollMensajes.paddingRight,
                    0
                )
                originalCardBottomY = 0
            } else {
                val rootHeight = rootView.height
                val keyboardTop = rootHeight - imeInset

                if (originalCardBottomY == 0) {
                    val location = IntArray(2)
                    cardInputAI.getLocationOnScreen(location)
                    originalCardBottomY = location[1] + cardInputAI.height - cardInputAI.translationY.toInt()
                }

                val marginPx = (8 * resources.displayMetrics.density).toInt()
                val overlap = originalCardBottomY - keyboardTop + marginPx
                val shiftUp = overlap.coerceAtLeast(0)

                cardInputAI.translationY = -shiftUp.toFloat()
                scrollMensajes.setPadding(
                    scrollMensajes.paddingLeft,
                    scrollMensajes.paddingTop,
                    scrollMensajes.paddingRight,
                    shiftUp
                )
                scrollMensajes.post { scrollMensajes.fullScroll(View.FOCUS_DOWN) }
            }
            insets
        }

        txtPregunta.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                scrollMensajes.postDelayed({ scrollMensajes.fullScroll(View.FOCUS_DOWN) }, 250)
            }
        }
        txtPregunta.setOnClickListener {
            scrollMensajes.postDelayed({ scrollMensajes.fullScroll(View.FOCUS_DOWN) }, 250)
        }

        txtEstado.text = if (AppConfig.aiBackendEnabled) {
            "Backend IA habilitado."
        } else {
            "Motor local activo. Responde con datos reales de Firebase."
        }

        agregarBurbuja(
            texto = getString(R.string.ai_client_greeting),
            esUsuario = false
        )

        btnEnviar.setOnClickListener { enviarPregunta() }
        txtPregunta.setOnEditorActionListener { _, actionId, event ->
            val pressedSend = actionId == EditorInfo.IME_ACTION_SEND
            val pressedEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (pressedSend || pressedEnter) {
                enviarPregunta()
                true
            } else {
                false
            }
        }
    }

    private fun enviarPregunta() {
        val pregunta = txtPregunta.text.toString().trim()
        if (pregunta.isBlank()) {
            txtPregunta.error = "Escribe una pregunta."
            return
        }

        agregarBurbuja(pregunta, esUsuario = true)
        txtPregunta.text?.clear()
        setLoadingState(true)
        txtEstado.text = "Consultando datos reales..."

        assistantRepository.ask(pregunta) { resultado ->
            runOnUiThread {
                setLoadingState(false)
                resultado.onSuccess { reply ->
                    txtEstado.text = when (reply.mode) {
                        AiAnswerMode.REMOTE -> "Respuesta generada desde tu backend."
                        AiAnswerMode.LOCAL -> "Respuesta generada con datos reales de Firebase."
                    }
                    agregarBurbuja(reply.answer, esUsuario = false)
                }
                resultado.onFailure { error ->
                    txtEstado.text = "No fue posible consultar la IA."
                    agregarBurbuja(
                        error.message ?: getString(R.string.ai_error_generic),
                        esUsuario = false
                    )
                }
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        btnEnviar.isEnabled = !isLoading
        txtPregunta.isEnabled = !isLoading
        btnEnviar.text = if (isLoading) getString(R.string.ai_loading) else getString(R.string.ai_send)
    }

    private fun agregarBurbuja(texto: String, esUsuario: Boolean) {
        val fila = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = if (esUsuario) Gravity.END else Gravity.START
        }

        val burbuja = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = texto
            textSize = 14f
            setTextColor(if (esUsuario) Color.BLACK else Color.WHITE)
            maxWidth = (resources.displayMetrics.widthPixels * 0.76f).toInt()
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = ContextCompat.getDrawable(
                this@ClienteAsistenteIA,
                if (esUsuario) R.drawable.bg_boton_dorado_solido else R.drawable.bg_dialog
            )
        }

        fila.addView(burbuja)
        contenedorMensajes.addView(fila)
        scrollMensajes.post { scrollMensajes.fullScroll(View.FOCUS_DOWN) }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}