package edu.swu.agrimart.trade.order.service;

import edu.swu.agrimart.common.api.dto.Api;
import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.payment.dto.RefundState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import java.math.BigDecimal;
import java.time.Duration;

/** P03/P04 对账独立于 MQ 启停，可修复漏消息、DLQ 和 broker 长时间中断造成的投影滞后。 */
@Component
@EnableScheduling
@ConditionalOnProperty(name="agrimart.reconciliation.enabled",havingValue="true")
public class FundsReconciliation {
    private static final Logger log=LoggerFactory.getLogger(FundsReconciliation.class);
    private final JdbcTemplate jdbc;
    private final OrderFundsService funds;
    private final MessageStore messages;
    private final RestClient http;
    private final TransactionTemplate tx;
    private long cursor;
    public FundsReconciliation(JdbcTemplate jdbc, OrderFundsService funds, MessageStore messages,
            PlatformTransactionManager transactionManager,
            @Value("${agrimart.payment-url:http://localhost:8083}") String url,
            @Value("${agrimart.service-token:dev-service-token}") String token) {
        this.jdbc=jdbc; this.funds=funds; this.messages=messages; this.tx=new TransactionTemplate(transactionManager);
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3)); factory.setReadTimeout(Duration.ofSeconds(5));
        http=RestClient.builder().baseUrl(url).requestFactory(factory).defaultHeader("X-Service-Token",token).build();
    }
    private record Candidate(long id,String payment,long version,String status,BigDecimal amount) {}
    @Scheduled(fixedDelayString="${agrimart.reconciliation.delay:30000}")
    public void reconcile() {
        var batch=jdbc.query("SELECT id,payment_no,payment_version,status,total_amount FROM orders WHERE payment_no IS NOT NULL AND id>? ORDER BY id LIMIT 50",
                (rs,n)->new Candidate(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getString(4),rs.getBigDecimal(5)),cursor);
        if (batch.isEmpty()) { cursor=0; return; }
        for (var order:batch) {
            try {
                var result=http.get().uri("/internal/payments/{no}",order.payment()).retrieve()
                        .body(new ParameterizedTypeReference<Api.Result<FundsEvent>>() {});
                if (result==null || result.data()==null) throw new IllegalStateException("MISSING_P03_STATE");
                FundsEvent state=result.data();
                if (state.kind()!=FundsEvent.Kind.PAYMENT_STATE || !order.payment().equals(state.paymentNo()) || state.orderId()!=order.id())
                    throw new IllegalArgumentException("P03_IDENTITY_MISMATCH");
                if (state.version()>order.version()) funds.accept(state);
                if ("CLOSING".equals(order.status()) && !"SUCCEEDED".equals(state.status()) && !"CLOSED".equals(state.status())) {
                    tx.executeWithoutResult(ignored -> messages.append(FundsEvent.of(FundsEvent.Kind.CLOSE_PAYMENT,
                            order.payment(),1,order.id(),order.payment(),null,order.amount(),null,null,null)));
                }
                reconcileRefund(order);
            } catch (HttpClientErrorException.NotFound missing) {
                if ("CLOSING".equals(order.status())) {
                    tx.executeWithoutResult(ignored -> messages.append(FundsEvent.of(FundsEvent.Kind.CLOSE_PAYMENT,
                            order.payment(),1,order.id(),order.payment(),null,order.amount(),null,null,null)));
                } else log.warn("P03 payment not found orderId={} paymentNo={}",order.id(),order.payment());
            } catch (RuntimeException failure) {
                log.warn("Funds reconciliation failed orderId={} paymentNo={}",order.id(),order.payment(),failure);
            }
            cursor=order.id();
        }
        if (batch.size()<50) cursor=0;
    }
    private void reconcileRefund(Candidate order) {
        var refunds=jdbc.queryForList("SELECT refund_no,version FROM fund_refunds WHERE order_id=?",order.id());
        for (var local:refunds) {
            String no=(String)local.get("refund_no");
            try {
                var result=http.get().uri("/internal/refunds/{no}",no).retrieve()
                        .body(new ParameterizedTypeReference<Api.Result<RefundState>>() {});
                if (result==null || result.data()==null) throw new IllegalStateException("MISSING_P04_STATE");
                RefundState state=result.data();
                if (!no.equals(state.refundNo()) || !order.payment().equals(state.paymentNo()) || state.orderId()!=order.id())
                    throw new IllegalArgumentException("P04_IDENTITY_MISMATCH");
                if (state.version()>((Number)local.get("version")).longValue()) {
                    funds.accept(FundsEvent.of(FundsEvent.Kind.REFUND_STATE,no,state.version(),order.id(),state.paymentNo(),no,
                            new BigDecimal(state.amount()),state.status(),state.providerRefundNo(),state.refundedAt()));
                }
            } catch (HttpClientErrorException.NotFound missing) {
                // P04 尚无执行记录：恢复已在本库自动授权的同号命令，不生成新退款。
                tx.executeWithoutResult(ignored -> messages.append(FundsEvent.of(FundsEvent.Kind.EXECUTE_REFUND,no,1,
                        order.id(),order.payment(),no,order.amount(),null,null,null)));
            }
        }
    }
}
