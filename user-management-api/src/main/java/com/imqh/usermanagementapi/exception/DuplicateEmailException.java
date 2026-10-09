package com.imqh.usermanagementapi.exception;

public class DuplicateEmailException extends RuntimeException {

    public DuplicateEmailException() {
        super("El correo ya registrado");
    }
}
