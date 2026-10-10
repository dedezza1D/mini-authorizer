package com.miniauthorizer.security;

import com.miniauthorizer.support.MySqlIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ApiUserRepositoryTest extends MySqlIntegrationTest {

    private static final String USER = "terminal";
    private static final Instant UNTIL = Instant.parse("2026-01-15T10:15:00.123456Z");

    @Autowired
    private ApiUserRepository repository;

    @BeforeEach
    void user() {
        repository.saveAndFlush(ApiUser.create(USER, "{bcrypt}hash"));
    }

    @Test
    void locksOnlyWhenTheFailedAttemptsReachTheLimit() {
        fail(2);
        assertThat(repository.lockWhenAttemptsReach(USER, 3, UNTIL)).isZero();
        assertThat(stored().getLockedUntil()).isEmpty();

        fail(1);
        assertThat(repository.lockWhenAttemptsReach(USER, 3, UNTIL)).isEqualTo(1);

        ApiUser locked = stored();
        assertThat(locked.getLockedUntil()).contains(UNTIL.truncatedTo(ChronoUnit.MICROS));
        assertThat(locked.getFailedAttempts()).isZero();
    }

    @Test
    void resetsTheCountAfterASuccessfulLogin() {
        fail(2);

        assertThat(repository.resetFailedAttempts(USER)).isEqualTo(1);
        assertThat(stored().getFailedAttempts()).isZero();
    }

    @Test
    void doesNotWriteWhenThereIsNothingToReset() {
        assertThat(repository.resetFailedAttempts(USER)).isZero();
    }

    @Test
    void ignoresUnknownUsers() {
        assertThat(repository.incrementFailedAttempts("nobody")).isZero();
        assertThat(repository.lockWhenAttemptsReach("nobody", 0, UNTIL)).isZero();
    }

    private void fail(int times) {
        IntStream.range(0, times).forEach(i -> repository.incrementFailedAttempts(USER));
    }

    private ApiUser stored() {
        return repository.findById(USER).orElseThrow();
    }
}
