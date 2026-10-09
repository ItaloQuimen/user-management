package com.imqh.usermanagementapi.controller;

import com.imqh.usermanagementapi.config.SecurityConfig;
import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.PhoneResponse;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.exception.CustomException;
import com.imqh.usermanagementapi.exception.DuplicateEmailException;
import com.imqh.usermanagementapi.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    private static final String VALID_JSON = """
            {"name":"Test User","email":"test@example.com","password":"Password1",
             "phones":[{"number":"1234567","citycode":"1","contrycode":"56"}]}
            """;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @Autowired
    private FilterChainProxy securityFilterChain;
    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;
    @MockitoBean
    private UserService userService;

    @Test
    void registerUser_returnsExplicitContractWithJackson3AndRealSecurity() throws Exception {
        assertTrue(handlerAdapter.getMessageConverters().stream()
                .anyMatch(JacksonJsonHttpMessageConverter.class::isInstance));
        assertFalse(securityFilterChain.getFilterChains().isEmpty());
        UserResponse response = response();
        when(userService.registerUser(any(UserRequest.class))).thenReturn(response);

        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(response.getId()))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.phones[0].number").value("1234567"))
                .andExpect(jsonPath("$.phones[0].citycode").value("1"))
                .andExpect(jsonPath("$.phones[0].contrycode").value("56"))
                .andExpect(jsonPath("$.token").value("dummy-token"))
                .andExpect(jsonPath("$.isactive").value(true))
                .andExpect(jsonPath("$.created").value("2026-10-09T12:30:00"))
                .andExpect(jsonPath("$.modified").value("2026-10-09T12:30:00"))
                .andExpect(jsonPath("$.last_login").value("2026-10-09T12:30:00"))
                .andExpect(jsonPath("$.lastLogin").doesNotExist())
                .andExpect(jsonPath("$.isActive").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(jsonPath("$.phones[0].id").doesNotExist())
                .andExpect(jsonPath("$.phones[0].user").doesNotExist())
                .andExpect(result -> {
                    var json = jsonMapper.readTree(result.getResponse().getContentAsString());
                    assertEquals(Set.of("id", "name", "email", "phones", "created", "modified",
                            "last_login", "token", "isactive"), json.propertyNames());
                    assertEquals(Set.of("number", "citycode", "contrycode"),
                            json.get("phones").get(0).propertyNames());
                });

        ArgumentCaptor<UserRequest> captured = ArgumentCaptor.forClass(UserRequest.class);
        verify(userService).registerUser(captured.capture());
        UserRequest request = captured.getValue();
        assertEquals("Test User", request.getName());
        assertEquals("test@example.com", request.getEmail());
        assertEquals("Password1", request.getPassword());
        assertEquals(1, request.getPhones().size());
        assertEquals("1234567", request.getPhones().getFirst().getNumber());
        assertEquals("1", request.getPhones().getFirst().getCitycode());
        assertEquals("56", request.getPhones().getFirst().getContrycode());
    }

    @Test
    void registerUser_acceptsEmptyPhones() throws Exception {
        UserResponse response = response();
        response.setPhones(List.of());
        when(userService.registerUser(any(UserRequest.class))).thenReturn(response);
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON.replace(
                                "[{\"number\":\"1234567\",\"citycode\":\"1\",\"contrycode\":\"56\"}]", "[]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.phones").isEmpty());
        ArgumentCaptor<UserRequest> captured = ArgumentCaptor.forClass(UserRequest.class);
        verify(userService).registerUser(captured.capture());
        assertTrue(captured.getValue().getPhones().isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequests")
    void registerUser_rejectsInvalidDataBeforeCallingService(String scenario, String json) throws Exception {
        assertError(post("/users").contentType(MediaType.APPLICATION_JSON).content(json), 400);
        verifyNoInteractions(userService);
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of("nombre en blanco", VALID_JSON.replace("\"Test User\"", "\"   \"")),
                Arguments.of("nombre nulo", VALID_JSON.replace("\"Test User\"", "null")),
                Arguments.of("nombre ausente", VALID_JSON.replace("\"name\":\"Test User\",", "")),
                Arguments.of("correo inválido", VALID_JSON.replace("test@example.com", "invalid")),
                Arguments.of("correo en blanco", VALID_JSON.replace("test@example.com", "   ")),
                Arguments.of("correo nulo", VALID_JSON.replace("\"test@example.com\"", "null")),
                Arguments.of("correo ausente", VALID_JSON.replace("\"email\":\"test@example.com\",", "")),
                Arguments.of("contraseña en blanco", VALID_JSON.replace("Password1", "   ")),
                Arguments.of("contraseña nula", VALID_JSON.replace("\"Password1\"", "null")),
                Arguments.of("contraseña ausente", VALID_JSON.replace("\"password\":\"Password1\",", "")),
                Arguments.of("teléfonos ausentes", "{\"name\":\"Test User\",\"email\":\"test@example.com\",\"password\":\"Password1\"}"),
                Arguments.of("teléfonos nulos", withPhones("null")),
                Arguments.of("elemento nulo", withPhones("[null]")),
                Arguments.of("teléfono incompleto", withPhones("[{}]")),
                Arguments.of("número en blanco", VALID_JSON.replace("1234567", "   ")),
                Arguments.of("citycode en blanco", VALID_JSON.replace("\"citycode\":\"1\"", "\"citycode\":\"   \"")),
                Arguments.of("contrycode en blanco", VALID_JSON.replace("\"contrycode\":\"56\"", "\"contrycode\":\"   \"")),
                Arguments.of("número nulo", VALID_JSON.replace("\"1234567\"", "null")),
                Arguments.of("citycode nulo", VALID_JSON.replace("\"citycode\":\"1\"", "\"citycode\":null")),
                Arguments.of("contrycode nulo", VALID_JSON.replace("\"contrycode\":\"56\"", "\"contrycode\":null")));
    }

    @Test
    void registerUser_doesNotRequireNumericPhoneFields() throws Exception {
        when(userService.registerUser(any(UserRequest.class))).thenReturn(response());
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_JSON.replace("1234567", "extension A")
                                .replace("\"citycode\":\"1\"", "\"citycode\":\"Santiago\"")
                                .replace("\"contrycode\":\"56\"", "\"contrycode\":\"Chile\"")))
                .andExpect(status().isCreated());
        ArgumentCaptor<UserRequest> captured = ArgumentCaptor.forClass(UserRequest.class);
        verify(userService).registerUser(captured.capture());
        assertEquals("extension A", captured.getValue().getPhones().getFirst().getNumber());
        assertEquals("Santiago", captured.getValue().getPhones().getFirst().getCitycode());
        assertEquals("Chile", captured.getValue().getPhones().getFirst().getContrycode());
    }

    @Test
    void registerUser_summarizesMultipleViolationsDeterministically() throws Exception {
        String json = VALID_JSON.replace("test@example.com", "");
        String expected = "El correo es obligatorio; El correo no tiene un formato válido (ej: aaaaaaa@dominio.cl)";
        for (int attempt = 0; attempt < 2; attempt++) {
            assertError(post("/users").contentType(MediaType.APPLICATION_JSON).content(json), 400)
                    .andExpect(jsonPath("$.mensaje").value(expected));
        }
        verifyNoInteractions(userService);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("protocolErrors")
    void returnsJsonForHttpErrors(String scenario, MockHttpServletRequestBuilder request, int status)
            throws Exception {
        assertError(request, status);
        verifyNoInteractions(userService);
    }

    static Stream<Arguments> protocolErrors() {
        return Stream.of(
                Arguments.of("JSON malformado", post("/users").contentType(MediaType.APPLICATION_JSON).content("{"), 400),
                Arguments.of("cuerpo ausente", post("/users").contentType(MediaType.APPLICATION_JSON), 400),
                Arguments.of("JSON nulo", post("/users").contentType(MediaType.APPLICATION_JSON).content("null"), 400),
                Arguments.of("contenido no JSON", post("/users").contentType(MediaType.TEXT_PLAIN).content(VALID_JSON), 415),
                Arguments.of("Accept incompatible", post("/users").contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_XML).content(VALID_JSON), 406),
                Arguments.of("método no admitido", get("/users"), 405),
                Arguments.of("ruta inexistente", get("/api/missing"), 404),
                Arguments.of("ruta inexistente con Accept incompatible", get("/api/missing").accept(MediaType.TEXT_HTML), 404),
                Arguments.of("contenido no JSON y Accept incompatible", post("/users").contentType(MediaType.TEXT_PLAIN)
                        .accept(MediaType.APPLICATION_XML).content(VALID_JSON), 415));
    }

    @Test
    void duplicateEmailIsConflictWithExactMessage() throws Exception {
        when(userService.registerUser(any(UserRequest.class))).thenThrow(new DuplicateEmailException());
        assertError(post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON), 409)
                .andExpect(jsonPath("$.mensaje").value("El correo ya registrado"));
    }

    @Test
    void invalidPasswordIsBadRequest() throws Exception {
        when(userService.registerUser(any(UserRequest.class)))
                .thenThrow(new CustomException("La contraseña no cumple con el formato requerido"));
        assertError(post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON), 400)
                .andExpect(jsonPath("$.mensaje").value("La contraseña no cumple con el formato requerido"));
    }

    @Test
    void unexpectedFailureDoesNotExposeInternalDetails() throws Exception {
        when(userService.registerUser(any(UserRequest.class)))
                .thenThrow(new IllegalStateException("SQL password=Password1 token=secret"));
        assertError(post("/users").contentType(MediaType.APPLICATION_JSON).content(VALID_JSON), 500)
                .andExpect(jsonPath("$.mensaje").value("Ocurrió un error interno"));
    }

    private ResultActions assertError(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return mockMvc.perform(request)
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    var body = jsonMapper.readTree(result.getResponse().getContentAsString());
                    assertEquals(Set.of("mensaje"), body.propertyNames());
                    assertTrue(body.get("mensaje").isString());
                    assertFalse(body.get("mensaje").asString().isBlank());
                });
    }

    private static String withPhones(String phones) {
        return "{\"name\":\"Test User\",\"email\":\"test@example.com\",\"password\":\"Password1\",\"phones\":" + phones + "}";
    }

    private UserResponse response() {
        UserResponse response = new UserResponse();
        response.setId("bcac0916-a23b-49a4-b442-ea99b2586c9a");
        response.setName("Test User");
        response.setEmail("test@example.com");
        response.setPhones(List.of(new PhoneResponse("1234567", "1", "56")));
        response.setToken("dummy-token");
        response.setActive(true);
        LocalDateTime registeredAt = LocalDateTime.of(2026, 10, 9, 12, 30);
        response.setCreated(registeredAt);
        response.setModified(registeredAt);
        response.setLastLogin(registeredAt);
        return response;
    }
}
