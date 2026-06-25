package org.utl.idgs903.appkaaxpark.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

data class UserProfile(
    val documentId: String,
    val email: String,
    val name: String,
    val username: String,
    val role: String,
    val active: Boolean
) {
    val isActive: Boolean
        get() = active

    val status: String
        get() = if (active) "ACTIVO" else "INACTIVO"

    val displayName: String
        get() = if (name.isBlank()) email else name
}

data class ActiveStay(
    val documentId: String,
    val userId: String,
    val vehicleId: String,
    val assignedSpotId: String,
    val status: String,
    val entryTimestamp: Timestamp,
    val paymentStatus: String
) {
    val isPaid: Boolean
        get() = paymentStatus.equals("PAGADA", ignoreCase = true)
}

data class VehicleInfo(
    val documentId: String,
    val brand: String,
    val model: String,
    val plate: String,
    val color: String,
    val isActive: Boolean = false
)

data class ClientStayDetails(
    val stay: ActiveStay,
    val vehicle: VehicleInfo
)

data class VisitHistoryItem(
    val documentId: String,
    val cajonId: String,
    val fechaEntrada: Timestamp,
    val fechaSalida: Timestamp?,
    val subtotal: Double,
    val iva: Double,
    val montoTotal: Double,
    val metodoPago: String
)

data class EstanciaResumen(
    val documentId: String,
    val usuarioId: String,
    val cajonId: String,
    val estatus: String,
    val fechaEntrada: Timestamp,
    val fechaSalida: Timestamp?
)

data class SustentabilidadInfo(
    val aguaCaptadaLitros: Double,
    val aguaUsadaRiegoLitros: Double,
    val energiaGeneradaKwh: Double,
    val nivelTanquePorcentaje: Double,
    val porcentajeSolar: Double,
    val bombaAguaEncendida: Boolean,
    val alertas: Map<String, Any?>
)

data class CajonInfo(
    val documentId: String,
    val nivel: Int,
    val numeroCajon: Int,
    val estado: String
) {
    val codigo: String
        get() = "N${nivel}C$numeroCajon"

    val isLibre: Boolean
        get() = estado.equals("Libre", ignoreCase = true)
}

class UserProfileNotFoundException : Exception("No existe un perfil de usuario en Firestore para este correo.")

class InactiveUserException : Exception("Tu usuario está inactivo. Consulta con un administrador.")

class UnsupportedRoleException(role: String) :
    Exception("El rol '$role' no está soportado por la aplicación.")

class MissingAuthenticatedUserException :
    Exception("No hay una sesión autenticada en Firebase.")

class CajonNoEncontradoException : Exception("Ese código QR no corresponde a ningún cajón registrado.")

class CajonOcupadoException : Exception("Ese cajón ya está ocupado por otro vehículo.")

class VehiculoNoEncontradoException : Exception("No se encontró un vehículo en uso. Selecciona uno en 'Mis vehículos'.")

class EstanciaActivaExistenteException : Exception("Ya tienes una estancia activa.")

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
                val resolvedEmail = auth.currentUser?.email ?: email
                fetchUserProfileByEmail(resolvedEmail) { result ->
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
        firestore.collection("usuariosc")
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

    fun fetchCajones(callback: (Result<List<CajonInfo>>) -> Unit) {
        firestore.collection("cajones-dev")
            .get()
            .addOnSuccessListener { snapshot ->
                val cajones = snapshot.documents
                    .mapNotNull { it.toCajonInfo() }
                    .sortedWith(compareBy({ it.nivel }, { it.numeroCajon }))

                callback(Result.success(cajones))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    fun fetchSustentabilidad(callback: (Result<SustentabilidadInfo?>) -> Unit) {
        firestore.collection("sustentabilidad")
            .document("actual")
            .get()
            .addOnSuccessListener { snapshot ->
                callback(Result.success(snapshot.toSustentabilidadInfo()))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    fun fetchEstancias(callback: (Result<List<EstanciaResumen>>) -> Unit) {
        firestore.collection("estancias")
            .get()
            .addOnSuccessListener { snapshot ->
                callback(Result.success(snapshot.documents.mapNotNull { it.toEstanciaResumen() }))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    fun fetchCajonOccupancy(
        cajonId: String,
        callback: (Result<ClientStayDetails?>) -> Unit
    ) {
        firestore.collection("estancias")
            .whereEqualTo("cajonId", cajonId)
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

    fun fetchVisitHistory(
        usuarioId: String,
        callback: (Result<List<VisitHistoryItem>>) -> Unit
    ) {
        firestore.collection("estancias")
            .whereEqualTo("usuarioId", usuarioId)
            .whereEqualTo("estatus", "FINALIZADA")
            .get()
            .addOnSuccessListener { snapshot ->
                val visitas = snapshot.documents
                    .mapNotNull { it.toVisitHistoryItem() }
                    .sortedByDescending { it.fechaSalida?.seconds ?: it.fechaEntrada.seconds }
                callback(Result.success(visitas))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    // --- Registro de cajón (escaneo de QR) ---

    /**
     * Asigna un cajón al vehículo [vehiculoId] que el cliente eligió después
     * de escanear el código. El vehículo ya viene determinado desde la UI
     * (Codigoqr), por lo que aquí ya no se busca "el vehículo en uso".
     */
    fun assignParkingSpot(
        usuarioId: String,
        codigoCajon: String,
        vehiculoId: String,
        callback: (Result<String>) -> Unit
    ) {
        firestore.collection("estancias")
            .whereEqualTo("usuarioId", usuarioId)
            .get()
            .addOnSuccessListener { snapshot ->
                val yaTieneActiva = snapshot.documents.any {
                    it.getString("estatus").equals("ACTIVA", ignoreCase = true)
                }
                if (yaTieneActiva) {
                    callback(Result.failure(EstanciaActivaExistenteException()))
                    return@addOnSuccessListener
                }

                runAssignmentTransaction(usuarioId, vehiculoId, codigoCajon, callback)
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    private fun runAssignmentTransaction(
        usuarioId: String,
        vehiculoId: String,
        codigoCajon: String,
        callback: (Result<String>) -> Unit
    ) {
        val cajonRef = firestore.collection("cajones-dev").document(codigoCajon)
        val estanciaRef = firestore.collection("estancias").document()

        firestore.runTransaction<String> { transaction ->
            val cajonSnapshot = transaction.get(cajonRef)
            if (!cajonSnapshot.exists()) {
                throw CajonNoEncontradoException()
            }

            val estadoActual = cajonSnapshot.getString("estado").orEmpty()
            if (!estadoActual.equals("Libre", ignoreCase = true)) {
                throw CajonOcupadoException()
            }

            transaction.update(cajonRef, "estado", "Ocupado")

            val estancia = hashMapOf(
                "usuarioId" to usuarioId,
                "vehiculoId" to vehiculoId,
                "cajonId" to codigoCajon,
                "estatus" to "ACTIVA",
                "estatusPago" to "PENDIENTE",
                "fechaEntrada" to Timestamp.now()
            )
            transaction.set(estanciaRef, estancia)

            codigoCajon
        }
            .addOnSuccessListener { assignedSpot ->
                callback(Result.success(assignedSpot))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    // --- Pago de la estancia ---

    fun registerPayment(
        estanciaId: String,
        metodoPago: String,
        subtotal: Double,
        iva: Double,
        monto: Double,
        callback: (Result<Unit>) -> Unit
    ) {
        val datosPago: HashMap<String, Any> = hashMapOf(
            "estatusPago" to "PAGADA",
            "metodoPago" to metodoPago,
            "subtotal" to subtotal,
            "iva" to iva,
            "montoTotal" to monto,
            "fechaPago" to Timestamp.now()
        )

        firestore.collection("estancias")
            .document(estanciaId)
            .update(datosPago)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    // --- Finalizar estancia (recuperar vehículo) ---

    fun finalizeStay(
        estanciaId: String,
        cajonId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        val estanciaRef = firestore.collection("estancias").document(estanciaId)
        val cajonRef = firestore.collection("cajones-dev").document(cajonId)

        firestore.runTransaction<Unit> { transaction ->
            transaction.update(
                estanciaRef,
                mapOf(
                    "estatus" to "FINALIZADA",
                    "fechaSalida" to Timestamp.now()
                )
            )
            transaction.update(cajonRef, "estado", "Libre")
            Unit
        }
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    // --- Vehículos del cliente (varios vehículos por cuenta + vehículo en uso) ---

    /**
     * Devuelve todos los vehículos NO eliminados registrados por el usuario,
     * con el vehículo marcado como "activo" (en uso) primero en la lista.
     */
    fun fetchVehiclesByUserId(
        usuarioId: String,
        callback: (Result<List<VehicleInfo>>) -> Unit
    ) {
        firestore.collection("vehiculos")
            .whereEqualTo("usuarioId", usuarioId)
            .get()
            .addOnSuccessListener { snapshot ->
                val vehiculos = snapshot.documents
                    .filter { !it.estaEliminado() }
                    .mapNotNull { it.toVehicleInfo() }
                    .sortedByDescending { it.isActive }
                callback(Result.success(vehiculos))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    /**
     * Agrega un nuevo vehículo a la cuenta del usuario. Si es el primer
     * vehículo (sin contar los eliminados) que registra, se marca
     * automáticamente como "en uso".
     */
    fun addVehicle(
        usuarioId: String,
        marca: String,
        modelo: String,
        color: String,
        placa: String,
        callback: (Result<Unit>) -> Unit
    ) {
        firestore.collection("vehiculos")
            .whereEqualTo("usuarioId", usuarioId)
            .get()
            .addOnSuccessListener { snapshot ->
                val vehiculosVigentes = snapshot.documents.filter { !it.estaEliminado() }
                val esElPrimerVehiculo = vehiculosVigentes.isEmpty()
                val nuevoVehiculo = firestore.collection("vehiculos").document()

                val datos = hashMapOf(
                    "usuarioId" to usuarioId,
                    "marca" to marca,
                    "modelo" to modelo,
                    "color" to color,
                    "placa" to placa,
                    "activo" to esElPrimerVehiculo,
                    "eliminado" to false,
                    "fechaRegistro" to Timestamp.now()
                )

                nuevoVehiculo.set(datos)
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { error -> callback(Result.failure(error)) }
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    /**
     * Marca un vehículo de la cuenta como el vehículo "en uso" y desmarca
     * el resto (ignorando los vehículos eliminados). Se usa cuando el
     * cliente elige cuál auto va a estacionar.
     */
    fun setActiveVehicle(
        usuarioId: String,
        vehiculoId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        firestore.collection("vehiculos")
            .whereEqualTo("usuarioId", usuarioId)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                snapshot.documents
                    .filter { !it.estaEliminado() }
                    .forEach { documento ->
                        batch.update(documento.reference, "activo", documento.id == vehiculoId)
                    }
                batch.commit()
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { error -> callback(Result.failure(error)) }
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    /**
     * Elimina lógicamente un vehículo: no se borra el documento, solo se
     * marca con "eliminado" = true para que deje de aparecer en las listas
     * (Mis vehículos, selección al escanear, etc). Si el vehículo eliminado
     * era el que estaba "en uso", se intenta marcar otro vehículo vigente
     * como el nuevo vehículo en uso.
     */
    fun deactivateVehicle(
        usuarioId: String,
        vehiculoId: String,
        callback: (Result<Unit>) -> Unit
    ) {
        firestore.collection("vehiculos")
            .whereEqualTo("usuarioId", usuarioId)
            .get()
            .addOnSuccessListener { snapshot ->
                val documentoAEliminar = snapshot.documents.firstOrNull { it.id == vehiculoId }
                if (documentoAEliminar == null) {
                    callback(Result.failure(IllegalStateException("No se encontró el vehículo a eliminar.")))
                    return@addOnSuccessListener
                }

                val estabaEnUso = documentoAEliminar.getBoolean("activo") ?: false
                val otroVehiculoVigente = snapshot.documents.firstOrNull {
                    it.id != vehiculoId && !it.estaEliminado()
                }

                val batch = firestore.batch()
                batch.update(
                    documentoAEliminar.reference,
                    mapOf("eliminado" to true, "activo" to false)
                )

                if (estabaEnUso && otroVehiculoVigente != null) {
                    batch.update(otroVehiculoVigente.reference, "activo", true)
                }

                batch.commit()
                    .addOnSuccessListener { callback(Result.success(Unit)) }
                    .addOnFailureListener { error -> callback(Result.failure(error)) }
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

    fun registerClient(
        nombre: String,
        telefono: String,
        correo: String,
        password: String,
        marca: String,
        modelo: String,
        color: String,
        placa: String,
        callback: (Result<Unit>) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(correo, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user
                val firebaseUid = firebaseUser?.uid

                if (firebaseUid.isNullOrBlank()) {
                    callback(Result.failure(IllegalStateException("No se pudo obtener el UID del usuario recién creado.")))
                    return@addOnSuccessListener
                }

                createUserDocument(firebaseUid, nombre, correo, telefono) { userResult ->
                    userResult.onSuccess { usuarioDocId ->
                        createVehicleDocument(usuarioDocId, marca, modelo, color, placa) { vehicleResult ->
                            vehicleResult.onSuccess {
                                auth.signOut()
                                callback(Result.success(Unit))
                            }
                            vehicleResult.onFailure { error ->
                                rollbackRegistration(firebaseUser)
                                callback(Result.failure(error))
                            }
                        }
                    }
                    userResult.onFailure { error ->
                        rollbackRegistration(firebaseUser)
                        callback(Result.failure(error))
                    }
                }
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    private fun createUserDocument(
        firebaseUid: String,
        nombre: String,
        correo: String,
        telefono: String,
        callback: (Result<String>) -> Unit
    ) {
        val usuario = hashMapOf(
            "authUid" to firebaseUid,
            "nombre" to nombre,
            "email" to correo,
            "telefono" to telefono,
            "rol" to "CLIENTE",
            "estado" to "ACTIVO",
            "fechaRegistro" to Timestamp.now()
        )

        val nuevoDocumento = firestore.collection("usuariosc").document()

        nuevoDocumento.set(usuario)
            .addOnSuccessListener { callback(Result.success(nuevoDocumento.id)) }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    private fun createVehicleDocument(
        usuarioDocId: String,
        marca: String,
        modelo: String,
        color: String,
        placa: String,
        callback: (Result<String>) -> Unit
    ) {
        val vehiculo = hashMapOf(
            "usuarioId" to usuarioDocId,
            "marca" to marca,
            "modelo" to modelo,
            "color" to color,
            "placa" to placa,
            "activo" to true,
            "eliminado" to false,
            "fechaRegistro" to Timestamp.now()
        )

        val nuevoVehiculo = firestore.collection("vehiculos").document()

        nuevoVehiculo.set(vehiculo)
            .addOnSuccessListener { callback(Result.success(nuevoVehiculo.id)) }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    private fun rollbackRegistration(user: FirebaseUser?) {
        user?.delete()
        auth.signOut()
    }

    private fun DocumentSnapshot.estaEliminado(): Boolean = getBoolean("eliminado") ?: false

    private fun DocumentSnapshot.toUserProfile(): UserProfile? {
        if (!exists()) return null

        val email = getString("email").orEmpty().trim()
        val puesto = getString("puesto").orEmpty().trim()
        val role = when {
            puesto.equals("Administrador", ignoreCase = true) -> "ADMIN"
            puesto.equals("Cliente", ignoreCase = true) -> "CLIENTE"
            else -> puesto.uppercase()
        }
        val active = getBoolean("activo")

        if (email.isBlank() || role.isBlank() || status.isBlank()) {
            return null
        }

        return UserProfile(
            documentId = id,
            email = email,
            name = getString("nombre").orEmpty().trim(),
            username = getString("usuario").orEmpty().trim(),
            role = role,
            active = active
        )
    }

    private fun DocumentSnapshot.toCajonInfo(): CajonInfo? {
        if (!exists()) {
            return null
        }

        val nivel = getLong("nivel")?.toInt()
        val numeroCajon = getLong("numeroCajon")?.toInt()
        val estado = getString("estado").orEmpty().trim()

        if (nivel == null || numeroCajon == null || estado.isBlank()) {
            return null
        }

        return CajonInfo(
            documentId = id,
            nivel = nivel,
            numeroCajon = numeroCajon,
            estado = estado
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun DocumentSnapshot.toSustentabilidadInfo(): SustentabilidadInfo? {
        if (!exists()) {
            return null
        }

        return SustentabilidadInfo(
            aguaCaptadaLitros = getDouble("aguaCaptadaLitros") ?: 0.0,
            aguaUsadaRiegoLitros = getDouble("aguaUsadaRiego") ?: 0.0,
            energiaGeneradaKwh = getDouble("energiaGeneradaKwh") ?: 0.0,
            nivelTanquePorcentaje = getDouble("nivelTanque") ?: 0.0,
            porcentajeSolar = getDouble("porcentajeSolar") ?: 0.0,
            bombaAguaEncendida = getBoolean("bombaAgua") ?: false,
            alertas = (get("alertas") as? Map<String, Any?>) ?: emptyMap()
        )
    }

    private fun DocumentSnapshot.toEstanciaResumen(): EstanciaResumen? {
        if (!exists()) {
            return null
        }

        val usuarioId = getString("usuarioId").orEmpty().trim()
        val cajonId = getString("cajonId").orEmpty().trim()
        val estatus = getString("estatus").orEmpty().trim().uppercase()
        val fechaEntrada = getTimestamp("fechaEntrada")
        val fechaSalida = getTimestamp("fechaSalida")

        if (usuarioId.isBlank() || cajonId.isBlank() || estatus.isBlank() || fechaEntrada == null) {
            return null
        }

        return EstanciaResumen(
            documentId = id,
            usuarioId = usuarioId,
            cajonId = cajonId,
            estatus = estatus,
            fechaEntrada = fechaEntrada,
            fechaSalida = fechaSalida
        )
    }

    private fun DocumentSnapshot.toActiveStay(): ActiveStay? {
        if (!exists()) return null

        val userId = getString("usuarioId").orEmpty().trim()
        val vehicleId = getString("vehiculoId").orEmpty().trim()
        val assignedSpotId = getString("cajonId").orEmpty().trim()
        val status = getString("estatus").orEmpty().trim().uppercase()
        val entryTimestamp = getTimestamp("fechaEntrada")
        val paymentStatus = getString("estatusPago").orEmpty().trim().uppercase().ifBlank { "PENDIENTE" }

        if (userId.isBlank() || vehicleId.isBlank() || status.isBlank() || entryTimestamp == null) {
            return null
        }

        return ActiveStay(
            documentId = id,
            userId = userId,
            vehicleId = vehicleId,
            assignedSpotId = assignedSpotId,
            status = status,
            entryTimestamp = entryTimestamp,
            paymentStatus = paymentStatus
        )
    }

    private fun DocumentSnapshot.toVisitHistoryItem(): VisitHistoryItem? {
        if (!exists()) return null
        val cajonId = getString("cajonId").orEmpty().trim()
        val fechaEntrada = getTimestamp("fechaEntrada") ?: return null
        val fechaSalida = getTimestamp("fechaSalida")
        val subtotal = getDouble("subtotal") ?: 0.0
        val iva = getDouble("iva") ?: 0.0
        val montoTotal = getDouble("montoTotal") ?: 0.0
        val metodoPago = getString("metodoPago").orEmpty().trim().uppercase()

        return VisitHistoryItem(
            documentId = id,
            cajonId = cajonId,
            fechaEntrada = fechaEntrada,
            fechaSalida = fechaSalida,
            subtotal = subtotal,
            iva = iva,
            montoTotal = montoTotal,
            metodoPago = metodoPago
        )
    }

    private fun DocumentSnapshot.toVehicleInfo(): VehicleInfo? {
        if (!exists()) return null

        val brand = getString("marca").orEmpty().trim()
        val plate = getString("placa").orEmpty().trim()
        val color = getString("color").orEmpty().trim()
        val model = getString("modelo").orEmpty().trim()
        val isActive = getBoolean("activo") ?: false

        if (brand.isBlank() || plate.isBlank() || color.isBlank()) return null

        return VehicleInfo(
            documentId = id,
            brand = brand,
            model = model,
            plate = plate,
            color = color,
            isActive = isActive
        )
    }
}