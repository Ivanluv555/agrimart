package edu.swu.agrimart.common.messaging.service;

import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 每个服务使用自己的数据源；业务、Outbox、Inbox 共用一个本地事务。 */
public class MessageStore {
    public static final int MAX_BYTES = 16384;
    private final JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
    public MessageStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public String encode(FundsEvent event) { return json.writeValueAsString(event); }
    public FundsEvent decode(byte[] body) {
        if (body.length > MAX_BYTES) throw new IllegalArgumentException("MESSAGE_TOO_LARGE");
        return json.readValue(body, FundsEvent.class);
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void append(FundsEvent event) {
        String payload = encode(event);
        if (payload.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IllegalArgumentException("MESSAGE_TOO_LARGE");
        jdbc.update("INSERT INTO mq_outbox(event_id,routing_key,payload,attempts,next_attempt_at,created_at) VALUES (?,?,?,0,?,?)",
                event.eventId(), event.kind().routingKey(), payload, now(), now());
    }

    /** 最新事实可跨版本归并，旧版本忽略；命令按各自聚合版本独立去重。并发首次建流冲突由整个消费事务重试。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void consume(String consumer, FundsEvent event, Runnable action) {
        String stream = consumer + ":" + event.kind() + ":" + event.aggregateId();
        lock(stream);
        Long version = jdbc.queryForObject("SELECT version FROM mq_inbox_stream WHERE stream_id=? FOR UPDATE", Long.class, stream);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM mq_inbox WHERE consumer=? AND event_id=?", Long.class, consumer, event.eventId()) > 0) return;
        if (event.version() > version) {
            action.run();
            jdbc.update("UPDATE mq_inbox_stream SET version=? WHERE stream_id=?", event.version(), stream);
        }
        jdbc.update("INSERT INTO mq_inbox(consumer,event_id,processed_at) VALUES (?,?,?)", consumer, event.eventId(), now());
    }

    /** 本地聚合互斥锁；首次并发建锁的唯一键冲突由调用方重试整个事务。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String stream) {
        if (jdbc.update("UPDATE mq_inbox_stream SET version=version WHERE stream_id=?", stream) == 0)
            jdbc.update("INSERT INTO mq_inbox_stream(stream_id,version) VALUES (?,0)", stream);
        jdbc.queryForObject("SELECT version FROM mq_inbox_stream WHERE stream_id=? FOR UPDATE", Long.class, stream);
    }

    public record Pending(String id, String routingKey, String payload, int attempts, String lease) {}
    public List<Pending> pending() {
        return jdbc.query("SELECT event_id,routing_key,payload,attempts FROM mq_outbox WHERE sent_at IS NULL AND next_attempt_at<=? ORDER BY created_at,event_id LIMIT 50",
                (rs,n) -> new Pending(rs.getString(1),rs.getString(2),rs.getString(3),rs.getInt(4),UUID.randomUUID().toString()), now());
    }
    public boolean claim(Pending p) {
        return jdbc.update("UPDATE mq_outbox SET lease_token=?,next_attempt_at=? WHERE event_id=? AND sent_at IS NULL AND next_attempt_at<=?",
                p.lease(), Timestamp.from(Instant.now().plusSeconds(60)), p.id(), now()) == 1;
    }
    public void sent(Pending p) {
        jdbc.update("UPDATE mq_outbox SET sent_at=?,lease_token=NULL,last_error=NULL WHERE event_id=? AND lease_token=?", now(),p.id(),p.lease());
    }
    public void failed(Pending p, Exception error) {
        String message = error.getClass().getSimpleName();
        long delay = Math.min(300, 1L << Math.min(8, p.attempts()));
        jdbc.update("UPDATE mq_outbox SET attempts=attempts+1,next_attempt_at=?,lease_token=NULL,last_error=? WHERE event_id=? AND lease_token=?",
                Timestamp.from(Instant.now().plusSeconds(delay)), message,p.id(),p.lease());
    }
    private static Timestamp now() { return Timestamp.from(Instant.now()); }
}
