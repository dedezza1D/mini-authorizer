package com.miniauthorizer.transaction;

import com.miniauthorizer.card.Card;
import com.miniauthorizer.card.CardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionAuthorizerTest {

    private static final PasswordEncoder ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private static final String NUMBER = "6549873025634501";
    private static final String PIN_HASH = ENCODER.encode("1234");
    private static final BigDecimal TEN = new BigDecimal("10.00");

    @Mock
    private CardRepository cardRepository;

    private TransactionAuthorizer authorizer() {
        return new TransactionAuthorizer(cardRepository,
                List.of(new PinMatchesRule(ENCODER), new SufficientBalanceRule()));
    }

    @Test
    void debitsTheCardWhenEveryRuleAllows() {
        existingCard();
        when(cardRepository.debitIfAffordable(NUMBER, TEN)).thenReturn(1);

        assertThatCode(() -> authorizer().authorize(new Transaction(NUMBER, "1234", TEN)))
                .doesNotThrowAnyException();
        verify(cardRepository).debitIfAffordable(NUMBER, TEN);
    }

    @Test
    void rejectsWhenAConcurrentTransactionSpentTheBalanceAfterTheRulesPassed() {
        existingCard();
        when(cardRepository.debitIfAffordable(NUMBER, TEN)).thenReturn(0);

        assertRejected(new Transaction(NUMBER, "1234", TEN), RejectionReason.INSUFFICIENT_BALANCE);
    }

    @Test
    void rejectsUnknownCard() {
        when(cardRepository.findById(NUMBER)).thenReturn(Optional.empty());

        assertRejected(new Transaction(NUMBER, "1234", TEN), RejectionReason.CARD_NOT_FOUND);
        verify(cardRepository, never()).debitIfAffordable(any(), any());
    }

    @Test
    void rejectsWrongPinWithoutDebiting() {
        existingCard();

        assertRejected(new Transaction(NUMBER, "9999", TEN), RejectionReason.INVALID_PIN);
        verify(cardRepository, never()).debitIfAffordable(any(), any());
    }

    @Test
    void rejectsAmountAboveTheBalanceWithoutDebiting() {
        existingCard();

        assertRejected(new Transaction(NUMBER, "1234", new BigDecimal("500.01")), RejectionReason.INSUFFICIENT_BALANCE);
        verify(cardRepository, never()).debitIfAffordable(any(), any());
    }

    @Test
    void reportsTheFirstFailingRuleWhenSeveralFail() {
        existingCard();

        assertRejected(new Transaction(NUMBER, "9999", new BigDecimal("999.00")), RejectionReason.INVALID_PIN);
    }

    private void existingCard() {
        when(cardRepository.findById(NUMBER)).thenReturn(Optional.of(Card.issue(NUMBER, PIN_HASH)));
    }

    private void assertRejected(Transaction transaction, RejectionReason reason) {
        assertThatThrownBy(() -> authorizer().authorize(transaction))
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        rejection -> assertThat(rejection.reason()).isEqualTo(reason));
    }
}
