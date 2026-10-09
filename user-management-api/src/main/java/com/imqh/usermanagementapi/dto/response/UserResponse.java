package com.imqh.usermanagementapi.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

public class UserResponse {

    @Schema(description = "UUID del usuario.", format = "uuid", example = "e2a5f0d8-3d3b-4d0a-9a8b-3d7a0f7e0c99", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;
    @Schema(example = "Juan Perez", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(description = "Correo almacenado sin normalización.", example = "juan@p.cl", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
    @Schema(description = "Teléfonos registrados; puede estar vacía.", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<PhoneResponse> phones;
    @Schema(implementation = String.class, description = "Fecha local ISO-8601 sin zona ni offset. Igual a modified y last_login al registrar.",
            example = "2026-10-09T14:00:00.123456789", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime created;
    @Schema(implementation = String.class, description = "Fecha local ISO-8601 sin zona ni offset. Igual a created al registrar.",
            example = "2026-10-09T14:00:00.123456789", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime modified;
    private LocalDateTime lastLogin;
    @Schema(description = "JWT completo firmado con HS512 y persistido. Contiene sub (UUID), email, iat y exp. No habilita autenticación en esta API.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String token;
    private boolean active;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public List<PhoneResponse> getPhones() {
        return phones;
    }

    public void setPhones(List<PhoneResponse> phones) {
        this.phones = phones;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDateTime getCreated() {
        return created;
    }

    public void setCreated(LocalDateTime created) {
        this.created = created;
    }

    public LocalDateTime getModified() {
        return modified;
    }

    public void setModified(LocalDateTime modified) {
        this.modified = modified;
    }

    @JsonProperty("last_login")
    @Schema(implementation = String.class, description = "Fecha local ISO-8601 sin zona ni offset. Igual a created al registrar; no representa un flujo de login.",
            example = "2026-10-09T14:00:00.123456789", requiredMode = Schema.RequiredMode.REQUIRED)
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    @JsonProperty("isactive")
    @Schema(description = "Estado inicial del usuario: true.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
