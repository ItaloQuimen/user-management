package com.imqh.usermanagementapi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.devtools.settings.DevToolsSettings;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DevelopmentConfigurationTest {

    @Test
    void normalConfigurationOverridesActualDevToolsDefaults() throws IOException {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new MapPropertySource("devtools",
                DevToolsSettings.get().getPropertyDefaults()));
        environment.getPropertySources().addFirst(new ResourcePropertySource("classpath:application.properties"));
        assertEquals(false, environment.getProperty("spring.h2.console.enabled", Boolean.class));
        assertEquals(false, environment.getProperty("spring.jpa.show-sql", Boolean.class));
    }

    @ParameterizedTest(name = "development mode = {0}")
    @ValueSource(booleans = {false, true})
    void realServerPreservesApiAndScopesConsoleToDevelopment(boolean development) throws Exception {
        try (var context = startApplication(development);
             var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
                     .connectTimeout(Duration.ofSeconds(5)).build()) {
            String baseUrl = "http://127.0.0.1:" + context.getWebServer().getPort();
            var environment = context.getEnvironment();
            assertEquals(development, environment.getProperty("spring.h2.console.enabled", Boolean.class));
            assertEquals(development, environment.getProperty("spring.jpa.show-sql", Boolean.class));
            assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("classpath:schema.sql", environment.getProperty("spring.sql.init.schema-locations"));
            assertEquals(development, context.containsBean("h2ConsoleFilterChain"));
            if (development) {
                assertEquals("127.0.0.1", environment.getProperty("server.address"));
                assertEquals(false, environment.getProperty("spring.h2.console.settings.web-allow-others", Boolean.class));
            }

            String email = "console-read@example.org";
            String registration = """
                    {"name":"Mode Test","email":"console-read@example.org","password":"Password1",
                     "phones":[{"number":"1234567","citycode":"1","contrycode":"56"}]}
                    """;
            var created = send(client, baseUrl, "POST", "/users", "application/json", "application/json", registration);
            assertEquals(201, created.statusCode());
            assertFramePolicy(created, "DENY");
            var mapper = context.getBean(JsonMapper.class);
            var body = mapper.readTree(created.body());
            assertEquals(Set.of("name", "email", "phones", "id", "created", "modified", "last_login", "token", "isactive"),
                    body.propertyNames());
            assertEquals(body.get("created"), body.get("modified"));
            assertEquals(body.get("created"), body.get("last_login"));
            var jdbc = context.getBean(JdbcTemplate.class);
            assertEquals(body.get("token").asString(), jdbc.queryForObject(
                    "select token from users where email = ?", String.class, email));
            assertError(mapper, send(client, baseUrl, "POST", "/users", "application/json", "application/json", registration),
                    409, "El correo ya registrado");
            assertError(mapper, send(client, baseUrl, "POST", "/users", "application/json", "application/json", "{"), 400, null);
            assertError(mapper, send(client, baseUrl, "GET", "/missing-api", null, "application/json", ""), 404, null);
            assertError(mapper, send(client, baseUrl, "GET", "/users", null, "application/json", ""), 405, null);
            assertError(mapper, send(client, baseUrl, "POST", "/users", "application/json", "text/plain", registration), 406, null);
            assertError(mapper, send(client, baseUrl, "POST", "/users", "text/plain", "application/json", registration), 415, null);
            for (String path : new String[]{"/v3/api-docs", "/swagger-ui/index.html", "/swagger-ui/swagger-ui.css", "/swagger-ui/swagger-ui-bundle.js"}) {
                var response = send(client, baseUrl, "GET", path, null, "*/*", "");
                assertEquals(200, response.statusCode(), path);
                assertFramePolicy(response, "DENY");
            }

            if (development) {
                verifyConsoleConnection(client, baseUrl, environment.getRequiredProperty("spring.datasource.url"), email);
            } else {
                assertError(mapper, send(client, baseUrl, "GET", "/h2-console/", null, "*/*", ""), 404, null);
                assertError(mapper, send(client, baseUrl, "POST", "/h2-console/login.do", "application/x-www-form-urlencoded", "*/*", ""), 404, null);
            }
        }
    }

    private ServletWebServerApplicationContext startApplication(boolean development) {
        var application = new SpringApplication(UserManagementApiApplication.class, ConsoleTestConfiguration.class);
        application.setRegisterShutdownHook(false);
        application.addInitializers(context -> {
            try {
                context.getEnvironment().getPropertySources().addFirst(
                        new ResourcePropertySource("classpath:application-test.properties"));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
        return (ServletWebServerApplicationContext) application.run("--server.port=0",
                "--spring.profiles.active=" + (development ? "dev" : ""),
                "--spring.datasource.url=jdbc:h2:mem:mode-" + development + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
    }

    private void verifyConsoleConnection(HttpClient client, String baseUrl, String jdbcUrl, String email) throws Exception {
        var loginPage = send(client, baseUrl, "GET", "/h2-console/", null, "*/*", "");
        assertEquals(200, loginPage.statusCode());
        assertFramePolicy(loginPage, "SAMEORIGIN");
        var session = Pattern.compile("jsessionid=([a-f0-9]+)").matcher(loginPage.body());
        assertTrue(session.find(), "H2 must provide a console session");
        String sessionId = session.group(1);
        String login = form(Map.of("driver", "org.h2.Driver", "url", jdbcUrl, "user", "sa", "password", ""));
        try {
            var frame = send(client, baseUrl, "POST", "/h2-console/login.do?jsessionid=" + sessionId,
                    "application/x-www-form-urlencoded", "*/*", login);
            assertEquals(200, frame.statusCode());
            assertFramePolicy(frame, "SAMEORIGIN");
            assertTrue(Pattern.compile("<frame\\b[^>]*\\bname=\"h2query\"").matcher(frame.body()).find(),
                    "H2 login must open the query frame");
            var query = send(client, baseUrl, "POST", "/h2-console/query.do?jsessionid=" + sessionId,
                    "application/x-www-form-urlencoded", "*/*",
                    form(Map.of("sql", "select email from users where email = '" + email + "'")));
            assertEquals(200, query.statusCode());
            assertFramePolicy(query, "SAMEORIGIN");
            assertTrue(Pattern.compile("<td[^>]*>" + Pattern.quote(email) + "</td>").matcher(query.body()).find(),
                    "The console must read the user committed by the API");
            var stylesheet = send(client, baseUrl, "GET", "/h2-console/stylesheet.css", null, "*/*", "");
            assertEquals(200, stylesheet.statusCode());
            assertFramePolicy(stylesheet, "SAMEORIGIN");
        } finally {
            send(client, baseUrl, "GET", "/h2-console/logout.do?jsessionid=" + sessionId, null, "*/*", "");
        }
    }

    private HttpResponse<String> send(HttpClient client, String baseUrl, String method, String path,
                                      String contentType, String accept, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(10))
                .header("Accept", accept);
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        return client.send(request.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private void assertError(JsonMapper mapper, HttpResponse<String> response, int status, String message) throws Exception {
        assertEquals(status, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
        assertFramePolicy(response, "DENY");
        var error = mapper.readTree(response.body());
        assertEquals(Set.of("mensaje"), error.propertyNames());
        assertFalse(error.get("mensaje").asString().isBlank());
        if (message != null) {
            assertEquals(message, error.get("mensaje").asString());
        }
    }

    private void assertFramePolicy(HttpResponse<String> response, String expected) {
        assertEquals(expected, response.headers().firstValue("X-Frame-Options").orElseThrow());
    }

    private String form(Map<String, String> fields) {
        return fields.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConsoleTestConfiguration {
        @Bean
        static BeanPostProcessor consolePreferencesIsolation() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (beanName.equals("h2Console") && bean instanceof ServletRegistrationBean<?> registration) {
                        // H2 guarda preferencias al conectar; las pruebas no deben escribir archivos personales.
                        registration.addInitParameter("properties", "null");
                    }
                    return bean;
                }
            };
        }
    }
}
