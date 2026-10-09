package com.imqh.usermanagementapi.dto.request;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class UserRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Schema(description = "Nombre obligatorio con texto no blanco.", example = "Juan Perez")
    private String name;

    @NotBlank(message = "El correo es obligatorio")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "El correo no tiene un formato válido (ej: aaaaaaa@dominio.cl)"
    )
    @Schema(description = "Correo obligatorio, validado mediante la expresión regular indicada. Se conserva sin normalización.",
            example = "juan@p.cl")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Schema(description = "Texto no blanco validado con app.password.regex. Por defecto: al menos ocho caracteres "
            + "alfanuméricos ASCII, una letra y un dígito. La política puede cambiar; no hay un patrón fijo en este esquema.",
            example = "Password1", accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @NotNull(message = "La lista de teléfonos no puede ser nula")
    @Schema(description = "Lista obligatoria y no nula. Admite [] y no admite elementos nulos; cada teléfono se valida en cascada.")
    private List<@NotNull(message = "Los teléfonos no pueden ser nulos") @Valid PhoneRequest> phones;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<PhoneRequest> getPhones() {
        return phones;
    }

    public void setPhones(List<PhoneRequest> phones) {
        this.phones = phones;
    }
}
