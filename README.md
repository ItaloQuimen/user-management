# user-management
Este proyecto es una API RESTful para la gestión de usuarios desarrollada con Spring Boot, Spring Data JPA, Hibernate, JWT, y Swagger UI. Utiliza H2 como base de datos en memoria y cuenta con pruebas unitarias e integración utilizando JUnit 6, Mockito y Spring Security Test.

Tecnologías utilizadas:
- Java: 21 LTS
- Spring Boot: 4.1.1
- Maven: 3.9.9 mediante el wrapper incluido
- Base de datos: H2 Database (en memoria)
- Seguridad: Spring Security
- JWT: io.jsonwebtoken 0.9.1
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

2- Configuración de Propiedades:

El archivo src/main/resources/application.properties ya incluye la configuración para:

Base de datos H2.
JWT (clave secreta y tiempo de expiración).
Validación de la contraseña (expresión regular).

Desde el directorio del módulo `user-management-api`, comprobar que Maven utiliza Java 21, ejecutar todas las pruebas y generar el artefacto:

```powershell
.\mvnw.cmd -version
.\mvnw.cmd clean verify
java -jar .\target\user-management-api-0.0.1-SNAPSHOT.jar
```

La aplicación arranca en `http://localhost:8080`. La documentación generada está en `/v3/api-docs` y Swagger UI en `/swagger-ui/index.html`.

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
