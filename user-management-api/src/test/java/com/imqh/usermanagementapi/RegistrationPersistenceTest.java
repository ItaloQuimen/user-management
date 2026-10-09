package com.imqh.usermanagementapi;

import com.imqh.usermanagementapi.dto.request.PhoneRequest;
import com.imqh.usermanagementapi.dto.request.UserRequest;
import com.imqh.usermanagementapi.dto.response.UserResponse;
import com.imqh.usermanagementapi.entity.User;
import com.imqh.usermanagementapi.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import org.h2.api.Trigger;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Connection;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:persistence-tests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@TestPropertySource("classpath:application-test.properties")
@AutoConfigureMockMvc
class RegistrationPersistenceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private Environment environment;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper jsonMapper;
    @MockitoSpyBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void clearCommittedData() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        jdbcTemplate.update("delete from phones");
        jdbcTemplate.update("delete from users");
    }

    @Test
    void schemaIsInitializedByScriptAndValidatedByJpa() {
        assertEquals("validate", environment.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("classpath:schema.sql", environment.getProperty("spring.sql.init.schema-locations"));
        assertEquals("CHARACTER LARGE OBJECT", jdbcTemplate.queryForObject("""
                select data_type from information_schema.columns
                where table_name = 'USERS' and column_name = 'TOKEN'
                """, String.class));
        assertEquals(255, jdbcTemplate.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_name = 'USERS' and column_name = 'EMAIL'
                """, Integer.class));
        assertEquals("NO", jdbcTemplate.queryForObject("""
                select is_nullable from information_schema.columns
                where table_name = 'PHONES' and column_name = 'USER_ID'
                """, String.class));
        assertEquals(Set.of("PK_USERS", "UK_USERS_EMAIL", "PK_PHONES", "FK_PHONES_USER"),
                Set.copyOf(jdbcTemplate.queryForList("""
                        select constraint_name from information_schema.table_constraints
                        where table_name in ('USERS', 'PHONES')
                        """, String.class)));
    }

    @Test
    void registrationPersistsCompleteRealJwtAndAllUserDataAfterClearingContext() throws Exception {
        UserRequest request = request("migration35@example.org", "Persisted User");
        Instant beforeRegistration = Instant.now();
        MvcResult result = register(request);
        assertEquals(201, result.getResponse().getStatus());
        UserResponse response = jsonMapper.readValue(result.getResponse().getContentAsString(), UserResponse.class);
        Instant afterRegistration = Instant.now();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        UUID.fromString(response.getId());
        assertTrue(response.getToken().length() > 255);
        assertEquals(3, response.getToken().split("\\.").length);
        assertEquals(response.getToken(), jdbcTemplate.queryForObject(
                "select token from users where id = ?", String.class, response.getId()));
        var verified = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(
                        environment.getRequiredProperty("app.jwt.secret-base64"))))
                .build().parseSignedClaims(response.getToken());
        assertEquals("HS512", verified.getHeader().getAlgorithm());
        var claims = verified.getPayload();
        assertEquals(response.getId(), claims.getSubject());
        assertEquals(request.getEmail(), claims.get("email", String.class));
        assertTrue(claims.getIssuedAt().toInstant().getEpochSecond() >= beforeRegistration.getEpochSecond());
        assertTrue(claims.getIssuedAt().toInstant().getEpochSecond() <= afterRegistration.getEpochSecond());
        assertEquals(environment.getRequiredProperty("app.jwt.expiration-seconds", Long.class),
                (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000);
        assertEquals(Set.of("sub", "email", "iat", "exp"), claims.keySet());
        System.out.println("Real JWT persistence regression: " + response.getToken().length() + " characters");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            entityManager.clear();
            User stored = entityManager.find(User.class, response.getId());
            assertNotNull(stored);
            assertEquals(request.getName(), stored.getName());
            assertEquals(request.getEmail(), stored.getEmail());
            assertEquals(response.getName(), stored.getName());
            assertEquals(response.getEmail(), stored.getEmail());
            assertEquals(response.getToken(), stored.getToken());
            assertNotEquals(request.getPassword(), stored.getPassword());
            assertTrue(new BCryptPasswordEncoder().matches(request.getPassword(), stored.getPassword()));
            assertTrue(stored.isActive());
            assertEquals(response.getCreated(), stored.getCreated());
            assertEquals(response.getModified(), stored.getModified());
            assertEquals(response.getLastLogin(), stored.getLastLogin());
            assertEquals(stored.getCreated(), stored.getModified());
            assertEquals(stored.getCreated(), stored.getLastLogin());
            assertEquals(2, stored.getPhones().size());
            assertEquals(Set.of("1234567/1/56", "7654321/2/57"), stored.getPhones().stream()
                    .map(phone -> {
                        assertNotNull(phone.getId());
                        assertEquals(stored.getId(), phone.getUser().getId());
                        return phone.getNumber() + "/" + phone.getCitycode() + "/" + phone.getContrycode();
                    }).collect(Collectors.toSet()));
            assertEquals(Set.of("1234567/1/56", "7654321/2/57"), response.getPhones().stream()
                    .map(phone -> phone.number() + "/" + phone.cityCode() + "/" + phone.countryCode())
                    .collect(Collectors.toSet()));
        });
        assertEquals(1, countUsers());
        assertEquals(2, countPhones());
    }

    @Test
    @Timeout(30)
    void simultaneousRegistrationsReachDatabaseUniquenessAndReturn201And409() throws Exception {
        String email = "race@example.org";
        CountDownLatch bothPassedPrecheck = new CountDownLatch(2);
        // Ambos registros superan la consulta previa; la generación del JWT sigue siendo real.
        doAnswer(invocation -> {
            Object token = invocation.callRealMethod();
            bothPassedPrecheck.countDown();
            if (!bothPassedPrecheck.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The second registration did not reach the barrier");
            }
            return token;
        }).when(jwtUtil).generateToken(anyString(), eq(email));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> register(request(email, "First User")));
            var second = executor.submit(() -> register(request(email, "Second User")));
            List<MvcResult> results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(Set.of(201, 409), results.stream()
                    .map(result -> result.getResponse().getStatus()).collect(Collectors.toSet()));
            MvcResult conflict = results.stream().filter(result -> result.getResponse().getStatus() == 409)
                    .findFirst().orElseThrow();
            assertError(conflict, 409, "El correo ya registrado");
            ConstraintViolationException violation = findConstraintViolation(conflict.getResolvedException());
            assertEquals(ConstraintViolationException.ConstraintKind.UNIQUE, violation.getKind());
            assertTrue(violation.getConstraintName().startsWith("PUBLIC.UK_USERS_EMAIL INDEX PUBLIC.UK_USERS_EMAIL_INDEX_"));
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(1, countUsers());
            assertEquals(2, countPhones());
            assertEquals(0, jdbcTemplate.queryForObject("""
                    select count(*) from phones p left join users u on p.user_id = u.id where u.id is null
                    """, Integer.class));
        }
    }

    @Test
    void unrelatedUniqueViolationRemainsGeneric500() throws Exception {
        assertEquals(201, register(request("first@example.org", "Same Name")).getResponse().getStatus());
        assertEquals(1, countUsers());
        jdbcTemplate.execute("create unique index uk_users_name_test on users(name)");
        try {
            MvcResult failed = register(request("second@example.org", "Same Name"));
            assertError(failed, 500, "Ocurrió un error interno");
            ConstraintViolationException violation = findConstraintViolation(failed.getResolvedException());
            assertEquals(ConstraintViolationException.ConstraintKind.UNIQUE, violation.getKind());
            assertEquals("PUBLIC.UK_USERS_NAME_TEST", violation.getConstraintName());
            assertEquals(1, countUsers());
            assertEquals(2, countPhones());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "select count(*) from users where email = ?", Integer.class, "second@example.org"));
        } finally {
            jdbcTemplate.execute("drop index uk_users_name_test");
        }
    }

    @Test
    void failedPhoneWriteRollsBackAlreadyInsertedUserAndPhone() throws Exception {
        WriteObserver.users.set(0);
        WriteObserver.phones.set(0);
        String observer = WriteObserver.class.getName();
        jdbcTemplate.execute("create trigger observe_users after insert on users for each row call '" + observer + "'");
        jdbcTemplate.execute("create trigger observe_phones after insert on phones for each row call '" + observer + "'");
        try {
            UserRequest request = request("rollback@example.org", "Rollback User");
            request.getPhones().get(1).setNumber("X".repeat(256));
            MvcResult failed = register(request);
            assertError(failed, 500, "Ocurrió un error interno");
            assertEquals(1, WriteObserver.users.get(), "The user INSERT reached H2 before the failure");
            assertEquals(1, WriteObserver.phones.get(), "The first phone INSERT reached H2 before the failure");
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0, countUsers());
            assertEquals(0, countPhones());
        } finally {
            jdbcTemplate.execute("drop trigger observe_users");
            jdbcTemplate.execute("drop trigger observe_phones");
        }
    }

    private MvcResult register(UserRequest request) throws Exception {
        return mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andReturn();
    }

    private void assertError(MvcResult result, int status, String message) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        assertTrue(MediaType.APPLICATION_JSON.isCompatibleWith(
                MediaType.parseMediaType(result.getResponse().getContentType())));
        var body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(Set.of("mensaje"), body.propertyNames());
        assertEquals(message, body.get("mensaje").asString());
    }

    private ConstraintViolationException findConstraintViolation(Throwable cause) {
        for (; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation;
            }
        }
        throw new AssertionError("No Hibernate constraint violation was observed");
    }

    private int countUsers() {
        return jdbcTemplate.queryForObject("select count(*) from users", Integer.class);
    }

    private int countPhones() {
        return jdbcTemplate.queryForObject("select count(*) from phones", Integer.class);
    }

    private UserRequest request(String email, String name) {
        UserRequest request = new UserRequest();
        request.setName(name);
        request.setEmail(email);
        request.setPassword("Password1");
        request.setPhones(List.of(phone("1234567", "1", "56"), phone("7654321", "2", "57")));
        return request;
    }

    private PhoneRequest phone(String number, String cityCode, String countryCode) {
        PhoneRequest phone = new PhoneRequest();
        phone.setNumber(number);
        phone.setCitycode(cityCode);
        phone.setContrycode(countryCode);
        return phone;
    }

    public static class WriteObserver implements Trigger {
        static final AtomicInteger users = new AtomicInteger();
        static final AtomicInteger phones = new AtomicInteger();
        private String tableName;

        @Override
        public void init(Connection connection, String schemaName, String triggerName,
                         String tableName, boolean before, int type) {
            this.tableName = tableName;
        }

        @Override
        public void fire(Connection connection, Object[] oldRow, Object[] newRow) {
            ("USERS".equals(tableName) ? users : phones).incrementAndGet();
        }
    }
}
