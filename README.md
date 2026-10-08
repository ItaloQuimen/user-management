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

Uso de la API
Registro de Usuario

Para registrar un usuario, realiza una solicitud POST a /users con un cuerpo JSON similar a:

{
"name": "Juan Perez",
"email": "juan@perez.com",
"password": "password88,
"phones": [
{
"number": "1234567",
"citycode": "1",
"contrycode": "57"
}
]
}

Si la validación (por ejemplo, del formato del correo) falla, el GlobalExceptionHandler devolverá un mensaje de error en formato JSON con la clave mensaje y los detalles en errors.

La respuesta exitosa incluirá campos como id, created, modified, lastLogin, token e isActive
