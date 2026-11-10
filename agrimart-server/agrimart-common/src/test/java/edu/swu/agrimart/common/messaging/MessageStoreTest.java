package edu.swu.agrimart.common.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.messaging.config.RabbitMessagingConfiguration.OutboxRelay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;
import org.junit.jupiter.api.AfterEach;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证本地事务、幂等/乱序、投递确认及失败恢复，不需要 RabbitMQ。 */
class MessageStoreTest {
    EmbeddedDatabase database;
    JdbcTemplate jdbc;
    MessageStore store;
    TransactionTemplate tx;
    @BeforeEach
    void setup() {
        database=new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2)
                .addScript("db/messaging-schema.sql").build();
        jdbc=new JdbcTemplate(database); store=new MessageStore(jdbc);
        tx=new TransactionTemplate(new DataSourceTransactionManager(database));
    }
    @AfterEach
    void cleanup() { database.shutdown(); }
    FundsEvent fact(long version) {
        return FundsEvent.of(FundsEvent.Kind.PAYMENT_STATE,"P1",version,1L,"P1",null,new BigDecimal("12.30"),
                "SUCCEEDED","provider-1",OffsetDateTime.now());
    }
    @Test
    void outboxAndInboxRollbackWithBusinessFailure() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> { store.append(fact(1)); throw new IllegalStateException("rollback"); }))
                .hasMessage("rollback");
        assertThat(store.pending()).isEmpty();
        var event=fact(1);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> store.consume("test",event,() -> {
            store.append(fact(2)); throw new IllegalStateException("rollback");
        }))).hasMessage("rollback");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_inbox",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_inbox_stream",Long.class)).isZero();
        assertThat(store.pending()).isEmpty();
    }
    @Test
    void deduplicatesEventIdsAndIgnoresOlderAggregateVersions() {
        AtomicInteger applied=new AtomicInteger(); var newest=fact(3);
        tx.executeWithoutResult(s -> store.consume("test",newest,applied::incrementAndGet));
        tx.executeWithoutResult(s -> store.consume("test",newest,applied::incrementAndGet));
        tx.executeWithoutResult(s -> store.consume("test",fact(1),applied::incrementAndGet));
        assertThat(applied).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_inbox",Long.class)).isEqualTo(2);
    }
    @Test
    void leasesPreventDoubleClaimAndRecoverAfterProcessCrash() {
        tx.executeWithoutResult(s -> store.append(fact(1)));
        var pending=store.pending().getFirst();
        assertThat(store.claim(pending)).isTrue(); assertThat(store.claim(pending)).isFalse();
        jdbc.update("UPDATE mq_outbox SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00'");
        var replacement=store.pending().getFirst(); assertThat(store.claim(replacement)).isTrue();
        store.sent(pending); assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_outbox WHERE sent_at IS NOT NULL",Long.class)).isZero();
        store.sent(replacement); assertThat(store.pending()).isEmpty();
    }
    @Test
    void publisherRequiresPositiveConfirmAndNoReturn() {
        var rabbit=mock(RabbitTemplate.class);
        tx.executeWithoutResult(s -> store.append(fact(1)));
        doAnswer(call -> { CorrelationData data=call.getArgument(3); data.getFuture().complete(new CorrelationData.Confirm(true,null)); return null; })
                .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        new OutboxRelay(store,rabbit).relay();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_outbox WHERE sent_at IS NOT NULL",Long.class)).isEqualTo(1);
    }
    @Test
    void nackAndUnroutableMessagesRemainInOutbox() {
        var rabbit=mock(RabbitTemplate.class);
        tx.executeWithoutResult(s -> store.append(fact(1)));
        doAnswer(call -> { CorrelationData data=call.getArgument(3); data.getFuture().complete(new CorrelationData.Confirm(false,"nack")); return null; })
                .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        var relay=new OutboxRelay(store,rabbit); relay.relay();
        assertThat(jdbc.queryForObject("SELECT attempts FROM mq_outbox",Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE mq_outbox SET next_attempt_at=TIMESTAMP '2000-01-01 00:00:00'");
        doAnswer(call -> { CorrelationData data=call.getArgument(3);
            data.setReturned(new ReturnedMessage(call.getArgument(2),312,"NO_ROUTE","exchange","key"));
            data.getFuture().complete(new CorrelationData.Confirm(true,null)); return null; })
                .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        relay.relay();
        assertThat(jdbc.queryForObject("SELECT attempts FROM mq_outbox",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mq_outbox WHERE sent_at IS NOT NULL",Long.class)).isZero();
    }
    @Test
    void codecSupportsV1AndRejectsOversizedMessages() {
        var event=fact(1);
        assertThat(store.decode(store.encode(event).getBytes(java.nio.charset.StandardCharsets.UTF_8))).isEqualTo(event);
        assertThatThrownBy(() -> store.decode(new byte[MessageStore.MAX_BYTES+1])).hasMessage("MESSAGE_TOO_LARGE");
    }
}
