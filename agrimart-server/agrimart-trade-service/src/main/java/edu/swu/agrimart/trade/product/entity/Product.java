package edu.swu.agrimart.trade.product.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 商品信息、可售状态及库存数量。 */
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ON_SALE;

    @Version private long version;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    /**
     * 返回商品创建时间，作为分页稳定排序依据。
     * @return 创建时间
     */
    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    /** 商品的销售状态。 */
    public enum Status {
        ON_SALE,
        OFF_SALE,
        OUT_OF_STOCK
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
     * 读取商品名称。
     *
     * @return 商品名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置商品名称。
     *
     * @param v 待设置的字段值
     */
    public void setName(String v) {
        name = v;
    }

    /**
     * 读取商品单价。
     *
     * @return 商品单价
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 设置商品单价。
     *
     * @param v 待设置的字段值
     */
    public void setPrice(BigDecimal v) {
        price = v;
    }

    /**
     * 读取可用库存。
     *
     * @return 可用库存
     */
    public Integer getStock() {
        return stock;
    }

    /**
     * 设置可用库存。
     *
     * @param v 待设置的字段值
     */
    public void setStock(Integer v) {
        stock = v;
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
}
