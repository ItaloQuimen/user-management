package com.imqh.usermanagementapi.util;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-10-09T12:00:00Z");
    private static final byte[] TEST_KEY_BYTES = "test-only-key-never-use-outside-tests-0123456789-0123456789-0123456"
            .getBytes(StandardCharsets.UTF_8);
    private static final String TEST_KEY_BASE64 = Base64.getEncoder().encodeToString(TEST_KEY_BYTES);
    private static final SecretKey TEST_KEY = Keys.hmacShaKeyFor(TEST_KEY_BYTES);
    private final JwtUtil jwtUtil = new JwtUtil(TEST_KEY_BASE64, "120", Clock.fixed(ISSUED_AT, ZoneOffset.UTC));

    @Test
    void generatesCryptographicallyVerifiedHs512WithExactClaimsAndLifetime() {
        var verified = parserAt(ISSUED_AT).parseSignedClaims(jwtUtil.generateToken("user-id", "user@example.org"));
        assertEquals("HS512", verified.getHeader().getAlgorithm());
        var claims = verified.getPayload();
        assertEquals("user-id", claims.getSubject());
        assertEquals("user@example.org", claims.get("email", String.class));
        assertEquals(Date.from(ISSUED_AT), claims.getIssuedAt());
        assertEquals(Date.from(ISSUED_AT.plusSeconds(120)), claims.getExpiration());
        assertEquals(Set.of("sub", "email", "iat", "exp"), claims.keySet());
    }

    @Test
    void rejectsAlteredPayloadWithOriginalSignature() {
        String token = jwtUtil.generateToken("user-id", "user@example.org");
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("user@example.org", "attacker@example.org");
        String altered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertThrows(SignatureException.class, () -> parserAt(ISSUED_AT).parseSignedClaims(altered));
    }

    @Test
    void rejectsDifferentSigningKey() {
        byte[] otherBytes = TEST_KEY_BYTES.clone();
        otherBytes[0] ^= 1;
        var parser = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(otherBytes))
                .clock(() -> Date.from(ISSUED_AT)).build();
        String token = jwtUtil.generateToken("user-id", "user@example.org");
        assertThrows(SignatureException.class, () -> parser.parseSignedClaims(token));
    }

    @Test
    void rejectsExpiredTokenWithoutWaitingOrClockSkew() {
        String token = jwtUtil.generateToken("user-id", "user@example.org");
        assertDoesNotThrow(() -> parserAt(ISSUED_AT.plusSeconds(119)).parseSignedClaims(token));
        assertThrows(ExpiredJwtException.class,
                () -> parserAt(ISSUED_AT.plusSeconds(121)).parseSignedClaims(token));
    }

    @Test
    void acceptsExactly64BytesAndLongerKeysWithoutChangingHs512() {
        for (int length : new int[]{64, 96}) {
            byte[] bytes = Arrays.copyOf(TEST_KEY_BYTES, length);
            JwtUtil generator = new JwtUtil(Base64.getEncoder().encodeToString(bytes), "1",
                    Clock.fixed(ISSUED_AT.plusNanos(999_999_999), ZoneOffset.UTC));
            var verified = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(bytes))
                    .clock(() -> Date.from(ISSUED_AT)).build()
                    .parseSignedClaims(generator.generateToken("user-id", "user@example.org"));
            assertEquals("HS512", verified.getHeader().getAlgorithm());
            assertEquals(Date.from(ISSUED_AT), verified.getPayload().getIssuedAt());
            assertEquals(Date.from(ISSUED_AT.plusSeconds(1)), verified.getPayload().getExpiration());
        }
    }

    private io.jsonwebtoken.JwtParser parserAt(Instant instant) {
        return Jwts.parser().verifyWith(TEST_KEY).clock(() -> Date.from(instant)).build();
    }
}
