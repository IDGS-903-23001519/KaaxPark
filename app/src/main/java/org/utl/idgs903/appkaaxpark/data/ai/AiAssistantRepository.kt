package org.utl.idgs903.appkaaxpark.data.ai

import org.utl.idgs903.appkaaxpark.data.AppConfig

interface AiAssistantRepository {
    fun ask(question: String, callback: (Result<AiAssistantReply>) -> Unit)
}

class ParkingAiAssistantRepository(
    private val contextProvider: ParkingAiContextProvider = ParkingAiContextProvider(),
    private val remoteClient: RemoteAiAnswerClient = RemoteAiAnswerClient(),
    private val rol: RolUsuario = RolUsuario.CLIENTE
) : AiAssistantRepository {

    override fun ask(question: String, callback: (Result<AiAssistantReply>) -> Unit) {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank()) {
            callback(Result.failure(IllegalArgumentException("La pregunta no puede estar vacia.")))
            return
        }

        contextProvider.load { contextResult ->
            contextResult.onSuccess { context ->
                val backendConfigurado = AppConfig.aiBackendEnabled &&
                    AppConfig.aiBackendBaseUrl.isNotBlank()

                if (backendConfigurado) {
                    remoteClient.ask(cleanQuestion, context) { remoteResult ->
                        remoteResult.onSuccess { callback(Result.success(it)) }
                        remoteResult.onFailure { error ->
                            callback(
                                Result.success(
                                    LocalAiFallbackEngine.answer(
                                        question = cleanQuestion,
                                        context = context,
                                        rol = rol,
                                        reason = error.message
                                    )
                                )
                            )
                        }
                    }
                } else {
                    callback(
                        Result.success(
                            LocalAiFallbackEngine.answer(cleanQuestion, context, rol)
                        )
                    )
                }
            }
            contextResult.onFailure { error ->
                callback(Result.failure(error))
            }
        }
    }

    constructor(userDocId: String, rol: RolUsuario) : this(
        contextProvider = ParkingAiContextProvider(userDocId = userDocId, rol = rol),
        remoteClient = RemoteAiAnswerClient(),
        rol = rol
    )
}
