package org.utl.idgs903.appkaaxpark.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import org.utl.idgs903.appkaaxpark.data.ai.UserVisitDetail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UserProfile(
    val documentId: String,
    val email: String,
    val name: String,
    val username: String,
    val phone: String,
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
    val metodoPago: String,
    val folio: String = "",
    val placa: String = ""
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

class SinCajonesDisponiblesException : Exception("No hay cajones disponibles en este momento. Intenta más tarde.")

data class ActividadDashboardItem(
    val tipo: String,
    val hora: String,
    val placa: String,
    val duracionMin: Int?,
    val descripcion: String = ""
)

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

    /**
     * Igual que assignParkingSpot, pero en vez de recibir un cajón ya
     * elegido (por código QR específico), elige uno al azar entre los
     * que estén "Libre". Si dos clientes escanean casi al mismo tiempo y
     * "chocan" por el mismo cajón, reintenta automáticamente con otro
     * candidato — el cliente nunca ve ese choque, solo el resultado final.
     */
    fun assignRandomParkingSpot(
        usuarioId: String,
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

                firestore.collection("cajones-dev")
                    .whereEqualTo("estado", "Libre")
                    .get()
                    .addOnSuccessListener { cajonesSnap ->
                        // Nivel 4 no es operativo (tampoco aparece en la página de
                        // administración de cajones), así que se excluye del sorteo.
                        val candidatos = cajonesSnap.documents
                            .filter { (it.getLong("nivel") ?: 0L) != 4L }
                            .map { it.id }
                            .shuffled()

                        if (candidatos.isEmpty()) {
                            callback(Result.failure(SinCajonesDisponiblesException()))
                            return@addOnSuccessListener
                        }
                        intentarAsignarCandidato(usuarioId, vehiculoId, candidatos, 0, callback)
                    }
                    .addOnFailureListener { error -> callback(Result.failure(error)) }
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    private fun intentarAsignarCandidato(
        usuarioId: String,
        vehiculoId: String,
        candidatos: List<String>,
        indice: Int,
        callback: (Result<String>) -> Unit
    ) {
        if (indice >= candidatos.size) {
            callback(Result.failure(SinCajonesDisponiblesException()))
            return
        }

        runAssignmentTransaction(usuarioId, vehiculoId, candidatos[indice]) { result ->
            result.onSuccess { callback(Result.success(it)) }
            result.onFailure { error ->
                if (error is CajonOcupadoException) {
                    // Otro cliente lo tomó justo antes — probamos el siguiente candidato.
                    intentarAsignarCandidato(usuarioId, vehiculoId, candidatos, indice + 1, callback)
                } else {
                    callback(Result.failure(error))
                }
            }
        }
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

    fun sendPasswordResetEmail(email: String, callback: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener { callback(Result.success(Unit)) }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
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
        val taskAdmin = firestore.collection("usuarios").document(documentId).get()
        val taskCliente = firestore.collection("usuariosc").document(documentId).get()

        com.google.android.gms.tasks.Tasks.whenAllComplete(taskAdmin, taskCliente)
            .addOnCompleteListener {
                val adminProfile = if (taskAdmin.isSuccessful) taskAdmin.result?.toUserProfile() else null
                if (adminProfile != null) {
                    resolveProfileResult(adminProfile, callback)
                    return@addOnCompleteListener
                }

                val clientProfile = if (taskCliente.isSuccessful) taskCliente.result?.toUserProfile() else null
                if (clientProfile != null) {
                    resolveProfileResult(clientProfile, callback)
                    return@addOnCompleteListener
                }

                callback(Result.failure(UserProfileNotFoundException()))
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

    fun fetchSecuenciaIdsDeCajon(
        cajonId: String,
        callback: (Result<Pair<String?, String?>>) -> Unit
    ) {
        firestore.collection("cajones-dev")
            .document(cajonId)
            .get()
            .addOnSuccessListener { snapshot ->
                val ingreso = snapshot.getString("secuenciaIngresoId")?.takeIf { it.isNotBlank() }
                val salida = snapshot.getString("secuenciaSalidaId")?.takeIf { it.isNotBlank() }
                callback(Result.success(ingreso to salida))
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    /**
     * Trae los pasos (motores/tiempos) de una secuencia guardada en la
     * colección 'secuencias' — la misma colección que ya usa la página web.
     */
    @Suppress("UNCHECKED_CAST")
    fun fetchPasosDeSecuencia(
        secuenciaId: String,
        callback: (Result<List<Map<String, Any>>>) -> Unit
    ) {
        firestore.collection("secuencias")
            .document(secuenciaId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    callback(Result.failure(Exception("La secuencia ya no existe.")))
                    return@addOnSuccessListener
                }
                val pasos = (snapshot.get("pasos") as? List<*>)
                    ?.mapNotNull { it as? Map<String, Any> }
                    ?: emptyList()
                callback(Result.success(pasos))
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
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
        // Consultamos la colección 'pagos' ya que es la que tiene la información
        // consolidada del pago, incluyendo folio, placa y estado 'Completado'.
        firestore.collection("pagos")
            .whereEqualTo("estado", "Completado")
            .get()
            .addOnSuccessListener { snapshot ->
                // Filtramos por la placa de los vehículos del usuario para asegurar
                // que solo vea su historial.
                fetchVehiclesByUserId(usuarioId) { vResult ->
                    val placasUsuario = vResult.getOrNull()?.map { it.plate } ?: emptyList()
                    
                    val visitas = snapshot.documents
                        .mapNotNull { it.toVisitHistoryItemFromPago() }
                        .filter { it.placa in placasUsuario }
                        .sortedByDescending { it.fechaSalida?.seconds ?: it.fechaEntrada.seconds }
                    
                    callback(Result.success(visitas))
                }
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
                // Registrar actividad de entrada
                registrarActividad(
                    tipo = "entrada",
                    vehiculoId = vehiculoId,
                    cajonId = assignedSpot,
                    duracionMin = null
                )
                callback(Result.success(assignedSpot))
            }
            .addOnFailureListener { error ->
                callback(Result.failure(error))
            }
    }

    private fun registrarActividad(
        tipo: String,
        vehiculoId: String,
        cajonId: String,
        duracionMin: Int? = null,
        folio: String? = null
    ) {
        val now = Date()
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now)
        val hora = SimpleDateFormat("HH:mm", Locale.US).format(now)

        fetchVehicleByDocumentId(vehiculoId) { result ->
            val vehicle = result.getOrNull()
            val placa = vehicle?.plate ?: ""
            val marca = vehicle?.brand ?: ""
            val modelo = vehicle?.model ?: ""

            firestore.collection("cajones-dev").document(cajonId).get()
                .addOnSuccessListener { cajonSnap ->
                    val nivel = cajonSnap.getLong("nivel") ?: 0
                    val numCajon = cajonSnap.getLong("numeroCajon") ?: 0
                    
                    var desc = "Nivel $nivel · Cajón $numCajon"
                    if (!folio.isNullOrBlank()) desc += " · Folio $folio"
                    else if (marca.isNotBlank()) desc += " · $marca $modelo"

                    val actividad = hashMapOf(
                        "tipo" to tipo,
                        "fecha" to fecha,
                        "hora" to hora,
                        "placa" to placa,
                        "timestamp" to System.currentTimeMillis(),
                        "duracionMin" to (duracionMin ?: 0),
                        "descripcion" to desc
                    )

                    firestore.collection("actividad").add(actividad)
                }
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

        firestore.collection("estancias").document(estanciaId).get()
            .addOnSuccessListener { estanciaSnap ->
                val vehiculoId = estanciaSnap.getString("vehiculoId") ?: ""
                val fechaEntrada = estanciaSnap.getTimestamp("fechaEntrada")
                val folio = estanciaSnap.getString("folio") // si tienes folio en estancia

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
                    .addOnSuccessListener {
                        val duracionMin = if (fechaEntrada != null) {
                            val diff = System.currentTimeMillis() - fechaEntrada.toDate().time
                            (diff / 60000L).toInt()
                        } else 0

                        registrarActividad(
                            tipo = "salida",
                            vehiculoId = vehiculoId,
                            cajonId = cajonId,
                            duracionMin = duracionMin,
                            folio = folio
                        )
                        callback(Result.success(Unit))
                    }
                    .addOnFailureListener { error -> callback(Result.failure(error)) }
            }
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
     * Agrega un nuevo vehículo a la cuenta del usuario. Verifica que la placa
     * no esté registrada por ningún otro usuario activo.
     */
    fun addVehicle(
        usuarioId: String,
        marca: String,
        modelo: String,
        color: String,
        placa: String,
        callback: (Result<Unit>) -> Unit
    ) {
        // Primero verificamos si la placa ya existe en el sistema (no eliminada)
        firestore.collection("vehiculos")
            .whereEqualTo("placa", placa.uppercase().trim())
            .whereEqualTo("eliminado", false)
            .get()
            .addOnSuccessListener { plateSnapshot ->
                if (!plateSnapshot.isEmpty) {
                    callback(Result.failure(Exception("Esta placa ya está registrada en el sistema.")))
                    return@addOnSuccessListener
                }

                // Si no existe, procedemos a agregar
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
                            "placa" to placa.uppercase().trim(),
                            "activo" to esElPrimerVehiculo,
                            "eliminado" to false,
                            "fechaRegistro" to Timestamp.now()
                        )

                        nuevoVehiculo.set(datos)
                            .addOnSuccessListener { callback(Result.success(Unit)) }
                            .addOnFailureListener { error -> callback(Result.failure(error)) }
                    }
                    .addOnFailureListener { error -> callback(Result.failure(error)) }
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
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
        val taskAdmin = firestore.collection("usuarios")
            .whereEqualTo("email", email)
            .limit(1)
            .get()

        val taskCliente = firestore.collection("usuariosc")
            .whereEqualTo("email", email)
            .limit(1)
            .get()

        com.google.android.gms.tasks.Tasks.whenAllComplete(taskAdmin, taskCliente)
            .addOnCompleteListener {
                val adminProfile = if (taskAdmin.isSuccessful) {
                    taskAdmin.result?.documents?.firstOrNull()?.toUserProfile()
                } else null

                if (adminProfile != null) {
                    resolveProfileResult(adminProfile, callback)
                    return@addOnCompleteListener
                }

                val clientProfile = if (taskCliente.isSuccessful) {
                    taskCliente.result?.documents?.firstOrNull()?.toUserProfile()
                } else null

                if (clientProfile != null) {
                    resolveProfileResult(clientProfile, callback)
                    return@addOnCompleteListener
                }

                val error = taskAdmin.exception ?: taskCliente.exception
                callback(Result.failure(error ?: UserProfileNotFoundException()))
            }
    }

    private fun resolveProfileResult(
        profile: UserProfile,
        callback: (Result<UserProfile>) -> Unit
    ) {
        if (!profile.isActive) {
            callback(Result.failure(InactiveUserException()))
            return
        }
        if (!profile.role.equals("ADMIN", ignoreCase = true)
            && !profile.role.equals("CLIENTE", ignoreCase = true)
        ) {
            callback(Result.failure(UnsupportedRoleException(profile.role)))
            return
        }
        callback(Result.success(profile))
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
        // Iniciamos con Auth.createUser para que el usuario esté autenticado.
        // Esto permite que las reglas "request.auth != null" de tu Firebase autoricen 
        // las consultas de validación (placa y admin) que siguen.
        auth.createUserWithEmailAndPassword(correo, password)
            .addOnSuccessListener { authResult ->
                val firebaseUser = authResult.user

                // Ahora que estamos autenticados, verificamos si es administrador
                firestore.collection("usuarios")
                    .whereEqualTo("email", correo.lowercase().trim())
                    .get()
                    .addOnSuccessListener { adminSnapshot ->
                        if (!adminSnapshot.isEmpty) {
                            // Si es admin, no puede registrarse como cliente. 
                            // Borramos el usuario de Auth y salimos.
                            firebaseUser?.delete()
                            auth.signOut()
                            callback(Result.failure(Exception("Este correo ya está registrado.")))
                            return@addOnSuccessListener
                        }

                        // Verificamos si la placa ya existe
                        firestore.collection("vehiculos")
                            .whereEqualTo("placa", placa.uppercase().trim())
                            .whereEqualTo("eliminado", false)
                            .get()
                            .addOnSuccessListener { plateSnapshot ->
                                if (!plateSnapshot.isEmpty) {
                                    // Placa duplicada: borrar usuario de Auth y salir.
                                    firebaseUser?.delete()
                                    auth.signOut()
                                    callback(Result.failure(Exception("Esta placa ya está registrada en el sistema.")))
                                    return@addOnSuccessListener
                                }

                                // Si todo es correcto, crear los documentos definitivos
                                val firebaseUid = firebaseUser?.uid ?: ""
                                createUserDocument(firebaseUid, nombre, correo, telefono) { userResult ->
                                    userResult.onSuccess { usuarioDocId ->
                                        createVehicleDocument(usuarioDocId, marca.trim(), modelo.trim(), color.trim(), placa.uppercase().trim()) { vehicleResult ->
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
                                rollbackRegistration(firebaseUser)
                                callback(Result.failure(error))
                            }
                    }
                    .addOnFailureListener { error ->
                        rollbackRegistration(firebaseUser)
                        callback(Result.failure(error))
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
        val rol = getString("rol").orEmpty().trim()
        val role = when {
            puesto.equals("Administrador", ignoreCase = true) -> "ADMIN"
            puesto.equals("Cliente", ignoreCase = true) -> "CLIENTE"
            rol.isNotBlank() -> rol.uppercase()
            puesto.isNotBlank() -> puesto.uppercase()
            else -> ""
        }

        val activoBooleano = getBoolean("activo")
        val estadoTexto = getString("estado").orEmpty().trim()
        val active = when {
            activoBooleano != null -> activoBooleano
            estadoTexto.isNotBlank() -> estadoTexto.equals("ACTIVO", ignoreCase = true)
            else -> false
        }

        if (email.isBlank() || role.isBlank()) {
            return null
        }

        return UserProfile(
            documentId = id,
            email = email,
            name = getString("nombre").orEmpty().trim(),
            username = getString("usuario").orEmpty().trim(),
            phone = getString("telefono").orEmpty().trim(),
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

    private fun DocumentSnapshot.toVisitHistoryItemFromPago(): VisitHistoryItem? {
        if (!exists()) return null
        
        val cajonId = getString("cajonId").orEmpty().trim()
        val placa = getString("placa").orEmpty().trim()
        val folio = getString("folio").orEmpty().trim()
        val metodo = getString("metodo").orEmpty().trim()
        val monto = getDouble("monto") ?: 0.0
        
        // El documento de pago tiene horaEntrada y horaSalida como Strings (HH:mm)
        // y fecha como String (yyyy-MM-dd). Necesitamos convertirlos a Timestamp.
        val fechaStr = getString("fecha") ?: return null
        val hEntrada = getString("horaEntrada") ?: "00:00"
        val hSalida = getString("horaSalida") ?: "00:00"
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val dateEntrada = try { sdf.parse("$fechaStr $hEntrada") } catch (e: Exception) { null }
        val dateSalida = try { sdf.parse("$fechaStr $hSalida") } catch (e: Exception) { null }
        
        if (dateEntrada == null) return null

        return VisitHistoryItem(
            documentId = id,
            cajonId = cajonId,
            fechaEntrada = Timestamp(dateEntrada),
            fechaSalida = dateSalida?.let { Timestamp(it) },
            subtotal = monto, // En pagos parece que guardamos monto final
            iva = 0.0,
            montoTotal = monto,
            metodoPago = metodo,
            folio = folio,
            placa = placa
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

    // ─── TARIFA ─────────────────────────────────────────────────────────────────
    /**
     * Lee la tarifa por hora desde el documento 'configuracion/tarifas'.
     * Si no existe el documento, devuelve 60.0 como valor por defecto.
     */
    fun fetchTarifaPorHora(callback: (Result<Double>) -> Unit) {
        firestore.collection("configuracion").document("tarifas")
            .get()
            .addOnSuccessListener { snapshot ->
                val tarifa = snapshot.getDouble("tarifaPorHora") ?: 60.0
                callback(Result.success(tarifa))
            }
            .addOnFailureListener { callback(Result.success(60.0)) }
    }

/**
     * Obtiene el historial de visitas del usuario [usuarioId] con detalles completos
     * (vehículo, cajón, tiempos, costo). Devuelve la lista ordenada cronológicamente.
     */
    fun fetchUserVisitHistoryWithDetails(
        usuarioId: String,
        callback: (Result<List<UserVisitDetail>>) -> Unit
    ) {
        fetchTarifaPorHora { tarifaResult ->
            val tarifaPorHoraReal = tarifaResult.getOrNull() ?: 60.0

            firestore.collection("estancias")
                .whereEqualTo("usuarioId", usuarioId)
                .whereEqualTo("estatus", "FINALIZADA")
                .get()
                .addOnSuccessListener { snapshot ->
                    val docIds = snapshot.documents.map { it.id }
                    if (docIds.isEmpty()) {
                        callback(Result.success(emptyList()))
                        return@addOnSuccessListener
                    }

                    val visitas = mutableListOf<UserVisitDetail>()
                    var processed = 0

                    snapshot.documents.forEach { doc ->
                        val cajonId = doc.getString("cajonId") ?: ""
                        val fechaEntrada = doc.getTimestamp("fechaEntrada") ?: return@forEach
                        val fechaSalida = doc.getTimestamp("fechaSalida") ?: return@forEach
                        val montoTotal = doc.getDouble("montoTotal") ?: 0.0
                        val vehiculoId = doc.getString("vehiculoId") ?: ""

                        val entrada = fechaEntrada.toDate()
                        val salida = fechaSalida.toDate()
                        val duracionMillis = salida.time - entrada.time
                        val totalSeconds = duracionMillis / 1000L
                        val horas = totalSeconds / 3600L
                        val minutos = (totalSeconds % 3600L) / 60L
                        val segundos = totalSeconds % 60L
                        val tiempoTexto = String.format("%02d:%02d:%02d", horas, minutos, segundos)

                        fetchVehicleByDocumentId(vehiculoId) { vehicleResult ->
                            val vehicle = vehicleResult.getOrNull()
                            val fechaClave = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(entrada)
                            val fechaDisplay = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(entrada)
                            visitas.add(
                                UserVisitDetail(
                                    fecha = fechaClave,
                                    fechaDisplay = fechaDisplay,
                                    vehiculo = vehicle?.brand ?: "Desconocido",
                                    placa = vehicle?.plate ?: "--",
                                    cajon = cajonId,
                                    entrada = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(entrada),
                                    salida = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(salida),
                                    tiempoEstacionado = tiempoTexto,
                                    tarifaPorHora = tarifaPorHoraReal,
                                    totalPagado = montoTotal
                                )
                            )
                            processed++
                            if (processed == docIds.size) {
                                callback(Result.success(visitas.sortedBy { it.fecha }))
                            }
                        }
                    }
                }
                .addOnFailureListener { error -> callback(Result.failure(error)) }
        }
    }

    // ─── PAGOS ──────────────────────────────────────────────────────────────────
    /**
     * Crea un registro en la colección 'pagos' (la misma que lee la página web).
     *
     *  estado = "Completado"   → pago con tarjeta: ya confirmado automáticamente.
     *  estado = "PendienteCaja" → pago en ventanilla: espera que el admin confirme.
     *
     * Devuelve el ID del documento creado. Para pagos en caja, el app escucha
     * ese ID con [listenPagoEstado] para detectar la confirmación del admin.
     */
    fun addPagoMovil(
        folio: String,
        cajonId: String,
        cajonDescripcion: String,
        placa: String,
        horaEntrada: String,
        horaSalida: String,
        duracionMin: Long,
        monto: Double,
        metodo: String,
        estado: String,
        estanciaId: String,
        callback: (Result<String>) -> Unit
    ) {
        val fecha = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val datos = hashMapOf(
            "folio"           to folio,
            "cajonId"         to cajonId,
            "cajonDescripcion" to cajonDescripcion,
            "placa"           to placa,
            "horaEntrada"     to horaEntrada,
            "horaSalida"      to horaSalida,
            "duracionMin"     to duracionMin,
            "monto"           to monto,
            "metodo"          to metodo,
            "estado"          to estado,
            "estanciaId"      to estanciaId, // necesario para que el web finalice la estancia
            "fecha"           to fecha,
            "timestamp"       to System.currentTimeMillis(),
            "pagadoPorApp"    to true
        )
        firestore.collection("pagos").add(datos)
            .addOnSuccessListener { ref -> callback(Result.success(ref.id)) }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    /**
     * Escucha en tiempo real el campo 'estado' del documento de un pago.
     * Úsalo después de crear un PendienteCaja para detectar cuando el admin
     * confirma el pago desde la página web.
     *
     * Devuelve una función lambda que cancela el listener — llámala en onDestroy.
     *
     *  cancelar = repository.listenPagoEstado(pagoId) { estado ->
     *      if (estado == "Completado") { ... }
     *  }
     *  // later:
     *  cancelar()
     */
    fun listenPagoEstado(pagoId: String, onCambio: (String) -> Unit): () -> Unit {
        val registration = firestore.collection("pagos").document(pagoId)
            .addSnapshotListener { snapshot, _ ->
                val estado = snapshot?.getString("estado") ?: return@addSnapshotListener
                onCambio(estado)
            }
        return { registration.remove() }
    }

    /**
     * Escucha en tiempo real el campo 'estatusPago' de una estancia.
     * Útil para detectar cuando un pago se realiza externamente (ej. sitio web).
     */
    fun listenStayStatus(estanciaId: String, onCambio: (String) -> Unit): () -> Unit {
        val registration = firestore.collection("estancias").document(estanciaId)
            .addSnapshotListener { snapshot, _ ->
                val estado = snapshot?.getString("estatusPago") ?: return@addSnapshotListener
                onCambio(estado)
            }
        return { registration.remove() }
    }

    // ─── CONTADORES GLOBALES ─────────────────────────────────────────────────

    /**
     * Cuenta los clientes registrados en la colección 'usuariosc'.
     */
    fun fetchClientCount(callback: (Result<Int>) -> Unit) {
        firestore.collection("usuariosc")
            .get()
            .addOnSuccessListener { snapshot ->
                callback(Result.success(snapshot.size()))
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    /**
     * Cuenta los administradores registrados en la colección 'usuarios'.
     */
    fun fetchAdminCount(callback: (Result<Int>) -> Unit) {
        firestore.collection("usuarios")
            .get()
            .addOnSuccessListener { snapshot ->
                callback(Result.success(snapshot.size()))
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    /**
     * Cuenta los vehículos registrados en la colección 'vehiculos'
     * (excluyendo los eliminados lógicamente).
     */
    fun fetchVehicleCount(callback: (Result<Int>) -> Unit) {
        firestore.collection("vehiculos")
            .get()
            .addOnSuccessListener { snapshot ->
                val count = snapshot.documents.count { !it.estaEliminado() }
                callback(Result.success(count))
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    fun fetchAllUsers(callback: (Result<List<UserProfile>>) -> Unit) {
        val taskAdmin = firestore.collection("usuarios").get()
        val taskCliente = firestore.collection("usuariosc").get()

        com.google.android.gms.tasks.Tasks.whenAllComplete(taskAdmin, taskCliente)
            .addOnCompleteListener {
                val list = mutableListOf<UserProfile>()

                if (taskAdmin.isSuccessful) {
                    taskAdmin.result?.documents?.mapNotNullTo(list) { it.toUserProfile() }
                }
                if (taskCliente.isSuccessful) {
                    taskCliente.result?.documents?.mapNotNullTo(list) { it.toUserProfile() }
                }

                if (list.isEmpty() && (!taskAdmin.isSuccessful || !taskCliente.isSuccessful)) {
                    val error = taskAdmin.exception ?: taskCliente.exception
                    callback(Result.failure(error ?: Exception("No se pudieron cargar los usuarios.")))
                } else {
                    callback(Result.success(list.sortedBy { it.name.lowercase() }))
                }
            }
    }

    
    // ─── DATOS ESPECÍFICOS DEL USUARIO ──────────────────────────────────────

    /**
     * Obtiene las visitas finalizadas del usuario [usuarioId] que ocurrieron
     * en la fecha [fecha] (formato "yyyy-MM-dd") y devuelve la duración
     * total en minutos junto con el detalle de cada estancia.
     */
    fun fetchUserVisitDurationsForDate(
        usuarioId: String,
        fecha: String,
        callback: (Result<Map<String, Any>>) -> Unit
    ) {
        firestore.collection("estancias")
            .whereEqualTo("usuarioId", usuarioId)
            .whereEqualTo("estatus", "FINALIZADA")
            .get()
            .addOnSuccessListener { snapshot ->
                val formato = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val visitasDelDia = snapshot.documents
                    .mapNotNull { doc ->
                        val entry = doc.getTimestamp("fechaEntrada") ?: return@mapNotNull null
                        val salida = doc.getTimestamp("fechaSalida")
                        if (salida == null) return@mapNotNull null
                        val entryDate = formato.format(entry.toDate())
                        if (entryDate != fecha) return@mapNotNull null
                        val duracionMin = Math.round((salida.toDate().time - entry.toDate().time) / 60000.0).toInt()
                        mapOf(
                            "cajonId" to (doc.getString("cajonId") ?: ""),
                            "duracionMin" to duracionMin,
                            "montoTotal" to (doc.getDouble("montoTotal") ?: 0.0)
                        )
                    }
                val duracionTotalMin = visitasDelDia.sumOf { (it["duracionMin"] as? Int) ?: 0 }
                val montoTotal = visitasDelDia.sumOf { (it["montoTotal"] as? Double) ?: 0.0 }
                callback(
                    Result.success(
                        mapOf(
                            "fecha" to fecha,
                            "totalVisitas" to visitasDelDia.size,
                            "duracionTotalMin" to duracionTotalMin,
                            "montoTotal" to montoTotal,
                            "detalle" to visitasDelDia
                        )
                    )
                )
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    /**
     * Obtiene la estancia activa del usuario [usuarioId] junto con la
     * información del vehículo asociado. Devuelve null si no hay estancia activa.
     */
    fun fetchCurrentStayForUser(
        usuarioId: String,
        callback: (Result<Map<String, Any>?>) -> Unit
    ) {
        firestore.collection("estancias")
            .whereEqualTo("usuarioId", usuarioId)
            .whereEqualTo("estatus", "ACTIVA")
            .get()
            .addOnSuccessListener { snapshot ->
                val activeDoc = snapshot.documents.firstOrNull() ?: run {
                    callback(Result.success(null))
                    return@addOnSuccessListener
                }
                val cajonId = activeDoc.getString("cajonId") ?: ""
                val vehiculoId = activeDoc.getString("vehiculoId") ?: ""
                val entryTimestamp = activeDoc.getTimestamp("fechaEntrada")
                if (entryTimestamp == null) {
                    callback(Result.success(null))
                    return@addOnSuccessListener
                }
                val entryMillis = entryTimestamp.toDate().time
                val elapsedMillis = System.currentTimeMillis() - entryMillis
                val horas = elapsedMillis / 3600000L
                val minutos = (elapsedMillis % 3600000L) / 60000L
                val duracionActualMin = (horas * 60 + minutos).toInt()

                fetchVehicleByDocumentId(vehiculoId) { vehicleResult ->
                    vehicleResult.onSuccess { vehicle ->
                        callback(
                            Result.success(
                                mapOf(
                                    "cajonId" to cajonId,
                                    "vehiculoMarca" to vehicle.brand,
                                    "vehiculoModelo" to vehicle.model,
                                    "vehiculoPlaca" to vehicle.plate,
                                    "vehiculoColor" to vehicle.color,
                                    "entradaMillis" to entryMillis,
                                    "duracionActualMin" to duracionActualMin,
                                    "duracionActualTexto" to formatDuration(elapsedMillis)
                                )
                            )
                        )
                    }.onFailure { error -> callback(Result.failure(error)) }
                }
            }
            .addOnFailureListener { error -> callback(Result.failure(error)) }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000L
        val horas = totalSeconds / 3600L
        val minutos = (totalSeconds % 3600L) / 60L
        val segundos = totalSeconds % 60L
        return String.format("%02d:%02d:%02d", horas, minutos, segundos)
    }

    // ─── DASHBOARD ───────────────────────────────────────────────────────────────

    /**
     * Listener en tiempo real de cajones.
     * Equivale al getCajones() Observable que usa el web DashboardComponent.
     * Devuelve una lambda para cancelar el listener — llámala en onDestroy.
     */
    fun listenCajones(onUpdate: (List<CajonInfo>) -> Unit): () -> Unit {
        val reg = firestore.collection("cajones-dev")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { it.toCajonInfo() }
                    .sortedWith(compareBy({ it.nivel }, { it.numeroCajon }))
                onUpdate(list)
            }
        return { reg.remove() }
    }

    /**
     * Listener de toda la actividad del día indicado (YYYY-MM-DD).
     * Equivale al getActividadPorFecha(hoy) del web.
     * Se usa para: entradasHoy, salidasHoy, tiempoPromedioEstancia y gráfica por hora.
     */
    fun listenActividadHoy(
        fecha: String,
        onUpdate: (List<ActividadDashboardItem>) -> Unit
    ): () -> Unit {
        val reg = firestore.collection("actividad")
            .whereEqualTo("fecha", fecha)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    val tipo = doc.getString("tipo") ?: return@mapNotNull null
                    val hora = doc.getString("hora") ?: return@mapNotNull null
                    ActividadDashboardItem(
                        tipo       = tipo,
                        hora       = hora,
                        placa      = doc.getString("placa").orEmpty(),
                        duracionMin = doc.getLong("duracionMin")?.toInt(),
                        descripcion = doc.getString("descripcion").orEmpty()
                    )
                }
                onUpdate(list)
            }
        return { reg.remove() }
    }

    /**
     * Listener de las últimas 5 actividades (cualquier día), ordenadas por timestamp.
     * Equivale al getActividadReciente() del web.
     */
    fun listenActividadReciente(onUpdate: (List<ActividadDashboardItem>) -> Unit): () -> Unit {
        val reg = firestore.collection("actividad")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val list = snap.documents.mapNotNull { doc ->
                    val tipo = doc.getString("tipo") ?: return@mapNotNull null
                    val hora = doc.getString("hora") ?: return@mapNotNull null
                    ActividadDashboardItem(
                        tipo       = tipo,
                        hora       = hora,
                        placa      = doc.getString("placa").orEmpty(),
                        duracionMin = doc.getLong("duracionMin")?.toInt(),
                        descripcion = doc.getString("descripcion").orEmpty()
                    )
                }
                onUpdate(list)
            }
        return { reg.remove() }
    }
}