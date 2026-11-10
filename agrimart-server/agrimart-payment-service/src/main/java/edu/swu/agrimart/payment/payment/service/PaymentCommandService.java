package edu.swu.agrimart.payment.payment.service;

import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** 消费命令后，业务状态、结果 Outbox 与 Inbox 一次提交；失败向容器抛出。 */
@Service
public class PaymentCommandService {
    private final MessageStore messages;
    private final PaymentService payments;
    private final RefundService refunds;
    public PaymentCommandService(MessageStore messages, PaymentService payments, RefundService refunds) {
        this.messages=messages; this.payments=payments; this.refunds=refunds;
    }
    @Transactional
    public void accept(FundsEvent event) {
        if (event.kind()!=FundsEvent.Kind.CLOSE_PAYMENT && event.kind()!=FundsEvent.Kind.EXECUTE_REFUND)
            throw new IllegalArgumentException("UNSUPPORTED_PAYMENT_COMMAND");
        messages.consume("payment-commands",event,() -> {
            if (event.kind()==FundsEvent.Kind.EXECUTE_REFUND) refunds.execute(event);
            else payments.closeCommand(event);
        });
    }
}
