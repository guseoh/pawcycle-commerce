package com.pawcycle.backend.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawcycle.backend.common.logging.BusinessEvent.Field;
import com.pawcycle.backend.common.logging.BusinessEvent.Name;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(OutputCaptureExtension.class)
class BusinessEventTests {

  @AfterEach
  void clearSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void textLineHasTheEventNameAndAllowlistedFields(CapturedOutput output) {
    BusinessEvent.of(Name.PAYMENT_CONFIRM_FINISHED)
        .with(Field.PAYMENT_ID, 7)
        .with(Field.ORDER_ID, 3)
        .with(Field.OUTCOME, "SUCCEEDED")
        .log();

    assertThat(output.getOut())
        .contains("event=payment.confirm.finished paymentId=7 orderId=3 outcome=SUCCEEDED");
  }

  @Test
  void freeTextValuesAreReplacedSoPersonalDataCannotBeLogged(CapturedOutput output) {
    BusinessEvent.of(Name.ADMIN_ACTION)
        .with(Field.ACTION, "owner@example.com")
        .with(Field.OUTCOME, "서울시 강남구 1")
        .with(Field.PROVIDER_STATUS, "x".repeat(65))
        .with(Field.TARGET_TYPE, null)
        .log();

    assertThat(output.getOut())
        .contains("event=admin.action action=INVALID outcome=INVALID providerStatus=INVALID")
        .doesNotContain("owner@example.com")
        .doesNotContain("강남구")
        .doesNotContain("targetType");
  }

  @Test
  void insideATransactionTheEventWaitsForCommitAndIsDroppedOnRollback(CapturedOutput output) {
    TransactionSynchronizationManager.initSynchronization();
    BusinessEvent.of(Name.REFUND_FINISHED).with(Field.REFUND_ID, 11).log();
    assertThat(output.getOut()).doesNotContain("refundId=11");

    TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
    assertThat(output.getOut()).contains("event=refund.finished refundId=11");
    TransactionSynchronizationManager.clearSynchronization();

    TransactionSynchronizationManager.initSynchronization();
    BusinessEvent.of(Name.REFUND_FINISHED).with(Field.REFUND_ID, 12).log();
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(sync -> sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
    assertThat(output.getOut()).doesNotContain("refundId=12");
  }

  @Test
  void jsonLinesCarryTheEventAndFieldsAsMembers() {
    BusinessEvent event = BusinessEvent.of(Name.PAYMENT_EXPIRED).with(Field.PAYMENT_ID, 21).with(Field.ORDER_ID, 5);

    String line = EcsLogLines.render(BusinessEvent.LOGGER, event.text(), java.util.Map.of(), event.keyValues());

    assertThat(line)
        .startsWith("{")
        .contains("\"message\":\"event=payment.expired paymentId=21 orderId=5\"")
        .contains("\"businessEvent\":\"payment.expired\"")
        .contains("\"paymentId\":21")
        .contains("\"orderId\":5");
  }
}
