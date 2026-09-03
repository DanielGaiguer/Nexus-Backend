package com.main.nexus.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        // Store real em memoria (sem Spring): exercita o Bucket4j de verdade.
        service = new LoginAttemptService(new CaffeineRateLimitStore());
    }

    @Test
    void blocksAfterFiveConsecutiveFailures() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("1.2.3.4", "vitima@nexus.com");
        }

        RateLimitExceededException ex = assertThrows(RateLimitExceededException.class,
                () -> service.assertNotBlocked("1.2.3.4", "vitima@nexus.com"));

        assertEquals(RateLimitExceededException.LOGIN_BLOCKED, ex.getErrorCode());
        // ~15 min ate liberar 1 token (refill greedy 1/15min). Folga generosa
        // para nao ficar fragil com o tempo gasto pelo proprio teste.
        assertTrue(ex.getRetryAfterSeconds() > 800 && ex.getRetryAfterSeconds() <= 900,
                "retryAfter esperado ~900s, veio " + ex.getRetryAfterSeconds());
    }

    @Test
    void fourFailuresDoNotBlock() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("1.2.3.4", "user@nexus.com");
        }
        assertDoesNotThrow(() -> service.assertNotBlocked("1.2.3.4", "user@nexus.com"));
    }

    @Test
    void successResetsTheCounter() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("1.2.3.4", "user@nexus.com");
        }
        service.recordSuccess("1.2.3.4", "user@nexus.com");

        // Depois do reset, 4 falhas de novo ainda nao bloqueiam.
        for (int i = 0; i < 4; i++) {
            service.recordFailure("1.2.3.4", "user@nexus.com");
        }
        assertDoesNotThrow(() -> service.assertNotBlocked("1.2.3.4", "user@nexus.com"));
    }

    @Test
    void blockIsScopedToIpPlusEmail() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("1.2.3.4", "vitima@nexus.com");
        }

        // Mesmo IP, outro e-mail: nao bloqueado.
        assertDoesNotThrow(() -> service.assertNotBlocked("1.2.3.4", "outro@nexus.com"));
        // Outro IP, mesmo e-mail: nao bloqueado.
        assertDoesNotThrow(() -> service.assertNotBlocked("9.9.9.9", "vitima@nexus.com"));
    }

    @Test
    void emailIsCaseAndSpaceInsensitive() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("1.2.3.4", "  User@Nexus.com ");
        }
        assertThrows(RateLimitExceededException.class,
                () -> service.assertNotBlocked("1.2.3.4", "user@nexus.com"));
    }

    @Test
    void freshPairIsNotBlocked() {
        assertDoesNotThrow(() -> service.assertNotBlocked("5.5.5.5", "novo@nexus.com"));
    }
}
