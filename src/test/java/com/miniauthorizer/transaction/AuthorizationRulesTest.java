package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import com.miniauthorizer.support.TestPins;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthorizationRulesTest {

    private static final Card CARD = Card.issue("6549873025634501", TestPins.HASHER.hash("6549873025634501", "1234"));

    private final PinMatchesRule pinMatches = new PinMatchesRule(TestPins.HASHER);
    private final SufficientBalanceRule sufficientBalance = new SufficientBalanceRule();

    @Test
    void pinRuleAllowsOnlyTheCardPin() {
        assertThat(pinMatches.allows(CARD, transaction("1234", "10.00"))).isTrue();
        assertThat(pinMatches.allows(CARD, transaction("1235", "10.00"))).isFalse();
        assertThat(pinMatches.rejectionReason()).isEqualTo(RejectionReason.INVALID_PIN);
    }

    @Test
    void balanceRuleAllowsAmountsUpToTheBalance() {
        assertThat(sufficientBalance.allows(CARD, transaction("1234", "500.00"))).isTrue();
        assertThat(sufficientBalance.allows(CARD, transaction("1234", "500.01"))).isFalse();
        assertThat(sufficientBalance.rejectionReason()).isEqualTo(RejectionReason.INSUFFICIENT_BALANCE);
    }

    @Test
    void checkRejectsWithTheRuleReasonWhenNotAllowed() {
        assertThatThrownBy(() -> pinMatches.check(CARD, transaction("0000", "10.00")))
                .isInstanceOf(TransactionRejectedException.class)
                .extracting(e -> ((TransactionRejectedException) e).reason())
                .isEqualTo(RejectionReason.INVALID_PIN);
    }

    @Test
    void checkPassesSilentlyWhenAllowed() {
        assertThatCode(() -> sufficientBalance.check(CARD, transaction("1234", "1.00"))).doesNotThrowAnyException();
    }

    private static Transaction transaction(String pin, String amount) {
        return new Transaction(CARD.getNumber(), pin, new BigDecimal(amount));
    }
}
