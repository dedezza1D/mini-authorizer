package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import com.miniauthorizer.card.PinAttempts;
import com.miniauthorizer.support.TestPins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuthorizationRulesTest {

    private static final String NUMBER = "6549873025634501";
    private static final Card CARD = Card.issue(NUMBER, TestPins.HASHER.hash(NUMBER, "1234"));
    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");

    @Mock
    private PinAttempts pinAttempts;

    private final SufficientBalanceRule sufficientBalance = new SufficientBalanceRule();
    private final CardNotBlockedRule notBlocked = new CardNotBlockedRule(Clock.fixed(NOW, ZoneOffset.UTC));

    private PinMatchesRule pinMatches() {
        return new PinMatchesRule(TestPins.HASHER, pinAttempts);
    }

    @Test
    void pinRuleAllowsOnlyTheCardPin() {
        assertThat(pinMatches().allows(CARD, transaction("1234", "10.00"))).isTrue();
        assertThat(pinMatches().allows(CARD, transaction("1235", "10.00"))).isFalse();
        assertThat(pinMatches().rejectionReason()).isEqualTo(RejectionReason.INVALID_PIN);
    }

    @Test
    void countsAWrongPinBeforeRejecting() {
        assertThatThrownBy(() -> pinMatches().check(CARD, transaction("0000", "10.00")))
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        rejection -> assertThat(rejection.reason()).isEqualTo(RejectionReason.INVALID_PIN));

        verify(pinAttempts).wrongPin(NUMBER);
    }

    @Test
    void reportsARightPinSoEarlierMistakesAreForgotten() {
        pinMatches().check(CARD, transaction("1234", "10.00"));

        verify(pinAttempts).rightPin(CARD);
    }

    @Test
    void balanceRuleAllowsAmountsUpToTheBalance() {
        assertThat(sufficientBalance.allows(CARD, transaction("1234", "500.00"))).isTrue();
        assertThat(sufficientBalance.allows(CARD, transaction("1234", "500.01"))).isFalse();
        assertThat(sufficientBalance.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_BALANCE);
    }

    @Test
    void checkPassesSilentlyWhenAllowed() {
        assertThatCode(() -> sufficientBalance.check(CARD, transaction("1234", "1.00"))).doesNotThrowAnyException();
    }

    @Test
    void blockedRuleRefusesACardOnlyWhileItIsBlocked() {
        Card blocked = Card.issue(NUMBER, "hash");
        ReflectionTestUtils.setField(blocked, "blockedUntil", NOW.plusSeconds(1));
        Card blockExpired = Card.issue(NUMBER, "hash");
        ReflectionTestUtils.setField(blockExpired, "blockedUntil", NOW);

        assertThat(notBlocked.allows(blocked, transaction("1234", "1.00"))).isFalse();
        assertThat(notBlocked.allows(blockExpired, transaction("1234", "1.00"))).isTrue();
        assertThat(notBlocked.allows(CARD, transaction("1234", "1.00"))).isTrue();
        assertThat(notBlocked.rejectionReason()).isEqualTo(RejectionReason.CARD_BLOCKED);
    }

    @Test
    void rulesWithoutReactionsHaveNoSideEffects() {
        assertThatThrownBy(() -> sufficientBalance.check(CARD, transaction("1234", "999.00")))
                .isInstanceOf(TransactionRejectedException.class);
        verifyNoInteractions(pinAttempts);
    }

    private static Transaction transaction(String pin, String amount) {
        return new Transaction(NUMBER, pin, new BigDecimal(amount));
    }
}
