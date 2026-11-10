package edu.swu.agrimart.payment.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.messaging.config.RabbitMessagingConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.UUID;

/** 显式启用的真实 broker 验收；普通 mvn test/verify 不运行，也不需要 Docker。 */
@EnabledIfEnvironmentVariable(named="AGRIMART_RABBIT_INTEGRATION",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE,properties={
    "spring.config.import=optional:file:src/main/resources/application.yml",
    "spring.datasource.url=jdbc:h2:mem:rabbit-broker-check;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false",
    "agrimart.messaging.enabled=true", "payment.provider.configured=true",
    "spring.rabbitmq.port=${RABBITMQ_TEST_PORT:5673}", "spring.rabbitmq.virtual-host=/agrimart-check"
})
class RabbitFundsBrokerIT {
    @Autowired RabbitTemplate rabbit;
    @MockitoSpyBean MessageStore messages;
    @Autowired PaymentService payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Test
    void confirmsOutboxDeliveryAndRejectsPoisonMessageAfterBoundedRetries() throws Exception {
        String payment="P"+UUID.randomUUID();
        var event=FundsEvent.of(FundsEvent.Kind.CLOSE_PAYMENT,payment,1,999L,payment,null,new BigDecimal("12.30"),null,null,null);
        new TransactionTemplate(transactionManager).executeWithoutResult(s -> messages.append(event));
        long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (jdbc.queryForObject("SELECT COUNT(*) FROM mq_inbox WHERE event_id=?",Long.class,event.eventId())==0 && System.nanoTime()<deadline)
            Thread.sleep(50);
        assertThat(payments.state(payment).status()).isEqualTo("CLOSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_outbox WHERE event_id=? AND sent_at IS NOT NULL",Long.class,event.eventId())).isEqualTo(1);
        byte[] poison="invalid-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MessageProperties properties=new MessageProperties(); properties.setMessageId(UUID.randomUUID().toString());
        rabbit.send(RabbitMessagingConfiguration.EXCHANGE,"payment.close.v1",new Message(poison,properties));
        Message dead=rabbit.receive(RabbitMessagingConfiguration.PAYMENT_QUEUE+".dlq",15000);
        assertThat(dead).isNotNull(); assertThat(dead.getBody()).isEqualTo(poison);
        verify(messages,times(3)).decode(poison);
    }
}
