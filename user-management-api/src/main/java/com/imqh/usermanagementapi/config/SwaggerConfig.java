package com.imqh.usermanagementapi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        var error = new ObjectSchema();
        error.setDescription("Error de la API con un único mensaje no vacío, sin detalles internos.");
        error.addProperty("mensaje", new StringSchema().minLength(1).example("El correo ya registrado"));
        error.addRequiredItem("mensaje");
        error.setAdditionalProperties(false);
        return new OpenAPI()
                .addServersItem(new Server().url("/").description("Servidor donde se ejecuta la aplicación"))
                .components(new Components().addSchemas("ApiError", error))
                .info(new Info()
                        .title("User Management API")
                        .version("1.0")
                        .description("Registro público de usuarios con persistencia en H2 en memoria. "
                                + "Emite y almacena un JWT HS512; no implementa login ni autorización mediante el token. "
                                + "Los errores de la API siempre contienen únicamente mensaje en JSON."));
    }
}
