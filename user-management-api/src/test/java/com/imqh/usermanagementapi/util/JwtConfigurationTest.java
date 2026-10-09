package com.imqh.usermanagementapi.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.StandardEnvironment;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class JwtConfigurationTest {

    private static final String TEST_KEY = Base64.getEncoder().encodeToString(
            "test-only-key-never-use-outside-tests-0123456789-0123456789-0123456".getBytes(StandardCharsets.UTF_8));
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(JwtOnlyConfiguration.class)
            .withInitializer(context -> {
                var sources = context.getEnvironment().getPropertySources();
                sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            });

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingOrBlankKeyPreventsStartup(String value, CapturedOutput output) {
        var configured = value == null ? runner : runner.withPropertyValues("app.jwt.secret-base64=" + value);
        assertRejected(configured, "Falta app.jwt.secret-base64", null, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-secret!", "!!!!", "YWJj\nZA==", "YWJj ZA==", "YWJjZA", "YWJjZA__"})
    void invalidBase64PreventsStartupWithoutRevealingValue(String value, CapturedOutput output) {
        assertRejected(runner.withPropertyValues("app.jwt.secret-base64=" + value),
                "Base64 estándar con padding", value, output);
    }

    @Test
    void insufficientDecodedKeyPreventsStartupWithoutRevealingKey(CapturedOutput output) {
        String shortKey = "test-only-short-key";
        String encoded = Base64.getEncoder().encodeToString(shortKey.getBytes(StandardCharsets.UTF_8));
        assertRejected(runner.withPropertyValues("app.jwt.secret-base64=" + encoded),
                "al menos 64 bytes decodificados", encoded, output);
        assertThat(output.getAll()).doesNotContain(shortKey);
    }

    @Test
    void rejects63DecodedBytesEvenWhenBase64ContainsMoreThan64Characters(CapturedOutput output) {
        String encoded = Base64.getEncoder().encodeToString(new byte[63]);
        assertThat(encoded).hasSizeGreaterThan(64);
        assertRejected(runner.withPropertyValues("app.jwt.secret-base64=" + encoded),
                "al menos 64 bytes decodificados", encoded, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0", "-1", "1.5", "abc", "9223372036854775807", "9223372036854775808"})
    void invalidDurationPreventsStartupWithoutRevealingKey(String value, CapturedOutput output) {
        assertRejected(runner.withPropertyValues("app.jwt.secret-base64=" + TEST_KEY,
                        "app.jwt.expiration-seconds=" + value),
                "entero positivo en segundos", TEST_KEY, output);
    }

    @Test
    void validConfigurationAndDefaultDurationAllowStartup() {
        runner.withPropertyValues("app.jwt.secret-base64=" + TEST_KEY).run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(JwtUtil.class);
            assertThat(context.getBean(JwtUtil.class).generateToken("id", "test@example.org"))
                    .matches("[^.]+\\.[^.]+\\.[^.]+");
        });
    }

    private void assertRejected(ApplicationContextRunner configured, String diagnostic,
                                String secret, CapturedOutput output) {
        configured.run(context -> {
            assertThat(context).hasFailed();
            StringWriter trace = new StringWriter();
            context.getStartupFailure().printStackTrace(new PrintWriter(trace));
            assertThat(trace.toString()).contains(diagnostic);
            if (secret != null) {
                assertThat(trace.toString()).doesNotContain(secret);
                assertThat(output.getAll()).doesNotContain(secret);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JwtUtil.class)
    static class JwtOnlyConfiguration {
    }
}
