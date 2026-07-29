# Arquitectura del Asistente IA

## Objetivo
Evitar respuestas genericas y contestar solo con informacion real del sistema.

## Flujo
1. El usuario abre `AsistenteIA`.
2. La pantalla envia la pregunta a `ParkingAiAssistantRepository`.
3. `ParkingAiContextProvider` consulta Firebase y arma un contexto real con:
   - cajones
   - estancias
   - sustentabilidad
   - tarifa por hora
4. `AiPromptBuilder` crea el prompt de control con esta regla:
   - responde solo con datos del contexto
   - si falta informacion, dilo con claridad
5. Si `AppConfig.aiBackendEnabled` esta activo, la app llama al backend configurado.
6. Si el backend no esta disponible, la app usa `LocalAiFallbackEngine` para responder con datos reales de Firebase.

## Contrato recomendado del backend
Endpoint:

`POST /api/kaaxpark/assistant`

Body:

```json
{
  "question": "Cuantos cajones libres hay?",
  "systemPrompt": "...",
  "locale": "es-MX",
  "context": {
    "totalCajones": 120,
    "cajonesLibres": 90,
    "cajonesOcupados": 30
  }
}
```

Respuesta:

```json
{
  "answer": "Hay 90 cajones libres.",
  "mode": "remote"
}
```

## Reglas importantes
- Nunca pongas una API key en la app movil.
- El backend debe consultar la base de datos y construir el contexto antes de pedirle al modelo que responda.
- Si el dato no existe, la respuesta debe decirlo sin inventar nada.
- Si cambias la estructura de Firebase, actualiza `ParkingAiContextProvider`.

## Resultado
Con esta arquitectura, la IA puede responder con datos reales y no con texto generico.
