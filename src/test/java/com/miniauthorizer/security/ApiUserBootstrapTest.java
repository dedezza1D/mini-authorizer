package com.miniauthorizer.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiUserBootstrapTest {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private ApiUserRepository repository;

    private ApiUserBootstrap bootstrap() {
        return new ApiUserBootstrap(repository, passwordEncoder, new ApiSecurityProperties(
                new ApiSecurityProperties.BootstrapUser("terminal", "s3cret")));
    }

    @Test
    void createsTheUserWithAHashedPasswordWhenMissing() {
        when(repository.findById("terminal")).thenReturn(Optional.empty());

        bootstrap().run(null);

        ArgumentCaptor<ApiUser> saved = ArgumentCaptor.forClass(ApiUser.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo("terminal");
        assertThat(saved.getValue().getPasswordHash()).isNotEqualTo("s3cret");
        assertThat(passwordEncoder.matches("s3cret", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void leavesAnExistingUserUntouched() {
        when(repository.findById("terminal")).thenReturn(Optional.of(ApiUser.create("terminal", "{bcrypt}old")));

        bootstrap().run(null);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void toleratesAnotherInstanceCreatingTheUserFirst() {
        when(repository.findById("terminal")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatCode(() -> bootstrap().run(null)).doesNotThrowAnyException();
    }

    @Test
    void neverPrintsThePassword() {
        assertThat(new ApiSecurityProperties.BootstrapUser("terminal", "s3cret").toString())
                .contains("terminal")
                .doesNotContain("s3cret");
    }
}
