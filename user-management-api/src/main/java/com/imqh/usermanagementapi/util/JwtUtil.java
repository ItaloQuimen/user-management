package com.imqh.usermanagementapi.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationSeconds;
    private final Clock clock;

    @Autowired
    public JwtUtil(@Value("${app.jwt.secret-base64:}") String secretBase64,
                   @Value("${app.jwt.expiration-seconds:3600}") String expirationSeconds) {
        this(secretBase64, expirationSeconds, Clock.systemUTC());
    }

    JwtUtil(String secretBase64, String expirationSeconds, Clock clock) {
        this.clock = clock;
        this.signingKey = decodeSigningKey(secretBase64);
        this.expirationSeconds = validateExpiration(expirationSeconds, clock);
    }

    public String generateToken(String userId, String email) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(expirationSeconds)))
                .signWith(signingKey, Jwts.SIG.HS512)
                .compact();
    }

    private static SecretKey decodeSigningKey(String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException("Falta app.jwt.secret-base64: configure JWT_SECRET_BASE64 con una clave Base64 de al menos 64 bytes");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secretBase64);
        } catch (IllegalArgumentException exception) {
            throw invalidKeyFormat();
        }
        if (!Base64.getEncoder().encodeToString(keyBytes).equals(secretBase64)) {
            throw invalidKeyFormat();
        }
        if (keyBytes.length < 64) {
            throw new IllegalArgumentException("app.jwt.secret-base64 debe contener al menos 64 bytes decodificados para HS512");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static IllegalArgumentException invalidKeyFormat() {
        return new IllegalArgumentException("app.jwt.secret-base64 debe usar Base64 estándar con padding, sin espacios ni saltos de línea");
    }

    private static long validateExpiration(String value, Clock clock) {
        try {
            long seconds = Long.parseLong(value);
            if (seconds > 0) {
                Math.addExact(clock.millis(), Math.multiplyExact(seconds, 1000L));
                return seconds;
            }
        } catch (NumberFormatException | ArithmeticException exception) {
            // El diagnóstico no conserva valores de configuración ni causas sensibles.
        }
        throw new IllegalArgumentException("app.jwt.expiration-seconds debe ser un entero positivo en segundos y producir una fecha representable");
    }
}
