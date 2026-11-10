package edu.swu.agrimart.trade.order.service;

import edu.swu.agrimart.common.payment.dto.PaymentAuthorization;
import edu.swu.agrimart.common.payment.dto.PaymentEvent;
import edu.swu.agrimart.trade.order.dto.CreateOrder;
import edu.swu.agrimart.trade.order.dto.Item;
import edu.swu.agrimart.trade.order.entity.TradeOrder;
import edu.swu.agrimart.trade.order.repository.OrderRepository;
import edu.swu.agrimart.trade.product.entity.Product;
import edu.swu.agrimart.trade.product.repository.ProductRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;

/** 处理单商家结算、库存扣减、订单取消及支付事件。 */
@Service
public class OrderService {
    final ProductRepository products;
    final OrderRepository orders;
    final edu.swu.agrimart.common.messaging.service.MessageStore messages;
    final OrderFundsService funds;

    /**
     * 创建订单结算与状态管理服务。
     *
     * @param p 商品仓储
     * @param o 订单仓储
     */
    public OrderService(ProductRepository p, OrderRepository o,
            edu.swu.agrimart.common.messaging.service.MessageStore messages, OrderFundsService funds) {
        this.messages=messages;
        this.funds=funds;
        products = p;
        orders = o;
    }

    /**
     * 按服务端商品价格结算单商家订单并扣减库存。
     *
     * @param buyer 当前买家标识
     * @param x 当前业务请求
     * @return 已保存的结算订单
     */
    @Transactional
    public TradeOrder order(Long buyer, CreateOrder x) {
        Map<Long, Integer> q = new LinkedHashMap<>();
        for (Item i : x.items()) q.merge(i.productId(), i.quantity(), Integer::sum);
        BigDecimal total = BigDecimal.ZERO;
        Long merchant = null;
        for (var e : q.entrySet()) {
            Product p =
                    products.findById(e.getKey())
                            .orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
            if (p.getStatus() != Product.Status.ON_SALE || p.getStock() < e.getValue())
                throw new IllegalStateException("STOCK_INSUFFICIENT");
            if (merchant != null && !merchant.equals(p.getMerchantId()))
                throw new IllegalStateException("MULTI_MERCHANT_NOT_SUPPORTED");
            merchant = p.getMerchantId();
            p.setStock(p.getStock() - e.getValue());
            products.save(p);
            total = total.add(p.getPrice().multiply(BigDecimal.valueOf(e.getValue())));
        }
        TradeOrder o = new TradeOrder();
        o.setOrderNo("O" + UUID.randomUUID().toString().replace("-", ""));
        o.setBuyerId(buyer);
        o.setMerchantId(merchant);
        o.setTotalAmount(total);
        return orders.save(o);
    }

    /**
     * 按订单标识查询记录，不存在时抛出资源缺失异常。
     *
     * @param id 记录标识
     * @return 对应的订单记录
     */
    public TradeOrder get(Long id) {
        return orders.findById(id)
                .orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
    }

    /**
     * 校验订单归属及状态并取消待支付订单。
     *
     * @param buyer 当前买家标识
     * @param id 记录标识
     * @return 已取消的订单
     */
    @Transactional
    public TradeOrder cancel(Long buyer, Long id) {
        TradeOrder o = orders.lock(id).orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
        if (!o.getBuyerId().equals(buyer)) throw new NoSuchElementException("RESOURCE_NOT_FOUND");
        if (o.getStatus() != TradeOrder.Status.PENDING_PAYMENT)
            throw new IllegalStateException("STATE_CONFLICT");
        if (o.getPaymentNo()==null) o.setStatus(TradeOrder.Status.CANCELLED);
        else {
            o.setStatus(TradeOrder.Status.CLOSING);
            messages.append(edu.swu.agrimart.common.messaging.dto.FundsEvent.of(
                    edu.swu.agrimart.common.messaging.dto.FundsEvent.Kind.CLOSE_PAYMENT,
                    o.getPaymentNo(),o.getVersion()+1,id,o.getPaymentNo(),null,o.getTotalAmount(),null,null,null));
        }
        return o;
    }

    /**
     * 为待支付订单生成包含可信金额的支付授权。
     *
     * @param id 记录标识
     * @param request 请求参数或请求上下文
     * @return 包含订单归属、金额与有效期的支付授权
     */
    @Transactional
    public PaymentAuthorization authorize(Long id, Map<String, Object> request) {
        TradeOrder order = orders.lock(id).orElseThrow(() -> new NoSuchElementException("RESOURCE_NOT_FOUND"));
        String paymentNo = Objects.toString(request.get("paymentNo"), "");
        if (paymentNo.isBlank() || paymentNo.length()>64 || !String.valueOf(order.getBuyerId()).equals(String.valueOf(request.get("buyerId"))))
            throw new IllegalArgumentException("INVALID_PAYMENT_AUTHORIZATION");
        if (order.getPaymentNo()!=null && !order.getPaymentNo().equals(paymentNo))
            throw new IllegalStateException("PAYMENT_ALREADY_BOUND");
        if (order.getStatus() != TradeOrder.Status.PENDING_PAYMENT)
            throw new IllegalStateException("STATE_CONFLICT");
        order.setPaymentNo(paymentNo);
        return new PaymentAuthorization(
                "A" + UUID.randomUUID(),
                String.valueOf(request.get("paymentNo")),
                id,
                order.getBuyerId(),
                order.getMerchantId(),
                order.getTotalAmount().setScale(2).toPlainString(),
                "CNY",
                order.getCreatedAt().plusMinutes(30));
    }

    /**
     * 应用已支持的支付成功或关闭事件。
     *
     * @param id 记录标识
     * @param event 测试输入或业务依赖
     */
    @Transactional
    public void event(Long id, PaymentEvent event) {
        if (!id.equals(event.orderId())) throw new IllegalArgumentException("ORDER_ID_MISMATCH");
        funds.accept(new edu.swu.agrimart.common.messaging.dto.FundsEvent(1,event.eventId(),
                edu.swu.agrimart.common.messaging.dto.FundsEvent.Kind.PAYMENT_STATE,event.paymentNo(),event.version(),
                OffsetDateTime.now(),id,event.paymentNo(),null,event.amount(),event.currency(),event.status(),
                event.providerTransactionNo(),event.paidAt()));
    }
}
