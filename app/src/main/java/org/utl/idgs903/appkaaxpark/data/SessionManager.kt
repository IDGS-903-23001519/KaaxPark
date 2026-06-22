package org.utl.idgs903.appkaaxpark.data

import android.content.Context

data class UserSession(
    val firebaseAuthUid: String,
    val email: String,
    val role: String,
    val userDocId: String
)

class SessionManager(context: Context) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun saveSession(firebaseAuthUid: String, profile: UserProfile) {
        preferences.edit()
            .putString(KEY_FIREBASE_UID, firebaseAuthUid)
            .putString(KEY_EMAIL, profile.email)
            .putString(KEY_ROLE, profile.role)
            .putString(KEY_USER_DOC_ID, profile.documentId)
            .apply()
    }

    fun getSession(): UserSession? {
        val firebaseAuthUid = preferences.getString(KEY_FIREBASE_UID, null).orEmpty()
        val email = preferences.getString(KEY_EMAIL, null).orEmpty()
        val role = preferences.getString(KEY_ROLE, null).orEmpty()
        val userDocId = preferences.getString(KEY_USER_DOC_ID, null).orEmpty()

        if (firebaseAuthUid.isBlank() || email.isBlank() || role.isBlank() || userDocId.isBlank()) {
            return null
        }

        return UserSession(
            firebaseAuthUid = firebaseAuthUid,
            email = email,
            role = role,
            userDocId = userDocId
        )
    }

    fun clearSession() {
        preferences.edit().clear().apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "kaaxpark_session"
        private const val KEY_FIREBASE_UID = "firebase_auth_uid"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_USER_DOC_ID = "user_doc_id"
    }
}
