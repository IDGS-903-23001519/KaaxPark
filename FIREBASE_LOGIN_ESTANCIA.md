# Firebase Login y Estancia Cliente

## 1. Objetivo
Este flujo usa:

- `Firebase Authentication` para validar correo y contrasena
- `Cloud Firestore` para guardar perfil, rol y datos de estancia

Despues del login:

- un `ADMIN` entra a `Dashboard`
- un `CLIENTE` entra a `EstanciaVehiculo`

## 2. Dependencias y configuracion
El proyecto ya incluye:

- plugin `com.google.gms.google-services`
- archivo `app/google-services.json`
- `firebase-auth`
- `firebase-firestore`
- permiso `INTERNET`

Si en otro equipo no compila, revisa que el archivo `app/google-services.json` corresponda al mismo proyecto Firebase.

## 3. Crear usuarios en Firebase Authentication
Los documentos en Firestore no sustituyen a Authentication. Para poder iniciar sesion:

1. Abre Firebase Console.
2. Ve a `Authentication`.
3. Entra a `Sign-in method`.
4. Activa `Email/Password`.
5. Ve a `Users`.
6. Crea un usuario por cada correo que quieras usar en la app.

Ejemplo:

- `leslimichel471@gmail.com`
- `miguel.hernandez@gmail.com`

Usa el mismo correo que guardaste en la coleccion `usuarios`.

## 4. Estructura minima en Firestore
### Coleccion `usuarios`
Cada usuario autenticado debe tener un documento con estos campos:

- `email: string`
- `nombre: string`
- `telefono: string`
- `rol: string`
- `estado: string`

Valores validos:

- `rol = ADMIN` o `CLIENTE`
- `estado = ACTIVO` o `INACTIVO`

Ejemplo:

```text
usuarios / uid-cli-001
email = "miguel.hernandez@gmail.com"
nombre = "Miguel Hernandez"
telefono = "9992003001"
rol = "CLIENTE"
estado = "ACTIVO"
```

### Coleccion `vehiculos`

- `usuarioId: string`
- `marca: string`
- `placa: string`
- `color: string`

El campo `usuarioId` debe contener el ID del documento en `usuarios`.

### Coleccion `estancias`

- `usuarioId: string`
- `vehiculoId: string`
- `estatus: string`
- `fechaEntrada: timestamp`

Para que `EstanciaVehiculo` muestre informacion, debe existir al menos una estancia con:

- `usuarioId` igual al documento del cliente
- `estatus = ACTIVA`

## 5. Como funciona el login
`MainActivity` hace este proceso:

1. Lee correo y contrasena.
2. Valida que no esten vacios.
3. Llama a `FirebaseAuth.signInWithEmailAndPassword`.
4. Si Authentication responde bien, consulta `usuarios` en Firestore por `email`.
5. Si no existe documento, bloquea el acceso.
6. Si `estado != ACTIVO`, bloquea el acceso.
7. Si `rol = ADMIN`, navega a `Dashboard`.
8. Si `rol = CLIENTE`, navega a `EstanciaVehiculo`.

Tambien intenta restaurar sesion al reabrir la app.

## 6. Como funciona la pantalla `EstanciaVehiculo`
La pantalla:

1. Lee la sesion local.
2. Revalida el perfil del usuario en Firestore.
3. Busca estancias del usuario.
4. Toma la primera con `estatus = ACTIVA`.
5. Lee el documento del vehiculo asociado.
6. Muestra:
   - marca
   - placa
   - color
   - tiempo transcurrido desde `fechaEntrada`

Si no existe estancia activa:

- no se cae la app
- muestra estado vacio con `--:--:--`

## 7. Como probar
### Caso admin
1. Crea un usuario en `Authentication`.
2. Crea su documento en `usuarios` con `rol = ADMIN`.
3. Inicia sesion en la app.
4. Debe abrir `Dashboard`.

### Caso cliente
1. Crea un usuario en `Authentication`.
2. Crea su documento en `usuarios` con `rol = CLIENTE`.
3. Crea un documento en `vehiculos` con `usuarioId` del cliente.
4. Crea una estancia en `estancias` con:
   - `usuarioId` del cliente
   - `vehiculoId` del vehiculo
   - `estatus = ACTIVA`
   - `fechaEntrada = timestamp actual`
5. Inicia sesion.
6. Debe abrir `EstanciaVehiculo` y mostrar los datos reales.

### Casos de error
Prueba tambien:

- contrasena incorrecta
- usuario sin documento en `usuarios`
- usuario con `estado = INACTIVO`
- cliente sin estancia activa

## 8. Archivos principales tocados
- `app/src/main/java/org/utl/idgs903/appkaaxpark/MainActivity.kt`
- `app/src/main/java/org/utl/idgs903/appkaaxpark/Cliente/EstanciaVehiculo.kt`
- `app/src/main/java/org/utl/idgs903/appkaaxpark/data/FirebaseRepository.kt`
- `app/src/main/java/org/utl/idgs903/appkaaxpark/data/SessionManager.kt`

## 9. Siguiente paso recomendado
El siguiente paso natural es conectar `Codigoqr.kt` para que:

1. valide el QR en `qr_accesos`
2. asigne un cajon disponible
3. cree una nueva estancia
4. cambie el cajon a `OCUPADO`
