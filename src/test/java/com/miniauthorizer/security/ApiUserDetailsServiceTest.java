package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiUserDetailsServiceTest {

    @Mock
    private ApiUserRepository repository;

    @InjectMocks
    private ApiUserDetailsService service;

    @Test
    void exposesTheStoredUserWithTheApiRole() {
        when(repository.findById("terminal")).thenReturn(Optional.of(ApiUser.create("terminal", "{bcrypt}hash")));

        UserDetails details = service.loadUserByUsername("terminal");

        assertThat(details.getUsername()).isEqualTo("terminal");
        assertThat(details.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_API");
    }

    @Test
    void exposesDisabledUsersAsDisabled() {
        ApiUser user = ApiUser.create("terminal", "{bcrypt}hash");
        user.disable();
        when(repository.findById("terminal")).thenReturn(Optional.of(user));

        assertThat(service.loadUserByUsername("terminal").isEnabled()).isFalse();
    }

    @Test
    void failsForUnknownUser() {
        when(repository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
