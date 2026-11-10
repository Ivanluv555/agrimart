package edu.swu.agrimart.payment.payment.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 订单对应的支付记录与当前资金状态。 */
@Entity
@Table(
        name = "payments",
        uniqueConstraints = @UniqueConstraint(name = "uk_payment_order", columnNames = "orderId"))
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(nullable = false, unique = true)
    String paymentNo;

    @Column(nullable = false)
    Long orderId;

    @Column(nullable = false)
    Long buyerId;

    @Column(nullable = false, precision = 10, scale = 2)
    BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    Status status = Status.INITIATING;

    @Column(nullable = false)
    OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(30);

    /** 支付记录的资金状态。 */
    public enum Status {
        INITIATING,
        PENDING,
        SUCCEEDED,
        CLOSED,
        FAILED,
        UNKNOWN
    }

    /**
     * 读取支付业务号。
     *
     * @return 支付业务号
     */
    public String getPaymentNo() {
        return paymentNo;
    }

    /**
     * 设置支付业务号。
     *
     * @param v 待设置的字段值
     */
    public void setPaymentNo(String v) {
        paymentNo = v;
    }

    /**
     * 读取订单标识。
     *
     * @return 订单标识
     */
    public Long getOrderId() {
        return orderId;
    }

    /**
     * 设置订单标识。
     *
     * @param v 待设置的字段值
     */
    public void setOrderId(Long v) {
        orderId = v;
    }

    /**
     * 读取买家标识。
     *
     * @return 买家标识
     */
    public Long getBuyerId() {
        return buyerId;
    }

    /**
     * 设置买家标识。
     *
     * @param v 待设置的字段值
     */
    public void setBuyerId(Long v) {
        buyerId = v;
    }

    /**
     * 读取支付金额。
     *
     * @return 支付金额
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * 设置支付金额。
     *
     * @param v 待设置的字段值
     */
    public void setAmount(BigDecimal v) {
        amount = v;
    }

    /**
     * 读取业务状态。
     *
     * @return 业务状态
     */
    public Status getStatus() {
        return status;
    }

    /**
     * 设置业务状态。
     *
     * @param v 待设置的字段值
     */
    public void setStatus(Status v) {
        status = v;
    }

    /**
     * 读取到期时间。
     *
     * @return 到期时间
     */
    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }
}
