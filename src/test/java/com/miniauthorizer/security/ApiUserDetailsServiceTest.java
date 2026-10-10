package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiUserDetailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private ApiUserRepository repository;

    private ApiUserDetailsService service() {
        return new ApiUserDetailsService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void exposesTheStoredUserWithTheApiRole() {
        when(repository.findById("terminal")).thenReturn(Optional.of(ApiUser.create("terminal", "{bcrypt}hash")));

        UserDetails details = service().loadUserByUsername("terminal");

        assertThat(details.getUsername()).isEqualTo("terminal");
        assertThat(details.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.isAccountNonLocked()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_API");
    }

    @Test
    void exposesDisabledUsersAsDisabled() {
        ApiUser user = ApiUser.create("terminal", "{bcrypt}hash");
        user.disable();
        when(repository.findById("terminal")).thenReturn(Optional.of(user));

        assertThat(service().loadUserByUsername("terminal").isEnabled()).isFalse();
    }

    @Test
    void exposesUsersAsLockedUntilTheLockExpires() {
        when(repository.findById("terminal")).thenReturn(Optional.of(lockedUntil(NOW.plusSeconds(1))));
        assertThat(service().loadUserByUsername("terminal").isAccountNonLocked()).isFalse();

        when(repository.findById("terminal")).thenReturn(Optional.of(lockedUntil(NOW)));
        assertThat(service().loadUserByUsername("terminal").isAccountNonLocked()).isTrue();
    }

    @Test
    void failsForUnknownUser() {
        when(repository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private static ApiUser lockedUntil(Instant until) {
        ApiUser user = ApiUser.create("terminal", "{bcrypt}hash");
        ReflectionTestUtils.setField(user, "lockedUntil", until);
        return user;
    }
}
