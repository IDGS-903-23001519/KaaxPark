package org.utl.idgs903.appkaaxpark.Admin

import android.os.Bundle
import android.widget.TextView
import org.utl.idgs903.appkaaxpark.R
import org.utl.idgs903.appkaaxpark.data.FirebaseRepository
import org.utl.idgs903.appkaaxpark.data.SessionManager

class Dashboard : BaseAdminActivity() {

    private lateinit var repository: FirebaseRepository
    private lateinit var sessionManager: SessionManager

    override fun getLayoutId(): Int = R.layout.activity_dashboard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FirebaseRepository()
        sessionManager = SessionManager(this)
    }

    override fun onStart() {
        super.onStart()
        loadAdminGreeting()
    }

    private fun loadAdminGreeting() {
        val session = sessionManager.getSession() ?: return
        repository.fetchUserProfileByDocumentId(session.userDocId) { result ->
            result.onSuccess { profile ->
                findViewById<TextView>(R.id.lblBienvenido)?.text = "Bienvenido, ${profile.displayName}"
            }
        }
    }
}
