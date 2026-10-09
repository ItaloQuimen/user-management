# user-management

API REST de registro público de usuarios. Recibe datos en JSON, valida la entrada y guarda usuario, teléfonos y un JWT en H2 en memoria. No implementa CRUD completo, login ni autorización mediante el token.

El proyecto incluye pruebas de contrato, persistencia y seguridad JWT, documentación OpenAPI y una [explicación de la arquitectura y del flujo](docs/arquitectura.md).

## Tecnologías y requisitos

- Java 21 LTS.
- Spring Boot 4.1.1, Spring MVC, Spring Security y JPA/Hibernate.
- Maven 3.9.9 mediante el wrapper incluido; no hace falta instalar Maven por separado.
- H2 en memoria y esquema SQL explícito.
- Jackson 3 para la API, JJWT 0.13.0 con HS512 y BCrypt para contraseñas.
- springdoc-openapi 3.1.1, Swagger UI, JUnit 6 y Mockito.

Instalar Git y un JDK 21. Los ejemplos de terminal están escritos y comprobados para **Windows PowerShell 5.1**. No requieren PowerShell 7 ni `-SkipHttpErrorCheck`.

## Preparar y ejecutar

### 1. Clonar y entrar al módulo

Desde una carpeta de trabajo:

```powershell
git clone https://github.com/ItaloQuimen/user-management.git
cd user-management/user-management-api
```

Los comandos de Maven y del JAR que siguen se ejecutan desde ese módulo.

### 2. Seleccionar Java 21

Reemplazar la ruta por la instalación local:

```powershell
$env:JAVA_HOME = 'C:\ruta\al\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
.\mvnw.cmd -version
```

Ambos comandos deben informar Java 21. En IntelliJ, seleccionar también Java 21 como SDK del proyecto y JDK del ejecutor Maven.

### 3. Ejecutar pruebas y construir

```powershell
.\mvnw.cmd -B clean verify
```

El resultado incluye `target/user-management-api-0.0.1-SNAPSHOT.jar`. La suite comprueba contrato JSON, validaciones, BCrypt, JWT real, persistencia, conflicto de unicidad, rollback, modos de ejecución y documentación.

Las pruebas usan configuración y clave exclusivas de `src/test/resources/application-test.properties`, con precedencia sobre el entorno. Esa clave es pública, solo para pruebas y no se incluye en el JAR. No se necesita una clave operativa para ejecutar la suite.

### 4. Configurar JWT en la terminal del servidor

No hay un secreto operativo predeterminado. Generar 64 bytes aleatorios y guardar su Base64 únicamente en el entorno de esta sesión, sin imprimirlo:

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

- `JWT_SECRET_BASE64` configura `app.jwt.secret-base64`: Base64 estándar canónico, con padding cuando corresponda y sin espacios ni saltos. Debe representar al menos 64 bytes efectivos; una cadena de 64 caracteres no garantiza ese tamaño.
- `JWT_EXPIRATION_SECONDS` configura `app.jwt.expiration-seconds`: entero positivo en segundos, con valor predeterminado `3600`. Se rechazan duración inválida y valores que desborden el vencimiento.

La aplicación rechaza al iniciar una clave ausente, mal formada o insuficiente y una duración inválida, con diagnóstico de la propiedad sin mostrar el secreto. No guardar claves en Git ni pasarlas como argumentos. Conservar la misma clave para verificar los tokens emitidos; cambiarla invalida su verificación con la nueva clave.

### 5. Iniciar el servidor

En la misma terminal donde se configuró JWT:

```powershell
java -jar .\target\user-management-api-0.0.1-SNAPSHOT.jar
```

Esta terminal queda ocupada por el servidor. Esperar el mensaje de arranque y mantenerla abierta. La aplicación está en `http://localhost:8080`; abrir **otra terminal** para enviar las peticiones del apartado siguiente. Detener el servidor con `Ctrl+C`; al reiniciarlo la base vuelve a estar vacía.

Para habilitar las herramientas locales de desarrollo, usar en lugar del comando anterior:

```powershell
java -jar .\target\user-management-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### Arranque desde IntelliJ

Abrir el módulo Maven. Seleccionar Java 21 como JRE de la ejecución de `UserManagementApiApplication` y como JDK de Maven. En **Run > Edit Configurations > Environment variables**, configurar `JWT_SECRET_BASE64` y `JWT_EXPIRATION_SECONDS` con una clave local generada como arriba. Transferirla localmente sin publicarla; las variables de una terminal no se propagan a IntelliJ ya abierto.

Para desarrollo, añadir `--spring.profiles.active=dev` en **Program arguments**. Para el arranque normal, quitarlo y retirar cualquier activación de `dev` en **Active profiles** o `SPRING_PROFILES_ACTIVE`. Mantener estas configuraciones personales fuera del repositorio. Ejecutar las pruebas mediante el wrapper o `clean verify` desde Maven.

## Probar el registro

En una **segunda terminal de Windows PowerShell 5.1**, definir esta función. Muestra estado y cuerpo tanto en éxito como cuando `Invoke-WebRequest` convierte un error HTTP en excepción:

```powershell
$baseUrl = 'http://localhost:8080'
function Send-Registration {
    param([string]$Body)
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Post -Uri "$baseUrl/users" -ContentType 'application/json' -Headers @{ Accept = 'application/json' } -Body $Body
        [pscustomobject]@{ StatusCode = [int]$response.StatusCode; Body = $response.Content }
    } catch {
        $errorResponse = $_.Exception.Response
        if ($null -eq $errorResponse) { throw }
        $errorBody = $_.ErrorDetails.Message
        if ([string]::IsNullOrWhiteSpace($errorBody)) {
            $reader = New-Object System.IO.StreamReader($errorResponse.GetResponseStream())
            try { $errorBody = $reader.ReadToEnd() } finally { $reader.Dispose() }
        }
        [pscustomobject]@{ StatusCode = [int]$errorResponse.StatusCode; Body = $errorBody }
    }
}
```

### Registro correcto - 201

```powershell
$registrationBody = '{"name":"Juan Perez","email":"juan@p.cl","password":"Password1","phones":[{"number":"1234567","citycode":"1","contrycode":"57"}]}'
$created = Send-Registration -Body $registrationBody
$created | Format-List
```

Devuelve `StatusCode: 201` y el usuario registrado. El token y los identificadores son valores generados en cada ejecución.

### Correo duplicado - 409

Sin reiniciar el servidor, repetir la misma petición:

```powershell
$duplicate = Send-Registration -Body $registrationBody
$duplicate | Format-List
```

El cuerpo es exactamente `{"mensaje":"El correo ya registrado"}`.

### Validación fallida - 400

Con la política de contraseña predeterminada, `hunter2` no alcanza ocho caracteres:

```powershell
$invalidBody = '{"name":"Ana Perez","email":"ana@p.cl","password":"hunter2","phones":[]}'
$invalid = Send-Registration -Body $invalidBody
$invalid | Format-List
```

Devuelve `400` y `{"mensaje":"La contraseña no cumple con el formato requerido"}`. Si se cambia la política, adaptar estos ejemplos a ella.

## Contrato y validación

El único endpoint de negocio es **POST /users**, público y con `Content-Type: application/json` y `Accept: application/json`. No requiere cabecera `Authorization`.

- `name`, `email` y `password`: obligatorios y no blancos.
- `email`: expresión regular `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$`. Se almacena sin normalización; el duplicado se determina por igualdad del valor almacenado.
- `phones`: obligatorio y no nulo; permite `[]`. Sus elementos no pueden ser nulos.
- Cada teléfono incluye `number`, `citycode` y `contrycode` con texto no blanco. No se exige formato numérico ni una cantidad mínima de teléfonos. Se conserva la grafía `contrycode`.

### Política configurable de contraseña

`app.password.regex` se aplica a la contraseña completa. La política predeterminada exige **ocho o más caracteres alfanuméricos ASCII, al menos una letra y un dígito**. No exige mayúscula, minúscula y símbolo simultáneamente; los símbolos no pertenecen al conjunto predeterminado.

Para cambiarla sin editar el producto, pasar una expresión regular Java entre comillas simples al arrancar. Este ejemplo conserva la composición y eleva el mínimo a diez:

```powershell
java -jar .\target\user-management-api-0.0.1-SNAPSHOT.jar '--app.password.regex=^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{10,}$'
```

En IntelliJ, añadir el mismo argumento en **Program arguments**, sin las comillas que usa la terminal para delimitarlo. En un archivo `.properties` hay que duplicar la barra invertida de `\d`, como en [application.properties](user-management-api/src/main/resources/application.properties). El correo no se configura mediante esta propiedad.

### Respuesta de creación

El estado es **201**. La respuesta contiene únicamente los nueve campos siguientes:

| Campos                              | Contenido                                                     |
| ----------------------------------- | ------------------------------------------------------------- |
| `id`                                | UUID del usuario.                                             |
| `name`, `email`, `phones`           | Datos registrados, con los nombres de teléfono anteriores.    |
| `created`, `modified`, `last_login` | Fechas iniciales iguales, ISO-8601 local, sin zona ni offset. |
| `token`                             | JWT completo emitido y persistido.                            |
| `isactive`                          | `true` al registrar.                                          |

No devuelve contraseña, hash ni relaciones internas, ni las variantes `lastLogin` o `isActive`. Las fechas locales pueden incluir fracciones de segundo; no son marcas UTC. Los claims JWT `iat` y `exp`, en cambio, representan instantes como segundos desde la época Unix.

### Errores JSON

El cuerpo contiene únicamente `mensaje`, con texto no vacío. No expone SQL, trazas ni secretos. Los errores de negociación también se devuelven en JSON aunque `Accept` solicite otro formato.

| Estado | Situación                                                       |
| ------ | --------------------------------------------------------------- |
| 400    | Validación, JSON malformado o cuerpo ausente.                   |
| 409    | Correo duplicado antes o durante la escritura.                  |
| 404    | Ruta API inexistente.                                           |
| 405    | Método no admitido, por ejemplo `GET /users`.                   |
| 406    | Formato solicitado por `Accept` incompatible.                   |
| 415    | `Content-Type` incompatible con JSON.                           |
| 500    | Error inesperado, con mensaje genérico y sin detalles internos. |

El 409 usa siempre `El correo ya registrado`; el 500 usa `Ocurrió un error interno`. Un conflicto de integridad distinto del correo no se presenta como duplicado.

## Persistencia y modos de ejecución

[Schema SQL](user-management-api/src/main/resources/schema.sql) crea tablas, relación usuario-teléfonos y restricción `uk_users_email`. Spring lo ejecuta antes de JPA; Hibernate valida con `ddl-auto=validate` y no compite por crear tablas.

Usuario, todos sus teléfonos y token se guardan en una única transacción. El token usa `CLOB` para conservarse completo. Las fechas usan `TIMESTAMP(9)`; los demás textos mantienen columnas de 255 caracteres. La API no añade límites de entrada de 255: valores que excedan la capacidad pueden producir un error de persistencia. H2 es volátil: **los datos se pierden al detener el proceso**.

| Ajuste                  | Normal                            | Perfil `dev` explícito                |
| ----------------------- | --------------------------------- | ------------------------------------- |
| Consola H2              | Desactivada, incluso con DevTools | Habilitada solo para acceso local.    |
| Impresión SQL           | Desactivada                       | Habilitada.                           |
| Dirección del servidor  | Valor predeterminado del servidor | `127.0.0.1`.                          |
| Frames de la consola    | Consola ausente                   | `SAMEORIGIN` solo para la consola.    |
| API y Swagger           | Disponibles, frames `DENY`        | Disponibles, frames `DENY`.           |

En `dev`, abrir `http://127.0.0.1:8080/h2-console/`. El acceso remoto permanece deshabilitado. Datos de conexión:

- Driver Class: `org.h2.Driver`.
- JDBC URL: `jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`.
- User Name: `sa`.
- Password: vacío.

Tras registrar un usuario, conectar y ejecutar `SELECT id, name, email FROM users;` y `SELECT number, citycode, contrycode FROM phones;`. Usar la consola del **mismo proceso**: una consola H2 separada no comparte esta base en memoria. Swagger y H2 son herramientas auxiliares HTML, no endpoints de negocio JSON.

## OpenAPI y Swagger

Con el servidor en ejecución:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`.
- Contrato generado JSON: `http://localhost:8080/v3/api-docs`.
- Contrato generado YAML: `http://localhost:8080/v3/api-docs.yaml`.
- [Copia estática del contrato OpenAPI 3.1.0](user-management-api-openapi.yaml).

Las anotaciones documentales y la configuración OpenAPI son la fuente de la copia. El servidor relativo `/` evita registrar puertos temporales y permite usar Swagger en el mismo origen.

Para actualizar el YAML, construir y arrancar la aplicación actual; después, desde una segunda terminal situada en `user-management-api`:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/v3/api-docs.yaml' -OutFile '..\user-management-api-openapi.yaml'
```

Revisar el diff, los nombres JSON, estados y esquemas contra el JSON generado y las pruebas antes de publicar. No editar la copia para introducir reglas que no tenga la API. El esquema describe la contraseña configurable, no impone una política fija; las fechas locales son cadenas sin el formato RFC 3339 `date-time`, que requiere offset.

## Arquitectura y límites

El [diagrama editable y las responsabilidades](docs/arquitectura.md) muestran validación, controlador, servicio transaccional, JWT, repositorio y H2, incluido el conflicto de correo durante la escritura.

- BCrypt protege la contraseña almacenada; el JWT contiene `sub` (UUID), `email`, `iat` y `exp`, sin contraseña ni hash.
- Firmar y persistir el token no añade login, verificación de peticiones ni autorización de negocio.
- El almacenamiento en memoria, las herramientas locales y el registro público corresponden a una aplicación demostrativa; no se afirma un despliegue productivo.
- JJWT y bibliotecas de documentación usan Jackson 2 internamente; la API conserva Jackson 3. JAXB es una dependencia de Hibernate.

Referencias: [springdoc](https://springdoc.org/), [OpenAPI 3.1.0](https://spec.openapis.org/oas/v3.1.0.html), [JJWT 0.13.0](https://github.com/jwtk/jjwt/tree/0.13.0), [inicialización SQL de Spring Boot](https://docs.spring.io/spring-boot/how-to/data-initialization.html) y [consola H2 con Spring Security](https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.h2-web-console).
