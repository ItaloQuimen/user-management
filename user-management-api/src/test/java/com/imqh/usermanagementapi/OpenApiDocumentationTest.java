package com.imqh.usermanagementapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource("classpath:application-test.properties")
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper mapper;

    @Test
    void generatedContractDescribesPublicRegistrationAndJsonErrors() throws Exception {
        var document = document();
        assertEquals("3.1.0", document.get("openapi").asString());
        assertEquals("/", document.get("servers").get(0).get("url").asString());
        assertEquals(Set.of("/users"), document.get("paths").propertyNames());
        var operation = document.at("/paths/~1users/post");
        assertFalse(document.has("security"));
        assertFalse(operation.has("security"));
        assertTrue(operation.get("requestBody").get("required").asBoolean());
        var responses = operation.get("responses");
        assertEquals(Set.of("201", "400", "404", "405", "406", "409", "415", "500"), responses.propertyNames());
        assertEquals("#/components/schemas/UserResponse",
                responses.at("/201/content/application~1json/schema/$ref").asString());
        for (String code : Set.of("400", "404", "405", "406", "409", "415", "500")) {
            assertEquals(Set.of("application/json"), responses.get(code).get("content").propertyNames());
            assertEquals("#/components/schemas/ApiError",
                    responses.get(code).at("/content/application~1json/schema/$ref").asString());
        }
        assertEquals("El correo ya registrado", responses.at("/409/content/application~1json/examples/ejemplo/value/mensaje").asString());
        var error = document.at("/components/schemas/ApiError");
        assertEquals(Set.of("mensaje"), error.get("properties").propertyNames());
        assertEquals(mapper.readTree("[\"mensaje\"]"), error.get("required"));
        assertFalse(error.get("additionalProperties").asBoolean());
        assertEquals(1, error.at("/properties/mensaje/minLength").asInt());
    }

    @Test
    void schemasPreserveActualFieldsAndAvoidInventingRestrictions() throws Exception {
        var schemas = document().at("/components/schemas");
        var request = schemas.get("UserRequest");
        assertEquals(Set.of("name", "email", "password", "phones"), request.get("properties").propertyNames());
        assertEquals(4, request.get("required").size());
        var password = request.at("/properties/password");
        assertTrue(password.get("writeOnly").asBoolean());
        assertFalse(password.has("pattern"));
        assertFalse(password.has("maxLength"));
        assertTrue(password.get("description").asString().contains("app.password.regex"));
        var phones = request.at("/properties/phones");
        assertFalse(phones.has("minItems"));
        assertFalse(phones.has("maxItems"));
        assertTrue(phones.get("description").asString().contains("[]"));
        for (String phoneSchema : Set.of("PhoneRequest", "PhoneResponse")) {
            var phone = schemas.get(phoneSchema);
            assertEquals(Set.of("number", "citycode", "contrycode"), phone.get("properties").propertyNames());
            assertEquals(3, phone.get("required").size());
            for (var field : phone.get("properties")) {
                assertFalse(field.has("pattern"));
                assertFalse(field.has("maxLength"));
            }
        }
        var response = schemas.get("UserResponse");
        assertEquals(Set.of("id", "name", "email", "phones", "created", "modified", "last_login", "token", "isactive"),
                response.get("properties").propertyNames());
        assertEquals(9, response.get("required").size());
        for (String date : Set.of("created", "modified", "last_login")) {
            var field = response.get("properties").get(date);
            assertEquals("string", field.get("type").asString());
            assertFalse(field.has("format"), "LocalDateTime must not claim the RFC 3339 date-time format");
            assertTrue(field.get("description").asString().contains("sin zona ni offset"));
        }
    }

    private JsonNode document() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json);
    }

    @Test
    void staticYamlIsTheSameContractAsGeneratedJsonAndYaml() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String yaml = mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String snapshot = Files.readString(Path.of("..", "user-management-api-openapi.yaml"), StandardCharsets.UTF_8);
        // Swagger aporta estos lectores de OpenAPI 3.1; la serialización HTTP de la API sigue con Jackson 3.
        var expected = io.swagger.v3.core.util.Json31.mapper().readTree(json);
        var yamlMapper = io.swagger.v3.core.util.Yaml31.mapper();
        assertEquals(expected, yamlMapper.readTree(yaml));
        assertEquals(expected, yamlMapper.readTree(snapshot), "Refresh the static YAML from the running application");
    }
}
