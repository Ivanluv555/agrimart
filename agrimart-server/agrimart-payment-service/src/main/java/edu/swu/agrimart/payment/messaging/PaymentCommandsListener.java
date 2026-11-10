package edu.swu.agrimart.payment.messaging;

import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.messaging.config.RabbitMessagingConfiguration;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 容器在业务事务提交并正常返回后 ack，失败按原生重试配置进入本服务 DLQ。 */
@Component
@ConditionalOnProperty(name="agrimart.messaging.enabled",havingValue="true")
public class PaymentCommandsListener {
    private final MessageStore messages;
    private final edu.swu.agrimart.payment.payment.service.PaymentCommandService handler;
    public PaymentCommandsListener(MessageStore messages, edu.swu.agrimart.payment.payment.service.PaymentCommandService handler) { this.messages=messages; this.handler=handler; }
    @RabbitListener(queues=RabbitMessagingConfiguration.PAYMENT_QUEUE)
    public void receive(Message message) { handler.accept(messages.decode(message.getBody())); }
}
