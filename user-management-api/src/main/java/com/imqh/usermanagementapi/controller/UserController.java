package com.imqh.usermanagementapi.controller;

import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    @Operation(summary = "Registrar un usuario", tags = "Usuarios",
            description = "Registro público, sin autenticación. Persiste usuario, teléfonos y JWT en una transacción. "
                    + "phones es obligatorio, pero admite una lista vacía. "
                    + "La contraseña se valida con app.password.regex; la política predeterminada exige "
                    + "ocho o más caracteres alfanuméricos ASCII, al menos una letra y un dígito.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado. Las tres fechas locales iniciales son iguales.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos, JSON malformado o cuerpo ausente.",
                    content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ApiError"),
                            examples = @ExampleObject(name = "ejemplo", value = "{\"mensaje\":\"La contraseña no cumple con el formato requerido\"}"))),
            @ApiResponse(responseCode = "409", description = "Correo duplicado, detectado antes o durante la escritura. Sin normalización del correo.",
                    content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ApiError"),
                            examples = @ExampleObject(name = "ejemplo", value = "{\"mensaje\":\"El correo ya registrado\"}"))),
            @ApiResponse(responseCode = "404", description = "Error global de la API: ruta inexistente; no es un resultado del registro válido.",
                    content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ApiError"),
                            examples = @ExampleObject(name = "ejemplo", value = "{\"mensaje\":\"La ruta solicitada no existe\"}"))),
            @ApiResponse(responseCode = "405", description = "Error global de la API: método no admitido, por ejemplo GET /users.",
                    content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ApiError"),
                            examples = @ExampleObject(name = "ejemplo", value = "{\"mensaje\":\"El método solicitado no está permitido\"}"))),
            @ApiResponse(responseCode = "406", description = "Accept incompatible; el cuerpo del error sigue siendo JSON.",
                    content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ApiError"),
                            examples = @ExampleObject(name = "ejemplo", value = "{\"mensaje\":\"La respuesta solo está disponible en JSON\"}"))),
            @ApiResponse(responseCode = "415", description = "Content-Type incompatible con JSON.",
                    content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ApiError"),
                            examples = @ExampleObject(name = "ejemplo", value = "{\"mensaje\":\"El contenido de la solicitud debe ser JSON\"}"))),
            @ApiResponse(responseCode = "500", description = "Error inesperado, incluida integridad distinta del correo duplicado. Sin detalles internos.",
                    content = @Content(mediaType = "application/json", schema = @Schema(ref = "#/components/schemas/ApiError"),
                            examples = @ExampleObject(name = "ejemplo", value = "{\"mensaje\":\"Ocurrió un error interno\"}")))
    })
    public ResponseEntity<UserResponse> registerUser(@Valid @RequestBody UserRequest userRequest) {
        UserResponse response = userService.registerUser(userRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
}
