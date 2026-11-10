package edu.swu.agrimart.payment.payment.repository;

import edu.swu.agrimart.payment.payment.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

/** 支付持久化仓储，支持按支付号和订单标识查询。 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {
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
