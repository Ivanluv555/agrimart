package edu.swu.agrimart.payment.payment.service;

import edu.swu.agrimart.payment.payment.dto.CreatePayment;
import edu.swu.agrimart.payment.payment.entity.Payment;
import edu.swu.agrimart.payment.payment.exception.ProviderMissing;
import edu.swu.agrimart.payment.payment.repository.PaymentRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** 处理支付记录创建、复用、查询与关闭。 */
@Service
public class PaymentService {
    final PaymentRepository repo;
    final boolean configured;

    /**
     * 创建支付记录业务服务。
     *
     * @param r 支付记录仓储
     * @param c 是否已配置支付供应商
     */
    public PaymentService(
            PaymentRepository r, @Value("${payment.provider.configured:false}") boolean c) {
        repo = r;
        configured = c;
    }

    /**
     * 创建或复用订单支付记录，未配置供应商时拒绝发起。
     *
     * @param buyer 当前买家标识
     * @param order 业务订单标识
     * @param x 当前业务请求
     * @return 新建或复用的订单支付记录
     */
    @Transactional
    public Payment create(Long buyer, Long order, CreatePayment x) {
        if (!configured) throw new ProviderMissing();
        return repo.findByOrderId(order)
                .orElseGet(
                        () -> {
                            Payment p = new Payment();
                            p.setPaymentNo("P" + UUID.randomUUID().toString().replace("-", ""));
                            p.setBuyerId(buyer);
                            p.setOrderId(order);
                            p.setAmount(
                                    new java.math.BigDecimal(
                                            x.amount() == null ? "0.00" : x.amount()));
                            p.setStatus(Payment.Status.PENDING);
                            return repo.save(p);
                        });
    }

    /**
     * 按支付业务号查询记录，不存在时抛出资源缺失异常。
     *
     * @param no 业务编号
     * @return 对应的支付记录
     */
    public Payment get(String no) {
        return repo.findByPaymentNo(no)
                .orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
    }

    /**
     * 关闭指定支付记录并提交状态变更。
     *
     * @param no 业务编号
     */
    @Transactional
    public void close(String no) {
        get(no).setStatus(Payment.Status.CLOSED);
    }
}
