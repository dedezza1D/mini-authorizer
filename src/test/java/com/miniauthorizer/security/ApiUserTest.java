package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ApiUserTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Test
    void newUserIsEnabledWithNoFailuresAndNoLock() {
        ApiUser user = ApiUser.create("terminal", "{bcrypt}hash");

        assertThat(user.getUsername()).isEqualTo("terminal");
        assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}hash");
        assertThat(user.getFailedAttempts()).isZero();
        assertThat(user.getLockedUntil()).isEmpty();
        assertThat(user.isLockedAt(NOW)).isFalse();
        assertThat(user.toUserDetails(NOW).isEnabled()).isTrue();
    }

    @Test
    void isLockedOnlyBeforeTheLockExpires() {
        ApiUser user = ApiUser.create("terminal", "{bcrypt}hash");
        ReflectionTestUtils.setField(user, "lockedUntil", NOW);

        assertThat(user.getLockedUntil()).contains(NOW);
        assertThat(user.isLockedAt(NOW.minusMillis(1))).isTrue();
        assertThat(user.isLockedAt(NOW)).isFalse();
        assertThat(user.isLockedAt(NOW.plusMillis(1))).isFalse();
    }

    @Test
    void exposesTheFailedAttemptsRecordedInTheDatabase() {
        ApiUser user = ApiUser.create("terminal", "{bcrypt}hash");
        ReflectionTestUtils.setField(user, "failedAttempts", 3);

        assertThat(user.getFailedAttempts()).isEqualTo(3);
    }

    @Test
    void requiresUsernameAndPasswordHash() {
        assertThatNullPointerException().isThrownBy(() -> ApiUser.create(null, "hash")).withMessage("username");
        assertThatNullPointerException().isThrownBy(() -> ApiUser.create("terminal", null)).withMessage("passwordHash");
    }
}
