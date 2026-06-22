package org.utl.idgs903.appkaaxpark.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

data class UserProfile(
    val documentId: String,
    val email: String,
    val name: String,
    val phone: String,
    val role: String,
    val status: String
) {
    val isActive: Boolean
        get() = status.equals("ACTIVO", ignoreCase = true)

    val displayName: String
        get() = if (name.isBlank()) email else name
}

data class ActiveStay(
    val documentId: String,
    val userId: String,
    val vehicleId: String,
    val status: String,
    val entryTimestamp: Timestamp
)

data class VehicleInfo(
    val documentId: String,
    val brand: String,
    val plate: String,
    val color: String
)

data class ClientStayDetails(
    val stay: ActiveStay,
    val vehicle: VehicleInfo
)

class UserProfileNotFoundException : Exception("No existe un perfil de usuario en Firestore para este correo.")

class InactiveUserException : Exception("Tu usuario está inactivo. Consulta con un administrador.")

class UnsupportedRoleException(role: String) :
    Exception("El rol '$role' no está soportado por la aplicación.")

class MissingAuthenticatedUserException :
    Exception("No hay una sesión autenticada en Firebase.")

class FirebaseRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun isAuthenticated(): Boolean = auth.currentUser != null

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    fun getCurrentUserEmail(): String? = auth.currentUser?.email

    fun signOut() {
        auth.signOut()
    }

    fun signIn(email: String, password: String, callback: (Result<UserProfile>) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                fetchUserProfileByEmail(email) { result ->
                    result.onSuccess { callback(Result.success(it)) }
                    result.onFailure { error ->
                        auth.signOut()
                        callback(Result.failure(error))
                    }
                }
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    fun restoreUserProfile(
        session: UserSession?,
        callback: (Result<UserProfile>) -> Unit
    ) {
        val currentEmail = getCurrentUserEmail()
        if (currentEmail.isNullOrBlank()) {
            callback(Result.failure(MissingAuthenticatedUserException()))
            return
        }

        val cachedDocumentId = session?.userDocId?.takeIf { it.isNotBlank() }
        if (cachedDocumentId.isNullOrBlank()) {
            fetchUserProfileByEmail(currentEmail, callback)
            return
        }

        fetchUserProfileByDocumentId(cachedDocumentId) { result ->
            result.onSuccess { profile ->
                if (profile.email.equals(currentEmail, ignoreCase = true)) {
                    callback(Result.success(profile))
                } else {
                    fetchUserProfileByEmail(currentEmail, callback)
                }
            }
            result.onFailure {
                fetchUserProfileByEmail(currentEmail, callback)
            }
        }
    }

    fun fetchUserProfileByDocumentId(
        documentId: String,
        callback: (Result<UserProfile>) -> Unit
    ) {
        firestore.collection("usuarios")
            .document(documentId)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.toUserProfile()
                if (profile == null) {
                    callback(Result.failure(UserProfileNotFoundException()))
                    return@addOnSuccessListener
                }

                if (!profile.isActive) {
                    callback(Result.failure(InactiveUserException()))
                    return@addOnSuccessListener
                }

                if (!profile.role.equals("ADMIN", ignoreCase = true)
                    && !profile.role.equals("CLIENTE", ignoreCase = true)
                ) {
                    callback(Result.failure(UnsupportedRoleException(profile.role)))
                    return@addOnSuccessListener
                }

                callback(Result.success(profile))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    fun fetchClientStayDetails(
        userDocumentId: String,
        callback: (Result<ClientStayDetails?>) -> Unit
    ) {
        firestore.collection("estancias")
            .whereEqualTo("usuarioId", userDocumentId)
            .get()
            .addOnSuccessListener { snapshot ->
                val activeStayDocument = snapshot.documents.firstOrNull {
                    it.getString("estatus").equals("ACTIVA", ignoreCase = true)
                }

                if (activeStayDocument == null) {
                    callback(Result.success(null))
                    return@addOnSuccessListener
                }

                val activeStay = activeStayDocument.toActiveStay()
                if (activeStay == null) {
                    callback(Result.failure(IllegalStateException("La estancia activa no tiene los campos requeridos.")))
                    return@addOnSuccessListener
                }

                fetchVehicleByDocumentId(activeStay.vehicleId) { vehicleResult ->
                    vehicleResult.onSuccess { vehicle ->
                        callback(Result.success(ClientStayDetails(activeStay, vehicle)))
                    }
                    vehicleResult.onFailure { error ->
                        callback(Result.failure(error))
                    }
                }
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    private fun fetchUserProfileByEmail(
        email: String,
        callback: (Result<UserProfile>) -> Unit
    ) {
        firestore.collection("usuarios")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.documents.firstOrNull()?.toUserProfile()
                if (profile == null) {
                    callback(Result.failure(UserProfileNotFoundException()))
                    return@addOnSuccessListener
                }

                if (!profile.isActive) {
                    callback(Result.failure(InactiveUserException()))
                    return@addOnSuccessListener
                }

                if (!profile.role.equals("ADMIN", ignoreCase = true)
                    && !profile.role.equals("CLIENTE", ignoreCase = true)
                ) {
                    callback(Result.failure(UnsupportedRoleException(profile.role)))
                    return@addOnSuccessListener
                }

                callback(Result.success(profile))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    private fun fetchVehicleByDocumentId(
        vehicleDocumentId: String,
        callback: (Result<VehicleInfo>) -> Unit
    ) {
        firestore.collection("vehiculos")
            .document(vehicleDocumentId)
            .get()
            .addOnSuccessListener { snapshot ->
                val vehicle = snapshot.toVehicleInfo()
                if (vehicle == null) {
                    callback(Result.failure(IllegalStateException("No se encontró el vehículo asociado a la estancia.")))
                    return@addOnSuccessListener
                }

                callback(Result.success(vehicle))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) {
            return null
        }

        val email = getString("email").orEmpty().trim()
        val role = getString("rol").orEmpty().trim().uppercase()
        val status = getString("estado").orEmpty().trim().uppercase()

        if (email.isBlank() || role.isBlank() || status.isBlank()) {
            return null
        }

        return UserProfile(
            documentId = id,
            email = email,
            name = getString("nombre").orEmpty().trim(),
            phone = getString("telefono").orEmpty().trim(),
            role = role,
            status = status
        )
    }

    private fun DocumentSnapshot.toActiveStay(): ActiveStay? {
        if (!exists()) {
            return null
        }

        val userId = getString("usuarioId").orEmpty().trim()
        val vehicleId = getString("vehiculoId").orEmpty().trim()
        val status = getString("estatus").orEmpty().trim().uppercase()
        val entryTimestamp = getTimestamp("fechaEntrada")

        if (userId.isBlank() || vehicleId.isBlank() || status.isBlank() || entryTimestamp == null) {
            return null
        }

        return ActiveStay(
            documentId = id,
            userId = userId,
            vehicleId = vehicleId,
            status = status,
            entryTimestamp = entryTimestamp
        )
    }

    private fun DocumentSnapshot.toVehicleInfo(): VehicleInfo? {
        if (!exists()) {
            return null
        }

        val brand = getString("marca").orEmpty().trim()
        val plate = getString("placa").orEmpty().trim()
        val color = getString("color").orEmpty().trim()

        if (brand.isBlank() || plate.isBlank() || color.isBlank()) {
            return null
        }

        return VehicleInfo(
            documentId = id,
            brand = brand,
            plate = plate,
            color = color
        )
    }
}
