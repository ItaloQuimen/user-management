package com.imqh.usermanagementapi.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Object> handleDuplicateEmail(DuplicateEmailException ex) {
        return errorResponse(ex.getMessage(), new HttpHeaders(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Object> handleBusinessError(CustomException ex) {
        return errorResponse(ex.getMessage(), new HttpHeaders(), HttpStatus.BAD_REQUEST);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null
                        ? "Los datos no son válidos" : error.getDefaultMessage())
                .distinct()
                .sorted()
                .collect(Collectors.joining("; "));
        return errorResponse(message, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String message = switch (status.value()) {
            case 400 -> "El cuerpo de la solicitud no es válido";
            case 404 -> "La ruta solicitada no existe";
            case 405 -> "El método solicitado no está permitido";
            case 406 -> "La respuesta solo está disponible en JSON";
            case 415 -> "El contenido de la solicitud debe ser JSON";
            default -> "Ocurrió un error interno";
        };
        return errorResponse(message, headers, status);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedError(Exception ex) {
        return errorResponse("Ocurrió un error interno", new HttpHeaders(), HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Object> errorResponse(String message, HttpHeaders headers, HttpStatusCode status) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        // El contrato de errores exige JSON incluso cuando Accept es incompatible.
        responseHeaders.setContentType(MediaType.APPLICATION_JSON);
        String safeMessage = message == null || message.isBlank() ? "Los datos no son válidos" : message;
        return new ResponseEntity<>(Map.of("mensaje", safeMessage), responseHeaders, status);
    }
}
