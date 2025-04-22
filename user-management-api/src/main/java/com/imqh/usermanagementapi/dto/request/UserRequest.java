package com.imqh.usermanagementapi.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class UserRequest {

    @NotEmpty(message = "El nombre es obligatorio")
    private String name;

    @NotEmpty(message = "El correo es obligatorio")
    @Pattern(
            regexp = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$",
            message = "El correo no tiene un formato válido (ej: aaaaaaa@dominio.cl)"
    )
    private String email;

    @NotEmpty(message = "La contraseña es obligatoria")
    private String password;

    @NotNull(message = "La lista de teléfonos no puede ser nula")
    private List<PhoneRequest> phones;

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
