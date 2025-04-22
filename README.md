# user-management
Este proyecto es una API RESTful para la gestión de usuarios desarrollada con Spring Boot, Spring Data JPA, Hibernate, JWT, y Swagger UI. Utiliza H2 como base de datos en memoria y cuenta con pruebas unitarias e integración utilizando JUnit 5, Mockito y Spring Security Test.

Tecnologías utilizadas:
•	Java: 17
•	Spring Boot: 3.4.3
•	Maven: 3.9.9
•	Base de datos: H2 Database (en memoria)
•	Seguridad: Spring Security
•	JWT: io.jsonwebtoken 0.9.1
•	Herramientas de prueba: JUnit 5, Mockito

Configuración Inicial

1- Clonar el Repositorio:

git clone https://github.com/Italo-Quimen/user-management.git
cd user-management-api

2- Configuración de Propiedades:

El archivo src/main/resources/application.properties ya incluye la configuración para:

Base de datos H2.
JWT (clave secreta y tiempo de expiración).
Validación de la contraseña (expresión regular).

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
