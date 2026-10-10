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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionAuthorizerTest {

    private static final PasswordEncoder ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private static final String NUMBER = "6549873025634501";
    private static final String PIN_HASH = ENCODER.encode("1234");

    @Mock
    private CardRepository cardRepository;

    private TransactionAuthorizer authorizer() {
        return new TransactionAuthorizer(cardRepository,
                List.of(new PinMatchesRule(ENCODER), new SufficientBalanceRule()));
    }

    @Test
    void debitsTheCardWhenEveryRuleAllows() {
        Card card = existingCard();

        authorizer().authorize(new Transaction(NUMBER, "1234", new BigDecimal("10.00")));

        assertThat(card.getBalance()).isEqualByComparingTo("490.00");
    }

    @Test
    void rejectsUnknownCard() {
        when(cardRepository.findById(NUMBER)).thenReturn(Optional.empty());

        assertRejected(new Transaction(NUMBER, "1234", BigDecimal.TEN), RejectionReason.CARD_NOT_FOUND);
    }

    @Test
    void rejectsWrongPinWithoutTouchingTheBalance() {
        Card card = existingCard();

        assertRejected(new Transaction(NUMBER, "9999", BigDecimal.TEN), RejectionReason.INVALID_PIN);
        assertThat(card.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void rejectsAmountAboveTheBalanceWithoutTouchingIt() {
        Card card = existingCard();

        assertRejected(new Transaction(NUMBER, "1234", new BigDecimal("500.01")), RejectionReason.INSUFFICIENT_BALANCE);
        assertThat(card.getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void reportsTheFirstFailingRuleWhenSeveralFail() {
        existingCard();

        assertRejected(new Transaction(NUMBER, "9999", new BigDecimal("999.00")), RejectionReason.INVALID_PIN);
    }

    private Card existingCard() {
        Card card = Card.issue(NUMBER, PIN_HASH);
        when(cardRepository.findById(NUMBER)).thenReturn(Optional.of(card));
        return card;
    }

    private void assertRejected(Transaction transaction, RejectionReason reason) {
        assertThatThrownBy(() -> authorizer().authorize(transaction))
                .isInstanceOfSatisfying(TransactionRejectedException.class,
                        rejection -> assertThat(rejection.reason()).isEqualTo(reason));
    }
}
