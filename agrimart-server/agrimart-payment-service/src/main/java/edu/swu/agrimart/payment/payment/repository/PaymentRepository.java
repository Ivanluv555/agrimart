package edu.swu.agrimart.payment.payment.repository;

import edu.swu.agrimart.payment.payment.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

/** 支付持久化仓储，支持按支付号和订单标识查询。 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.paymentNo = :key")
    Optional<Payment> lock(@org.springframework.data.repository.query.Param("key") String key);

    /**
     * 按支付业务号查找支付记录。
     *
     * @param no 业务编号
     * @return 查询结果，记录不存在时为空
     */
    Optional<Payment> findByPaymentNo(String no);

    /**
     * 按订单标识查找对应支付记录。
     *
     * @param id 记录标识
     * @return 查询结果，记录不存在时为空
     */
    Optional<Payment> findByOrderId(Long id);
}
