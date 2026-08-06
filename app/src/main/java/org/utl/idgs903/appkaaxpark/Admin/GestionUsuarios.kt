package org.utl.idgs903.appkaaxpark.Admin

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.UserProfile
import org.utl.idgs903.appkaaxpark.global.InfoUsuario

class GestionUsuarios : BaseAdminActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var txtBuscarUsuario: EditText
    private lateinit var layoutListaUsuarios: LinearLayout
    private lateinit var lblTotalUsuarios: TextView
    private lateinit var lblUsuariosActivos: TextView
    private lateinit var lblUsuariosInactivos: TextView

    private var todosLosUsuarios: List<UserProfile> = emptyList()

    override fun getLayoutId(): Int = R.layout.activity_gestion_usuarios

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        repository = FirebaseRepository()

        txtBuscarUsuario = findViewById(R.id.txtBuscarUsuario)
        // El ScrollView tiene un LinearLayout interno sin ID en el XML proporcionado.
        // Vamos a buscar el LinearLayout dentro del ScrollView.
        layoutListaUsuarios = findViewById<View>(R.id.scrollUsuarios).let {
            if (it is android.widget.ScrollView) it.getChildAt(0) as LinearLayout
            else findViewById(R.id.scrollUsuarios) // Fallback si no es scroll
        }

        lblTotalUsuarios = findViewById(R.id.lblTotalUsuarios)
        lblUsuariosActivos = findViewById(R.id.lblUsuariosActivos)
        lblUsuariosInactivos = findViewById(R.id.lblUsuariosInactivos)

        txtBuscarUsuario.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filtrarYRenderizar(s?.toString().orEmpty())
            }
        })

        findViewById<View>(R.id.btnAgregarUsuario)?.setOnClickListener {
            Toast.makeText(this, "Funcionalidad de agregar usuario próximamente.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStart() {
        super.onStart()
        cargarUsuarios()
    }

    private fun cargarUsuarios() {
        repository.fetchAllUsers { result ->
            result.onSuccess { lista ->
                todosLosUsuarios = lista
                actualizarContadores()
                filtrarYRenderizar(txtBuscarUsuario.text.toString())
            }
            result.onFailure { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun actualizarContadores() {
        val total = todosLosUsuarios.size
        val activos = todosLosUsuarios.count { it.isActive }
        val inactivos = total - activos

        lblTotalUsuarios.text = total.toString()
        lblUsuariosActivos.text = activos.toString()
        lblUsuariosInactivos.text = inactivos.toString()
    }

    private fun filtrarYRenderizar(query: String) {
        val filtrados = if (query.isBlank()) {
            todosLosUsuarios
        } else {
            todosLosUsuarios.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.email.contains(query, ignoreCase = true) ||
                it.role.contains(query, ignoreCase = true)
            }
        }
        renderLista(filtrados)
    }

    private fun renderLista(lista: List<UserProfile>) {
        layoutListaUsuarios.removeAllViews()
        val inflater = LayoutInflater.from(this)

        lista.forEach { usuario ->
            val item = inflater.inflate(R.layout.item_usuario, layoutListaUsuarios, false)

            item.findViewById<TextView>(R.id.lblNombreUsuario).text = usuario.displayName
            item.findViewById<TextView>(R.id.lblCorreoUsuario).text = usuario.email
            item.findViewById<TextView>(R.id.lblRolTag).text = "Rol: ${usuario.role}"

            val dot = item.findViewById<View>(R.id.dotEstadoFila)
            val lblEstado = item.findViewById<TextView>(R.id.lblEstadoTag)
            val color = if (usuario.isActive) "#2ECC71" else "#E74C3C"

            lblEstado.text = usuario.status
            lblEstado.setTextColor(Color.parseColor(color))
            (dot.background.mutate() as? GradientDrawable)?.setColor(Color.parseColor(color))

            item.setOnClickListener {
                // Podríamos abrir InfoUsuario pasando el ID del usuario seleccionado
                // Pero InfoUsuario actualmente solo muestra el perfil del usuario logueado.
                Toast.makeText(this, "Usuario: ${usuario.name}", Toast.LENGTH_SHORT).show()
            }

            layoutListaUsuarios.addView(item)
        }
    }
}
