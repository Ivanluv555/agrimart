package edu.swu.agrimart.payment.payment.service;

import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.exception.ProviderMissing;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

/** 支付状态机及本地 Outbox；当前仅支持显式启用的 sandbox 供应商。 */
@Service
public class PaymentService {
    final PaymentRepository repo;
    final boolean configured;
    final MessageStore messages;
    final TradeAuthorizationClient trade;
    final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public PaymentService(PaymentRepository repo, @Value("${payment.provider.configured:false}") boolean configured,
            MessageStore messages, TradeAuthorizationClient trade, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.repo=repo; this.configured=configured; this.messages=messages; this.trade=trade; this.jdbc=jdbc;
    }
    @Transactional
    public Payment create(Long buyer, Long order, CreatePayment request) {
        if (!configured) throw new ProviderMissing();
        // 本地聚合锁与关单命令共用，覆盖 T01 绑定成功而支付库事务回滚的窗口。
        String no = "P" + UUID.nameUUIDFromBytes(("agrimart-order:"+order).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        messages.lock("payment-creation:"+no);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM payment_close_tombstones WHERE payment_no=?",Long.class,no)>0)
            throw new IllegalStateException("PAYMENT_ALREADY_CLOSED");
        var existing = repo.findByOrderId(order);
        if (existing.isPresent()) {
            if (!existing.get().getBuyerId().equals(buyer)) throw new NoSuchElementException("RESOURCE_NOT_FOUND");
            return existing.get();
        }
        var authorization = trade.authorize(order,buyer,no);
        if (!order.equals(authorization.orderId()) || !buyer.equals(authorization.buyerId())
                || !no.equals(authorization.paymentNo()) || !"CNY".equals(authorization.currency())
                || authorization.expiresAt() == null || authorization.expiresAt().isBefore(OffsetDateTime.now()))
            throw new IllegalStateException("INVALID_PAYMENT_AUTHORIZATION");
        BigDecimal amount = new BigDecimal(authorization.amount()).setScale(2);
        if (amount.signum()<=0 || (request.amount()!=null && amount.compareTo(new BigDecimal(request.amount()))!=0))
            throw new IllegalArgumentException("PAYMENT_AMOUNT_MISMATCH");
        Payment payment = new Payment();
        payment.setPaymentNo(no); payment.setOrderId(order); payment.setBuyerId(buyer);
        payment.setExpiresAt(authorization.expiresAt());
        payment.setAmount(amount); payment.setStatus(Payment.Status.PENDING);
        repo.save(payment);
        messages.append(fact(payment));
        return payment;
    }
    public Payment get(String no) {
        return repo.findByPaymentNo(no).orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
    }
    @Transactional
    public void close(String no) {
        Payment payment = repo.lock(no).orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
        // sandbox 的关单与回调由同一行锁串行化；真实通道必须先查单/关单确认，再调用状态归并。
        if (payment.getStatus()!=Payment.Status.SUCCEEDED && payment.getStatus()!=Payment.Status.CLOSED) {
            if (!configured) throw new ProviderMissing();
            payment.setStatus(Payment.Status.CLOSED); payment.advanceEventVersion();
        }
        messages.append(fact(payment));
    }
    @Transactional
    public void callback(String no, BigDecimal amount, String reference, OffsetDateTime paidAt) {
        if (!configured) throw new ProviderMissing();
        if (reference==null || reference.isBlank() || reference.length()>128 || paidAt==null)
            throw new IllegalArgumentException("MISSING_PROVIDER_FACT");
        Payment payment = repo.lock(no).orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
        if (payment.getAmount().compareTo(amount)!=0) throw new IllegalArgumentException("PAYMENT_AMOUNT_MISMATCH");
        if (payment.getStatus()==Payment.Status.SUCCEEDED) {
            if (!reference.equals(payment.getProviderTransactionNo())) throw new IllegalStateException("PAYMENT_FACT_CONFLICT");
            return;
        }
        // 延迟到达的已验签成功事实也必须记录；交易侧对已关单订单发起补偿退款。
        payment.setStatus(Payment.Status.SUCCEEDED); payment.advanceEventVersion();
        payment.setProviderTransactionNo(reference); payment.setPaidAt(paidAt);
        messages.append(fact(payment));
    }
    @Transactional
    public void closeCommand(FundsEvent command) {
        messages.lock("payment-creation:"+command.paymentNo());
        var payment=repo.findByPaymentNo(command.paymentNo());
        if (payment.isPresent()) {
            if (!command.orderId().equals(payment.get().getOrderId()) || payment.get().getAmount().compareTo(new BigDecimal(command.amount()))!=0)
                throw new IllegalArgumentException("PAYMENT_COMMAND_MISMATCH");
            close(command.paymentNo());
        } else {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM payment_close_tombstones WHERE payment_no=?",Long.class,command.paymentNo())==0)
                jdbc.update("INSERT INTO payment_close_tombstones(payment_no,order_id,amount) VALUES (?,?,?)",command.paymentNo(),command.orderId(),new BigDecimal(command.amount()));
            messages.append(state(command.paymentNo()));
        }
    }
    public FundsEvent state(String no) {
        var payment=repo.findByPaymentNo(no);
        if (payment.isPresent()) return fact(payment.get());
        return jdbc.query("SELECT order_id,amount FROM payment_close_tombstones WHERE payment_no=?",
                (rs,n)->FundsEvent.of(FundsEvent.Kind.PAYMENT_STATE,no,1,rs.getLong(1),no,null,rs.getBigDecimal(2),"CLOSED",null,null),no)
                .stream().findFirst().orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
    }
    public static FundsEvent fact(Payment payment) {
        return FundsEvent.of(FundsEvent.Kind.PAYMENT_STATE,payment.getPaymentNo(),payment.getEventVersion(),
                payment.getOrderId(),payment.getPaymentNo(),null,payment.getAmount(),payment.getStatus().name(),
                payment.getProviderTransactionNo(),payment.getPaidAt());
    }
}
