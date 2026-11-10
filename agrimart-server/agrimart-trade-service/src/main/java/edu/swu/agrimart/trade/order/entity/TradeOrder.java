package edu.swu.agrimart.trade.order.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 单商家订单、应付金额及履约状态。 */
@Entity
@Table(
        name = "orders",
        uniqueConstraints = @UniqueConstraint(name = "uk_order_no", columnNames = "orderNo"))
public class TradeOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderNo;

    @Column(nullable = false)
    private Long buyerId;

    @Column(nullable = false)
    private Long merchantId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING_PAYMENT;

    @Column(nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Version
    private long version;
    private String paymentNo;
    @Column(nullable = false)
    private long paymentVersion;
    public String getPaymentNo() { return paymentNo; }
    public void setPaymentNo(String value) { paymentNo = value; }
    public long getPaymentVersion() { return paymentVersion; }
    public void setPaymentVersion(long value) { paymentVersion = value; }
    public long getVersion() { return version; }

    /** 订单的履约状态。 */
    public enum Status {
        PENDING_PAYMENT,
        CLOSING,
        PAID,
        SHIPPED,
        COMPLETED,
        CANCELLED,
        REFUNDING,
        REFUNDED
    }

    /**
     * 读取记录标识。
     *
     * @return 记录标识
     */
    public Long getId() {
        return id;
    }

    /**
     * 读取业务订单号。
     *
     * @return 业务订单号
     */
    public String getOrderNo() {
        return orderNo;
    }

    /**
     * 设置业务订单号。
     *
     * @param v 待设置的字段值
     */
    public void setOrderNo(String v) {
        orderNo = v;
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
     * 读取商家标识。
     *
     * @return 商家标识
     */
    public Long getMerchantId() {
        return merchantId;
    }

    /**
     * 设置商家标识。
     *
     * @param v 待设置的字段值
     */
    public void setMerchantId(Long v) {
        merchantId = v;
    }

    /**
     * 读取订单总金额。
     *
     * @return 对应业务字段
     */
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * 设置订单总金额。
     *
     * @param v 待设置的字段值
     */
    public void setTotalAmount(BigDecimal v) {
        totalAmount = v;
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
     * 读取创建时间。
     *
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
