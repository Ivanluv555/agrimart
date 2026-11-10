package edu.swu.agrimart.trade.order.service;

import edu.swu.agrimart.common.messaging.dto.FundsEvent;
import edu.swu.agrimart.common.messaging.service.MessageStore;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.repository.OrderRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

/** 归并资金事实。成功与关单竞态通过订单行锁、聚合版本和同号退款处理。 */
@Service
public class OrderFundsService {
    private final OrderRepository orders;
    private final MessageStore messages;
    private final JdbcTemplate jdbc;
    public OrderFundsService(OrderRepository orders, MessageStore messages, JdbcTemplate jdbc) {
        this.orders=orders; this.messages=messages; this.jdbc=jdbc;
    }
    @Transactional
    public void accept(FundsEvent event) {
        if (event.kind()!=FundsEvent.Kind.PAYMENT_STATE && event.kind()!=FundsEvent.Kind.REFUND_STATE)
            throw new IllegalArgumentException("UNSUPPORTED_TRADE_EVENT");
        messages.consume("trade-funds",event,() -> merge(event));
    }
    private void merge(FundsEvent event) {
        TradeOrder order = orders.lock(event.orderId()).orElseThrow();
        if (!event.paymentNo().equals(order.getPaymentNo())
                || order.getTotalAmount().compareTo(new BigDecimal(event.amount()))!=0)
            throw new IllegalArgumentException("PAYMENT_BINDING_OR_AMOUNT_MISMATCH");
        if (event.kind()==FundsEvent.Kind.REFUND_STATE) {
            int changed=jdbc.update("UPDATE fund_refunds SET status=?,version=?,provider_reference=?,completed_at=? WHERE refund_no=? AND payment_no=? AND order_id=? AND version<?",
                    event.status(),event.version(),event.providerReference(),event.completedAt()==null?null:java.sql.Timestamp.from(event.completedAt().toInstant()),event.refundNo(),event.paymentNo(),event.orderId(),event.version());
            if (changed==0 && jdbc.queryForObject("SELECT COUNT(*) FROM fund_refunds WHERE refund_no=? AND payment_no=? AND order_id=?",Long.class,event.refundNo(),event.paymentNo(),event.orderId())==0)
                throw new IllegalArgumentException("REFUND_NOT_AUTHORIZED");
            if (changed>0 && "SUCCEEDED".equals(event.status())) order.setStatus(TradeOrder.Status.REFUNDED);
            return;
        }
        if (event.version()<=order.getPaymentVersion()) return;
        order.setPaymentVersion(event.version());
        if ("SUCCEEDED".equals(event.status())) {
            if (order.getStatus()==TradeOrder.Status.CLOSING || order.getStatus()==TradeOrder.Status.CANCELLED) compensate(order,event);
            else if (order.getStatus()==TradeOrder.Status.PENDING_PAYMENT) order.setStatus(TradeOrder.Status.PAID);
        } else if ("CLOSED".equals(event.status()) && (order.getStatus()==TradeOrder.Status.PENDING_PAYMENT || order.getStatus()==TradeOrder.Status.CLOSING)) {
            order.setStatus(TradeOrder.Status.CANCELLED);
        }
    }
    private void compensate(TradeOrder order, FundsEvent event) {
        String refund = "R" + event.paymentNo();
        if (jdbc.queryForObject("SELECT COUNT(*) FROM fund_refunds WHERE refund_no=?",Long.class,refund)==0) {
            jdbc.update("INSERT INTO fund_refunds(refund_no,payment_no,order_id,amount,status,version) VALUES (?,?,?,?,'PROCESSING',0)",
                    refund,event.paymentNo(),event.orderId(),order.getTotalAmount());
            messages.append(FundsEvent.of(FundsEvent.Kind.EXECUTE_REFUND,refund,1,event.orderId(),event.paymentNo(),refund,
                    order.getTotalAmount(),null,null,null));
        }
        order.setStatus(TradeOrder.Status.REFUNDING);
    }
}
