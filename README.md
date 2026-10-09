# user-management
Este proyecto es una API RESTful para la gestión de usuarios desarrollada con Spring Boot, Spring Data JPA, Hibernate, JWT, y Swagger UI. Utiliza H2 como base de datos en memoria y cuenta con pruebas unitarias e integración utilizando JUnit 6, Mockito y Spring Security Test.

Tecnologías utilizadas:
- Java: 21 LTS
- Spring Boot: 4.1.1
- Maven: 3.9.9 mediante el wrapper incluido
- Base de datos: H2 Database (en memoria)
- Seguridad: Spring Security
- JWT: JJWT 0.13.0 (HS512)
- JSON de la API: Jackson 3
- OpenAPI y Swagger UI: springdoc-openapi 3.1.1
- Herramientas de prueba: JUnit 6, Mockito

Prerrequisitos de ejecución

Instalar un JDK 21 y configurar `JAVA_HOME` para ese JDK. En IntelliJ, seleccionar también Java 21 como SDK del proyecto y JDK de Maven. En PowerShell, reemplazar la ruta de ejemplo por la instalación local:

```powershell
$env:JAVA_HOME = 'C:\ruta\al\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

Configuración Inicial

1- Clonar el Repositorio:

git clone https://github.com/Italo-Quimen/user-management.git
cd user-management-api

## Configuración de JWT, pruebas y arranque

El archivo `user-management-api/src/main/resources/application.properties` incluye H2 y la expresión regular de contraseña. Para ejecutar la aplicación se necesita una clave JWT externa; no hay una clave operativa predeterminada.

### Clave y duración

- `JWT_SECRET_BASE64` configura `app.jwt.secret-base64`: Base64 estándar con padding (`=` cuando corresponda), sin espacios ni saltos de línea. Debe representar al menos 64 bytes, el mínimo para HS512; no basta una cadena de 64 caracteres.
- `JWT_EXPIRATION_SECONDS` configura `app.jwt.expiration-seconds`: entero positivo en segundos. Su valor predeterminado es `3600` (una hora). Se rechazan valores no enteros, no positivos o que desborden la fecha de vencimiento.

Generar una clave local de 64 bytes mediante un generador criptográfico y guardarla solo en el entorno de la sesión de PowerShell, sin imprimirla:

```powershell
$jwtKeyBytes = New-Object byte[] 64
$jwtRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtRandom.GetBytes($jwtKeyBytes)
    $env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtKeyBytes)
} finally {
    $jwtRandom.Dispose()
    [Array]::Clear($jwtKeyBytes, 0, $jwtKeyBytes.Length)
}
$env:JWT_EXPIRATION_SECONDS = '3600'
```

Conservar la misma clave mientras se necesite verificar los tokens emitidos. Generar otra cambia la clave de firma. No guardar la clave en Git, compartirla ni pasarla como argumento de línea de comandos. El arranque falla con un diagnóstico de la propiedad si falta la clave o el formato, tamaño o duración son inválidos; el diagnóstico no incluye su contenido.

### Pruebas y arranque desde PowerShell

Desde el directorio del módulo `user-management-api`, con el JDK 21 configurado según los prerrequisitos:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd -B clean verify
java -jar .\target\user-management-api-0.0.1-SNAPSHOT.jar
```

Las pruebas no necesitan una clave local: los contextos de integración cargan explícitamente `src/test/resources/application-test.properties`, con precedencia sobre las variables del entorno. Esa clave es pública y solo para pruebas; el archivo y su clave no se incluyen en el JAR. Las pruebas verifican firma, claims, vencimiento, rechazo de tokens/configuración inválidos y persistencia del mismo token, además del contrato y las transacciones.

Antes de ejecutar el JAR, generar la clave en esa misma sesión con el bloque anterior. La aplicación arranca en `http://localhost:8080` sobre H2 vacía. La documentación generada está en `/v3/api-docs` y Swagger UI en `/swagger-ui/index.html`. Para comprobar el registro público con los datos del ejemplo:

```powershell
$registrationBody = '{"name":"Juan Perez","email":"juan@p.cl","password":"Password1","phones":[{"number":"1234567","citycode":"1","contrycode":"57"}]}'
$registration = Invoke-WebRequest -UseBasicParsing -Method Post -Uri 'http://localhost:8080/users' -ContentType 'application/json' -Headers @{ Accept = 'application/json' } -Body $registrationBody
$registration.StatusCode
```

La primera petición devuelve 201; repetirla devuelve 409. El JWT firmado contiene `sub` (UUID del usuario), `email`, `iat` y `exp` (fechas UTC en segundos). No contiene contraseña ni hash y se almacena completo. El registro sigue siendo público; emitir un JWT no añade login ni autorización.

### Arranque desde IntelliJ

Seleccionar Java 21 como SDK del proyecto, JDK del ejecutor Maven y JRE de la configuración de ejecución de `UserManagementApiApplication`. En **Run > Edit Configurations**, configurar `JWT_SECRET_BASE64` y `JWT_EXPIRATION_SECONDS` en **Environment variables**; usar una clave generada por el bloque de PowerShell anterior, transferida localmente al campo de entorno sin publicarla. Mantener la configuración local sin compartirla en el repositorio. Las variables de una terminal no se propagan a una instancia de IntelliJ ya abierta.

Ejecutar `clean verify` desde Maven o mediante el wrapper en la terminal con Java 21. Las pruebas cargan su configuración propia; al ejecutar la aplicación desde IntelliJ se necesita la clave local. La base H2 en memoria se crea de nuevo en cada proceso, igual que al ejecutar el JAR.

JJWT utiliza los módulos `jjwt-api`, `jjwt-impl` y `jjwt-jackson`. Su adaptador JSON usa Jackson 2 internamente; la API conserva Jackson 3. La configuración modular y el mínimo de clave HS512 se describen en la [documentación oficial de JJWT 0.13.0](https://github.com/jwtk/jjwt/tree/0.13.0#installation).

## Modos de ejecución y consola H2

El arranque sin el perfil `dev` desactiva explícitamente la consola H2 y la impresión de SQL, incluso con DevTools en el classpath. El registro público, los errores JSON y Swagger siguen disponibles. La base continúa en memoria, creada con `schema.sql` y validada por JPA.

Para habilitar las herramientas locales de desarrollo, configurar primero la clave JWT como se explica arriba y ejecutar desde `user-management-api`:

```powershell
java -jar .\target\user-management-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

En IntelliJ, añadir `--spring.profiles.active=dev` en **Program arguments** de la configuración de ejecución. Para volver al modo normal, quitar ese argumento y cualquier activación de `dev` en **Active profiles** o `SPRING_PROFILES_ACTIVE`. Mantener estas opciones en la configuración local del IDE.

El perfil `dev` liga el servidor a `127.0.0.1`, habilita la impresión de SQL y permite la consola en `http://127.0.0.1:8080/h2-console/`. El acceso remoto de la consola permanece deshabilitado. Los frames `SAMEORIGIN` y los ajustes de seguridad de H2 se limitan a la consola en este perfil; las respuestas de la API y Swagger conservan `X-Frame-Options: DENY`.

Para conectar desde la consola, usar estos datos de la base local del mismo proceso:

- Driver Class: `org.h2.Driver`.
- JDBC URL: `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`.
- User Name: `sa`.
- Password: vacío.

Tras registrar un usuario mediante la API, conectar y ejecutar `SELECT id, name, email FROM users;` y `SELECT number, citycode, contrycode FROM phones;` para comprobar sus datos. No usar una URL de base en archivo ni abrir otra consola H2 en un proceso separado: la base en memoria pertenece a esta aplicación. La consola devuelve HTML como herramienta auxiliar; el contrato JSON de la API se conserva. Al detener el proceso se pierden los datos.

## Persistencia

El esquema H2 se crea al arrancar mediante [schema.sql](user-management-api/src/main/resources/schema.sql). Spring ejecuta el script antes de inicializar JPA; Hibernate valida las tablas y sus mapeos con `spring.jpa.hibernate.ddl-auto=validate`. La base está en memoria y sus datos desaparecen al terminar el proceso.

El registro guarda usuario, teléfonos y token en una única transacción. La relación de cada teléfono con su usuario es obligatoria y el correo tiene la restricción única `uk_users_email`. Tanto el duplicado detectado previamente como el conflicto de correo durante la escritura devuelven 409; otros errores de integridad devuelven 500 sin detalles internos.

El token se conserva íntegro en una columna `CLOB`, sin el límite anterior de 255 caracteres. Las fechas usan `TIMESTAMP(9)` para preservar la precisión de `LocalDateTime`. Los demás textos mantienen sus columnas de 255 caracteres; no se añaden reglas de validación de entrada por este ajuste del esquema.

## Uso de la API

### Registro de usuario

El registro es público. Enviar `POST /users` con `Content-Type: application/json` y `Accept: application/json`:

```json
{
  "name": "Juan Perez",
  "email": "juan@p.cl",
  "password": "Password1",
  "phones": [
    {
      "number": "1234567",
      "citycode": "1",
      "contrycode": "57"
    }
  ]
}
```

`name`, `email` y `password` son obligatorios y no pueden estar en blanco. El correo debe respetar el formato configurado en la validación. La política predeterminada de contraseña exige al menos ocho caracteres alfanuméricos, una letra y un dígito; se puede cambiar con `app.password.regex`. `Password1` cumple esa política; `hunter2` no la cumple.

`phones` debe existir y no ser nulo; se permite `[]`. Sus elementos no pueden ser nulos y cada teléfono debe incluir `number`, `citycode` y `contrycode` con texto no blanco. No se exige un formato numérico para esos campos.

La respuesta HTTP 201 contiene `name`, `email`, `phones`, `id` (UUID), `created`, `modified`, `last_login`, `token` e `isactive`. Las tres fechas iniciales son iguales y se serializan en ISO-8601 sin zona horaria. No se devuelve contraseña, hash ni relaciones internas.

### Errores

Todos los errores de la API devuelven JSON con un único campo `mensaje`, incluso si `Accept` solicita otro formato:

```json
{"mensaje": "El correo ya registrado"}
```

- 400: datos inválidos, JSON malformado o cuerpo ausente.
- 409: correo ya registrado, con el mensaje exacto del ejemplo. Se compara el correo sin normalización automática.
- 404: ruta inexistente.
- 405: método no admitido.
- 406: formato de respuesta solicitado incompatible.
- 415: contenido de entrada distinto de JSON.
- 500: error inesperado, con mensaje genérico y sin detalles internos.
