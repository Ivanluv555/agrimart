package edu.swu.agrimart.payment.payment.service;

import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.common.payment.dto.RefundState;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;
import edu.swu.agrimart.payment.payment.exception.ProviderMissing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** sandbox 退款执行记录。按 refundNo 永久唯一，重试查询同一记录，不产生新退款号。 */
@Service
public class RefundService {
    private final JdbcTemplate jdbc;
    private final PaymentRepository payments;
    private final MessageStore messages;
    private final boolean configured;
    private final TradeAuthorizationClient trade;
    public RefundService(JdbcTemplate jdbc, PaymentRepository payments, MessageStore messages,
            @Value("${payment.provider.configured:false}") boolean configured, TradeAuthorizationClient trade) {
        this.jdbc=jdbc; this.payments=payments; this.messages=messages; this.configured=configured; this.trade=trade;
    }
    @Transactional
    public void execute(FundsEvent command) {
        if (!configured) throw new ProviderMissing();
        var authorization=trade.authorizeRefund(command.refundNo());
        if (!command.refundNo().equals(authorization.refundNo()) || !command.paymentNo().equals(authorization.paymentNo())
                || !command.orderId().equals(authorization.orderId()) || !"CNY".equals(authorization.currency())
                || new BigDecimal(command.amount()).compareTo(new BigDecimal(authorization.amount()))!=0)
            throw new IllegalArgumentException("INVALID_REFUND_AUTHORIZATION");
        Payment payment=payments.lock(command.paymentNo()).orElseThrow();
        if (!payment.getOrderId().equals(command.orderId()) || payment.getStatus()!=Payment.Status.SUCCEEDED
                || payment.getAmount().compareTo(new BigDecimal(command.amount()))!=0)
            throw new IllegalArgumentException("REFUND_PAYMENT_MISMATCH");
        // 此队列仅允许交易服务写入，当前执行系统补偿全额退款。
        if (!command.refundNo().equals("R"+command.paymentNo())) throw new IllegalArgumentException("REFUND_NOT_AUTHORIZED");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM fund_refunds WHERE refund_no=?",Long.class,command.refundNo())==0) {
            jdbc.update("INSERT INTO fund_refunds(refund_no,payment_no,order_id,amount,status,version) VALUES (?,?,?,?,'PROCESSING',1)",
                    command.refundNo(),command.paymentNo(),command.orderId(),payment.getAmount());
        }
        // sandbox 接受请求后等待已验签退款回调；真实通道适配器必须先查后重试。
        messages.append(fact(get(command.refundNo())));
    }
    public RefundState get(String no) {
        return jdbc.query("SELECT * FROM fund_refunds WHERE refund_no=?",(rs,n) -> {
            var completed=rs.getTimestamp("completed_at");
            return new RefundState(rs.getString("refund_no"),rs.getString("payment_no"),rs.getLong("order_id"),
                    rs.getBigDecimal("amount").setScale(2).toPlainString(),"CNY",rs.getString("status"),rs.getLong("version"),
                    rs.getString("provider_reference"), completed==null ? null : completed.toInstant().atOffset(ZoneOffset.UTC));
        },no).stream().findFirst().orElseThrow(() -> new java.util.NoSuchElementException("RESOURCE_NOT_FOUND"));
    }
    @Transactional
    public void callback(String no, BigDecimal amount, String reference, OffsetDateTime completed) {
        if (!configured) throw new ProviderMissing();
        if (reference==null || reference.isBlank() || reference.length()>128 || completed==null)
            throw new IllegalArgumentException("MISSING_PROVIDER_FACT");
        // 和执行命令使用同一个支付聚合行锁，退款回调也不会与重试交叉覆盖。
        var before=get(no);
        payments.lock(before.paymentNo()).orElseThrow();
        var refund=get(no);
        if (new BigDecimal(refund.amount()).compareTo(amount)!=0) throw new IllegalArgumentException("REFUND_AMOUNT_MISMATCH");
        if ("SUCCEEDED".equals(refund.status())) {
            if (!reference.equals(refund.providerRefundNo())) throw new IllegalStateException("REFUND_FACT_CONFLICT");
            return;
        }
        jdbc.update("UPDATE fund_refunds SET status='SUCCEEDED',version=version+1,provider_reference=?,completed_at=? WHERE refund_no=?",
                reference,java.sql.Timestamp.from(completed.toInstant()),no);
        messages.append(fact(get(no)));
    }
    public static FundsEvent fact(RefundState refund) {
        return FundsEvent.of(FundsEvent.Kind.REFUND_STATE,refund.refundNo(),refund.version(),refund.orderId(),
                refund.paymentNo(),refund.refundNo(),new BigDecimal(refund.amount()),refund.status(),refund.providerRefundNo(),refund.refundedAt());
    }
}
