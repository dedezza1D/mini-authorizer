package com.miniauthorizer.card;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceTest {

    private static final String NUMBER = "6549873025634501";
    private static final String PIN = "1234";

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Mock
    private CardRepository repository;

    private CardService service() {
        return new CardService(repository, passwordEncoder);
    }

    @Test
    void issuesCardWithInitialBalanceAndHashedPin() {
        when(repository.findById(NUMBER)).thenReturn(Optional.empty());

        CardIssuance issuance = service().issue(NUMBER, PIN);

        ArgumentCaptor<Card> saved = ArgumentCaptor.forClass(Card.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(issuance).isEqualTo(CardIssuance.CREATED);
        assertThat(saved.getValue().getNumber()).isEqualTo(NUMBER);
        assertThat(saved.getValue().getBalance()).isEqualByComparingTo("500.00");
        assertThat(saved.getValue().getPinHash()).doesNotContain(PIN).startsWith("{bcrypt}");
        assertThat(passwordEncoder.matches(PIN, saved.getValue().getPinHash())).isTrue();
    }

    @Test
    void doesNotIssueACardWhoseNumberIsTaken() {
        when(repository.findById(NUMBER)).thenReturn(Optional.of(Card.issue(NUMBER, "hash")));

        assertThat(service().issue(NUMBER, PIN)).isEqualTo(CardIssuance.ALREADY_EXISTS);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reportsConcurrentInsertOfTheSameNumberAsExistingCard() {
        when(repository.findById(NUMBER)).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThat(service().issue(NUMBER, PIN)).isEqualTo(CardIssuance.ALREADY_EXISTS);
    }

    @Test
    void returnsBalanceOfExistingCard() {
        when(repository.findById(NUMBER)).thenReturn(Optional.of(Card.issue(NUMBER, "hash")));

        assertThat(service().balanceOf(NUMBER)).hasValueSatisfying(
                balance -> assertThat(balance).isEqualByComparingTo("500.00"));
    }

    @Test
    void returnsNoBalanceForUnknownCard() {
        when(repository.findById(NUMBER)).thenReturn(Optional.empty());

        assertThat(service().balanceOf(NUMBER)).isEmpty();
    }
}
