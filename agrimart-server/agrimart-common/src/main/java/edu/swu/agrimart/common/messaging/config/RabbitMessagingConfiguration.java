package edu.swu.agrimart.common.messaging.config;

import edu.swu.agrimart.common.messaging.service.MessageStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 使用 Boot 原生 Rabbit 自动配置；消息确认后才标记 Outbox 已投递。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "agrimart.messaging.enabled", havingValue = "true")
@EnableScheduling
public class RabbitMessagingConfiguration {
    public static final String EXCHANGE = "agrimart.funds.v1";
    public static final String DLX = "agrimart.funds.dlx.v1";
    public static final String TRADE_QUEUE = "agrimart.trade.funds.v1";
    public static final String PAYMENT_QUEUE = "agrimart.payment.commands.v1";
    @Bean
    Declarables fundsTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue trade = queue(TRADE_QUEUE), payment = queue(PAYMENT_QUEUE);
        Queue tradeDead = QueueBuilder.durable(TRADE_QUEUE + ".dlq").quorum().build();
        Queue paymentDead = QueueBuilder.durable(PAYMENT_QUEUE + ".dlq").quorum().build();
        return new Declarables(exchange, dlx, trade, payment, tradeDead, paymentDead,
                BindingBuilder.bind(trade).to(exchange).with("payment.state.v1"),
                BindingBuilder.bind(trade).to(exchange).with("refund.state.v1"),
                BindingBuilder.bind(payment).to(exchange).with("payment.close.v1"),
                BindingBuilder.bind(payment).to(exchange).with("refund.execute.v1"),
                BindingBuilder.bind(tradeDead).to(dlx).with(TRADE_QUEUE),
                BindingBuilder.bind(paymentDead).to(dlx).with(PAYMENT_QUEUE));
    }
    private Queue queue(String name) {
        return QueueBuilder.durable(name).quorum().deadLetterExchange(DLX).deadLetterRoutingKey(name)
                .withArgument("x-overflow", "reject-publish")
                .withArgument("x-dead-letter-strategy", "at-least-once").build();
    }
    @Bean
    OutboxRelay outboxRelay(MessageStore store, RabbitTemplate rabbit) {
        rabbit.setMandatory(true);
        return new OutboxRelay(store, rabbit);
    }
    public static class OutboxRelay {
        private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
        private final MessageStore store;
        private final RabbitTemplate rabbit;
        public OutboxRelay(MessageStore store, RabbitTemplate rabbit) { this.store=store; this.rabbit=rabbit; }
        @Scheduled(fixedDelayString = "${agrimart.messaging.relay-delay:1000}")
        public void relay() {
            if (TransactionSynchronizationManager.isActualTransactionActive())
                throw new IllegalStateException("BROKER_PUBLISH_INSIDE_TRANSACTION");
            for (var pending : store.pending()) {
                if (!store.claim(pending)) continue;
                try {
                    MessageProperties props = new MessageProperties();
                    props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                    props.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    props.setMessageId(pending.id());
                    CorrelationData correlation = new CorrelationData(pending.id());
                    rabbit.send(EXCHANGE, pending.routingKey(), new Message(pending.payload().getBytes(StandardCharsets.UTF_8), props), correlation);
                    var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
                    if (!confirm.ack() || correlation.getReturned() != null)
                        throw new IllegalStateException("PUBLISH_NOT_ROUTED_OR_NACKED");
                    store.sent(pending);
                } catch (Exception failure) {
                    store.failed(pending, failure);
                    log.warn("Outbox delivery failed eventId={} attempts={}", pending.id(), pending.attempts()+1, failure);
                    if (failure instanceof InterruptedException) { Thread.currentThread().interrupt(); return; }
                }
            }
        }
    }
}
